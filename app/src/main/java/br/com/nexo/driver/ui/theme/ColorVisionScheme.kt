package br.com.nexo.driver.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour-vision adaptations for the decision palette.
 *
 * The overlay signals accept/reject with green and red, the exact pair that red-green colour
 * blindness collapses -- and that affects roughly 8% of men, a large share of this app's users.
 * The three-bulb traffic light already encodes the verdict by position, and the metric cells carry
 * ▲/=/▼ glyphs, so colour is never the only channel; these schemes make the colour channel work
 * too instead of relying on the redundancy alone.
 *
 * Each variant keeps the "good" and "bad" hues maximally separated along an axis the given
 * deficiency preserves: blue/orange for the red-green types, and red/teal for tritanopia, which
 * loses the blue-yellow axis instead.
 */
enum class ColorVisionScheme(val label: String) {
    NORMAL("Padrão"),
    PROTANOPIA("Protanopia (vermelho)"),
    DEUTERANOPIA("Deuteranopia (verde)"),
    TRITANOPIA("Tritanopia (azul)"),
}

/**
 * Remaps the decision colours of [base] for [scheme]. Neutral roles (unknown, home) and every
 * "on" colour are recomputed alongside so contrast is preserved.
 */
internal fun DriverStatusColors.adaptedFor(
    scheme: ColorVisionScheme,
    darkTheme: Boolean,
): DriverStatusColors = when (scheme) {
    ColorVisionScheme.NORMAL -> this
    ColorVisionScheme.PROTANOPIA, ColorVisionScheme.DEUTERANOPIA -> copy(
        // Red-green deficiencies retain the blue-yellow axis: accept becomes blue, reject orange.
        accept = if (darkTheme) Color(0xFF64B5FF) else Color(0xFF0B6BCB),
        onAccept = if (darkTheme) Color(0xFF00243F) else Color.White,
        analyze = if (darkTheme) Color(0xFFF2D24B) else Color(0xFF9A7B00),
        onAnalyze = if (darkTheme) Color(0xFF2B2200) else Color.White,
        reject = if (darkTheme) Color(0xFFFF9E4D) else Color(0xFFC25200),
        onReject = if (darkTheme) Color(0xFF3A1A00) else Color.White,
    )
    ColorVisionScheme.TRITANOPIA -> copy(
        // Tritanopia loses blue-yellow, so the red-green axis is the usable one here.
        accept = if (darkTheme) Color(0xFF5BD6C0) else Color(0xFF00796B),
        onAccept = if (darkTheme) Color(0xFF00312A) else Color.White,
        analyze = if (darkTheme) Color(0xFFE59BB5) else Color(0xFFA1305C),
        onAnalyze = if (darkTheme) Color(0xFF3B0020) else Color.White,
        reject = if (darkTheme) Color(0xFFFF8A80) else Color(0xFFC62828),
        onReject = if (darkTheme) Color(0xFF4A0000) else Color.White,
    )
}
