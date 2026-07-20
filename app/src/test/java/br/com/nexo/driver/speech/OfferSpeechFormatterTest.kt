package br.com.nexo.driver.speech

import br.com.nexo.driver.overlay.OfferOverlayUiModel
import br.com.nexo.driver.overlay.OverlayMetricUi
import br.com.nexo.driver.overlay.OverlayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferSpeechFormatterTest {
    @Test
    fun `speaks the decision and value`() {
        assertEquals("aceitar corrida, R$ 25,90", OfferSpeechFormatter.phraseFor(model(OverlayStatus.ACCEPT)))
    }

    @Test
    fun `appends sentido casa when the trip goes home`() {
        val phrase = OfferSpeechFormatter.phraseFor(model(OverlayStatus.ACCEPT, isTowardHome = true))
        assertEquals("aceitar corrida, R$ 25,90, sentido casa", phrase)
    }

    @Test
    fun `announces supermercado for a blocked pickup and ignores home`() {
        val phrase = OfferSpeechFormatter.phraseFor(
            model(OverlayStatus.REJECT, isTowardHome = true, isBlockedSupermarket = true),
        )
        assertEquals("recusar corrida, supermercado", phrase)
    }

    @Test
    fun `stays silent when the decision is unknown`() {
        assertNull(OfferSpeechFormatter.phraseFor(model(OverlayStatus.UNKNOWN)))
    }

    private fun model(
        status: OverlayStatus,
        isTowardHome: Boolean = false,
        isBlockedSupermarket: Boolean = false,
    ) = OfferOverlayUiModel(
        status = status,
        totalDuration = "20 min",
        payout = "R$ 25,90",
        ratePerKm = OverlayMetricUi("1,95", status),
        ratePerHour = OverlayMetricUi("48,30", status),
        passengerRating = OverlayMetricUi("4,95", status),
        pickup = OverlayMetricUi("3 min · 1,2 km", status),
        isTowardHome = isTowardHome,
        isBlockedSupermarket = isBlockedSupermarket,
    )
}
