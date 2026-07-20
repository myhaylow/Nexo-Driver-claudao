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
import org.junit.Assert.assertEquals
import org.junit.Test

class OfferSessionMetricsRepositoryTest {
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
}
