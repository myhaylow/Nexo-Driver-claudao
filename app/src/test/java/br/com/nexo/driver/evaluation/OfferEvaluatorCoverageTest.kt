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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the scoring change that keeps an unreadable field from voting: it used to contribute a
 * neutral 50, which made a half-parsed card score the same as a genuinely borderline one.
 */
class OfferEvaluatorCoverageTest {

    private val evaluator = OfferEvaluator()

    @Test
    fun `an unreadable metric does not pull a failing offer up towards analyze`() {
        // Payout clearly fails; the rating is unreadable. Under the old averaging the unknown
        // contributed 50 and dragged the total to the ANALYZE band despite no supporting evidence.
        val offer = offer(payoutCents = 500L, ratingScaled = null)
        val result = evaluator.evaluate(
            offer = offer,
            rules = listOf(
                FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 5_000L, tolerancePercent = 0),
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 470L),
            ),
        )

        assertEquals(0, result.weightedScore)
        assertEquals(OfferDecision.REJECT, result.decision)
    }

    @Test
    fun `coverage reports the share of rule weight that was readable`() {
        val result = evaluator.evaluate(
            offer = offer(payoutCents = 5_000L, ratingScaled = null),
            rules = listOf(
                FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 1_000L),
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 470L),
            ),
        )

        assertEquals(50, result.coveragePercent)
    }

    @Test
    fun `a mostly unreadable card is never decided outright`() {
        val result = evaluator.evaluate(
            offer = offer(payoutCents = 100_000L, ratingScaled = null),
            rules = listOf(
                FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 1_000L, weight = 1),
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 470L, weight = 5),
                FilterRule(Metric.NET_PROFIT, Comparator.AT_LEAST, target = 1_000L, weight = 5),
            ),
        )

        // The payout passes handsomely, but it is a small slice of the configured weight.
        assertTrue(result.coveragePercent < DEFAULT_MINIMUM_COVERAGE_PERCENT)
        assertEquals(OfferDecision.ANALYZE, result.decision)
    }

    @Test
    fun `field confidence scales a rule's contribution`() {
        // Two passing rules of equal nominal weight, one read with lower confidence: the resulting
        // coverage reflects the weaker reading rather than treating both as equally trustworthy.
        val result = evaluator.evaluate(
            offer = offer(payoutCents = 5_000L, ratingScaled = 490L, payoutConfidence = 0.9f),
            rules = listOf(
                FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 1_000L),
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 470L),
            ),
        )

        assertEquals(100, result.weightedScore)
        assertEquals(OfferDecision.ACCEPT, result.decision)
    }

    @Test
    fun `the rating band is absolute rather than a meaningless percentage`() {
        // 10% under a 4.70 target would reach 4.23, which is not "almost passing".
        val nearMiss = evaluator.evaluate(
            offer = offer(payoutCents = 5_000L, ratingScaled = 466L),
            rules = listOf(FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 470L)),
        )
        val clearMiss = evaluator.evaluate(
            offer = offer(payoutCents = 5_000L, ratingScaled = 430L),
            rules = listOf(FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 470L)),
        )

        assertEquals(MetricStatus.NEAR, nearMiss.metrics.single().status)
        assertEquals(MetricStatus.FAIL, clearMiss.metrics.single().status)
    }

    @Test
    fun `an eliminatory failure is always the reported reason`() {
        val result = evaluator.evaluate(
            offer = offer(payoutCents = 500L, ratingScaled = 500L),
            rules = listOf(
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 470L, weight = 9),
                FilterRule(
                    Metric.PAYOUT,
                    Comparator.AT_LEAST,
                    target = 5_000L,
                    tolerancePercent = 0,
                    mode = EvaluationMode.ELIMINATORY,
                ),
            ),
        )

        assertEquals(OfferDecision.REJECT, result.decision)
        assertNotNull(result.primaryReason)
        assertEquals(Metric.PAYOUT, result.primaryReason?.rule?.metric)
    }

    @Test
    fun `a scored analyze blames the failing rule, not an unrelated unreadable field`() {
        // Reproduces what a real profile produced on device: several scored rules, one of which
        // fails, plus an unrelated field that could not be read. The card must point at the
        // shortfall the driver can act on, not at the missing reading.
        val result = evaluator.evaluate(
            offer = offer(payoutCents = 500L, ratingScaled = null),
            rules = listOf(
                FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 800L, tolerancePercent = 0),
                FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_MOST, target = 20_000L),
                FilterRule(Metric.TOTAL_DURATION, Comparator.AT_MOST, target = 3_600L),
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 480L),
            ),
        )

        assertEquals(OfferDecision.ANALYZE, result.decision)
        assertEquals(Metric.PAYOUT, result.primaryReason?.rule?.metric)
    }

    @Test
    fun `an unreadable field is still the reason when nothing else explains the verdict`() {
        val result = evaluator.evaluate(
            offer = offer(payoutCents = 5_000L, ratingScaled = null),
            rules = listOf(
                FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 800L),
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 480L),
            ),
        )

        assertEquals(Metric.PASSENGER_RATING, result.primaryReason?.rule?.metric)
        assertEquals(MetricStatus.UNKNOWN, result.primaryReason?.status)
    }

    @Test
    fun `a blocked pickup rejects through the engine like any other eliminatory rule`() {
        val result = evaluator.evaluate(
            offer = offer(payoutCents = 100_000L, ratingScaled = 500L, blocked = true),
            rules = listOf(
                FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 1_000L),
                FilterRule(
                    Metric.PICKUP_IS_BLOCKED,
                    Comparator.IS_FALSE,
                    mode = EvaluationMode.ELIMINATORY,
                ),
            ),
        )

        assertEquals(OfferDecision.REJECT, result.decision)
        assertEquals(Metric.PICKUP_IS_BLOCKED, result.primaryReason?.rule?.metric)
    }

    private fun offer(
        payoutCents: Long?,
        ratingScaled: Long?,
        payoutConfidence: Float = 1f,
        blocked: Boolean? = null,
    ) = NormalizedOffer(
        source = OfferSource.UBER,
        kind = OfferKind.UBER_STANDARD,
        detectedAtEpochMs = 0L,
        payout = confident(payoutCents?.let(::Money), payoutConfidence),
        displayedRatePerKm = unknown(),
        bonus = unknown(),
        pickup = leg(120L, 1_000L),
        trip = leg(1_200L, 10_000L),
        passenger = Passenger(
            rating = confident(ratingScaled),
            tripCount = unknown(),
            profile = unknown(),
        ),
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

    private fun <T> confident(value: T?, score: Float = 1f): Confidence<T> =
        if (value == null) unknown() else Confidence(value, score, FieldSource.OCR)

    private fun <T> unknown(): Confidence<T> = Confidence(null, 0f, FieldSource.OCR)
}
