package br.com.nexo.driver.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Three palettes built around the conditions a driver actually reads this card in.
 *
 * They are additions: the four original identities in [DriverInteligenteColors] are untouched, and
 * the offer card keeps its existing look unless one of these is selected. Each answers a specific
 * problem rather than offering another arbitrary colourway, and each is defined for light and dark
 * so the system theme still applies.
 */

// -------------------------------------------------------------------------------------------
// Sol forte — midday glare.
//
// A phone mounted on a windscreen in direct sun loses perceived contrast: mid-tones collapse
// together and desaturated hues read as grey. This palette answers with near-black on white, no
// mid-tone surfaces, and verdict colours pushed to full saturation so they still separate once
// the screen washes out. The dark variant stays deliberately harsh rather than softened.
// -------------------------------------------------------------------------------------------

internal val DriverSolForteLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF00458A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE0FF),
    onPrimaryContainer = Color(0xFF001A3D),
    secondary = Color(0xFF2E4160),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E2F7),
    onSecondaryContainer = Color(0xFF0D1B2E),
    tertiary = Color(0xFF7A4A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2A1600),
    error = Color(0xFFA5000E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF3B0004),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0C0F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0C0F),
    surfaceVariant = Color(0xFFE7EAEF),
    onSurfaceVariant = Color(0xFF2A2F36),
    outline = Color(0xFF4A505A),
)

internal val DriverSolForteDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF00224A),
    primaryContainer = Color(0xFF00458A),
    onPrimaryContainer = Color(0xFFCFE0FF),
    secondary = Color(0xFFBBC9E4),
    onSecondary = Color(0xFF243348),
    secondaryContainer = Color(0xFF3A4A63),
    onSecondaryContainer = Color(0xFFD6E2F7),
    tertiary = Color(0xFFFFC06B),
    onTertiary = Color(0xFF412600),
    tertiaryContainer = Color(0xFF5D3800),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFFB3AC),
    onError = Color(0xFF5F0009),
    errorContainer = Color(0xFF860010),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF0A0C0F),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFD3D8E0),
    outline = Color(0xFF98A0AC),
)

internal val DriverSolForteLightStatusColors = DriverStatusColors(
    accept = Color(0xFF00701F),
    onAccept = Color.White,
    analyze = Color(0xFF8A5000),
    onAnalyze = Color.White,
    reject = Color(0xFFB3000F),
    onReject = Color.White,
    unknown = Color(0xFF3F454E),
    onUnknown = Color.White,
    home = Color(0xFF5A1E9E),
    onHome = Color.White,
)

internal val DriverSolForteDarkStatusColors = DriverStatusColors(
    accept = Color(0xFF3BE06A),
    onAccept = Color(0xFF00220A),
    analyze = Color(0xFFFFB020),
    onAnalyze = Color(0xFF2A1700),
    reject = Color(0xFFFF4A4A),
    onReject = Color(0xFF2E0000),
    unknown = Color(0xFFD3D8E0),
    onUnknown = Color(0xFF1B1F25),
    home = Color(0xFFC08BFF),
    onHome = Color(0xFF23004A),
)

// -------------------------------------------------------------------------------------------
// Noite quente — night driving.
//
// Rod cells recover slowly from short-wavelength light, so a bright bluish card costs real
// seconds of dark adaptation every time it appears. Backgrounds here are warm near-black, text
// is a warm off-white rather than pure white, and every hue is shifted toward amber. Accept
// becomes a warm yellow-green instead of a cyan-leaning green; reject stays red, which carries
// the least blue of any warning hue. Overall luminance is held down: this is the dimmest of the
// seven identities.
// -------------------------------------------------------------------------------------------

internal val DriverNoiteQuenteLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF7A4B12),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB8),
    onPrimaryContainer = Color(0xFF2A1600),
    secondary = Color(0xFF6B5844),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DFC8),
    onSecondaryContainer = Color(0xFF241A0C),
    tertiary = Color(0xFF5E4A25),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8D4A8),
    onTertiaryContainer = Color(0xFF1F1600),
    error = Color(0xFF9C2A1C),
    onError = Color.White,
    errorContainer = Color(0xFFFFD9D2),
    onErrorContainer = Color(0xFF3A0A04),
    background = Color(0xFFFFF7EC),
    onBackground = Color(0xFF20180E),
    surface = Color(0xFFFFF7EC),
    onSurface = Color(0xFF20180E),
    surfaceVariant = Color(0xFFEEDFCC),
    onSurfaceVariant = Color(0xFF4E4335),
    outline = Color(0xFF80735F),
)

internal val DriverNoiteQuenteDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFE8B473),
    onPrimary = Color(0xFF3D2400),
    primaryContainer = Color(0xFF573600),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFD6C0A5),
    onSecondary = Color(0xFF392C1B),
    secondaryContainer = Color(0xFF51422F),
    onSecondaryContainer = Color(0xFFF3DFC8),
    tertiary = Color(0xFFCCB88E),
    onTertiary = Color(0xFF322505),
    tertiaryContainer = Color(0xFF493B19),
    onTertiaryContainer = Color(0xFFE8D4A8),
    error = Color(0xFFE8A08F),
    onError = Color(0xFF551308),
    errorContainer = Color(0xFF742315),
    onErrorContainer = Color(0xFFFFD9D2),
    background = Color(0xFF15100A),
    onBackground = Color(0xFFEADFD0),
    surface = Color(0xFF1B150E),
    onSurface = Color(0xFFEADFD0),
    surfaceVariant = Color(0xFF4E4335),
    onSurfaceVariant = Color(0xFFD3C4B0),
    outline = Color(0xFF9B8D79),
)

internal val DriverNoiteQuenteLightStatusColors = DriverStatusColors(
    accept = Color(0xFF5E6B00),
    onAccept = Color.White,
    analyze = Color(0xFF9A6400),
    onAnalyze = Color.White,
    reject = Color(0xFFA32F1E),
    onReject = Color.White,
    unknown = Color(0xFF6E6153),
    onUnknown = Color.White,
    home = Color(0xFF7D4E7A),
    onHome = Color.White,
)

internal val DriverNoiteQuenteDarkStatusColors = DriverStatusColors(
    // Warm yellow-green rather than a cyan-leaning green: the same "good" reading, far less blue.
    accept = Color(0xFFB8C46A),
    onAccept = Color(0xFF232A00),
    analyze = Color(0xFFE0A652),
    onAnalyze = Color(0xFF2E1D00),
    reject = Color(0xFFE08872),
    onReject = Color(0xFF3A0F06),
    unknown = Color(0xFFB2A48F),
    onUnknown = Color(0xFF241C12),
    home = Color(0xFFC996C3),
    onHome = Color(0xFF351431),
)

// -------------------------------------------------------------------------------------------
// Monocromo — colour spent only on the verdict.
//
// The card sits on top of a map that is already full of colour. Here every surface, label and
// metric is neutral grey, so the only saturated pixels are the traffic light, the frame and the
// decision label. Nothing on the card competes with the thing the driver needs to read.
// -------------------------------------------------------------------------------------------

internal val DriverMonocromoLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF33383E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1E6),
    onPrimaryContainer = Color(0xFF14181C),
    secondary = Color(0xFF4A4F55),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E5E9),
    onSecondaryContainer = Color(0xFF171A1E),
    tertiary = Color(0xFF5A5F65),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E8EB),
    onTertiaryContainer = Color(0xFF1A1D21),
    error = Color(0xFF9B2226),
    onError = Color.White,
    errorContainer = Color(0xFFF6DADB),
    onErrorContainer = Color(0xFF37080A),
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF16191D),
    surface = Color(0xFFFAFAFB),
    onSurface = Color(0xFF16191D),
    surfaceVariant = Color(0xFFE4E6E9),
    onSurfaceVariant = Color(0xFF43474C),
    outline = Color(0xFF74787E),
)

internal val DriverMonocromoDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC4C8CE),
    onPrimary = Color(0xFF2A2E33),
    primaryContainer = Color(0xFF3E4349),
    onPrimaryContainer = Color(0xFFDDE1E6),
    secondary = Color(0xFFC0C4C9),
    onSecondary = Color(0xFF2C3035),
    secondaryContainer = Color(0xFF42464B),
    onSecondaryContainer = Color(0xFFE2E5E9),
    tertiary = Color(0xFFBDC1C6),
    onTertiary = Color(0xFF2E3237),
    tertiaryContainer = Color(0xFF44484D),
    onTertiaryContainer = Color(0xFFE6E8EB),
    error = Color(0xFFF0A9AB),
    onError = Color(0xFF4E1013),
    errorContainer = Color(0xFF6E191C),
    onErrorContainer = Color(0xFFF6DADB),
    background = Color(0xFF111315),
    onBackground = Color(0xFFE3E5E8),
    surface = Color(0xFF17191C),
    onSurface = Color(0xFFE3E5E8),
    surfaceVariant = Color(0xFF43474C),
    onSurfaceVariant = Color(0xFFC5C9CE),
    outline = Color(0xFF8D9197),
)

internal val DriverMonocromoLightStatusColors = DriverStatusColors(
    accept = Color(0xFF1B7F3B),
    onAccept = Color.White,
    analyze = Color(0xFF9A6B00),
    onAnalyze = Color.White,
    reject = Color(0xFFB02A2E),
    onReject = Color.White,
    // The only neutral verdict, so an unreadable card recedes instead of reading as a state.
    unknown = Color(0xFF868A90),
    onUnknown = Color.White,
    home = Color(0xFF6B3FA0),
    onHome = Color.White,
)

internal val DriverMonocromoDarkStatusColors = DriverStatusColors(
    accept = Color(0xFF4ECB78),
    onAccept = Color(0xFF00250F),
    analyze = Color(0xFFE0AE45),
    onAnalyze = Color(0xFF2B1D00),
    reject = Color(0xFFF2726F),
    onReject = Color(0xFF3B0407),
    unknown = Color(0xFF868A90),
    onUnknown = Color(0xFF16191D),
    home = Color(0xFFB68CF0),
    onHome = Color(0xFF2A0E52),
)
