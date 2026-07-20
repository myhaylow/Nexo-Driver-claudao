package br.com.nexo.driver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

enum class DriverThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

enum class DriverVisualStyle {
    CURRENT,
    OVERLAY_NEON,
    COCKPIT_PRO,
    MINIMAL_PREMIUM,
}

private val LocalDriverStatusColors = staticCompositionLocalOf { DriverInteligenteLightStatusColors }

/** Access to semantic evaluation colours within [DriverInteligenteTheme]. */
object DriverInteligenteTheme {
    val statusColors: DriverStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDriverStatusColors.current
}

@Composable
fun DriverInteligenteTheme(
    mode: DriverThemeMode = DriverThemeMode.SYSTEM,
    fontScale: Float = 1f,
    visualStyle: DriverVisualStyle = DriverVisualStyle.COCKPIT_PRO,
    colorVisionScheme: ColorVisionScheme = ColorVisionScheme.NORMAL,
    content: @Composable () -> Unit,
) {
    require(fontScale > 0f) { "The app font scale must be positive." }
    val darkTheme = when (mode) {
        DriverThemeMode.LIGHT -> false
        DriverThemeMode.DARK -> true
        DriverThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = colorSchemeFor(visualStyle, darkTheme)
    val statusColors = statusColorsFor(visualStyle, darkTheme).adaptedFor(colorVisionScheme, darkTheme)

    val density = LocalDensity.current
    val scaledDensity = Density(
        density = density.density,
        fontScale = density.fontScale * fontScale,
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalDriverStatusColors provides statusColors,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = DriverInteligenteTypography,
            content = content,
        )
    }
}
