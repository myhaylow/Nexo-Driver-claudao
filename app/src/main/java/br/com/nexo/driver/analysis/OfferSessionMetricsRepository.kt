package br.com.nexo.driver.analysis

import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.offer.NormalizedOffer

/** How the last offer was read. Surfaced so the driver can see whether the fast path is working. */
enum class OfferReadPath { ACCESSIBILITY, OCR }

/**
 * Process-local session counters plus a small read-health summary.
 *
 * The health fields exist because live testing showed the app could look "Ativo" on Home while
 * every offer was actually coming through the slow OCR fallback -- something only visible in the
 * log until now. If the accessibility tree stops yielding when a ride app changes its layout, the
 * app would keep working silently on OCR (heavier on battery) or, if OCR also broke, stop reading
 * with nothing on screen to say so. These fields make that observable without exposing any offer
 * content: they are counts and the last read's path/latency/coverage only -- never an address,
 * OCR text, or image.
 */
data class OfferSessionMetrics(
    val offersEvaluated: Int = 0,
    val lastReadPath: OfferReadPath? = null,
    val lastLatencyMs: Long? = null,
    val lastCoveragePercent: Int? = null,
    /** Of the offers read this session, how many came through the OCR fallback. */
    val readsViaOcr: Int = 0,
    val totalReads: Int = 0,
) {
    /** True once enough reads exist to say the accessibility tree is not delivering. */
    val isOcrOnly: Boolean get() = totalReads >= MIN_READS_FOR_SIGNAL && readsViaOcr == totalReads

    private companion object {
        const val MIN_READS_FOR_SIGNAL = 3
    }
}

fun interface OfferSessionMetricsSubscription : AutoCloseable {
    override fun close()
}

/** Process-local counters for the Home summary; no address, OCR text or image is retained. */
object OfferSessionMetricsRepository {
    private const val DEDUP_WINDOW_MS = 30_000L
    private val lock = Any()
    private val observers = linkedSetOf<(OfferSessionMetrics) -> Unit>()
    private val recent = linkedMapOf<String, Long>()
    private var snapshot = OfferSessionMetrics()

    fun current(): OfferSessionMetrics = synchronized(lock) { snapshot }

    fun record(offer: NormalizedOffer, nowMs: Long = System.currentTimeMillis()) {
        val key = listOf(
            offer.source.name,
            offer.payout.value?.cents,
            offer.pickup.duration.value?.seconds,
            offer.pickup.distance.value?.meters,
            offer.trip.duration.value?.seconds,
            offer.trip.distance.value?.meters,
        ).joinToString("|")
        synchronized(lock) {
            recent.entries.removeAll { nowMs - it.value > DEDUP_WINDOW_MS }
            if (recent.put(key, nowMs) != null) return
            snapshot = snapshot.copy(offersEvaluated = snapshot.offersEvaluated + 1)
        }.also { publish() }
    }

    /**
     * Records the read-health of a served offer, independent of the offer-count dedup above. Called
     * once per served card from the reader, which is the only place that knows which path produced
     * it and how long it took. Kept separate from [record] so it can never double-count offers.
     */
    fun recordReadHealth(health: OfferReadHealth) {
        synchronized(lock) {
            snapshot = snapshot.copy(
                lastReadPath = health.path,
                lastLatencyMs = health.latencyMs,
                lastCoveragePercent = health.coveragePercent,
                readsViaOcr = snapshot.readsViaOcr + if (health.path == OfferReadPath.OCR) 1 else 0,
                totalReads = snapshot.totalReads + 1,
            )
        }.also { publish() }
    }

    private fun publish() {
        val listeners: List<(OfferSessionMetrics) -> Unit>
        val next: OfferSessionMetrics
        synchronized(lock) {
            next = snapshot
            listeners = observers.toList()
        }
        listeners.forEach { it(next) }
    }

    fun subscribe(observer: (OfferSessionMetrics) -> Unit): OfferSessionMetricsSubscription {
        val initial = synchronized(lock) {
            observers += observer
            snapshot
        }
        observer(initial)
        return OfferSessionMetricsSubscription { synchronized(lock) { observers -= observer } }
    }

    // --- Session-only metric samples, for the rule editor's live impact preview ---------------
    //
    // A bounded ring of per-offer metric values. Numbers only -- the observed R$/km, distance,
    // rating and so on -- never a source, address, OCR text or image, matching the privacy posture
    // of the counters above. It lives in memory, is capped, and is cleared when the reader stops or
    // the process dies. This deliberately does NOT persist a ride history: it answers "of the
    // offers seen this session, how many would this target pass" without keeping anything after.

    private const val MAX_SAMPLES = 60
    private val sampleRing = ArrayDeque<Map<Metric, Long>>(MAX_SAMPLES)

    /** Records the numeric values an evaluation observed. Only metrics that produced a value. */
    fun recordSamples(values: Map<Metric, Long>) {
        if (values.isEmpty()) return
        synchronized(lock) {
            if (sampleRing.size >= MAX_SAMPLES) sampleRing.removeFirst()
            sampleRing.addLast(values)
        }
    }

    /**
     * How many of this session's samples would pass [target] for [metric], as passing/total. Null
     * when there is nothing to compare against yet, so the caller shows a waiting state rather than
     * a misleading "0 de 0".
     */
    fun impactOf(metric: Metric, target: Long, atLeast: Boolean): ImpactSample? {
        val samples = synchronized(lock) { sampleRing.mapNotNull { it[metric] } }
        if (samples.isEmpty()) return null
        val passing = samples.count { if (atLeast) it >= target else it <= target }
        return ImpactSample(passing = passing, total = samples.size)
    }

    /** Clears the session sample ring; called when the reader stops. */
    fun clearSamples() {
        synchronized(lock) { sampleRing.clear() }
    }
}

/** Passing/total over this session's samples for one target. */
data class ImpactSample(val passing: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else passing * 100 / total
}

/** The read-health of one served offer. Numbers only; no offer content. */
data class OfferReadHealth(
    val path: OfferReadPath,
    val latencyMs: Long,
    val coveragePercent: Int,
)
