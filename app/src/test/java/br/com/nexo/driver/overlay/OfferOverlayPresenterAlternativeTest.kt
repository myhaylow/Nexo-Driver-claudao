package br.com.nexo.driver.overlay

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

class OfferOverlayPresenterAlternativeTest {
    private val presenter = OfferOverlayPresenter()

    private val overlay = OfferOverlayUiModel(
        status = OverlayStatus.ANALYZE,
        totalDuration = "20 min",
        payout = "R$ 24,00",
        isPayoutAvailable = true,
        ratePerKm = OverlayMetricUi("R$ 1,55", OverlayStatus.ANALYZE, isAvailable = true),
        ratePerHour = OverlayMetricUi("R$ 40,00", OverlayStatus.ACCEPT),
        passengerRating = OverlayMetricUi("4,90", OverlayStatus.ACCEPT),
        pickup = OverlayMetricUi("3 min · 1,0 km", OverlayStatus.ACCEPT),
    )

    @Test
    fun `uses the service tier as the provider label when present`() {
        val alt = presenter.alternativeOf(offer(serviceType = "Comfort"), overlay)

        assertEquals("Comfort", alt.provider)
        assertEquals("R$ 24,00", alt.payout)
        assertEquals("R$ 1,55/km", alt.ratePerKm)
        assertEquals(OverlayStatus.ANALYZE, alt.status)
    }

    @Test
    fun `falls back to the platform name when the tier was not read`() {
        assertEquals("Uber", presenter.alternativeOf(offer(serviceType = null, source = OfferSource.UBER), overlay).provider)
        assertEquals("99", presenter.alternativeOf(offer(serviceType = null, source = OfferSource.NINETY_NINE), overlay).provider)
    }

    @Test
    fun `unavailable payout and rate collapse to a dash`() {
        val blank = overlay.copy(
            isPayoutAvailable = false,
            ratePerKm = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
        )

        val alt = presenter.alternativeOf(offer(serviceType = "UberX"), blank)

        assertEquals("—", alt.payout)
        assertEquals("—", alt.ratePerKm)
    }

    private fun offer(serviceType: String?, source: OfferSource = OfferSource.UBER) = NormalizedOffer(
        source = source,
        kind = OfferKind.UBER_STANDARD,
        detectedAtEpochMs = 0L,
        payout = Confidence(Money(2_400), 1f, FieldSource.OCR),
        displayedRatePerKm = Confidence(null, 0f, FieldSource.OCR),
        bonus = Confidence(null, 0f, FieldSource.OCR),
        pickup = OfferLeg(Confidence(Duration(180), 1f, FieldSource.OCR), Confidence(Distance(1_000), 1f, FieldSource.OCR), Confidence(GeoText(null, null), 1f, FieldSource.OCR)),
        trip = OfferLeg(Confidence(Duration(1_200), 1f, FieldSource.OCR), Confidence(Distance(6_000), 1f, FieldSource.OCR), Confidence(GeoText(null, null), 1f, FieldSource.OCR)),
        passenger = Passenger(Confidence(490L, 1f, FieldSource.OCR), Confidence(null, 0f, FieldSource.OCR), Confidence(null, 0f, FieldSource.OCR)),
        serviceType = Confidence(serviceType, if (serviceType == null) 0f else 1f, FieldSource.OCR),
        stopCount = Confidence(null, 0f, FieldSource.OCR),
        longTripHint = Confidence(null, 0f, FieldSource.OCR),
        destinationDirectionHint = Confidence(null, 0f, FieldSource.OCR),
        rawLayoutVersion = "test",
        fieldConfidence = emptyMap(),
    )
}
