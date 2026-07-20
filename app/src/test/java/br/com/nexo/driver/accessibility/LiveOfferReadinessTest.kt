package br.com.nexo.driver.accessibility

import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.parser.OfferParserRegistry
import br.com.nexo.driver.parser.RawOfferText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOfferReadinessTest {
    private val parser = OfferParserRegistry()

    @Test
    fun `complete real Uber accessibility shape is ready`() {
        val offer = parser.parse(
            RawOfferText(
                text = """
                    Radar de Viagens
                    2
                    UberX
                    R$ 36,57
                    R$ 1,77/km est.
                    4,98 (93)
                    1 min (0.3 km)
                    Local de retirada
                    45 minutos (20.4 km)
                    Destino
                    Viagem longa (mais de 30 min)
                    Selecionar
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
                fieldSource = FieldSource.ACCESSIBILITY,
            ),
        )!!

        assertTrue(offer.isReadyForLiveAnalysis())
    }

    @Test
    fun `earnings chip without route legs is not ready`() {
        val offer = parser.parse(
            RawOfferText(
                text = "UberX\nR$ 0,00",
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
                fieldSource = FieldSource.ACCESSIBILITY,
            ),
        )!!

        assertFalse(offer.isReadyForLiveAnalysis())
    }
}
