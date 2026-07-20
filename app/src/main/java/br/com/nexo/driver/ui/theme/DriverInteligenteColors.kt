package br.com.nexo.driver.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours are intentionally separate from [ColorScheme]. The overlay uses these
 * colours for a decision, while the rest of the app can continue to use Material roles.
 */
data class DriverStatusColors(
    val accept: Color,
    val onAccept: Color,
    val analyze: Color,
    val onAnalyze: Color,
    val reject: Color,
    val onReject: Color,
    val unknown: Color,
    val onUnknown: Color,
    /**
     * "Sentido casa" is a card-level theme, not a decision. When an offer ends near or heads toward
     * the driver's home, the frame and label adopt this purple while the traffic light keeps showing
     * the real accept/analyze/reject decision.
     */
    val home: Color,
    val onHome: Color,
)

internal val DriverInteligenteLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E5F42),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6F3D7),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4C6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF775A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE083),
    onTertiaryContainer = Color(0xFF241A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FBF7),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF8FBF7),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDDE5DC),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
)

internal val DriverInteligenteDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF94D5AB),
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF075230),
    onPrimaryContainer = Color(0xFFC6F3D7),
    secondary = Color(0xFFB5CCBC),
    onSecondary = Color(0xFF20352A),
    secondaryContainer = Color(0xFF364B3E),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFE8C34C),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF594500),
    onTertiaryContainer = Color(0xFFFFE083),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E4DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E4DF),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C0),
    outline = Color(0xFF8B938B),
)

internal val DriverInteligenteLightStatusColors = DriverStatusColors(
    // Pure neon identity requested for the offer overlay: matches the reference card design exactly.
    accept = Color(0xFF00FF00),
    onAccept = Color(0xFF001A00),
    analyze = Color(0xFFFFFF00),
    onAnalyze = Color(0xFF1A1A00),
    reject = Color(0xFFFF0000),
    onReject = Color.White,
    unknown = Color(0xFF5D645F),
    onUnknown = Color.White,
    home = Color(0xFFB026FF),
    onHome = Color.White,
)

internal val DriverInteligenteDarkStatusColors = DriverStatusColors(
    // Neon softened ~33% for dark backgrounds so it stays readable without glare.
    accept = Color(0xFF00CC00),
    onAccept = Color(0xFF002200),
    analyze = Color(0xFFFFCC00),
    onAnalyze = Color(0xFF221A00),
    reject = Color(0xFFCC0000),
    onReject = Color(0xFFFFDDDD),
    unknown = Color(0xFFC1C9C0),
    onUnknown = Color(0xFF29302B),
    home = Color(0xFF8C1FCC),
    onHome = Color(0xFFF3E0FF),
)

internal fun colorSchemeFor(style: DriverVisualStyle, darkTheme: Boolean): ColorScheme = when (style) {
    DriverVisualStyle.CURRENT -> if (darkTheme) DriverInteligenteDarkColors else DriverInteligenteLightColors
    DriverVisualStyle.OVERLAY_NEON -> if (darkTheme) DriverOverlayNeonDarkColors else DriverOverlayNeonLightColors
    DriverVisualStyle.COCKPIT_PRO -> if (darkTheme) DriverCockpitProDarkColors else DriverCockpitProLightColors
    DriverVisualStyle.MINIMAL_PREMIUM -> if (darkTheme) DriverMinimalPremiumDarkColors else DriverMinimalPremiumLightColors
    DriverVisualStyle.SOL_FORTE -> if (darkTheme) DriverSolForteDarkColors else DriverSolForteLightColors
    DriverVisualStyle.NOITE_QUENTE -> if (darkTheme) DriverNoiteQuenteDarkColors else DriverNoiteQuenteLightColors
    DriverVisualStyle.MONOCROMO -> if (darkTheme) DriverMonocromoDarkColors else DriverMonocromoLightColors
}

internal fun statusColorsFor(style: DriverVisualStyle, darkTheme: Boolean): DriverStatusColors = when (style) {
    DriverVisualStyle.CURRENT -> if (darkTheme) DriverInteligenteDarkStatusColors else DriverInteligenteLightStatusColors
    DriverVisualStyle.OVERLAY_NEON -> if (darkTheme) DriverOverlayNeonDarkStatusColors else DriverOverlayNeonLightStatusColors
    DriverVisualStyle.COCKPIT_PRO -> if (darkTheme) DriverCockpitProDarkStatusColors else DriverCockpitProLightStatusColors
    DriverVisualStyle.MINIMAL_PREMIUM -> if (darkTheme) DriverMinimalPremiumDarkStatusColors else DriverMinimalPremiumLightStatusColors
    DriverVisualStyle.SOL_FORTE -> if (darkTheme) DriverSolForteDarkStatusColors else DriverSolForteLightStatusColors
    DriverVisualStyle.NOITE_QUENTE -> if (darkTheme) DriverNoiteQuenteDarkStatusColors else DriverNoiteQuenteLightStatusColors
    DriverVisualStyle.MONOCROMO -> if (darkTheme) DriverMonocromoDarkStatusColors else DriverMonocromoLightStatusColors
}

private val DriverOverlayNeonLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006B35),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F7C9),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF315B44),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7ECD6),
    onSecondaryContainer = Color(0xFF002113),
    tertiary = Color(0xFF6E5D00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE66D),
    onTertiaryContainer = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FCF6),
    onBackground = Color(0xFF171D18),
    surface = Color(0xFFF7FCF6),
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFD9E8D9),
    onSurfaceVariant = Color(0xFF3E4A40),
    outline = Color(0xFF6E7A70),
)

private val DriverOverlayNeonDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF39FF88),
    onPrimary = Color(0xFF00210E),
    primaryContainer = Color(0xFF005226),
    onPrimaryContainer = Color(0xFFB7F7C9),
    secondary = Color(0xFFABCDB8),
    onSecondary = Color(0xFF163526),
    secondaryContainer = Color(0xFF2C4C3A),
    onSecondaryContainer = Color(0xFFC7ECD6),
    tertiary = Color(0xFFFFD84D),
    onTertiary = Color(0xFF393000),
    tertiaryContainer = Color(0xFF544600),
    onTertiaryContainer = Color(0xFFFFE66D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1310),
    onBackground = Color(0xFFDEE5DE),
    surface = Color(0xFF0E1310),
    onSurface = Color(0xFFDEE5DE),
    surfaceVariant = Color(0xFF3E4A40),
    onSurfaceVariant = Color(0xFFBECABC),
    outline = Color(0xFF88948A),
)

private val DriverCockpitProLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF245A46),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EBDD),
    onPrimaryContainer = Color(0xFF072018),
    secondary = Color(0xFF50635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E7DC),
    onSecondaryContainer = Color(0xFF0D1F18),
    tertiary = Color(0xFF665F35),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE3AD),
    onTertiaryContainer = Color(0xFF211C00),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF8FAF8),
    onBackground = Color(0xFF181C1A),
    surface = Color(0xFFF8FAF8),
    onSurface = Color(0xFF181C1A),
    surfaceVariant = Color(0xFFDDE5DF),
    onSurfaceVariant = Color(0xFF414944),
    outline = Color(0xFF717973),
)

private val DriverCockpitProDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA7D8C2),
    onPrimary = Color(0xFF0B3828),
    primaryContainer = Color(0xFF25513D),
    onPrimaryContainer = Color(0xFFC8EBDD),
    secondary = Color(0xFFB7CBC0),
    onSecondary = Color(0xFF22352D),
    secondaryContainer = Color(0xFF384B42),
    onSecondaryContainer = Color(0xFFD3E7DC),
    tertiary = Color(0xFFD0C791),
    onTertiary = Color(0xFF36300B),
    tertiaryContainer = Color(0xFF4E4820),
    onTertiaryContainer = Color(0xFFEDE3AD),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E4E0),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E4E0),
    surfaceVariant = Color(0xFF414944),
    onSurfaceVariant = Color(0xFFC1C9C2),
    outline = Color(0xFF8B938D),
)

private val DriverMinimalPremiumLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF3E5147),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7DD),
    onPrimaryContainer = Color(0xFF0A1F16),
    secondary = Color(0xFF59635D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE6DF),
    onSecondaryContainer = Color(0xFF171D19),
    tertiary = Color(0xFF646055),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAE4D6),
    onTertiaryContainer = Color(0xFF201D17),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFE0E3DE),
    onSurfaceVariant = Color(0xFF444844),
    outline = Color(0xFF747873),
)

private val DriverMinimalPremiumDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBECFC4),
    onPrimary = Color(0xFF29352E),
    primaryContainer = Color(0xFF3E5147),
    onPrimaryContainer = Color(0xFFD9E7DD),
    secondary = Color(0xFFC4CBC5),
    onSecondary = Color(0xFF2E342F),
    secondaryContainer = Color(0xFF454B46),
    onSecondaryContainer = Color(0xFFDDE6DF),
    tertiary = Color(0xFFCEC8BB),
    onTertiary = Color(0xFF353027),
    tertiaryContainer = Color(0xFF4B473D),
    onTertiaryContainer = Color(0xFFEAE4D6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE2E3E0),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE2E3E0),
    surfaceVariant = Color(0xFF444844),
    onSurfaceVariant = Color(0xFFC4C8C2),
    outline = Color(0xFF8E928C),
)

private val DriverOverlayNeonLightStatusColors = DriverStatusColors(
    accept = Color(0xFF00FF00),
    onAccept = Color(0xFF001A00),
    analyze = Color(0xFFFFFF00),
    onAnalyze = Color(0xFF1A1A00),
    reject = Color(0xFFFF0000),
    onReject = Color.White,
    unknown = Color(0xFF5D645F),
    onUnknown = Color.White,
    home = Color(0xFFB026FF),
    onHome = Color.White,
)

private val DriverOverlayNeonDarkStatusColors = DriverStatusColors(
    accept = Color(0xFF39FF88),
    onAccept = Color(0xFF00210E),
    analyze = Color(0xFFFFD84D),
    onAnalyze = Color(0xFF2A2100),
    reject = Color(0xFFFF5A52),
    onReject = Color(0xFF300000),
    unknown = Color(0xFFC1C9C0),
    onUnknown = Color(0xFF29302B),
    home = Color(0xFFC16BFF),
    onHome = Color(0xFF280047),
)

private val DriverCockpitProLightStatusColors = DriverStatusColors(
    accept = Color(0xFF268B57),
    onAccept = Color.White,
    analyze = Color(0xFFC29000),
    onAnalyze = Color(0xFF211800),
    reject = Color(0xFFC83A32),
    onReject = Color.White,
    unknown = Color(0xFF69736D),
    onUnknown = Color.White,
    home = Color(0xFF7B4FB2),
    onHome = Color.White,
)

private val DriverCockpitProDarkStatusColors = DriverStatusColors(
    accept = Color(0xFF77D59A),
    onAccept = Color(0xFF00391D),
    analyze = Color(0xFFECC25C),
    onAnalyze = Color(0xFF332500),
    reject = Color(0xFFFF9A94),
    onReject = Color(0xFF5D0000),
    unknown = Color(0xFFC1C9C2),
    onUnknown = Color(0xFF2B312D),
    home = Color(0xFFC3A4F4),
    onHome = Color(0xFF32115E),
)

private val DriverMinimalPremiumLightStatusColors = DriverStatusColors(
    accept = Color(0xFF3E6F55),
    onAccept = Color.White,
    analyze = Color(0xFF7A6500),
    onAnalyze = Color.White,
    reject = Color(0xFF9D3430),
    onReject = Color.White,
    unknown = Color(0xFF666B66),
    onUnknown = Color.White,
    home = Color(0xFF66507F),
    onHome = Color.White,
)

private val DriverMinimalPremiumDarkStatusColors = DriverStatusColors(
    accept = Color(0xFFA6CDB5),
    onAccept = Color(0xFF183525),
    analyze = Color(0xFFD4C06A),
    onAnalyze = Color(0xFF342C00),
    reject = Color(0xFFE8A09A),
    onReject = Color(0xFF4F1110),
    unknown = Color(0xFFC4C8C2),
    onUnknown = Color(0xFF2D302C),
    home = Color(0xFFD2B8EA),
    onHome = Color(0xFF37224F),
)