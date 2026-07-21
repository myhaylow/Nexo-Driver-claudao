package br.com.nexo.driver.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferCardTextRegionExtractorTest {
    @Test
    fun `Uber card excludes map balance and keeps offer fields`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf(
                "R$ 0,00",
                "Mapa e zonas",
                "UberX",
                "R$ 13,58",
                "R$ 1,29/km est.",
                "4,89 (245)",
                "3 min (1.2 km)",
                "Retirada",
                "19 minutos (9.3 km)",
                "Destino",
                "Selecionar",
                "Texto fora do card",
            ),
            layoutHint = "uber",
        )

        assertNotNull(result)
        assertEquals("UberX", result!!.first())
        assertFalse(result.contains("R$ 0,00"))
        assertFalse(result.contains("Texto fora do card"))
        assertTrue(result.contains("R$ 13,58"))
    }

    @Test
    fun `99 standard card keeps two route legs`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf(
                "Mapa",
                "Pgto. no app",
                "R$ 8,50",
                "R$ 1,50/km",
                "4,96 · 112 corridas · Perfil Essencial",
                "5min (1,6km)",
                "Retirada",
                "6min (4,1km)",
                "Destino",
            ),
            layoutHint = "99",
        )

        assertNotNull(result)
        assertEquals(2, result!!.count { it.contains("min") })
    }

    @Test
    fun `Uber Priority card is a valid accessibility anchor`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf(
                "Mapa",
                "Priority",
                "R$ 23,11",
                "R$ 1,76/km est.",
                "5,00 (2)",
                "14 min (6.6 km)",
                "Rua 3, Ipê, São José dos Pinhais",
                "11 minutos (6.5 km)",
                "Rua Antônio Emílio Cumin, Uberaba, Curitiba",
                "Selecionar",
            ),
            layoutHint = "uber",
        )

        assertNotNull(result)
        assertEquals("Priority", result!!.first())
        assertTrue(result.contains("R$ 23,11"))
    }

    @Test
    fun `Uber card can start at payout when service anchor is absent`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf(
                "Mapa",
                "R$ 23,11",
                "R$ 1,76/km est.",
                "5,00 (2)",
                "14 min",
                "6.6 km",
                "Rua 3, Ipê, São José dos Pinhais",
                "11 minutos",
                "6.5 km",
                "Rua Antônio Emílio Cumin, Uberaba, Curitiba",
                "Selecionar",
            ),
            layoutHint = "uber",
        )

        assertNotNull(result)
        assertEquals("R$ 23,11", result!!.first())
        assertTrue(result.contains("11 minutos"))
    }

    @Test
    fun `Uber card keeps split route legs with extra accessibility labels`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf(
                "Mapa",
                "Solicitação de corrida",
                "R$ 23,11",
                "R$ 1,76/km est.",
                "5,00 (2)",
                "14 min até o local de partida",
                "6.6 km",
                "11 minutos até o destino",
                "6.5 km",
                "Selecionar",
            ),
            layoutHint = "uber",
        )

        assertNotNull(result)
        assertEquals("R$ 23,11", result!!.first())
        assertTrue(result.contains("14 min até o local de partida"))
        assertTrue(result.contains("11 minutos até o destino"))
    }

    @Test
    fun `partial balance is not an offer card`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf("UberX", "R$ 5,75"),
            layoutHint = "uber",
        )

        assertEquals(null, result)
    }

    @Test
    fun `an Uber tray yields one window per card`() {
        val windows = OfferCardTextRegionExtractor.extractAll(
            rawLines = listOf(
                "Mapa",
                "UberX",
                "R$ 13,58",
                "R$ 1,29/km est.",
                "3 min (1.2 km)",
                "Retirada",
                "19 minutos (9.3 km)",
                "Destino",
                "Selecionar",
                "Comfort",
                "R$ 24,00",
                "R$ 1,80/km est.",
                "5 min (2.0 km)",
                "Retirada",
                "25 minutos (12.0 km)",
                "Destino",
                "Selecionar",
            ),
            layoutHint = "uber",
        )

        assertEquals(2, windows.size)
        assertTrue(windows.any { it.first() == "UberX" })
        assertTrue(windows.any { it.first() == "Comfort" })
        // Each window keeps only its own card's payout.
        assertTrue(windows.first { it.first() == "UberX" }.contains("R$ 13,58"))
        assertFalse(windows.first { it.first() == "UberX" }.contains("R$ 24,00"))
    }

    @Test
    fun `a single card yields exactly one window and matches extract`() {
        val lines = listOf(
            "Mapa",
            "Priority",
            "R$ 23,11",
            "R$ 1,76/km est.",
            "5,00 (2)",
            "14 min (6.6 km)",
            "Rua 3, Ipê",
            "11 minutos (6.5 km)",
            "Rua Antônio Emílio Cumin",
            "Selecionar",
        )

        val windows = OfferCardTextRegionExtractor.extractAll(lines, layoutHint = "uber")

        assertEquals(1, windows.size)
        assertEquals(OfferCardTextRegionExtractor.extract(lines, layoutHint = "uber"), windows.first())
    }
}
