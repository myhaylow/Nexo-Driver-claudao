package br.com.nexo.driver.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Esquema de cores do redesign (mockup): fundo slate quase-preto, destaque esmeralda, cards
 * slate-900. Aplicado só às telas do app (via [NexoAppTheme]); o overlay tem paleta própria.
 *
 * Mapeado sobre os papéis do Material3 para que os componentes existentes (Card, Switch, Slider,
 * TopAppBar, RadioButton) adotem o visual sem reescrever cada tela.
 */
val NexoMockupDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),            // emerald-500
    onPrimary = Color(0xFF04140D),
    primaryContainer = Color(0xFF065F46),   // emerald-800
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF3B82F6),          // blue-500
    onSecondary = Color(0xFF04122B),
    secondaryContainer = Color(0xFF0F172A), // slate-900
    onSecondaryContainer = Color(0xFFCBD5E1),
    tertiary = Color(0xFFFBBF24),           // amber-400
    onTertiary = Color(0xFF1A1204),
    tertiaryContainer = Color(0xFF3F2D06),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = Color(0xFF020617),          // slate-950
    onBackground = Color(0xFFF1F5F9),        // slate-100
    surface = Color(0xFF020617),             // slate-950
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),      // slate-800
    onSurfaceVariant = Color(0xFF94A3B8),    // slate-400
    surfaceContainerLowest = Color(0xFF010309),
    surfaceContainerLow = Color(0xFF0F172A), // slate-900
    surfaceContainer = Color(0xFF0F172A),    // slate-900
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerHighest = Color(0xFF1E293B),
    surfaceBright = Color(0xFF1E293B),
    surfaceDim = Color(0xFF020617),
    outline = Color(0xFF334155),             // slate-700
    outlineVariant = Color(0xFF1E293B),      // slate-800
    error = Color(0xFFF87171),               // red-400
    onError = Color(0xFF1A0505),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0F172A),
    scrim = Color(0xFF000000),
)

/** Gradiente esmeralda→azul do título "Nexo Driver". */
val NexoTitleGradient: Brush = Brush.horizontalGradient(
    listOf(Color(0xFF34D399), Color(0xFF3B82F6)),
)

/**
 * Aplica o visual do mockup às telas do app, preservando a tipografia do projeto e a escala de
 * densidade (fonte do app) já provida por [DriverInteligenteTheme] ao redor.
 */
@Composable
fun NexoAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexoMockupDarkColors,
        typography = DriverInteligenteTypography,
        content = content,
    )
}
