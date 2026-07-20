package br.com.nexo.driver.analysis

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
import br.com.nexo.driver.evaluation.Metric
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferSessionMetricsRepositoryTest {

    @After
    fun tearDown() {
        // The repository is a process-global singleton; leave the sample ring clean between tests.
        OfferSessionMetricsRepository.clearSamples()
    }

    @Test
    fun repeatedOfferWithinWindowCountsOnce() {
        val before = OfferSessionMetricsRepository.current().offersEvaluated
        fun <T> missing() = Confidence<T>(null, 0f, FieldSource.DERIVED)
        val offer = NormalizedOffer(
            source = OfferSource.UBER,
            kind = OfferKind.UBER_STANDARD,
            detectedAtEpochMs = 1L,
            payout = Confidence(Money(1_234L), 1f, FieldSource.OCR),
            displayedRatePerKm = missing(),
            bonus = missing(),
            pickup = OfferLeg(missing<Duration>(), missing<Distance>(), missing<GeoText>()),
            trip = OfferLeg(missing<Duration>(), missing<Distance>(), missing<GeoText>()),
            passenger = Passenger(missing(), missing(), missing()),
            serviceType = missing(),
            stopCount = missing(),
            longTripHint = missing(),
            destinationDirectionHint = missing(),
            rawLayoutVersion = "test",
            fieldConfidence = emptyMap(),
        )

        OfferSessionMetricsRepository.record(offer, nowMs = 10_000L)
        OfferSessionMetricsRepository.record(offer, nowMs = 11_000L)

        assertEquals(before + 1, OfferSessionMetricsRepository.current().offersEvaluated)
    }

    @Test
    fun `an all-OCR session is only flagged once there is enough signal`() {
        assertFalse(OfferSessionMetrics(readsViaOcr = 1, totalReads = 1).isOcrOnly)
        assertTrue(OfferSessionMetrics(readsViaOcr = 3, totalReads = 3).isOcrOnly)
    }

    @Test
    fun `a session with any accessibility read is not flagged as OCR-only`() {
        assertFalse(OfferSessionMetrics(readsViaOcr = 4, totalReads = 5).isOcrOnly)
    }

    @Test
    fun `impact is null before any sample and passing-of-total after`() {
        assertNull(
            "no samples yet must read as unknown, not 0 of 0",
            OfferSessionMetricsRepository.impactOf(Metric.RATE_PER_KM, target = 175L, atLeast = true),
        )

        listOf(149L, 218L, 180L).forEach { rate ->
            OfferSessionMetricsRepository.recordSamples(mapOf(Metric.RATE_PER_KM to rate))
        }

        val atLeast = OfferSessionMetricsRepository.impactOf(Metric.RATE_PER_KM, target = 175L, atLeast = true)
        assertEquals(ImpactSample(passing = 2, total = 3), atLeast)
        assertEquals(66, atLeast?.percent)

        assertEquals(
            ImpactSample(passing = 3, total = 3),
            OfferSessionMetricsRepository.impactOf(Metric.RATE_PER_KM, target = 149L, atLeast = true),
        )
    }

    @Test
    fun `at-most rules count the other direction`() {
        listOf(1_000L, 3_000L, 5_000L).forEach {
            OfferSessionMetricsRepository.recordSamples(mapOf(Metric.PICKUP_DISTANCE to it))
        }

        assertEquals(
            ImpactSample(passing = 2, total = 3),
            OfferSessionMetricsRepository.impactOf(Metric.PICKUP_DISTANCE, target = 3_000L, atLeast = false),
        )
    }

    @Test
    fun `a metric with no samples reports unknown even when others have data`() {
        OfferSessionMetricsRepository.recordSamples(mapOf(Metric.RATE_PER_KM to 200L))

        assertNull(OfferSessionMetricsRepository.impactOf(Metric.PASSENGER_RATING, target = 480L, atLeast = true))
    }

    @Test
    fun `the sample ring is bounded`() {
        repeat(200) { OfferSessionMetricsRepository.recordSamples(mapOf(Metric.RATE_PER_KM to it.toLong())) }

        val impact = OfferSessionMetricsRepository.impactOf(Metric.RATE_PER_KM, target = Long.MAX_VALUE, atLeast = false)
        assertTrue("ring must stay bounded", (impact?.total ?: 0) in 1..60)
    }
}
