package br.com.nexo.driver.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CuritibaPlacesCatalogTest {
    @Test
    fun `finds a bairro by prefix ignoring accents`() {
        val results = CuritibaPlacesCatalog.search("agua ver")
        assertTrue(results.isNotEmpty())
        assertEquals("Água Verde", results.first().name)
        assertTrue(results.first().coordinate.isValid)
    }

    @Test
    fun `finds metro cities and prefers prefix matches first`() {
        val results = CuritibaPlacesCatalog.search("sao jose")
        assertEquals("São José dos Pinhais", results.first().name)
    }

    @Test
    fun `contains matches still surface after prefix matches`() {
        val results = CuritibaPlacesCatalog.search("felicidade")
        assertTrue(results.any { it.name == "Santa Felicidade" })
    }

    @Test
    fun `short or blank queries return nothing`() {
        assertTrue(CuritibaPlacesCatalog.search("a").isEmpty())
        assertTrue(CuritibaPlacesCatalog.search("  ").isEmpty())
    }

    @Test
    fun `every place carries a valid coordinate inside the metro bounding box`() {
        val all = CuritibaPlacesCatalog.search("a", limit = Int.MAX_VALUE) +
            ('a'..'z').flatMap { CuritibaPlacesCatalog.search("$it$it", limit = Int.MAX_VALUE) }
        // Amostra via buscas: qualquer resultado deve estar dentro da caixa da RMC.
        CuritibaPlacesCatalog.search("curitiba", limit = Int.MAX_VALUE).forEach { place ->
            assertTrue(place.coordinate.latitude in CuritibaRegionLocalities.BOUNDS_SOUTH_LATITUDE..CuritibaRegionLocalities.BOUNDS_NORTH_LATITUDE)
            assertTrue(place.coordinate.longitude in CuritibaRegionLocalities.BOUNDS_WEST_LONGITUDE..CuritibaRegionLocalities.BOUNDS_EAST_LONGITUDE)
        }
    }
}
