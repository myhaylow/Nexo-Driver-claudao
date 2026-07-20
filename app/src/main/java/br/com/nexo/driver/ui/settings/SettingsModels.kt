package br.com.nexo.driver.ui.settings

import br.com.nexo.driver.analysis.DEFAULT_CARD_DURATION_MS
import br.com.nexo.driver.evaluation.DEFAULT_ACCEPT_THRESHOLD
import br.com.nexo.driver.evaluation.DEFAULT_ANALYZE_THRESHOLD
import br.com.nexo.driver.ui.theme.ColorVisionScheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.ui.theme.DriverVisualStyle
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import br.com.nexo.driver.overlay.preferences.OverlaySlot
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.OverlayPosition

/**
 * Preferences exposed by the first version of the settings screen.
 *
 * Persistence and app-wide theme application stay with the screen owner so this UI remains
 * reusable from the main application and previews.
 */
data class SettingsScreenState(
    val themeMode: DriverThemeMode = DriverThemeMode.SYSTEM,
    val visualStyle: DriverVisualStyle = DriverVisualStyle.COCKPIT_PRO,
    val fontScale: AppFontScale = AppFontScale.DEFAULT,
    val overlayPreferences: OverlayPreferences = OverlayPreferences.DEFAULT,
    val accessibilityServiceEnabled: Boolean = false,
    val speakDecision: Boolean = true,
    val galleryTestStatus: String? = null,
    val showDebugTools: Boolean = false,
    val overlayPosition: OverlayPosition = OverlayPosition.BOTTOM,
    val blockSupermarkets: Boolean = false,
    /** Fuel price in BRL cents per litre, used to estimate the offer's net profit. */
    val fuelPricePerLiterCents: Long = 400L,
    /** Vehicle consumption in kilometres per litre. */
    val fuelKilometersPerLiter: Double = 8.0,
    /** Adapts the accept/reject palette for colour-vision deficiencies. */
    val colorVisionScheme: ColorVisionScheme = ColorVisionScheme.NORMAL,
    /** How long an offer card stays on screen, in milliseconds. */
    val cardDurationMs: Long = DEFAULT_CARD_DURATION_MS,
    /** Weighted score at or above which the card shows ACEITAR. */
    val acceptThreshold: Int = DEFAULT_ACCEPT_THRESHOLD,
    /** Weighted score at or above which the card shows EM ANÁLISE rather than RECUSAR. */
    val analyzeThreshold: Int = DEFAULT_ANALYZE_THRESHOLD,
)

fun ColorVisionScheme.displayName(): String = label

/** Card durations offered in settings, in milliseconds. */
val CARD_DURATION_OPTIONS: List<Long> = listOf(6_000L, 9_000L, 12_000L, 16_000L, 20_000L)

/**
 * Named presets over the evaluator's accept/analyze thresholds. Exposing two raw 0..100 numbers
 * would invite contradictory settings; the presets keep the pair coherent while still letting a
 * driver in a slow city loosen the bar the way a fixed default cannot.
 */
enum class DecisionStrictness(
    val label: String,
    val acceptThreshold: Int,
    val analyzeThreshold: Int,
) {
    TOLERANT("Tolerante", acceptThreshold = 70, analyzeThreshold = 40),
    BALANCED("Equilibrado", acceptThreshold = DEFAULT_ACCEPT_THRESHOLD, analyzeThreshold = DEFAULT_ANALYZE_THRESHOLD),
    STRICT("Exigente", acceptThreshold = 90, analyzeThreshold = 65),
    ;

    companion object {
        /** Falls back to the closest preset so a legacy or hand-edited pair still renders. */
        fun forThresholds(accept: Int, analyze: Int): DecisionStrictness =
            entries.minByOrNull { preset ->
                kotlin.math.abs(preset.acceptThreshold - accept) +
                    kotlin.math.abs(preset.analyzeThreshold - analyze)
            } ?: BALANCED
    }
}

enum class AppFontScale(
    val label: String,
    val multiplier: Float,
) {
    SMALL(label = "Pequena", multiplier = 0.88f),
    DEFAULT(label = "Padrão", multiplier = 1f),
    LARGE(label = "Grande", multiplier = 1.16f),
}

fun DriverThemeMode.displayName(): String = when (this) {
    DriverThemeMode.LIGHT -> "Claro"
    DriverThemeMode.DARK -> "Escuro"
    DriverThemeMode.SYSTEM -> "Sistema"
}

fun DriverVisualStyle.displayName(): String = when (this) {
    DriverVisualStyle.CURRENT -> "Atual"
    DriverVisualStyle.OVERLAY_NEON -> "Overlay neon"
    DriverVisualStyle.COCKPIT_PRO -> "Cockpit Pro (recomendado)"
    DriverVisualStyle.MINIMAL_PREMIUM -> "Minimal premium"
}

fun OverlaySlot.displayName(): String = when (this) {
    OverlaySlot.TOP_START -> "Superior esquerdo"
    OverlaySlot.TOP_END -> "Superior direito"
    OverlaySlot.BOTTOM_START -> "Inferior esquerdo"
    OverlaySlot.BOTTOM_END -> "Inferior direito"
}

/**
 * Fields that can safely replace [slot]. Existing values in the remaining cells are hidden,
 * preserving the overlay's four-distinct-fields contract before a callback is emitted.
 */
fun OverlayPreferences.availableFieldsFor(slot: OverlaySlot): List<OverlayMetricField> {
    val current = this[slot]
    return OverlayMetricField.entries.filter { field -> field == current || field !in fields }
}
