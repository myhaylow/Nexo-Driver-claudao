package br.com.nexo.driver.ui.theme

import androidx.compose.ui.graphics.Color
import br.com.nexo.driver.ui.settings.displayName
import br.com.nexo.driver.ui.settings.usageHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A half-declared visual style fails here rather than shipping a screen painted in defaults.
 */
class DriverVisualStyleTest {

    @Test
    fun `every style resolves a distinct palette in both themes`() {
        DriverVisualStyle.entries.forEach { style ->
            listOf(true, false).forEach { dark ->
                val status = statusColorsFor(style, dark)
                // The three verdicts must never collapse into each other: the traffic light and
                // the frame are the card's primary signal.
                assertNotEquals("$style/$dark accept == reject", status.accept, status.reject)
                assertNotEquals("$style/$dark accept == analyze", status.accept, status.analyze)
                assertNotEquals("$style/$dark analyze == reject", status.analyze, status.reject)
                assertNotEquals("$style/$dark home == accept", status.home, status.accept)
            }
        }
    }

    @Test
    fun `light and dark differ for every style`() {
        DriverVisualStyle.entries.forEach { style ->
            assertNotEquals(
                "$style renders identically in light and dark",
                colorSchemeFor(style, darkTheme = false).background,
                colorSchemeFor(style, darkTheme = true).background,
            )
        }
    }

    @Test
    fun `every style is named and explained in settings`() {
        DriverVisualStyle.entries.forEach { style ->
            assertTrue("$style has no display name", style.displayName().isNotBlank())
            assertTrue("$style has no usage hint", style.usageHint().isNotBlank())
        }
    }

    @Test
    fun `the four original identities keep their exact colours`() {
        // These are the palettes already on drivers' phones. The three new styles are additions;
        // if this test fails, an existing card silently changed appearance.
        assertEquals(Color(0xFF00FF00), statusColorsFor(DriverVisualStyle.CURRENT, false).accept)
        assertEquals(Color(0xFF00CC00), statusColorsFor(DriverVisualStyle.CURRENT, true).accept)
        assertEquals(Color(0xFF39FF88), statusColorsFor(DriverVisualStyle.OVERLAY_NEON, true).accept)
        assertEquals(Color(0xFF268B57), statusColorsFor(DriverVisualStyle.COCKPIT_PRO, false).accept)
        assertEquals(Color(0xFFA6CDB5), statusColorsFor(DriverVisualStyle.MINIMAL_PREMIUM, true).accept)
    }

    @Test
    fun `noite quente really is the warmest dark palette`() {
        // Warmth is measured on the surface, not on a verdict swatch: the card background covers
        // almost every pixel, so it dominates what actually reaches the eye. Measuring the accept
        // swatch instead would be misleading -- a pure neon green like CURRENT has a blue channel
        // of zero while sitting on a cold near-black surface.
        val warmth = { style: DriverVisualStyle ->
            colorSchemeFor(style, darkTheme = true).surface.let { it.red - it.blue }
        }
        val noiteQuente = warmth(DriverVisualStyle.NOITE_QUENTE)

        DriverVisualStyle.entries.filterNot { it == DriverVisualStyle.NOITE_QUENTE }.forEach { style ->
            assertTrue(
                "$style surface (${warmth(style)}) is warmer than NOITE_QUENTE ($noiteQuente)",
                warmth(style) < noiteQuente,
            )
        }
    }

    @Test
    fun `monocromo keeps its surfaces neutral`() {
        // Neutral means the channels stay within a narrow band of each other; a tinted surface
        // would start competing with the verdict colours.
        listOf(true, false).forEach { dark ->
            val surface = colorSchemeFor(DriverVisualStyle.MONOCROMO, dark).surface
            val channels = listOf(surface.red, surface.green, surface.blue)
            val spread = (channels.max() - channels.min()) * 255f
            assertTrue("Monocromo surface is tinted (spread $spread) in dark=$dark", spread <= 12f)
        }
    }
}
