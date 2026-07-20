package br.com.nexo.driver.capture.performance

import kotlin.math.ceil

/**
 * Measures the elapsed time from a captured offer frame to the moment its overlay is displayed.
 *
 * Timestamps should come from a monotonic clock (for example, `SystemClock.elapsedRealtime()`),
 * rather than wall-clock time. The tracker retains only the newest [windowSize] samples so a
 * temporary slow device or session does not permanently skew operational diagnostics.
 */
class OfferResponseLatencyTracker(
    private val targetMillis: Long = DEFAULT_TARGET_MILLIS,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private val samples = ArrayDeque<Long>()

    init {
        require(targetMillis > 0) { "targetMillis must be greater than zero" }
        require(windowSize > 0) { "windowSize must be greater than zero" }
    }

    /** Adds a completed frame-to-overlay measurement and returns the refreshed rolling snapshot. */
    @Synchronized
    fun record(frameReceivedAtMillis: Long, overlayShownAtMillis: Long): OfferResponseLatencySnapshot {
        require(overlayShownAtMillis >= frameReceivedAtMillis) {
            "overlayShownAtMillis must not be earlier than frameReceivedAtMillis"
        }
        samples.addLast(overlayShownAtMillis - frameReceivedAtMillis)
        if (samples.size > windowSize) samples.removeFirst()
        return snapshotLocked()
    }

    /** Convenience overload for the normal path where the overlay has just been rendered. */
    fun recordOverlayShown(frameReceivedAtMillis: Long): OfferResponseLatencySnapshot =
        record(frameReceivedAtMillis, nowMillis())

    /** Returns a consistent view of the current rolling window without recording a new value. */
    @Synchronized
    fun snapshot(): OfferResponseLatencySnapshot = snapshotLocked()

    @Synchronized
    fun clear(): OfferResponseLatencySnapshot {
        samples.clear()
        return snapshotLocked()
    }

    private fun snapshotLocked(): OfferResponseLatencySnapshot {
        if (samples.isEmpty()) {
            return OfferResponseLatencySnapshot.empty(targetMillis = targetMillis, windowSize = windowSize)
        }

        val ordered = samples.sorted()
        val violationCount = samples.count { it > targetMillis }
        return OfferResponseLatencySnapshot(
            targetMillis = targetMillis,
            windowSize = windowSize,
            sampleCount = samples.size,
            latestMillis = samples.last(),
            p50Millis = percentile(ordered, 0.50),
            p95Millis = percentile(ordered, 0.95),
            maxMillis = ordered.last(),
            violationCount = violationCount,
        )
    }

    private fun percentile(sorted: List<Long>, percentile: Double): Long {
        val nearestRankIndex = ceil(percentile * sorted.size).toInt().coerceAtLeast(1) - 1
        return sorted[nearestRankIndex]
    }

    companion object {
        const val DEFAULT_TARGET_MILLIS: Long = 1_000
        const val DEFAULT_WINDOW_SIZE: Int = 100
        private const val NANOS_PER_MILLI: Long = 1_000_000
    }
}

/** Immutable rolling diagnostics for the offer-response performance budget. */
data class OfferResponseLatencySnapshot(
    val targetMillis: Long,
    val windowSize: Int,
    val sampleCount: Int,
    val latestMillis: Long?,
    val p50Millis: Long?,
    val p95Millis: Long?,
    val maxMillis: Long?,
    val violationCount: Int,
) {
    /** Samples over the target. A result equal to the target still meets the one-second goal. */
    val violationRate: Double
        get() = if (sampleCount == 0) 0.0 else violationCount.toDouble() / sampleCount

    val meetsTarget: Boolean
        get() = violationCount == 0

    companion object {
        fun empty(targetMillis: Long, windowSize: Int) = OfferResponseLatencySnapshot(
            targetMillis = targetMillis,
            windowSize = windowSize,
            sampleCount = 0,
            latestMillis = null,
            p50Millis = null,
            p95Millis = null,
            maxMillis = null,
            violationCount = 0,
        )
    }
}
