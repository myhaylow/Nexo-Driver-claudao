package br.com.nexo.driver.screenreader.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FieldParsersTest {
    @Test fun currencySupportsBrazilianFormats() {
        assertEquals(BigDecimal("18.72"), CurrencyParser.parse("R$ 18,72"))
        assertEquals(BigDecimal("1234.56"), CurrencyParser.parse("R$ 1.234,56"))
    }
    @Test fun distanceNormalizesMetresAndKilometres() {
        assertEquals(0.85, DistanceParser.parseKilometres("850 m"))
        assertEquals(1.2, DistanceParser.parseKilometres("1,2 km"))
    }
    @Test fun durationNormalizesBrazilianFormats() {
        assertEquals(72, DurationParser.parseMinutes("1 h 12 min"))
        assertEquals(8, DurationParser.parseMinutes("8 min"))
    }
    @Test fun ratingAcceptsCommaAndStar() {
        assertEquals(4.92, RatingParser.parse("★ 4,92"))
        assertEquals(4.85, RatingParser.parse("4.85"))
    }
}
