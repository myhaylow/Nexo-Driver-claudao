package br.com.nexo.driver.overlay

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPlacementTest {

    @Test
    fun `a fullscreen ride app keeps the card centred`() {
        val placement = OverlayPlacement.resolve(
            preferred = OverlayPosition.BOTTOM,
            appWindow = OverlayWindowBounds(0, 0, 1080, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, placement.gravity)
    }

    @Test
    fun `a left-docked split-screen app pushes the card to the left`() {
        val placement = OverlayPlacement.resolve(
            preferred = OverlayPosition.BOTTOM,
            appWindow = OverlayWindowBounds(0, 0, 540, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.BOTTOM or Gravity.START, placement.gravity)
    }

    @Test
    fun `a right-docked split-screen app pushes the card to the right`() {
        val placement = OverlayPlacement.resolve(
            preferred = OverlayPosition.TOP,
            appWindow = OverlayWindowBounds(540, 0, 1080, 2340),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.END, placement.gravity)
    }

    @Test
    fun `unknown bounds fall back to the stored centred behaviour`() {
        val placement = OverlayPlacement.resolve(
            preferred = OverlayPosition.BOTTOM,
            appWindow = null,
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, placement.gravity)
    }

    @Test
    fun `degenerate bounds are ignored rather than trusted`() {
        val placement = OverlayPlacement.resolve(
            preferred = OverlayPosition.TOP,
            appWindow = OverlayWindowBounds(0, 0, 0, 0),
            screenWidth = 1080,
            screenHeight = 2340,
        )

        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
    }

    @Test
    fun `the stored vertical preference still decides top versus bottom`() {
        val top = OverlayPlacement.resolve(OverlayPosition.TOP, null, 1080, 2340)
        val bottom = OverlayPlacement.resolve(OverlayPosition.BOTTOM, null, 1080, 2340)

        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, top.gravity)
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottom.gravity)
        // Bottom stays lifted clear of the ride app's action row.
        assertEquals(true, bottom.verticalOffsetDp > top.verticalOffsetDp)
    }
}
