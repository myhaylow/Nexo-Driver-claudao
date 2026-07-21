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
 * The second relational rule: a strong total payout lifts an offer whose pickup is only just over
 * the driver's limit. Same shape as [KmCompensatesHourTest] -- it may only rescue a BORDERLINE
 * ANALYZE, never a hard block, a FAIL pickup, or a merely-adequate payout.
 *
 * A rating rule that lands NEAR is included in each scenario purely to hold the weighted score in
 * the borderline band, so the compensation (not the raw score) is what decides the outcome.
 */
class PayoutCompensatesPickupTest {

    private val evaluator = OfferEvaluator()

    // Payout must clear 25,00 by 15% (>= 28,75) to count as strong.
    private val payoutRule = FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 2_500L, tolerancePercent = 10)
    // Pickup 20,00 m limit; 10% band => NEAR up to 2_200 m.
    private val pickupRule = FilterRule(Metric.PICKUP_DISTANCE, Comparator.AT_MOST, target = 2_000L, tolerancePercent = 10)
    // Rating held NEAR (target 4,80; observed 4,76 is inside the +/-0.05 band) to pin the score.
    private val ratingRule = FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 480L)

    @Test
    fun `strong payout rescues a near-miss pickup`() {
        val result = evaluator.evaluate(
            offer(payoutCents = 3_000, pickupMeters = 2_100, ratingScaled = 476),
            listOf(payoutRule, pickupRule, ratingRule),
        )

        assertEquals(OfferDecision.ACCEPT, result.decision)
        assertEquals(DecisionReason.PAYOUT_COMPENSATES_PICKUP, result.reason)
        assertEquals(Metric.PAYOUT, result.primaryReason?.rule?.metric)
    }

    @Test
    fun `a merely-adequate payout does not rescue the pickup`() {
        // 26,00 >= 25,00 so PASS, but below the 28,75 strong bar.
        val result = evaluator.evaluate(
            offer(payoutCents = 2_600, pickupMeters = 2_100, ratingScaled = 476),
            listOf(payoutRule, pickupRule, ratingRule),
        )

        assertEquals(OfferDecision.ANALYZE, result.decision)
        assertEquals(DecisionReason.BORDERLINE, result.reason)
    }

    @Test
    fun `compensation never rescues a failed pickup, only a near one`() {
        // 3_000 m is past the 2_200 m band: FAIL, not NEAR.
        val result = evaluator.evaluate(
            offer(payoutCents = 3_000, pickupMeters = 3_000, ratingScaled = 476),
            listOf(payoutRule, pickupRule, ratingRule),
        )

        assertNotEquals(OfferDecision.ACCEPT, result.decision)
        assertNotEquals(DecisionReason.PAYOUT_COMPENSATES_PICKUP, result.reason)
    }

    @Test
    fun `compensation needs a pickup rule present`() {
        // Strong payout, no pickup rule to compensate: the score simply stands on its own.
        val result = evaluator.evaluate(
            offer(payoutCents = 3_000, pickupMeters = 2_100, ratingScaled = 476),
            listOf(payoutRule, ratingRule),
        )

        assertNotEquals(DecisionReason.PAYOUT_COMPENSATES_PICKUP, result.reason)
    }

    @Test
    fun `an eliminatory block is never overridden by compensation`() {
        val blocked = FilterRule(Metric.PICKUP_IS_BLOCKED, Comparator.IS_FALSE, mode = EvaluationMode.ELIMINATORY)
        val result = evaluator.evaluate(
            offer(payoutCents = 3_000, pickupMeters = 2_100, ratingScaled = 476, blocked = true),
            listOf(payoutRule, pickupRule, ratingRule, blocked),
        )

        assertEquals(OfferDecision.REJECT, result.decision)
        assertEquals(DecisionReason.ELIMINATORY_BLOCK, result.reason)
    }

    private fun offer(
        payoutCents: Long,
        pickupMeters: Long,
        ratingScaled: Long,
        blocked: Boolean = false,
    ) = NormalizedOffer(
        source = OfferSource.UBER,
        kind = OfferKind.UBER_STANDARD,
        detectedAtEpochMs = 0L,
        payout = confident(Money(payoutCents)),
        displayedRatePerKm = unknown(),
        bonus = unknown(),
        pickup = OfferLeg(confident(Duration(300)), confident(Distance(pickupMeters)), unknown<GeoText>()),
        trip = OfferLeg(confident(Duration(1_200)), confident(Distance(6_000)), unknown<GeoText>()),
        passenger = Passenger(confident(ratingScaled), unknown(), unknown()),
        serviceType = unknown(),
        stopCount = unknown(),
        longTripHint = unknown(),
        destinationDirectionHint = unknown(),
        pickupIsBlocked = confident(blocked),
        rawLayoutVersion = "test",
        fieldConfidence = emptyMap(),
    )

    private fun <T> confident(value: T?): Confidence<T> =
        if (value == null) unknown() else Confidence(value, 1f, FieldSource.OCR)

    private fun <T> unknown(): Confidence<T> = Confidence(null, 0f, FieldSource.OCR)
}
