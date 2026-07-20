package br.com.nexo.driver.capture.performance

import android.content.Context

/**
 * Privacy-preserving runtime evidence for the capture-to-overlay performance budget.
 *
 * This deliberately persists only aggregate timings from the current rolling window. It never
 * contains a bitmap, OCR block, passenger detail, address, ride value, or the text used to
 * recognize an offer. Keeping the final aggregate after a session ends lets a developer inspect
 * the observed p95 and worst case without retaining any offer content.
 */
data class CaptureLatencyDiagnostics(
    val isSessionActive: Boolean,
    val updatedAtEpochMillis: Long?,
    val snapshot: OfferResponseLatencySnapshot,
) {
    val hasMeasurements: Boolean
        get() = snapshot.sampleCount > 0

    /** Empty data must never be interpreted as proof that the one-second budget was met. */
    val targetMetInObservedWindow: Boolean
        get() = hasMeasurements && snapshot.meetsTarget

    /**
     * The nearest-rank p95 is mathematically available with fewer samples, but 20 observations
     * are required before presenting it as a useful runtime signal.
     */
    val hasRepresentativeP95: Boolean
        get() = snapshot.sampleCount >= MINIMUM_REPRESENTATIVE_SAMPLE_COUNT

    companion object {
        const val MINIMUM_REPRESENTATIVE_SAMPLE_COUNT = 20

        fun empty(isSessionActive: Boolean = false): CaptureLatencyDiagnostics =
            CaptureLatencyDiagnostics(
                isSessionActive = isSessionActive,
                updatedAtEpochMillis = null,
                snapshot = OfferResponseLatencySnapshot.empty(
                    targetMillis = OfferResponseLatencyTracker.DEFAULT_TARGET_MILLIS,
                    windowSize = OfferResponseLatencyTracker.DEFAULT_WINDOW_SIZE,
                ),
            )
    }
}

/**
 * Small local store used by the foreground service. Values are aggregate numeric diagnostics
 * only; no frame or recognized offer survives the call that produced it.
 */
class CaptureLatencyDiagnosticsStore private constructor(
    private val context: Context,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun startNewSession(): CaptureLatencyDiagnostics =
        save(CaptureLatencyDiagnostics.empty(isSessionActive = true))

    fun record(snapshot: OfferResponseLatencySnapshot): CaptureLatencyDiagnostics = save(
        CaptureLatencyDiagnostics(
            isSessionActive = true,
            updatedAtEpochMillis = nowEpochMillis(),
            snapshot = snapshot,
        ),
    )

    fun finishSession(): CaptureLatencyDiagnostics {
        val current = load()
        return save(
            current.copy(
                isSessionActive = false,
                updatedAtEpochMillis = current.updatedAtEpochMillis ?: nowEpochMillis(),
            ),
        )
    }

    fun load(): CaptureLatencyDiagnostics = CaptureLatencyDiagnosticsCodec.decode(
        mapOf(
            CaptureLatencyDiagnosticsCodec.KEY_SCHEMA_VERSION to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_SCHEMA_VERSION,
                CaptureLatencyDiagnosticsCodec.SCHEMA_VERSION,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_SESSION_ACTIVE to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_SESSION_ACTIVE,
                0L,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_UPDATED_AT_EPOCH_MILLIS to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_UPDATED_AT_EPOCH_MILLIS,
                CaptureLatencyDiagnosticsCodec.UNSET,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_TARGET_MILLIS to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_TARGET_MILLIS,
                OfferResponseLatencyTracker.DEFAULT_TARGET_MILLIS,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_WINDOW_SIZE to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_WINDOW_SIZE,
                OfferResponseLatencyTracker.DEFAULT_WINDOW_SIZE.toLong(),
            ),
            CaptureLatencyDiagnosticsCodec.KEY_SAMPLE_COUNT to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_SAMPLE_COUNT,
                0L,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_LATEST_MILLIS to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_LATEST_MILLIS,
                CaptureLatencyDiagnosticsCodec.UNSET,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_P50_MILLIS to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_P50_MILLIS,
                CaptureLatencyDiagnosticsCodec.UNSET,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_P95_MILLIS to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_P95_MILLIS,
                CaptureLatencyDiagnosticsCodec.UNSET,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_MAX_MILLIS to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_MAX_MILLIS,
                CaptureLatencyDiagnosticsCodec.UNSET,
            ),
            CaptureLatencyDiagnosticsCodec.KEY_VIOLATION_COUNT to preferences.getLong(
                CaptureLatencyDiagnosticsCodec.KEY_VIOLATION_COUNT,
                0L,
            ),
        ),
    )

    private fun save(diagnostics: CaptureLatencyDiagnostics): CaptureLatencyDiagnostics {
        val values = CaptureLatencyDiagnosticsCodec.encode(diagnostics)
        preferences.edit().apply {
            values.forEach { (key, value) -> putLong(key, value) }
        }.commit()
        return diagnostics
    }

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    companion object {
        private const val PREFERENCES = "capture_latency_diagnostics"

        fun create(context: Context): CaptureLatencyDiagnosticsStore =
            CaptureLatencyDiagnosticsStore(context.applicationContext)
    }
}

/** Pure codec so diagnostic persistence can be verified on the JVM without Android storage. */
internal object CaptureLatencyDiagnosticsCodec {
    const val SCHEMA_VERSION = 1L
    const val UNSET = -1L

    const val KEY_SCHEMA_VERSION = "schema_version"
    const val KEY_SESSION_ACTIVE = "session_active"
    const val KEY_UPDATED_AT_EPOCH_MILLIS = "updated_at_epoch_millis"
    const val KEY_TARGET_MILLIS = "target_millis"
    const val KEY_WINDOW_SIZE = "window_size"
    const val KEY_SAMPLE_COUNT = "sample_count"
    const val KEY_LATEST_MILLIS = "latest_millis"
    const val KEY_P50_MILLIS = "p50_millis"
    const val KEY_P95_MILLIS = "p95_millis"
    const val KEY_MAX_MILLIS = "max_millis"
    const val KEY_VIOLATION_COUNT = "violation_count"

    fun encode(diagnostics: CaptureLatencyDiagnostics): Map<String, Long> = buildMap {
        put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        put(KEY_SESSION_ACTIVE, if (diagnostics.isSessionActive) 1L else 0L)
        put(KEY_UPDATED_AT_EPOCH_MILLIS, diagnostics.updatedAtEpochMillis ?: UNSET)
        put(KEY_TARGET_MILLIS, diagnostics.snapshot.targetMillis)
        put(KEY_WINDOW_SIZE, diagnostics.snapshot.windowSize.toLong())
        put(KEY_SAMPLE_COUNT, diagnostics.snapshot.sampleCount.toLong())
        put(KEY_LATEST_MILLIS, diagnostics.snapshot.latestMillis ?: UNSET)
        put(KEY_P50_MILLIS, diagnostics.snapshot.p50Millis ?: UNSET)
        put(KEY_P95_MILLIS, diagnostics.snapshot.p95Millis ?: UNSET)
        put(KEY_MAX_MILLIS, diagnostics.snapshot.maxMillis ?: UNSET)
        put(KEY_VIOLATION_COUNT, diagnostics.snapshot.violationCount.toLong())
    }

    fun decode(values: Map<String, Long>): CaptureLatencyDiagnostics {
        val targetMillis = values[KEY_TARGET_MILLIS]
            ?.takeIf { it > 0L }
            ?: OfferResponseLatencyTracker.DEFAULT_TARGET_MILLIS
        val windowSize = values[KEY_WINDOW_SIZE]
            ?.takeIf { it > 0L && it <= Int.MAX_VALUE }
            ?.toInt()
            ?: OfferResponseLatencyTracker.DEFAULT_WINDOW_SIZE
        val sampleCount = values[KEY_SAMPLE_COUNT]
            ?.coerceIn(0L, windowSize.toLong())
            ?.toInt()
            ?: 0
        val latestMillis = values[KEY_LATEST_MILLIS].asLatencyOrNull()
        val p50Millis = values[KEY_P50_MILLIS].asLatencyOrNull()
        val p95Millis = values[KEY_P95_MILLIS].asLatencyOrNull()
        val maxMillis = values[KEY_MAX_MILLIS].asLatencyOrNull()
        val allMeasurementsPresent = listOf(latestMillis, p50Millis, p95Millis, maxMillis).all { it != null }
        val orderedPercentiles = p50Millis != null && p95Millis != null && maxMillis != null &&
            p50Millis <= p95Millis && p95Millis <= maxMillis && latestMillis!! <= maxMillis

        val validSampleCount = if (sampleCount > 0 && allMeasurementsPresent && orderedPercentiles) {
            sampleCount
        } else {
            0
        }
        val snapshot = if (validSampleCount == 0) {
            OfferResponseLatencySnapshot.empty(targetMillis, windowSize)
        } else {
            OfferResponseLatencySnapshot(
                targetMillis = targetMillis,
                windowSize = windowSize,
                sampleCount = validSampleCount,
                latestMillis = latestMillis,
                p50Millis = p50Millis,
                p95Millis = p95Millis,
                maxMillis = maxMillis,
                violationCount = values[KEY_VIOLATION_COUNT]
                    ?.coerceIn(0L, validSampleCount.toLong())
                    ?.toInt()
                    ?: 0,
            )
        }
        return CaptureLatencyDiagnostics(
            isSessionActive = values[KEY_SESSION_ACTIVE] == 1L,
            updatedAtEpochMillis = values[KEY_UPDATED_AT_EPOCH_MILLIS]
                ?.takeIf { it >= 0L },
            snapshot = snapshot,
        )
    }

    private fun Long?.asLatencyOrNull(): Long? = this?.takeIf { it >= 0L }
}
