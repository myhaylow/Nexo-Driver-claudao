package br.com.nexo.driver.destination.offline

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Decodes the bundled Paraná base pack (docs/osm/parana-places.tsv, generated from OpenStreetMap)
 * with the real codec, so a hand or tooling edit that breaks the format is caught here rather than
 * only when a driver imports it. The pack lives outside any source set, so if the working directory
 * changes the test skips instead of failing falsely.
 */
class ParanaAddressPackDecodeTest {
    @Test
    fun `bundled Parana pack decodes and validates`() {
        val file = locatePack()
        assumeTrue("parana-places.tsv not found from ${File(".").absolutePath}", file != null)
        val pkg = OfflineAddressPackageTsvCodec.decode(requireNotNull(file).readBytes())
        assertTrue("expected a statewide set of places", pkg.places.size > 500)
        assertTrue("pack must pass validation", pkg.validate().isValid)
        assertTrue("Curitiba should be present", pkg.places.any { it.label == "Curitiba" })
    }

    private fun locatePack(): File? {
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "docs/osm/parana-places.tsv")
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}
