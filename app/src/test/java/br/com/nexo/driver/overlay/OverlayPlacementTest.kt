package br.com.nexo.driver.overlay

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlacementTest {

    @Test
    fun `topo on a fullscreen ride app stays at the top, clear of the bottom-sheet offer`() {
        val placement = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = OverlayWindowBounds(0, 0, 1080, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
    }

    @Test
    fun `topo drops to the bottom when the ride app occupies the top half of the screen`() {
        // Split-screen com o app de corrida em cima: a oferta está lá — o card desce.
        val placement = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = OverlayWindowBounds(0, 0, 1080, 1100),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertTrue(placement.gravity and Gravity.BOTTOM == Gravity.BOTTOM)
    }

    @Test
    fun `horizontal follows the same top-bottom avoidance and hugs the screen edges`() {
        val fullscreen = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.HORIZONTAL,
            appWindow = OverlayWindowBounds(0, 0, 1080, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )
        val splitTop = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.HORIZONTAL,
            appWindow = OverlayWindowBounds(0, 0, 1080, 1100),
            screenWidth = 1080,
            screenHeight = 2340,
        )
        val topo = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = OverlayWindowBounds(0, 0, 1080, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, fullscreen.gravity)
        assertTrue(splitTop.gravity and Gravity.BOTTOM == Gravity.BOTTOM)
        // A barra horizontal vai mais de ponta a ponta que o card Topo.
        assertTrue(fullscreen.horizontalMargin < topo.horizontalMargin)
    }

    @Test
    fun `vertical anchors the narrow column to the top end`() {
        val placement = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.VERTICAL,
            appWindow = OverlayWindowBounds(0, 0, 1080, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.END, placement.gravity)
    }

    @Test
    fun `a left-docked split-screen app pushes the card to the left`() {
        val placement = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = OverlayWindowBounds(0, 0, 540, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.START, placement.gravity)
    }

    @Test
    fun `a right-docked split-screen app pushes the card to the right`() {
        val placement = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = OverlayWindowBounds(540, 0, 1080, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.END, placement.gravity)
    }

    @Test
    fun `unknown bounds fall back to the centred top behaviour`() {
        val placement = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = null,
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
    }

    @Test
    fun `degenerate bounds are ignored rather than trusted`() {
        val placement = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = OverlayWindowBounds(0, 0, 0, 0),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
    }

    @Test
    fun `a bottom-placed card is lifted clear of the ride app action row`() {
        val top = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = null,
            screenWidth = 1080,
            screenHeight = 2340,
        )
        val bottom = OverlayPlacement.resolveFor(
            layout = OverlayLayoutStyle.TOPO,
            appWindow = OverlayWindowBounds(0, 0, 1080, 1100),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertTrue(bottom.verticalOffsetDp > top.verticalOffsetDp)
    }
}
