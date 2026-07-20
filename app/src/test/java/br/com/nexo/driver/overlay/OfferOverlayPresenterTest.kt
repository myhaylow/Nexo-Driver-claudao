package br.com.nexo.driver.overlay

import br.com.nexo.driver.cost.FuelSettings
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.EvaluationResult
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.evaluation.MetricEvaluation
import br.com.nexo.driver.evaluation.MetricStatus
import br.com.nexo.driver.evaluation.OfferDecision
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
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferOverlayPresenterTest {
    private val presenter = OfferOverlayPresenter()

    @Test
    fun `presents Driver Inteligente with one primary payout and four configured metrics`() {
        val model = presenter.present(
            offer = sampleOffer(),
            result = result(
                decision = OfferDecision.ANALYZE,
                Metric.PAYOUT to MetricStatus.NEAR,
                Metric.RATE_PER_KM to MetricStatus.PASS,
                Metric.RATE_PER_HOUR to MetricStatus.FAIL,
                Metric.PASSENGER_RATING to MetricStatus.PASS,
                Metric.PICKUP_DURATION to MetricStatus.PASS,
                Metric.PICKUP_DISTANCE to MetricStatus.PASS,
            ),
        )

        assertEquals("Driver inteligente", model.appName)
        assertEquals("R$ 25,90", model.payout)
        assertEquals(OverlayStatus.ANALYZE, model.payoutStatus)
        assertEquals("R$ 5,18", model.ratePerKm.value)
        assertEquals(OverlayStatus.ACCEPT, model.ratePerKm.status)
        assertEquals("R$ 77,70", model.ratePerHour.value)
        assertEquals(OverlayStatus.REJECT, model.ratePerHour.status)
        assertEquals("4,95", model.passengerRating.value)
        assertEquals("5 min · 1,0 km", model.pickup.value)

        // The model has exactly one payout field. The 2×2 grid is reserved
        // for R$/km, R$/h, rating and pickup, preventing duplicated payment.
        assertEquals(4, listOf(model.ratePerKm, model.ratePerHour, model.passengerRating, model.pickup).size)
    }

    @Test
    fun `keeps pickup under analysis when one pickup rule is near`() {
        val model = presenter.present(
            offer = sampleOffer(),
            result = result(
                decision = OfferDecision.ANALYZE,
                Metric.PICKUP_DURATION to MetricStatus.PASS,
                Metric.PICKUP_DISTANCE to MetricStatus.NEAR,
            ),
        )

        assertEquals(OverlayStatus.ANALYZE, model.pickup.status)
        assertTrue(model.pickup.isAvailable)
    }

    @Test
    fun `marks unavailable OCR values as unknown without borrowing decision colour`() {
        val offer = sampleOffer().copy(
            payout = Confidence(Money(2_590), 0.50f, FieldSource.OCR),
            passenger = sampleOffer().passenger.copy(
                rating = Confidence(495L, 0.50f, FieldSource.OCR),
            ),
        )
        val model = presenter.present(
            offer = offer,
            result = result(
                decision = OfferDecision.ACCEPT,
                Metric.PAYOUT to MetricStatus.PASS,
                Metric.PASSENGER_RATING to MetricStatus.PASS,
            ),
        )

        assertFalse(model.isPayoutAvailable)
        assertEquals(OverlayStatus.UNKNOWN, model.payoutStatus)
        assertEquals("—", model.payout)
        assertFalse(model.passengerRating.isAvailable)
        assertEquals(OverlayStatus.UNKNOWN, model.passengerRating.status)
        assertEquals("—", model.passengerRating.value)
    }

    @Test
    fun `uses the persisted four cell order without adding payment again`() {
        val selectedFields = listOf(
            OverlayMetricField.TOTAL_DISTANCE,
            OverlayMetricField.TOTAL_DURATION,
            OverlayMetricField.PASSENGER_RATING,
            OverlayMetricField.PICKUP,
        )
        val model = presenter.present(
            offer = sampleOffer(),
            result = result(
                decision = OfferDecision.ACCEPT,
                Metric.TOTAL_DISTANCE to MetricStatus.PASS,
                Metric.TOTAL_DURATION to MetricStatus.PASS,
            ),
            gridFields = selectedFields,
        )

        assertEquals(selectedFields, model.gridFields)
        assertEquals("5,0 km", model.metricFor(OverlayMetricField.TOTAL_DISTANCE).value)
        assertEquals("20 min", model.metricFor(OverlayMetricField.TOTAL_DURATION).value)
        assertEquals(OverlayStatus.ACCEPT, model.metricFor(OverlayMetricField.TOTAL_DURATION).status)
        assertEquals("R$ 25,90", model.payout)
    }

    @Test
    fun `renders independently calculated total time distance rates and passenger rating`() {
        val model = presenter.present(
            offer = sampleOffer().copy(
                payout = Confidence(Money(2_400), 0.95f, FieldSource.OCR),
                pickup = OfferLeg(
                    duration = Confidence(Duration(12 * 60L), 0.95f, FieldSource.OCR),
                    distance = Confidence(Distance(1_500), 0.95f, FieldSource.OCR),
                    location = Confidence(GeoText(null, null), 0.95f, FieldSource.OCR),
                ),
                trip = OfferLeg(
                    duration = Confidence(Duration(18 * 60L), 0.95f, FieldSource.OCR),
                    distance = Confidence(Distance(3_500), 0.95f, FieldSource.OCR),
                    location = Confidence(GeoText(null, null), 0.95f, FieldSource.OCR),
                ),
                passenger = sampleOffer().passenger.copy(
                    rating = Confidence(487L, 0.95f, FieldSource.OCR),
                ),
            ),
            result = result(
                decision = OfferDecision.ACCEPT,
                Metric.RATE_PER_KM to MetricStatus.PASS,
                Metric.RATE_PER_HOUR to MetricStatus.PASS,
                Metric.TOTAL_DURATION to MetricStatus.PASS,
                Metric.TOTAL_DISTANCE to MetricStatus.PASS,
                Metric.PASSENGER_RATING to MetricStatus.PASS,
            ),
            gridFields = listOf(
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_HOUR,
                OverlayMetricField.TOTAL_DURATION,
                OverlayMetricField.TOTAL_DISTANCE,
            ),
        )

        assertEquals("30 min", model.totalDuration)
        assertEquals("R$\u00a04,80", model.ratePerKm.value)
        assertEquals("R$\u00a048,00", model.ratePerHour.value)
        assertEquals("4,87", model.passengerRating.value)
        assertEquals("30 min", model.totalDurationMetric.value)
        assertEquals("5,0 km", model.totalDistance.value)
        assertEquals(OverlayStatus.ACCEPT, model.ratePerKm.status)
        assertEquals(OverlayStatus.ACCEPT, model.ratePerHour.status)
        assertEquals(OverlayStatus.ACCEPT, model.passengerRating.status)
        assertEquals(OverlayStatus.ACCEPT, model.totalDurationMetric.status)
        assertEquals(OverlayStatus.ACCEPT, model.totalDistance.status)
    }

    @Test
    fun `uses the most restrictive colour when a metric has both bounds`() {
        val model = presenter.present(
            offer = sampleOffer(),
            result = result(
                decision = OfferDecision.REJECT,
                Metric.TOTAL_DISTANCE to MetricStatus.PASS,
                Metric.TOTAL_DISTANCE to MetricStatus.FAIL,
            ),
            gridFields = listOf(
                OverlayMetricField.TOTAL_DISTANCE,
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_HOUR,
                OverlayMetricField.PICKUP,
            ),
        )

        assertEquals(OverlayStatus.REJECT, model.metricFor(OverlayMetricField.TOTAL_DISTANCE).status)
    }

    @Test
    fun `renders profit metrics with their own rule colours`() {
        val model = presenter.present(
            offer = sampleOffer(),
            result = result(
                decision = OfferDecision.ACCEPT,
                Metric.NET_PROFIT to MetricStatus.PASS,
                Metric.NET_PROFIT_PERCENT to MetricStatus.NEAR,
                Metric.NET_PROFIT_PER_HOUR to MetricStatus.FAIL,
            ),
            gridFields = listOf(
                OverlayMetricField.NET_PROFIT,
                OverlayMetricField.NET_PROFIT_PERCENT,
                OverlayMetricField.NET_PROFIT_PER_HOUR,
                OverlayMetricField.RATE_PER_KM,
            ),
            fuelSettings = FuelSettings(pricePerLiterCents = 400, kilometersPerLiter = 10.0),
        )

        assertEquals("R$ 23,90", model.netProfit.value)
        assertEquals(OverlayStatus.ACCEPT, model.netProfit.status)
        assertEquals("92%", model.netProfitPercent.value)
        assertEquals(OverlayStatus.ANALYZE, model.netProfitPercent.status)
        assertEquals("R$ 71,70", model.netProfitPerHour.value)
        assertEquals(OverlayStatus.REJECT, model.netProfitPerHour.status)
    }

    private fun result(
        decision: OfferDecision,
        vararg statuses: Pair<Metric, MetricStatus>,
    ): EvaluationResult = EvaluationResult(
        metrics = statuses.map { (metric, status) ->
            MetricEvaluation(
                rule = FilterRule(metric, Comparator.AT_LEAST, target = 1),
                observedValue = 1,
                confidence = 0.95f,
                status = status,
                score = 100,
            )
        },
        weightedScore = 0,
        decision = decision,
    )

    private fun sampleOffer() = NormalizedOffer(
        source = OfferSource.UBER,
        kind = OfferKind.UBER_STANDARD,
        detectedAtEpochMs = 0L,
        payout = Confidence(Money(2_590), 0.95f, FieldSource.OCR),
        displayedRatePerKm = Confidence(Money(518), 0.95f, FieldSource.OCR),
        bonus = Confidence(null, 0f, FieldSource.OCR),
        pickup = OfferLeg(
            duration = Confidence(Duration(300), 0.95f, FieldSource.OCR),
            distance = Confidence(Distance(1_000), 0.95f, FieldSource.OCR),
            location = Confidence(GeoText(null, null), 0.95f, FieldSource.OCR),
        ),
        trip = OfferLeg(
            duration = Confidence(Duration(900), 0.95f, FieldSource.OCR),
            distance = Confidence(Distance(4_000), 0.95f, FieldSource.OCR),
            location = Confidence(GeoText(null, null), 0.95f, FieldSource.OCR),
        ),
        passenger = Passenger(
            rating = Confidence(495L, 0.95f, FieldSource.OCR),
            tripCount = Confidence(200L, 0.95f, FieldSource.OCR),
            profile = Confidence("Verificado", 0.95f, FieldSource.OCR),
        ),
        serviceType = Confidence("UberX", 0.95f, FieldSource.OCR),
        stopCount = Confidence(1L, 0.95f, FieldSource.OCR),
        longTripHint = Confidence(false, 0.95f, FieldSource.OCR),
        destinationDirectionHint = Confidence(true, 0.95f, FieldSource.OCR),
        rawLayoutVersion = "overlay-test",
        fieldConfidence = emptyMap(),
    )
}
