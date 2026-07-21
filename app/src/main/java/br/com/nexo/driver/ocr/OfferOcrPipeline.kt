package br.com.nexo.driver.ocr

import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.parser.OfferParserRegistry
import br.com.nexo.driver.parser.RawOfferText

/**
 * A text fragment returned by an on-device OCR engine.  This contract deliberately has no
 * dependency on a specific engine (ML Kit, Tesseract, Accessibility, etc.).
 */
data class OcrTextBlock(
    val text: String,
    val readingOrder: Int,
    val confidence: Float = 1f,
) {
    init {
        require(confidence in 0f..1f) { "OCR confidence must be between 0 and 1." }
    }
}

/** Snapshot of the text visible when an offer card is detected. */
data class OcrTextSnapshot(
    val blocks: List<OcrTextBlock>,
    val capturedAtEpochMs: Long,
    val layoutHint: String? = null,
    val fieldSource: FieldSource = FieldSource.OCR,
)

/**
 * The integration point for a local OCR implementation.  Implementations are intentionally
 * outside this module so the MVP can be exercised from captured text before selecting an engine.
 */
interface LocalOcrEngine<in Frame> {
    fun recognize(frame: Frame): List<OcrTextBlock>
}

interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

data class OfferOcrOutput(
    val raw: RawOfferText,
    val offer: NormalizedOffer?,
    /** Time spent assembling, parsing and deduplicating this OCR snapshot. */
    val processingLatencyNanos: Long,
    /** True when an equivalent parsed offer was already emitted within the deduplication window. */
    val isDuplicate: Boolean,
    /**
     * Set when a known Uber/99 card marker was recognised in [raw] but no parser could extract a
     * complete offer from it -- a likely sign the target app's layout/copy has drifted from the
     * hardcoded strings/regex, rather than simply "no offer visible right now".
     */
    val unrecognizedLayoutSource: OfferSource? = null,
) {
    val processingLatencyMillis: Long get() = processingLatencyNanos / NANOS_PER_MILLISECOND
    val shouldEmit: Boolean get() = offer != null && !isDuplicate
}

data class OfferOcrMetrics(
    val processedSnapshots: Long = 0,
    val parsedOffers: Long = 0,
    val duplicateOffers: Long = 0,
    val totalProcessingNanos: Long = 0,
    val maxProcessingNanos: Long = 0,
    /** Snapshots where a recognised card marker failed to yield a complete offer. */
    val unrecognizedLayoutCount: Long = 0,
) {
    val averageProcessingNanos: Long
        get() = if (processedSnapshots == 0L) 0L else totalProcessingNanos / processedSnapshots
}

/**
 * Converts ordered OCR blocks to [RawOfferText], delegates layout recognition to the existing
 * parser registry and suppresses repeated frames of the same offer card.
 */
class OfferOcrPipeline(
    private val parserRegistry: OfferParserRegistry = OfferParserRegistry(),
    private val deduplicator: OfferDeduplicator = OfferDeduplicator(),
    private val clock: MonotonicClock = SystemMonotonicClock,
) {
    private var metrics = OfferOcrMetrics()

    @Synchronized
    fun process(snapshot: OcrTextSnapshot): OfferOcrOutput {
        val startedAt = clock.nowNanos()
        val raw = snapshot.toRawOfferText()
        if (raw.isTransientOfferFrame()) {
            val latency = (clock.nowNanos() - startedAt).coerceAtLeast(0L)
            metrics = metrics.copy(
                processedSnapshots = metrics.processedSnapshots + 1,
                totalProcessingNanos = metrics.totalProcessingNanos + latency,
                maxProcessingNanos = maxOf(metrics.maxProcessingNanos, latency),
            )
            return OfferOcrOutput(raw, offer = null, processingLatencyNanos = latency, isDuplicate = false)
        }
        val attempt = parserRegistry.parseAttempt(raw)
        val offer = attempt.offer
        val duplicate = offer?.let { deduplicator.isDuplicate(it) } ?: false
        val latency = (clock.nowNanos() - startedAt).coerceAtLeast(0L)
        metrics = metrics.copy(
            processedSnapshots = metrics.processedSnapshots + 1,
            parsedOffers = metrics.parsedOffers + if (offer != null) 1 else 0,
            duplicateOffers = metrics.duplicateOffers + if (duplicate) 1 else 0,
            totalProcessingNanos = metrics.totalProcessingNanos + latency,
            maxProcessingNanos = maxOf(metrics.maxProcessingNanos, latency),
            unrecognizedLayoutCount = metrics.unrecognizedLayoutCount +
                if (attempt.unrecognizedLayoutSource != null) 1 else 0,
        )
        return OfferOcrOutput(raw, offer, latency, duplicate, attempt.unrecognizedLayoutSource)
    }

    /**
     * Parses a single snapshot to an offer without deduplication or metrics. Used for the extra
     * cards in a multi-offer tray, which decorate the primary card and must not disturb the
     * dedup window or the counters the primary offer drives.
     */
    @Synchronized
    fun parse(snapshot: OcrTextSnapshot): NormalizedOffer? =
        parserRegistry.parseAttempt(snapshot.toRawOfferText()).offer

    @Synchronized
    fun metrics(): OfferOcrMetrics = metrics

    @Synchronized
    fun reset() {
        metrics = OfferOcrMetrics()
        deduplicator.clear()
    }
}

fun OcrTextSnapshot.toRawOfferText(): RawOfferText = RawOfferText(
    text = blocks
        .asSequence()
        .sortedBy(OcrTextBlock::readingOrder)
        .map(OcrTextBlock::text)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(separator = "\n"),
    capturedAtEpochMs = capturedAtEpochMs,
    layoutHint = layoutHint,
    fieldSource = fieldSource,
)

private fun RawOfferText.isTransientOfferFrame(): Boolean {
    val normalized = text.lowercase()
    return TRANSIENT_FRAME_MARKERS.any(normalized::contains)
}

private val TRANSIENT_FRAME_MARKERS = listOf(
    "buscando",
    "carregando",
    "procurando corrida",
    "solicitacao de corrida cancelada",
    "solicitação de corrida cancelada",
    "expirou",
    "aguarde.",
    "motorista aceitou",
    "corrida aceita",
)

/**
 * In-memory deduplication tuned for persistent screen captures: an offer is repeated only when
 * its source, layout and decision-relevant monetary/time/distance fields are equal within the
 * configured time window.  It does not persist across app launches.
 */
class OfferDeduplicator(
    private val windowMs: Long = DEFAULT_DEDUPLICATION_WINDOW_MS,
) {
    init {
        require(windowMs >= 0L) { "Deduplication window cannot be negative." }
    }

    private val recentOffers = mutableMapOf<OfferFingerprint, Long>()

    @Synchronized
    fun isDuplicate(offer: NormalizedOffer): Boolean {
        val fingerprint = OfferFingerprint.from(offer)
        val detectedAt = offer.detectedAtEpochMs
        purgeBefore(detectedAt - windowMs)
        val previous = recentOffers[fingerprint]
        recentOffers[fingerprint] = detectedAt
        return previous != null && detectedAt >= previous && detectedAt - previous <= windowMs
    }

    @Synchronized
    fun clear() = recentOffers.clear()

    private fun purgeBefore(threshold: Long) {
        recentOffers.entries.removeAll { (_, seenAt) -> seenAt < threshold }
    }
}

private data class OfferFingerprint(
    val source: String,
    val kind: String,
    val payoutCents: Long?,
    val pickupSeconds: Long?,
    val pickupMeters: Long?,
    val tripSeconds: Long?,
    val tripMeters: Long?,
    val passengerRating: Long?,
    val stopCount: Long?,
) {
    companion object {
        fun from(offer: NormalizedOffer) = OfferFingerprint(
            source = offer.source.name,
            kind = offer.kind.name,
            payoutCents = offer.payout.value?.cents,
            pickupSeconds = offer.pickup.duration.value?.seconds,
            pickupMeters = offer.pickup.distance.value?.meters,
            tripSeconds = offer.trip.duration.value?.seconds,
            tripMeters = offer.trip.distance.value?.meters,
            passengerRating = offer.passenger.rating.value,
            stopCount = offer.stopCount.value,
        )
    }
}

private const val NANOS_PER_MILLISECOND = 1_000_000L
const val DEFAULT_DEDUPLICATION_WINDOW_MS = 15_000L
