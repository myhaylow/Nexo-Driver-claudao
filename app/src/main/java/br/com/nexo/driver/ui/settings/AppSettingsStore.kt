package br.com.nexo.driver.ui.settings

import android.content.Context
import android.content.SharedPreferences
import br.com.nexo.driver.analysis.DEFAULT_CARD_DURATION_MS
import br.com.nexo.driver.analysis.MAX_CARD_DURATION_MS
import br.com.nexo.driver.analysis.MIN_CARD_DURATION_MS
import br.com.nexo.driver.evaluation.DEFAULT_ACCEPT_THRESHOLD
import br.com.nexo.driver.evaluation.DEFAULT_ANALYZE_THRESHOLD
import br.com.nexo.driver.overlay.OverlayLayoutStyle
import br.com.nexo.driver.ui.theme.ColorVisionScheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.ui.theme.DriverVisualStyle

/**
 * The app-level display settings, in one place.
 *
 * These keys were previously spelled out twice -- once as private constants and reader extensions
 * inside the 535-line `NexoApp` composable, and again inside `OfferAnalysisProcessor`, which reads
 * the same file from the capture thread. Two independent copies of the same key strings and the
 * same clamping rules is how a setting silently stops applying to the overlay: change one spelling
 * and the other side keeps reading the old key without any error.
 *
 * Every other store in the project (`profile`, `destination`, `block`, `cost`) already follows this
 * shape, so this also brings app settings in line with the rest.
 */
data class AppSettings(
    val themeMode: DriverThemeMode = DriverThemeMode.SYSTEM,
    val visualStyle: DriverVisualStyle = DriverVisualStyle.COCKPIT_PRO,
    val fontScale: AppFontScale = AppFontScale.DEFAULT,
    val colorVisionScheme: ColorVisionScheme = ColorVisionScheme.NORMAL,
    val cardDurationMs: Long = DEFAULT_CARD_DURATION_MS,
    val acceptThreshold: Int = DEFAULT_ACCEPT_THRESHOLD,
    val analyzeThreshold: Int = DEFAULT_ANALYZE_THRESHOLD,
    /** Multiplies only the overlay card's text size, independent of the app-wide font scale. */
    val overlayFontScale: Float = DEFAULT_OVERLAY_FONT_SCALE,
    /** Layout do card do overlay, como desenhado no mockup. */
    val overlayLayout: OverlayLayoutStyle = OverlayLayoutStyle.TOPO,
) {
    init {
        require(analyzeThreshold <= acceptThreshold) {
            "The analyze threshold cannot exceed the accept threshold."
        }
        require(overlayFontScale in MIN_OVERLAY_FONT_SCALE..MAX_OVERLAY_FONT_SCALE) {
            "The overlay font scale must be within [$MIN_OVERLAY_FONT_SCALE, $MAX_OVERLAY_FONT_SCALE]."
        }
    }

    companion object {
        const val DEFAULT_OVERLAY_FONT_SCALE = 1f
        const val MIN_OVERLAY_FONT_SCALE = 0.8f
        const val MAX_OVERLAY_FONT_SCALE = 1.6f
    }
}

class AppSettingsStore private constructor(private val preferences: SharedPreferences) {

    fun load(): AppSettings {
        val accept = preferences.getInt(KEY_ACCEPT_THRESHOLD, DEFAULT_ACCEPT_THRESHOLD).coerceIn(0, 100)
        return AppSettings(
            themeMode = preferences.readEnum(KEY_THEME_MODE, DriverThemeMode.entries, DriverThemeMode.SYSTEM),
            visualStyle = preferences.readEnum(KEY_VISUAL_STYLE, DriverVisualStyle.entries, DriverVisualStyle.COCKPIT_PRO),
            fontScale = preferences.readEnum(KEY_FONT_SCALE, AppFontScale.entries, AppFontScale.DEFAULT),
            colorVisionScheme = preferences.readEnum(KEY_COLOR_VISION, ColorVisionScheme.entries, ColorVisionScheme.NORMAL),
            cardDurationMs = preferences.getLong(KEY_CARD_DURATION_MS, DEFAULT_CARD_DURATION_MS)
                .coerceIn(MIN_CARD_DURATION_MS, MAX_CARD_DURATION_MS),
            acceptThreshold = accept,
            // Clamped against the accept value so a partially written pair can never violate the
            // data class invariant and crash the settings screen on launch.
            analyzeThreshold = preferences.getInt(KEY_ANALYZE_THRESHOLD, DEFAULT_ANALYZE_THRESHOLD)
                .coerceIn(0, accept),
            overlayFontScale = preferences.getFloat(KEY_OVERLAY_FONT_SCALE, AppSettings.DEFAULT_OVERLAY_FONT_SCALE)
                .coerceIn(AppSettings.MIN_OVERLAY_FONT_SCALE, AppSettings.MAX_OVERLAY_FONT_SCALE),
            overlayLayout = preferences.readEnum(KEY_OVERLAY_LAYOUT, OverlayLayoutStyle.entries, OverlayLayoutStyle.TOPO),
        )
    }

    fun save(settings: AppSettings): AppSettings {
        preferences.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_VISUAL_STYLE, settings.visualStyle.name)
            .putString(KEY_FONT_SCALE, settings.fontScale.name)
            .putString(KEY_COLOR_VISION, settings.colorVisionScheme.name)
            .putLong(KEY_CARD_DURATION_MS, settings.cardDurationMs)
            .putInt(KEY_ACCEPT_THRESHOLD, settings.acceptThreshold)
            .putInt(KEY_ANALYZE_THRESHOLD, settings.analyzeThreshold)
            .putFloat(KEY_OVERLAY_FONT_SCALE, settings.overlayFontScale)
            .putString(KEY_OVERLAY_LAYOUT, settings.overlayLayout.name)
            .apply()
        return settings
    }

    private fun <T : Enum<T>> SharedPreferences.readEnum(key: String, values: List<T>, fallback: T): T =
        getString(key, null)?.let { saved -> values.firstOrNull { it.name == saved } } ?: fallback

    companion object {
        const val PREFERENCES_NAME = "driver_inteligente_app_settings"

        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_VISUAL_STYLE = "visual_style"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_COLOR_VISION = "color_vision_scheme"
        const val KEY_CARD_DURATION_MS = "overlay_card_duration_ms"
        const val KEY_ACCEPT_THRESHOLD = "decision_accept_threshold"
        const val KEY_ANALYZE_THRESHOLD = "decision_analyze_threshold"
        const val KEY_OVERLAY_FONT_SCALE = "overlay_font_scale"
        const val KEY_OVERLAY_LAYOUT = "overlay_layout_style"

        fun create(context: Context): AppSettingsStore = AppSettingsStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }
}
