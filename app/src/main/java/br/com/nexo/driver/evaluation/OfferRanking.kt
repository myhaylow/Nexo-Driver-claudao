package br.com.nexo.driver.evaluation

/** One evaluated offer with the position it was ranked into (1 = best). */
data class RankedOffer<T>(
    val offer: T,
    val result: EvaluationResult,
    val rank: Int,
)

/**
 * Orders several simultaneously-visible offers best-first, for the multi-offer overlay that mirrors
 * Uber's job-board tray (`CardsTrayV2`). It is pure ranking only: it never accepts, selects or
 * dismisses anything -- the driver still acts in the ride app itself. The overlay stays a read-only
 * annotation on top of whatever the tray shows.
 *
 * The order matches how a driver reads the tray: a clear recommendation first, then within the same
 * recommendation the higher-scoring, better-read, higher-paying offer.
 */
object OfferRanking {
    fun <T> rank(
        offers: List<T>,
        resultOf: (T) -> EvaluationResult,
        payoutCentsOf: (T) -> Long? = { null },
    ): List<RankedOffer<T>> = offers
        .map { offer -> offer to resultOf(offer) }
        .sortedWith(
            compareBy<Pair<T, EvaluationResult>> { decisionOrder(it.second.decision) }
                .thenByDescending { it.second.weightedScore }
                .thenByDescending { it.second.coveragePercent }
                .thenByDescending { payoutCentsOf(it.first) ?: Long.MIN_VALUE },
        )
        .mapIndexed { index, (offer, result) -> RankedOffer(offer, result, rank = index + 1) }

    /** A concrete recommendation sorts ahead of "analyze", which sorts ahead of "reject". */
    private fun decisionOrder(decision: OfferDecision): Int = when (decision) {
        OfferDecision.ACCEPT -> 0
        OfferDecision.ANALYZE -> 1
        OfferDecision.REJECT -> 2
    }
}
