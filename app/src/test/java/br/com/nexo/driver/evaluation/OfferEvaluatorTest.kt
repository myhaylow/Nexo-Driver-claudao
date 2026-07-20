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
import org.junit.Test

class OfferEvaluatorTest {
    private val confidentMoney = Confidence(Money(2_590), 0.95f, FieldSource.OCR)
    private val confidentDistance = Confidence(Distance(3_000), 0.95f, FieldSource.OCR)
    private val confidentDuration = Confidence(Duration(600), 0.95f, FieldSource.OCR)
    private val location = Confidence(GeoText(null, null), 0.95f, FieldSource.OCR)

    @Test
    fun `derives metrics from pickup and trip`() {
        val result = OfferEvaluator().derive(sampleOffer())

        assertEquals(6_000L, result.totalDistance.value?.meters)
        assertEquals(1_200L, result.totalDuration.value?.seconds)
        assertEquals(431L, result.ratePerKm.value)
        assertEquals(7_770L, result.ratePerHour.value)
    }

    @Test
    fun `calculates overlay economic metrics from the complete driver commitment`() {
        val offer = sampleOffer().copy(
            // R$24,00 for 1.5 km to pickup + 3.5 km with the passenger,
            // taking 12 min + 18 min respectively.
            payout = Confidence(Money(2_400), 0.95f, FieldSource.OCR),
            pickup = OfferLeg(
                duration = Confidence(Duration(12 * 60L), 0.95f, FieldSource.OCR),
                distance = Confidence(Distance(1_500), 0.95f, FieldSource.OCR),
                location = location,
            ),
            trip = OfferLeg(
                duration = Confidence(Duration(18 * 60L), 0.95f, FieldSource.OCR),
                distance = Confidence(Distance(3_500), 0.95f, FieldSource.OCR),
                location = location,
            ),
            passenger = sampleOffer().passenger.copy(
                rating = Confidence(487L, 0.95f, FieldSource.OCR),
            ),
        )

        val derived = OfferEvaluator().derive(offer)
        val result = OfferEvaluator().evaluate(
            offer,
            listOf(
                FilterRule(Metric.RATE_PER_KM, Comparator.AT_LEAST, target = 480),
                FilterRule(Metric.RATE_PER_HOUR, Comparator.AT_LEAST, target = 4_800),
                FilterRule(Metric.TOTAL_DURATION, Comparator.AT_MOST, target = 30 * 60L),
                FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_MOST, target = 5_000),
                FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 485),
            ),
        )

        // Stored monetary rates are centavos/km and centavos/hour. This proves that
        // both values include pickup plus passenger legs, never the passenger leg alone.
        assertEquals(5_000L, derived.totalDistance.value?.meters)
        assertEquals(1_800L, derived.totalDuration.value?.seconds)
        assertEquals(480L, derived.ratePerKm.value)
        assertEquals(4_800L, derived.ratePerHour.value)
        assertEquals(487L, offer.passenger.rating.value)
        assertEquals(
            listOf(
                Metric.RATE_PER_KM,
                Metric.RATE_PER_HOUR,
                Metric.TOTAL_DURATION,
                Metric.TOTAL_DISTANCE,
                Metric.PASSENGER_RATING,
            ),
            result.metrics.map { it.rule.metric },
        )
        assertEquals(OfferDecision.ACCEPT, result.decision)
    }

    @Test
    fun `does not derive a rate when total distance or duration is zero`() {
        val offer = sampleOffer().copy(
            pickup = OfferLeg(
                duration = Confidence(Duration(0), 0.95f, FieldSource.OCR),
                distance = Confidence(Distance(0), 0.95f, FieldSource.OCR),
                location = location,
            ),
            trip = OfferLeg(
                duration = Confidence(Duration(0), 0.95f, FieldSource.OCR),
                distance = Confidence(Distance(0), 0.95f, FieldSource.OCR),
                location = location,
            ),
        )

        val derived = OfferEvaluator().derive(offer)

        assertEquals(0L, derived.totalDistance.value?.meters)
        assertEquals(0L, derived.totalDuration.value?.seconds)
        assertEquals(null, derived.ratePerKm.value)
        assertEquals(null, derived.ratePerHour.value)
    }

    @Test
    fun `returns analyze when a field is not reliable`() {
        val offer = sampleOffer().copy(payout = Confidence(Money(2_590), 0.60f, FieldSource.OCR))
        val result = OfferEvaluator().evaluate(
            offer,
            listOf(FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 2_000)),
        )

        assertEquals(OfferDecision.ANALYZE, result.decision)
        assertEquals(MetricStatus.UNKNOWN, result.metrics.single().status)
    }

    @Test
    fun `rejects an eliminatory rule failure`() {
        val result = OfferEvaluator().evaluate(
            sampleOffer(),
            listOf(
                FilterRule(
                    metric = Metric.PICKUP_DISTANCE,
                    comparator = Comparator.AT_MOST,
                    target = 2_000,
                    mode = EvaluationMode.ELIMINATORY,
                ),
            ),
        )

        assertEquals(OfferDecision.REJECT, result.decision)
    }

    @Test
    fun `uses tolerance as a near result`() {
        val result = OfferEvaluator().evaluate(
            sampleOffer(),
            listOf(FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 2_800, tolerancePercent = 10)),
        )

        assertEquals(MetricStatus.NEAR, result.metrics.single().status)
        assertEquals(OfferDecision.ANALYZE, result.decision)
    }

    @Test
    fun `uses analyze when no rules are active`() {
        val result = OfferEvaluator().evaluate(sampleOffer(), emptyList())

        assertEquals(OfferDecision.ANALYZE, result.decision)
        assertEquals(0, result.weightedScore)
    }

    @Test
    fun `evaluates ends near home independently from direction hint`() {
        val offer = sampleOffer().copy(
            endsNearHome = Confidence(true, 1f, FieldSource.DERIVED),
            destinationDirectionHint = Confidence(false, 1f, FieldSource.OCR),
        )

        val result = OfferEvaluator().evaluate(
            offer,
            listOf(FilterRule(Metric.ENDS_NEAR_HOME, Comparator.IS_TRUE)),
        )

        assertEquals(MetricStatus.PASS, result.metrics.single().status)
        assertEquals(OfferDecision.ACCEPT, result.decision)
    }

    @Test
    fun `uses net profit metrics as first class decision filters`() {
        val result = OfferEvaluator().evaluate(
            offer = sampleOffer(),
            rules = listOf(
                FilterRule(Metric.NET_PROFIT, Comparator.AT_LEAST, target = 2_000),
                FilterRule(Metric.NET_PROFIT_PERCENT, Comparator.AT_LEAST, target = 75_00),
                FilterRule(Metric.NET_PROFIT_PER_HOUR, Comparator.AT_LEAST, target = 6_000),
            ),
            netProfit = Confidence(2_200L, 0.95f, FieldSource.DERIVED),
            netProfitPercent = Confidence(84_94L, 0.95f, FieldSource.DERIVED),
            netProfitPerHour = Confidence(6_600L, 0.95f, FieldSource.DERIVED),
        )

        assertEquals(
            listOf(Metric.NET_PROFIT, Metric.NET_PROFIT_PERCENT, Metric.NET_PROFIT_PER_HOUR),
            result.metrics.map { it.rule.metric },
        )
        assertEquals(listOf(MetricStatus.PASS, MetricStatus.PASS, MetricStatus.PASS), result.metrics.map { it.status })
        assertEquals(OfferDecision.ACCEPT, result.decision)
    }

    private fun sampleOffer() = NormalizedOffer(
        source = OfferSource.UBER,
        kind = OfferKind.UBER_STANDARD,
        detectedAtEpochMs = 0L,
        payout = confidentMoney,
        displayedRatePerKm = confidentMoney,
        bonus = Confidence(null, 0f, FieldSource.OCR),
        pickup = OfferLeg(confidentDuration, confidentDistance, location),
        trip = OfferLeg(confidentDuration, confidentDistance, location),
        passenger = Passenger(
            rating = Confidence(495L, 0.95f, FieldSource.OCR),
            tripCount = Confidence(100L, 0.95f, FieldSource.OCR),
            profile = Confidence("Verificado", 0.95f, FieldSource.OCR),
        ),
        serviceType = Confidence("UberX", 0.95f, FieldSource.OCR),
        stopCount = Confidence(1L, 0.95f, FieldSource.OCR),
        longTripHint = Confidence(false, 0.95f, FieldSource.OCR),
        destinationDirectionHint = Confidence(null, 0f, FieldSource.OCR),
        rawLayoutVersion = "fixture-uber-light-v1",
        fieldConfidence = emptyMap(),
    )
}
