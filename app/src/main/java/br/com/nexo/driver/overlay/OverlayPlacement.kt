package br.com.nexo.driver.overlay

import android.view.Gravity

/** Screen bounds of another app's window, in pixels. */
data class OverlayWindowBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val centerX: Int get() = (left + right) / 2
    val isValid: Boolean get() = right > left && bottom > top
}

/** The resolved window attributes for one card. */
data class ResolvedOverlayPlacement(
    val gravity: Int,
    val verticalOffsetDp: Int,
    val horizontalMargin: Float,
)

/**
 * Chooses where the card sits.
 *
 * A fixed centred placement assumes the ride app owns the whole screen. In split-screen or
 * multi-window the app occupies one side, and a centred card then lands on top of the very card
 * the driver is trying to read. When the ride app's bounds are known and it is not full-width, the
 * overlay is pushed to the same side the app is on, mirroring how comparable apps pick a slot.
 *
 * Without bounds this degrades to the previous stored TOP/BOTTOM behaviour.
 */
internal object OverlayPlacement {

    fun resolve(
        preferred: OverlayPosition,
        appWindow: OverlayWindowBounds?,
        screenWidth: Int,
        screenHeight: Int,
    ): ResolvedOverlayPlacement {
        val vertical = when (preferred) {
            OverlayPosition.TOP -> Gravity.TOP
            OverlayPosition.BOTTOM -> Gravity.BOTTOM
        }
        // Bottom placement is lifted above the ride-app action area; top stays near the status area.
        val verticalOffsetDp = if (preferred == OverlayPosition.BOTTOM) BOTTOM_OFFSET_DP else TOP_OFFSET_DP

        val horizontal = horizontalGravity(appWindow, screenWidth)
        val margin = if (horizontal == Gravity.CENTER_HORIZONTAL) FULL_WIDTH_MARGIN else SIDE_MARGIN
        return ResolvedOverlayPlacement(
            gravity = vertical or horizontal,
            verticalOffsetDp = verticalOffsetDp,
            horizontalMargin = margin,
        )
    }

    private fun horizontalGravity(appWindow: OverlayWindowBounds?, screenWidth: Int): Int {
        if (appWindow == null || !appWindow.isValid || screenWidth <= 0) return Gravity.CENTER_HORIZONTAL
        // A window covering nearly the whole width is effectively fullscreen: keep the card centred.
        if (appWindow.width >= screenWidth * FULL_WIDTH_RATIO) return Gravity.CENTER_HORIZONTAL
        val centreRatio = appWindow.centerX.toFloat() / screenWidth
        return when {
            centreRatio < LEFT_SLOT_RATIO -> Gravity.START
            centreRatio < RIGHT_SLOT_RATIO -> Gravity.CENTER_HORIZONTAL
            else -> Gravity.END
        }
    }

    private const val FULL_WIDTH_RATIO = 0.85f
    private const val LEFT_SLOT_RATIO = 0.33f
    private const val RIGHT_SLOT_RATIO = 0.67f
    private const val TOP_OFFSET_DP = 24
    private const val BOTTOM_OFFSET_DP = 160
    private const val FULL_WIDTH_MARGIN = 0.04f

    /** A side-docked card is inset further so it does not straddle the split-screen divider. */
    private const val SIDE_MARGIN = 0.02f
}
