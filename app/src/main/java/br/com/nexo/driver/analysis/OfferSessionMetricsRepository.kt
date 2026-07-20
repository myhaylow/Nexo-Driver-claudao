package br.com.nexo.driver.analysis

import br.com.nexo.driver.offer.NormalizedOffer

data class OfferSessionMetrics(val offersEvaluated: Int = 0)

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
        val listeners: List<(OfferSessionMetrics) -> Unit>
        val next: OfferSessionMetrics
        synchronized(lock) {
            recent.entries.removeAll { nowMs - it.value > DEDUP_WINDOW_MS }
            if (recent.put(key, nowMs) != null) return
            next = snapshot.copy(offersEvaluated = snapshot.offersEvaluated + 1)
            snapshot = next
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
}
