package br.com.nexo.driver.evaluation

import org.junit.Assert.assertEquals
import org.junit.Test

class OfferRankingTest {
    private fun result(
        decision: OfferDecision,
        score: Int,
        coverage: Int = 100,
    ) = EvaluationResult(
        metrics = emptyList(),
        weightedScore = score,
        decision = decision,
        coveragePercent = coverage,
    )

    @Test
    fun `a concrete recommendation outranks analyze and reject`() {
        val offers = listOf("reject" to result(OfferDecision.REJECT, 90), "analyze" to result(OfferDecision.ANALYZE, 60), "accept" to result(OfferDecision.ACCEPT, 55))

        val ranked = OfferRanking.rank(offers, resultOf = { it.second })

        assertEquals(listOf("accept", "analyze", "reject"), ranked.map { it.offer.first })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
    }

    @Test
    fun `within the same decision the higher score wins`() {
        val offers = listOf("low" to result(OfferDecision.ACCEPT, 82), "high" to result(OfferDecision.ACCEPT, 95))

        val ranked = OfferRanking.rank(offers, resultOf = { it.second })

        assertEquals(listOf("high", "low"), ranked.map { it.offer.first })
    }

    @Test
    fun `payout breaks a tie on decision, score and coverage`() {
        val a = "a" to result(OfferDecision.ANALYZE, 60)
        val b = "b" to result(OfferDecision.ANALYZE, 60)

        val ranked = OfferRanking.rank(
            listOf(a, b),
            resultOf = { it.second },
            payoutCentsOf = { pair -> if (pair.first == "b") 3_000L else 1_000L },
        )

        assertEquals(listOf("b", "a"), ranked.map { it.offer.first })
    }

    @Test
    fun `coverage breaks a tie before payout`() {
        val offers = listOf("thin" to result(OfferDecision.ANALYZE, 60, coverage = 40), "full" to result(OfferDecision.ANALYZE, 60, coverage = 100))

        val ranked = OfferRanking.rank(offers, resultOf = { it.second })

        assertEquals(listOf("full", "thin"), ranked.map { it.offer.first })
    }
}
