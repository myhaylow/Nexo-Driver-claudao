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
 * Chooses where the card window sits, following the mockup layout rule:
 *
 * - [OverlayLayoutStyle.TOPO] é adaptativo entre topo e inferior: o card fica no topo (os cards de
 *   oferta da Uber/99 são bottom sheets), mas se a janela do app de corrida ocupa só a metade de
 *   cima da tela (split-screen), a oferta está lá em cima — então o card desce para o rodapé.
 *   Assim ele nunca cobre a oferta.
 * - [OverlayLayoutStyle.HORIZONTAL] é a barra de largura total no topo (mesma adaptação vertical).
 * - [OverlayLayoutStyle.VERTICAL] é a coluna estreita ancorada à direita, na área do mapa.
 *
 * In split-screen the card is additionally pushed to the same side the ride app is on, so a
 * centred card never lands on top of the very card the driver is trying to read.
 */
internal object OverlayPlacement {

    fun resolveFor(
        layout: OverlayLayoutStyle,
        appWindow: OverlayWindowBounds?,
        screenWidth: Int,
        screenHeight: Int,
    ): ResolvedOverlayPlacement {
        val verticalGravity = when (layout) {
            OverlayLayoutStyle.VERTICAL -> Gravity.TOP
            OverlayLayoutStyle.TOPO, OverlayLayoutStyle.HORIZONTAL ->
                if (rideAppSitsOnTopHalf(appWindow, screenHeight)) Gravity.BOTTOM else Gravity.TOP
        }
        // Bottom placement is lifted above the ride-app action area; top stays near the status area.
        val verticalOffsetDp = if (verticalGravity == Gravity.BOTTOM) BOTTOM_OFFSET_DP else TOP_OFFSET_DP

        val horizontal = when (layout) {
            OverlayLayoutStyle.VERTICAL -> Gravity.END
            else -> horizontalGravity(appWindow, screenWidth)
        }
        val margin = when {
            layout == OverlayLayoutStyle.HORIZONTAL -> HORIZONTAL_BAR_MARGIN
            horizontal == Gravity.CENTER_HORIZONTAL -> FULL_WIDTH_MARGIN
            else -> SIDE_MARGIN
        }
        return ResolvedOverlayPlacement(
            gravity = verticalGravity or horizontal,
            verticalOffsetDp = verticalOffsetDp,
            horizontalMargin = margin,
        )
    }

    /**
     * True when the ride app's window lives in the top region of the screen, meaning its offer
     * sheet is up there and a top-placed card would cover it.
     */
    private fun rideAppSitsOnTopHalf(appWindow: OverlayWindowBounds?, screenHeight: Int): Boolean =
        appWindow != null && appWindow.isValid && screenHeight > 0 &&
            appWindow.bottom <= screenHeight * TOP_REGION_RATIO

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
    private const val TOP_REGION_RATIO = 0.6f
    private const val TOP_OFFSET_DP = 24
    private const val BOTTOM_OFFSET_DP = 160
    private const val FULL_WIDTH_MARGIN = 0.04f

    /** A barra Horizontal vai quase de ponta a ponta, como no mockup. */
    private const val HORIZONTAL_BAR_MARGIN = 0.01f

    /** A side-docked card is inset further so it does not straddle the split-screen divider. */
    private const val SIDE_MARGIN = 0.02f
}
