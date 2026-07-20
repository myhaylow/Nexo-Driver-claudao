package br.com.nexo.driver.screenreader.orchestration

import br.com.nexo.driver.screenreader.domain.RideOffer

data class OfferFingerprint(
    val source: String,
    val category: String?,
    val price: String?,
    val pickupDistanceKm: Double?,
    val tripDistanceKm: Double?,
    val pickupDurationMinutes: Int?,
    val tripDurationMinutes: Int?,
    val passengerRating: Double?,
) {
    companion object {
        fun from(offer: RideOffer) = OfferFingerprint(
            offer.source.name, offer.category, offer.price?.toPlainString(), offer.pickupDistanceKm,
            offer.tripDistanceKm, offer.pickupDurationMinutes, offer.tripDurationMinutes, offer.passengerRating,
        )
    }
}

/** In-memory per-window deduplication; it never persists offer details. */
class WindowOfferDeduplicator(private val windowMs: Long = 10_000L) {
    private val recent = mutableMapOf<Pair<Int, OfferFingerprint>, Long>()
    fun isDuplicate(offer: RideOffer): Boolean {
        val key = offer.windowId to OfferFingerprint.from(offer)
        val previous = recent[key]
        recent.entries.removeAll { it.value < offer.capturedAt - windowMs }
        recent[key] = offer.capturedAt
        return previous != null && offer.capturedAt - previous in 0..windowMs
    }
}

class OfferExpirationManager(private val disappearanceDelayMs: Long = 650L) {
    private val lastSeen = mutableMapOf<Int, Long>()
    fun markSeen(windowId: Int, now: Long) { lastSeen[windowId] = now }
    fun expiredWindows(now: Long, visibleWindowIds: Set<Int>): Set<Int> = lastSeen
        .filter { (windowId, seenAt) -> windowId !in visibleWindowIds && now - seenAt >= disappearanceDelayMs }
        .keys
        .also { expired -> expired.forEach(lastSeen::remove) }
}
