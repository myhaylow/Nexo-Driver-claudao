package br.com.nexo.driver.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLineSanitizerTest {

    @Test
    fun `repairs letters misread as digits inside numbers`() {
        assertEquals("R$ 1058", OcrLineSanitizer.sanitize("R$ 1O58"))
        assertEquals("12 min", OcrLineSanitizer.sanitize("l2 min"))
        assertEquals("R$ 18,50", OcrLineSanitizer.sanitize("R$ 1B,50"))
    }

    @Test
    fun `leaves letters inside words untouched`() {
        // The whole point of the digit lookaround: street names must survive intact, because the
        // destination matching downstream consumes them.
        assertEquals("Rua Ipiranga, Bloco G", OcrLineSanitizer.sanitize("Rua Ipiranga, Bloco G"))
        assertEquals("Pgto. no app", OcrLineSanitizer.sanitize("Pgto. no app"))
        assertEquals("UberX Comfort", OcrLineSanitizer.sanitize("UberX Comfort"))
    }

    @Test
    fun `normalizes currency spacing without throwing on the dollar sign`() {
        assertEquals("R$ 24,50", OcrLineSanitizer.sanitize("R $ 24,50"))
        assertEquals("R$ 24,50", OcrLineSanitizer.sanitize("R$24,50"))
    }

    @Test
    fun `repairs the unit words the leg regex depends on`() {
        assertEquals("5 min (1,6 km)", OcrLineSanitizer.sanitize("5 mnin (1,6 km)"))
        assertEquals("19 min (9,3 km)", OcrLineSanitizer.sanitize("19 rnin (9,3 km)"))
    }

    @Test
    fun `separates a unit fused to its number`() {
        assertEquals("12,3 km", OcrLineSanitizer.sanitize("12,3km"))
        assertEquals("5 min", OcrLineSanitizer.sanitize("5min"))
    }

    @Test
    fun `a damaged Uber card becomes parseable again`() {
        val damaged = listOf("UberX", "R$ 1O58", "4,89 (245)", "3 mnin (1,2 km)", "19 min (9,3km)")
        val offer = OfferParserRegistry().parse(
            RawOfferText(
                text = damaged.joinToString("\n"),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(1_058L, offer?.payout?.value?.cents)
        assertEquals(1_200L, offer?.pickup?.distance?.value?.meters)
        assertEquals(1_140L, offer?.trip?.duration?.value?.seconds)
    }
}
