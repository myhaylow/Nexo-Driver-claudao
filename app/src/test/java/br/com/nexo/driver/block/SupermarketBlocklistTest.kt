package br.com.nexo.driver.block

import br.com.nexo.driver.destination.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupermarketBlocklistTest {
    private val tsv = """
        # comment line is ignored
        id	nome	aliases	rede	cidade	uf	latitude	longitude	raio_m	decisao	fonte	versao
        osm-1	Atacadão	atacadao|atacadão	Atacadão	Curitiba	PR	-25.4146766	-49.3520968	180	BLOQUEAR	OSM	2026-07
        osm-2	Condor	condor|supermercado condor	Condor	Curitiba	PR	-25.4926137	-49.2423624	180	BLOQUEAR	OSM	2026-07
    """.trimIndent()

    private val blocklist = SupermarketBlocklistTsvCodec.decode(tsv)

    @Test
    fun `parses rows and ignores comments and header`() {
        assertEquals(2, blocklist.points.size)
        assertEquals("Atacadão", blocklist.points.first().name)
    }

    @Test
    fun `matches a pickup within the establishment radius`() {
        // ~50 m north of the Atacadão point stays inside the 180 m radius.
        val pickup = GeoCoordinate(-25.4142, -49.3520968)
        val result = blocklist.matchPickup(pickup, pickupAddress = null)

        assertTrue(result.matched)
        assertEquals(SupermarketMatchMethod.GEO_DISTANCE, result.method)
        assertEquals("osm-1", result.point?.id)
    }

    @Test
    fun `does not match a pickup outside every radius`() {
        val pickup = GeoCoordinate(-25.5000, -49.4000)
        assertFalse(blocklist.matchPickup(pickup, pickupAddress = null).matched)
    }

    @Test
    fun `falls back to an alias match when no coordinate is available`() {
        val result = blocklist.matchPickup(pickup = null, pickupAddress = "Supermercado Condor, Curitiba")

        assertTrue(result.matched)
        assertEquals(SupermarketMatchMethod.ALIAS_TEXT, result.method)
    }

    @Test
    fun `an alias does not match inside a longer word`() {
        // The Extra chain's alias must not fire on a street named "Extrema" -- the substring bug
        // that rejected good rides. Uses a coordinate far from every point so only text can match.
        val withExtra = SupermarketBlocklistTsvCodec.decode(
            """
            id	nome	aliases	rede	cidade	uf	latitude	longitude	raio_m	decisao	fonte	versao
            osm-3	Extra	extra|supermercado extra	Extra	Curitiba	PR	-25.40	-49.30	180	BLOQUEAR	OSM	2026-07
            """.trimIndent(),
        )

        assertFalse(withExtra.matchPickup(pickup = null, pickupAddress = "Rua Extrema, 100, Curitiba").matched)
        assertTrue(withExtra.matchPickup(pickup = null, pickupAddress = "Extra Batel, Curitiba").matched)
    }

    @Test
    fun `a multi-word alias still matches`() {
        val result = blocklist.matchPickup(pickup = null, pickupAddress = "Rua X, Supermercado Condor")

        assertTrue(result.matched)
        assertEquals(SupermarketMatchMethod.ALIAS_TEXT, result.method)
    }
}
