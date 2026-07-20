package br.com.nexo.driver.evaluation

import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.Distance
import br.com.nexo.driver.offer.Duration
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.GeoText
import br.com.nexo.driver.offer.Money
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferKind
import br.com.nexo.driver.offer.OfferLeg
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.offer.Passenger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The relational rule adopted from the sibling decision engine: a strong R$/km lifts an offer
 * whose R$/h is only just short. Mirrors that project's `accept-km-compensates-hour` scenario --
 * fare R$21, total 10 km / 34 min, so R$/km 2.10 and R$/h ~37 against a 1.80 / 40 profile.
 */
class KmCompensatesHourTest {

    private val evaluator = OfferEvaluator()

    private val kmRule = FilterRule(Metric.RATE_PER_KM, Comparator.AT_LEAST, target = 180L, tolerancePercent = 10)
    private val hourRule = FilterRule(Metric.RATE_PER_HOUR, Comparator.AT_LEAST, target = 4_000L, tolerancePercent = 10)

    @Test
    fun `strong km rescues a near-miss hour`() {
        // R$/km 210 (>= 180 * 1.10 = 198, strong) and R$/h 3706 (NEAR: >= 3600, below 4000).
        val result = evaluator.evaluate(offer(fareCents = 2_100, totalMeters = 10_000, totalSeconds = 2_040), listOf(kmRule, hourRule))

        assertEquals(OfferDecision.ACCEPT, result.decision)
        assertEquals(DecisionReason.KM_COMPENSATES_HOUR, result.reason)
        assertEquals(Metric.RATE_PER_KM, result.primaryReason?.rule?.metric)
    }

    @Test
    fun `a merely-adequate km does not rescue the hour`() {
        // R$/km exactly at target is not "strong"; it must clear the 10% bonus. Payout tuned so
        // per-km lands at 1.90 (>= 1.80 so PASS, but < 1.98) while per-hour stays NEAR.
        val result = evaluator.evaluate(offer(fareCents = 1_900, totalMeters = 10_000, totalSeconds = 3_500), listOf(kmRule, hourRule))

        assertEquals(OfferDecision.ANALYZE, result.decision)
        assertEquals(DecisionReason.BORDERLINE, result.reason)
    }

    @Test
    fun `compensation never rescues a failed hour, only a near one`() {
        // R$/h far below target (FAIL, not NEAR): a strong km must not lift it to ACCEPT, and the
        // verdict must not carry the compensation reason -- whatever the score band works out to.
        val result = evaluator.evaluate(offer(fareCents = 2_100, totalMeters = 10_000, totalSeconds = 6_000), listOf(kmRule, hourRule))

        assertNotEquals(OfferDecision.ACCEPT, result.decision)
        assertNotEquals(DecisionReason.KM_COMPENSATES_HOUR, result.reason)
    }

    @Test
    fun `compensation needs both rules present`() {
        // Only the km rule: nothing to compensate, so the strong km simply passes on its own.
        val result = evaluator.evaluate(offer(fareCents = 2_100, totalMeters = 10_000, totalSeconds = 2_040), listOf(kmRule))

        assertEquals(DecisionReason.MEETS_TARGETS, result.reason)
    }

    @Test
    fun `an eliminatory block is never overridden by compensation`() {
        val blocked = FilterRule(Metric.PICKUP_IS_BLOCKED, Comparator.IS_FALSE, mode = EvaluationMode.ELIMINATORY)
        val result = evaluator.evaluate(
            offer(fareCents = 2_100, totalMeters = 10_000, totalSeconds = 2_040, blocked = true),
            listOf(kmRule, hourRule, blocked),
        )

        assertEquals(OfferDecision.REJECT, result.decision)
        assertEquals(DecisionReason.ELIMINATORY_BLOCK, result.reason)
    }

    private fun offer(fareCents: Long, totalMeters: Long, totalSeconds: Long, blocked: Boolean = false) =
        NormalizedOffer(
            source = OfferSource.UBER,
            kind = OfferKind.UBER_STANDARD,
            detectedAtEpochMs = 0L,
            payout = confident(Money(fareCents)),
            displayedRatePerKm = unknown(),
            bonus = unknown(),
            // All distance/time on the trip leg; pickup is a real zero leg so the combined totals
            // stay non-null (combineDistance nulls out if either leg is unknown).
            pickup = leg(0L, 0L),
            trip = leg(totalSeconds, totalMeters),
            passenger = Passenger(unknown(), unknown(), unknown()),
            serviceType = unknown(),
            stopCount = unknown(),
            longTripHint = unknown(),
            destinationDirectionHint = unknown(),
            pickupIsBlocked = confident(blocked),
            rawLayoutVersion = "test",
            fieldConfidence = emptyMap(),
        )

    private fun leg(seconds: Long, meters: Long) = OfferLeg(
        duration = confident(Duration(seconds)),
        distance = confident(Distance(meters)),
        location = unknown<GeoText>(),
    )

    private fun <T> confident(value: T?): Confidence<T> =
        if (value == null) unknown() else Confidence(value, 1f, FieldSource.OCR)

    private fun <T> unknown(): Confidence<T> = Confidence(null, 0f, FieldSource.OCR)
}
