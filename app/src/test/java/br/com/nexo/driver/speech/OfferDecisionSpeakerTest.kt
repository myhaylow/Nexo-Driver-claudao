package br.com.nexo.driver.speech

import br.com.nexo.driver.overlay.OfferOverlayUiModel
import br.com.nexo.driver.overlay.OverlayMetricUi
import br.com.nexo.driver.overlay.OverlayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferDecisionSpeakerTest {
    @Test
    fun `formats green yellow and red speech phrases with payout`() {
        assertEquals("aceitar corrida, R$ 25,90", OfferSpeechFormatter.phraseFor(model(OverlayStatus.ACCEPT)))
        assertEquals("analisar corrida, R$ 25,90", OfferSpeechFormatter.phraseFor(model(OverlayStatus.ANALYZE)))
        assertEquals("recusar corrida, R$ 25,90", OfferSpeechFormatter.phraseFor(model(OverlayStatus.REJECT)))
    }

    @Test
    fun `uses unavailable value phrase when payout is not readable`() {
        assertEquals(
            "aceitar corrida, valor não identificado",
            OfferSpeechFormatter.phraseFor(model(OverlayStatus.ACCEPT, payoutAvailable = false)),
        )
    }

    @Test
    fun `does not speak unknown status`() {
        assertNull(OfferSpeechFormatter.phraseFor(model(OverlayStatus.UNKNOWN)))
    }

    @Test
    fun `deduplicates the same offer phrase inside the window`() {
        var now = 1_000L
        val deduplicator = OfferSpeechDeduplicator(windowMs = 15_000L, nowMs = { now })
        val model = model(OverlayStatus.ACCEPT)
        val phrase = requireNotNull(OfferSpeechFormatter.phraseFor(model))

        assertFalse(deduplicator.isDuplicate(model, phrase))
        now += 1_000L
        assertTrue(deduplicator.isDuplicate(model, phrase))
        now += 20_000L
        assertFalse(deduplicator.isDuplicate(model, phrase))
    }

    private fun model(
        status: OverlayStatus,
        payoutAvailable: Boolean = true,
    ) = OfferOverlayUiModel(
        status = status,
        totalDuration = "20 min",
        payout = "R$ 25,90",
        isPayoutAvailable = payoutAvailable,
        ratePerKm = OverlayMetricUi("R$ 2,50", OverlayStatus.ACCEPT),
        ratePerHour = OverlayMetricUi("R$ 70,00", OverlayStatus.ACCEPT),
        passengerRating = OverlayMetricUi("4,95", OverlayStatus.ACCEPT),
        pickup = OverlayMetricUi("3 min · 1,2 km", OverlayStatus.ACCEPT),
    )
}
