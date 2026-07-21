package br.com.nexo.driver.destination

import br.com.nexo.driver.destination.offline.OfflineAddressNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuritibaRegionLocalitiesTest {

    @Test
    fun `strips single trailing locality suffixes`() {
        assertEquals(
            listOf("rua", "xv", "de", "novembro", "1500"),
            CuritibaRegionLocalities.stripTrailingLocalities(
                listOf("rua", "xv", "de", "novembro", "1500", "curitiba"),
            ),
        )
    }

    @Test
    fun `strips stacked suffixes like city plus state plus country`() {
        assertEquals(
            listOf("rua", "das", "flores", "100"),
            CuritibaRegionLocalities.stripTrailingLocalities(
                listOf("rua", "das", "flores", "100", "sao", "jose", "dos", "pinhais", "pr", "brasil"),
            ),
        )
    }

    @Test
    fun `keeps locality words in the middle of a street name`() {
        assertEquals(
            listOf("rua", "curitiba", "250", "centro"),
            CuritibaRegionLocalities.stripTrailingLocalities(
                listOf("rua", "curitiba", "250", "centro"),
            ),
        )
    }

    @Test
    fun `normalizer strips provider-appended metro localities so the pack key still matches`() {
        assertEquals(
            "rua das flores 100",
            OfflineAddressNormalizer.normalize("R. das Flores, 100 - Fazenda Rio Grande, PR, Brasil"),
        )
        assertEquals(
            "avenida das torres 3000",
            OfflineAddressNormalizer.normalize("Av. das Torres, 3000 — São José dos Pinhais - PR"),
        )
    }

    @Test
    fun `a query that is only a locality keeps its text instead of becoming empty`() {
        // "Curitiba" sozinho nunca deve virar chave vazia (ambígua); permanece como estava.
        assertEquals("curitiba", OfflineAddressNormalizer.normalize("Curitiba"))
    }

    @Test
    fun `home matcher no longer confuses a city-only destination with a street in that city`() {
        val matcher = HomeMatcher()
        val home = HomeDestination(
            enabled = true,
            originalAddress = "Rua São José, 100 - São José dos Pinhais",
            standardizedAddress = null,
            coordinate = null,
            arrivalRadiusMeters = 300.0,
        )
        // A oferta termina no centro da cidade, não na rua da casa: não pode casar por texto.
        val result = matcher.match(
            home,
            OfferDestination(originalAddress = "São José dos Pinhais - PR, Brasil"),
        )

        assertNull(result.endsNearHome)
    }

    @Test
    fun `home matcher still matches the same street despite metro locality suffixes`() {
        val matcher = HomeMatcher()
        val home = HomeDestination(
            enabled = true,
            originalAddress = "Rua Marechal Deodoro, 850",
            standardizedAddress = null,
            coordinate = null,
            arrivalRadiusMeters = 300.0,
        )
        val result = matcher.match(
            home,
            OfferDestination(originalAddress = "R. Marechal Deodoro, 850 - Centro, Curitiba - PR, Brasil"),
        )

        assertEquals(true, result.endsNearHome)
    }
}
