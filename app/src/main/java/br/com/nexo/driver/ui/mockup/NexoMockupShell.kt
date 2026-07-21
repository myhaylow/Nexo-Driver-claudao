package br.com.nexo.driver.ui.mockup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.nexo.driver.analysis.OfferHistoryEntry
import br.com.nexo.driver.analysis.OfferReadPath
import br.com.nexo.driver.analysis.OfferSessionMetrics
import br.com.nexo.driver.analysis.SessionTelemetry
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.overlay.OverlayLayoutStyle
import br.com.nexo.driver.overlay.OverlayStatus
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.ui.settings.CARD_DURATION_OPTIONS
import br.com.nexo.driver.ui.settings.DecisionStrictness
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Paleta Tailwind do mockup (valores exatos).
// ---------------------------------------------------------------------------------------------
internal object M {
    val black = Color(0xFF000000)
    val white = Color(0xFFFFFFFF)
    val slate950 = Color(0xFF020617)
    val slate900 = Color(0xFF0F172A)
    val slate800 = Color(0xFF1E293B)
    val slate700 = Color(0xFF334155)
    val slate500 = Color(0xFF64748B)
    val slate400 = Color(0xFF94A3B8)
    val slate300 = Color(0xFFCBD5E1)
    val slate200 = Color(0xFFE2E8F0)
    val emerald400 = Color(0xFF34D399)
    val emerald500 = Color(0xFF10B981)
    val emerald600 = Color(0xFF059669)
    val blue400 = Color(0xFF60A5FA)
    val blue500 = Color(0xFF3B82F6)
    val blue800 = Color(0xFF1E40AF)
    val blue100 = Color(0xFFDBEAFE)
    val amber400 = Color(0xFFFBBF24)
    val amber500 = Color(0xFFF59E0B)
    val indigo400 = Color(0xFF818CF8)
    val indigo500 = Color(0xFF6366F1)
    val indigo600 = Color(0xFF4F46E5)
    val red400 = Color(0xFFF87171)
    val red500 = Color(0xFFEF4444)
    val yellow500 = Color(0xFFEAB308)
}

private val PT_BR = Locale.forLanguageTag("pt-BR")

private fun money(value: Double): String = "R$ " + String.format(PT_BR, "%.2f", value)

/** Aba ativa do shell (espelha o menu inferior do mockup). */
enum class MockupTab { HOME, FILTERS, SETTINGS }

/** Estado real do app projetado na estrutura do mockup. */
data class MockupShellState(
    val readerEnabled: Boolean,
    val readerStatusText: String,
    val profileName: String,
    val profileEnabled: Boolean,
    val rules: List<FilterRule>,
    val sessionKm: Double,
    val locationEnabled: Boolean,
    val fuelPriceCents: Long,
    val fuelKmPerLiter: Double,
    val overlayFields: List<OverlayMetricField>,
    val overlayLayout: OverlayLayoutStyle,
    val overlayFontScale: Float,
    val homeDestinationName: String?,
    val galleryTestStatus: String?,
    val showDebugTools: Boolean,
    val accessibilityEnabled: Boolean,
    val speakDecision: Boolean,
    val blockSupermarkets: Boolean,
    /** Histórico real das ofertas avaliadas na sessão, mais recente primeiro. */
    val history: List<OfferHistoryEntry> = emptyList(),
    /** Saúde real da leitura de tela desta sessão. */
    val readMetrics: OfferSessionMetrics = OfferSessionMetrics(),
    /** Permissão de sobreposição concedida (verificação de leitura). */
    val overlayPermissionGranted: Boolean = false,
    /** Início da sessão de telemetria (relógio ligado pelo serviço de GPS), ou null se parada. */
    val sessionStartEpochMs: Long? = null,
    /** Limiar de pontuação para ACEITAR (rigor da decisão). */
    val acceptThreshold: Int = 80,
    /** Limiar de pontuação para EM ANÁLISE. */
    val analyzeThreshold: Int = 50,
    /** Quanto tempo o card fica na tela. */
    val cardDurationMs: Long = 12_000L,
)

/** Callbacks para as funções reais. */
data class MockupShellActions(
    val onReaderToggle: (Boolean) -> Unit,
    val onProfileEnabledChange: (Boolean) -> Unit,
    val onRuleTargetChange: (Metric, Comparator, Long) -> Unit,
    val onRuleEnabledChange: (Metric, Comparator, Boolean) -> Unit,
    val onEditRule: (Metric, Comparator) -> Unit,
    val onAddFilter: () -> Unit,
    val onLocationToggle: (Boolean) -> Unit,
    val onFuelPriceChange: (Long) -> Unit,
    val onFuelConsumptionChange: (Double) -> Unit,
    val onOverlayFieldsChange: (List<OverlayMetricField>) -> Unit,
    val onOverlayLayoutChange: (OverlayLayoutStyle) -> Unit,
    val onOverlayFontScaleChange: (Float) -> Unit,
    val onConfigureHomeDestination: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onSpeakDecisionChange: (Boolean) -> Unit,
    val onBlockSupermarketsChange: (Boolean) -> Unit,
    val onTestGallery: () -> Unit,
    val onDecisionThresholdsChange: (accept: Int, analyze: Int) -> Unit,
    val onCardDurationChange: (Long) -> Unit,
)

/**
 * O app com a cara exata do mockup `nexo_driver_sem_foro.tsx`: header com título gradiente e botão
 * power, botão de teste do overlay, três abas sanfonadas e menu inferior. As funções reais moram
 * nas seções desenhadas; o que ainda não existe (histórico, telemetria fina) imita o mockup.
 */
@Composable
fun NexoMockupShell(
    tab: MockupTab,
    onTabChange: (MockupTab) -> Unit,
    state: MockupShellState,
    actions: MockupShellActions,
) {
    var showOverlaySim by remember { mutableStateOf(false) }
    var expandedHome by remember { mutableStateOf(setOf("historico", "telemetriaHome")) }
    var expandedFilters by remember { mutableStateOf(setOf("ganhos", "distancias")) }
    var expandedSettings by remember { mutableStateOf(setOf("layoutOverlay", "combustivel", "telemetriaSettings")) }

    fun toggle(current: Set<String>, section: String): Set<String> =
        if (section in current) current - section else current + section

    val custoPorKm = (state.fuelPriceCents / 100.0) / state.fuelKmPerLiter.coerceAtLeast(0.1)

    Box(Modifier.fillMaxSize().background(M.black)) {
        Column(Modifier.fillMaxSize().background(M.slate950)) {
            // ---------------- Header ----------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(M.slate900)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Nexo Driver",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            brush = Brush.horizontalGradient(listOf(M.emerald400, M.blue500)),
                        ),
                    )
                    Text(
                        "Night Mode Pro • Semáforo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = M.slate400,
                    )
                }
                val on = state.readerEnabled
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) M.emerald500.copy(alpha = 0.2f) else M.slate800)
                        .border(1.dp, if (on) M.emerald500.copy(alpha = 0.4f) else M.slate700, CircleShape)
                        .clickable { actions.onReaderToggle(!on) }
                        .padding(12.dp),
                ) {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        "Leitor de ofertas",
                        tint = if (on) M.emerald400 else M.slate500,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ---------------- Corpo ----------------
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedPill(
                    text = "Testar Overlay na Uber (${state.overlayLayout.label.uppercase(PT_BR)})",
                    icon = Icons.Filled.ErrorOutline,
                    onClick = { showOverlaySim = true },
                )

                when (tab) {
                    MockupTab.HOME -> HomeTab(
                        state = state,
                        custoPorKm = custoPorKm,
                        expanded = expandedHome,
                        onToggle = { expandedHome = toggle(expandedHome, it) },
                        actions = actions,
                    )
                    MockupTab.FILTERS -> FiltersTab(
                        state = state,
                        expanded = expandedFilters,
                        onToggle = { expandedFilters = toggle(expandedFilters, it) },
                        actions = actions,
                    )
                    MockupTab.SETTINGS -> SettingsTab(
                        state = state,
                        custoPorKm = custoPorKm,
                        expanded = expandedSettings,
                        onToggle = { expandedSettings = toggle(expandedSettings, it) },
                        actions = actions,
                    )
                }
            }

            // ---------------- Menu inferior ----------------
            Column(Modifier.fillMaxWidth().background(M.slate950.copy(alpha = 0.9f))) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(M.slate800))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavButton("Início", Icons.Outlined.Home, tab == MockupTab.HOME) { onTabChange(MockupTab.HOME) }
                    NavButton("Filtros", Icons.Outlined.Tune, tab == MockupTab.FILTERS) { onTabChange(MockupTab.FILTERS) }
                    NavButton("Ajustes", Icons.Outlined.Settings, tab == MockupTab.SETTINGS) { onTabChange(MockupTab.SETTINGS) }
                }
            }
        }

        BackHandler(enabled = showOverlaySim) { showOverlaySim = false }
        if (showOverlaySim) {
            UberScreenSimulation(
                layout = state.overlayLayout,
                fields = state.overlayFields,
                fontScale = state.overlayFontScale,
                custoPorKm = custoPorKm,
                onClose = { showOverlaySim = false },
            )
        }
    }
}

// ============================================================================================
// Componentes base (idênticos ao mockup)
// ============================================================================================

@Composable
private fun OutlinedPill(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(M.slate900)
            .border(1.dp, M.emerald500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = M.emerald400, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = M.emerald400, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
internal fun AccordionSection(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(M.slate900)
            .border(1.dp, M.slate800, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(M.slate800.copy(alpha = 0.3f))
                .clickable(onClick = onToggle)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(iconBg).padding(8.dp)) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, color = M.white, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (subtitle != null) Text(subtitle, color = M.slate400, fontSize = 11.sp)
                }
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null, tint = M.slate500, modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) Box(Modifier.fillMaxWidth().background(M.slate900)) { content() }
    }
}

@Composable
internal fun ThemedSlider(
    value: Float,
    onValue: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValue,
        valueRange = range,
        modifier = modifier.height(24.dp),
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = M.slate800,
        ),
    )
}

@Composable
internal fun SliderRow(label: String, value: String, valueColor: Color, slider: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        slider()
    }
}

@Composable
internal fun SelectButton(
    text: String,
    selected: Boolean,
    selectedBg: Color,
    selectedBorder: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) selectedBg else M.slate800)
            .border(1.dp, if (selected) selectedBorder else M.slate700, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) M.white else M.slate400,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun MockupSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = M.emerald500,
            checkedThumbColor = M.white,
            uncheckedTrackColor = M.slate700,
            uncheckedThumbColor = M.white,
            uncheckedBorderColor = M.slate700,
        ),
    )
}

@Composable
private fun NavButton(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val color = if (active) M.emerald400 else M.slate500
    Column(
        Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun DarkStatRow(label: String, value: String, valueColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(M.slate950)
            .border(1.dp, M.slate800, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = M.slate400, fontSize = 12.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// ============================================================================================
// ABA INÍCIO
// ============================================================================================

private fun OverlayStatus.historyColor(): Color = when (this) {
    OverlayStatus.ACCEPT -> M.emerald400
    OverlayStatus.ANALYZE -> M.amber400
    OverlayStatus.REJECT -> M.red400
    OverlayStatus.UNKNOWN -> M.slate400
}

@Composable
private fun HomeTab(
    state: MockupShellState,
    custoPorKm: Double,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    actions: MockupShellActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Status rápido
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(M.slate900)
                    .border(1.dp, M.slate800, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MonitorHeart, null, tint = M.emerald400, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Status Leitor", color = M.slate400, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (state.readerEnabled) M.emerald400 else M.slate500),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.readerStatusText,
                        color = if (state.readerEnabled) M.emerald400 else M.slate400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(M.slate900)
                    .border(1.dp, M.slate800, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, null, tint = M.blue400, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Custo / KM", color = M.slate400, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(money(custoPorKm), color = M.white, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Telemetria da sessão (dados reais de GPS + combustível)
        AccordionSection(
            icon = Icons.Filled.LocalFireDepartment,
            iconTint = M.amber400,
            iconBg = M.amber500.copy(alpha = 0.1f),
            title = "Telemetria da Sessão",
            subtitle = "Gasto em tempo real",
            expanded = "telemetriaHome" in expanded,
            onToggle = { onToggle("telemetriaHome") },
        ) {
            val litros = state.sessionKm / state.fuelKmPerLiter.coerceAtLeast(0.1)
            val reais = litros * (state.fuelPriceCents / 100.0)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(M.slate950)
                            .border(1.dp, M.slate800, RoundedCornerShape(8.dp)).padding(12.dp),
                    ) {
                        Text("KM PERCORRIDO", color = M.slate400, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(
                            String.format(PT_BR, "%.1f km", state.sessionKm),
                            color = M.white, fontWeight = FontWeight.Black, fontSize = 18.sp,
                        )
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(M.slate950)
                            .border(1.dp, M.slate800, RoundedCornerShape(8.dp)).padding(12.dp),
                    ) {
                        Text("COMBUSTÍVEL EST.", color = M.slate400, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(
                            String.format(PT_BR, "%.1f L", litros),
                            color = M.amber400, fontWeight = FontWeight.Black, fontSize = 18.sp,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(M.slate950)
                        .border(1.dp, M.slate800, RoundedCornerShape(8.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Gasto total aproximado:", color = M.slate400, fontSize = 12.sp)
                    Text(money(reais), color = M.red400, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                // GPS real alimenta o km da sessão
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Localização GPS (km da sessão)", color = M.slate300, fontSize = 12.sp)
                    MockupSwitch(state.locationEnabled, actions.onLocationToggle)
                }
            }
        }

        // Histórico de ofertas (real: alimentado pelo motor de análise)
        AccordionSection(
            icon = Icons.Outlined.History,
            iconTint = M.blue400,
            iconBg = M.blue500.copy(alpha = 0.1f),
            title = "Histórico de Ofertas",
            subtitle = if (state.history.isEmpty()) {
                "Aguardando a primeira oferta da sessão"
            } else {
                "${state.history.size} análise(s) nesta sessão"
            },
            expanded = "historico" in expanded,
            onToggle = { onToggle("historico") },
        ) {
            val timeFormat = remember { SimpleDateFormat("HH:mm", PT_BR) }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.history.isEmpty()) {
                    Text(
                        "Nenhuma oferta avaliada ainda. Ligue o leitor e as análises aparecem aqui.",
                        color = M.slate400,
                        fontSize = 12.sp,
                    )
                }
                state.history.take(10).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(M.slate950)
                            .border(1.dp, M.slate800, RoundedCornerShape(8.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.provider, color = M.white, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(timeFormat.format(Date(item.atEpochMs)), color = M.slate500, fontSize = 10.sp)
                            }
                            Text(
                                listOfNotNull(
                                    item.totalDistanceMeters?.let { String.format(PT_BR, "%.1f km", it / 1_000.0) },
                                    item.totalDurationSeconds?.let { "${(it + 59) / 60} min" },
                                ).joinToString(" • ").ifBlank { "dados parciais" },
                                color = M.slate400,
                                fontSize = 12.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                item.payoutCents?.let { money(it / 100.0) } ?: "—",
                                color = M.white, fontWeight = FontWeight.Black, fontSize = 14.sp,
                            )
                            Text(
                                item.ratePerKmCents?.let { money(it / 100.0) + "/km" } ?: "—",
                                color = item.status.historyColor(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }

        // Verificação real da leitura de tela (permissões + saúde da última leitura)
        AccordionSection(
            icon = Icons.Filled.MonitorHeart,
            iconTint = M.emerald400,
            iconBg = M.emerald500.copy(alpha = 0.1f),
            title = "Verificação de Leitura",
            subtitle = "Saúde real da leitura de tela",
            expanded = "verificacao" in expanded,
            onToggle = { onToggle("verificacao") },
        ) {
            val metrics = state.readMetrics
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkStatRow(
                    "Acessibilidade:",
                    if (state.accessibilityEnabled) "✓ Ativa" else "✗ Inativa",
                    if (state.accessibilityEnabled) M.emerald400 else M.red400,
                )
                DarkStatRow(
                    "Sobreposição:",
                    if (state.overlayPermissionGranted) "✓ Autorizada" else "✗ Não autorizada",
                    if (state.overlayPermissionGranted) M.emerald400 else M.red400,
                )
                DarkStatRow(
                    "Leitor:",
                    state.readerStatusText,
                    if (state.readerEnabled) M.emerald400 else M.slate400,
                )
                if (metrics.totalReads == 0) {
                    Text(
                        "Nenhuma leitura nesta sessão ainda — os números aparecem após a primeira oferta.",
                        color = M.slate400,
                        fontSize = 11.sp,
                    )
                } else {
                    DarkStatRow("Ofertas lidas:", "${metrics.totalReads} (${metrics.readsViaOcr} via OCR)", M.white)
                    metrics.lastReadPath?.let { path ->
                        DarkStatRow(
                            "Última leitura:",
                            if (path == OfferReadPath.ACCESSIBILITY) "acessibilidade (rápida)" else "OCR (fallback)",
                            if (path == OfferReadPath.ACCESSIBILITY) M.emerald400 else M.amber400,
                        )
                    }
                    metrics.lastLatencyMs?.let { latency ->
                        DarkStatRow(
                            "Tempo até o card:",
                            "$latency ms",
                            if (latency <= 1_000) M.emerald400 else M.amber400,
                        )
                    }
                    metrics.lastCoveragePercent?.let { coverage ->
                        DarkStatRow(
                            "Dados lidos:",
                            "$coverage%",
                            if (coverage >= 60) M.emerald400 else M.amber400,
                        )
                    }
                    if (metrics.isOcrOnly) {
                        Text(
                            "Só por OCR: a leitura rápida por acessibilidade não está funcionando neste app. " +
                                "O OCR consome mais bateria.",
                            color = M.amber400,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // Destino casa (função real, vestida como seção do mockup)
        AccordionSection(
            icon = Icons.Filled.Home,
            iconTint = M.indigo400,
            iconBg = M.indigo500.copy(alpha = 0.1f),
            title = "Destino Casa",
            subtitle = state.homeDestinationName ?: "Toque para configurar",
            expanded = false,
            onToggle = actions.onConfigureHomeDestination,
        ) {}
    }
}

// ============================================================================================
// ABA FILTROS — sliders do mockup ligados às regras reais
// ============================================================================================

private fun List<FilterRule>.target(metric: Metric, comparator: Comparator): Long? =
    firstOrNull { it.metric == metric && it.comparator == comparator }?.target

@Composable
private fun FiltersTab(
    state: MockupShellState,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    actions: MockupShellActions,
) {
    val rules = state.rules
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header da aba (perfil real + toggle real)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(M.slate900)
                .border(1.dp, M.slate800, RoundedCornerShape(12.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Filtros de Aceite", color = M.white, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Perfil: ${state.profileName}", color = M.emerald400, fontSize = 12.sp)
            }
            MockupSwitch(state.profileEnabled, actions.onProfileEnabledChange)
        }

        // Ganhos mínimos (regras reais)
        AccordionSection(
            icon = Icons.Filled.AttachMoney,
            iconTint = M.emerald400,
            iconBg = M.emerald500.copy(alpha = 0.1f),
            title = "Ganhos Mínimos",
            subtitle = null,
            expanded = "ganhos" in expanded,
            onToggle = { onToggle("ganhos") },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Passos iguais aos do mockup (R$ 0,50 / R$ 1 / R$ 0,10): o valor gravado é sempre
                // redondo e o arrasto não salva dezenas de alvos intermediários quebrados.
                val pagamento = (rules.target(Metric.PAYOUT, Comparator.AT_LEAST) ?: 850L) / 100.0
                SliderRow("Pagamento Total Mínimo", money(pagamento), M.emerald400) {
                    ThemedSlider(pagamento.toFloat(), { v ->
                        actions.onRuleTargetChange(Metric.PAYOUT, Comparator.AT_LEAST, (v * 2).roundToInt() * 50L)
                    }, 5f..30f, M.emerald500)
                }
                val valorHora = (rules.target(Metric.RATE_PER_HOUR, Comparator.AT_LEAST) ?: 4_200L) / 100.0
                SliderRow("Valor por Hora (Min)", money(valorHora), M.emerald400) {
                    ThemedSlider(valorHora.toFloat(), { v ->
                        actions.onRuleTargetChange(Metric.RATE_PER_HOUR, Comparator.AT_LEAST, v.roundToInt() * 100L)
                    }, 20f..80f, M.emerald500)
                }
                val valorKm = (rules.target(Metric.RATE_PER_KM, Comparator.AT_LEAST) ?: 180L) / 100.0
                SliderRow("Valor por KM (Min)", money(valorKm), M.emerald400) {
                    ThemedSlider(valorKm.toFloat(), { v ->
                        actions.onRuleTargetChange(Metric.RATE_PER_KM, Comparator.AT_LEAST, (v * 10).roundToInt() * 10L)
                    }, 1f..4f, M.emerald500)
                }
            }
        }

        // Busca e deslocamento (regras reais)
        AccordionSection(
            icon = Icons.Filled.Route,
            iconTint = M.blue400,
            iconBg = M.blue500.copy(alpha = 0.1f),
            title = "Busca e Deslocamento",
            subtitle = null,
            expanded = "distancias" in expanded,
            onToggle = { onToggle("distancias") },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val tempoMin = ((rules.target(Metric.PICKUP_DURATION, Comparator.AT_MOST) ?: 300L) / 60.0)
                SliderRow("Tempo de Busca (Máx)", "${tempoMin.toInt()} min", M.blue400) {
                    ThemedSlider(tempoMin.toFloat(), { v ->
                        actions.onRuleTargetChange(Metric.PICKUP_DURATION, Comparator.AT_MOST, (v.toInt() * 60).toLong())
                    }, 1f..15f, M.blue500)
                }
                val distKm = ((rules.target(Metric.PICKUP_DISTANCE, Comparator.AT_MOST) ?: 2_500L) / 1_000.0)
                SliderRow("Distância de Busca (Máx)", String.format(PT_BR, "%.1f km", distKm), M.blue400) {
                    ThemedSlider(distKm.toFloat(), { v ->
                        actions.onRuleTargetChange(Metric.PICKUP_DISTANCE, Comparator.AT_MOST, (v * 2).roundToInt() * 500L)
                    }, 0.5f..10f, M.blue500)
                }
            }
        }

        // Outros filtros reais (nota, paradas, sentido casa, etc.)
        AccordionSection(
            icon = Icons.Outlined.Tune,
            iconTint = M.indigo400,
            iconBg = M.indigo500.copy(alpha = 0.1f),
            title = "Outros Filtros",
            subtitle = "Regras avançadas do perfil",
            expanded = "outros" in expanded,
            onToggle = { onToggle("outros") },
        ) {
            val principal = setOf(
                Metric.PAYOUT to Comparator.AT_LEAST,
                Metric.RATE_PER_HOUR to Comparator.AT_LEAST,
                Metric.RATE_PER_KM to Comparator.AT_LEAST,
                Metric.PICKUP_DURATION to Comparator.AT_MOST,
                Metric.PICKUP_DISTANCE to Comparator.AT_MOST,
            )
            val outros = rules.filter { (it.metric to it.comparator) !in principal }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                outros.forEach { rule ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(M.slate950)
                            .border(1.dp, M.slate800, RoundedCornerShape(8.dp))
                            .clickable { actions.onEditRule(rule.metric, rule.comparator) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            rule.metric.label,
                            color = if (rule.enabled) M.white else M.slate500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        MockupSwitch(rule.enabled) { actions.onRuleEnabledChange(rule.metric, rule.comparator, it) }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .border(1.dp, M.emerald500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable(onClick = actions.onAddFilter)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+ Adicionar filtro", color = M.emerald400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ============================================================================================
// ABA AJUSTES
// ============================================================================================

@Composable
private fun SettingsTab(
    state: MockupShellState,
    custoPorKm: Double,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    actions: MockupShellActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Layout do overlay (real: layout + campos + fonte)
        AccordionSection(
            icon = Icons.Outlined.GridView,
            iconTint = M.indigo400,
            iconBg = M.indigo500.copy(alpha = 0.1f),
            title = "Layout do Overlay",
            subtitle = "Posição, orientação e campos",
            expanded = "layoutOverlay" in expanded,
            onToggle = { onToggle("layoutOverlay") },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text("Posição do Semáforo", color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayLayoutStyle.entries.forEach { style ->
                            SelectButton(
                                text = style.label,
                                selected = state.overlayLayout == style,
                                selectedBg = M.indigo600,
                                selectedBorder = M.indigo500,
                                modifier = Modifier.weight(1f),
                            ) { actions.onOverlayLayoutChange(style) }
                        }
                    }
                }
                Column {
                    Text(
                        "Quantidade de Campos Exibidos",
                        color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 4).forEach { num ->
                            SelectButton(
                                text = "$num Campos Intercambiáveis",
                                selected = state.overlayFields.size == num,
                                selectedBg = M.emerald600,
                                selectedBorder = M.emerald500,
                                modifier = Modifier.weight(1f),
                            ) {
                                val current = state.overlayFields
                                val resized = when {
                                    num < current.size -> current.take(num)
                                    num > current.size -> current + OverlayMetricField.entries
                                        .first { it !in current }
                                    else -> current
                                }
                                actions.onOverlayFieldsChange(resized)
                            }
                        }
                    }
                }
                Column {
                    Text("Selecione as Métricas Ativas", color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayMetricField.entries.chunked(2).forEach { rowFields ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowFields.forEach { field ->
                                    val isSelected = field in state.overlayFields
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) M.slate800 else M.slate950)
                                            .border(
                                                1.dp,
                                                if (isSelected) M.emerald500.copy(alpha = 0.5f) else M.slate800,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .clickable {
                                                val current = state.overlayFields
                                                val next = when {
                                                    isSelected && current.size > 3 -> current - field
                                                    !isSelected && current.size < 4 -> current + field
                                                    !isSelected -> current.drop(1) + field
                                                    else -> current
                                                }
                                                actions.onOverlayFieldsChange(next)
                                            }
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            field.label,
                                            color = if (isSelected) M.emerald400 else M.slate500,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (isSelected) "✓" else "+",
                                            color = if (isSelected) M.emerald400 else M.slate500,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                if (rowFields.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                SliderRow(
                    "Tamanho da Fonte do Overlay",
                    "${(state.overlayFontScale * 100).toInt()}%",
                    M.indigo400,
                ) {
                    ThemedSlider(state.overlayFontScale, actions.onOverlayFontScaleChange, 0.8f..1.6f, M.indigo500)
                }
            }
        }

        // 2. Custo de combustível (real)
        AccordionSection(
            icon = Icons.Filled.LocalGasStation,
            iconTint = M.amber400,
            iconBg = M.amber500.copy(alpha = 0.1f),
            title = "Custo de Combustível",
            subtitle = "Sliders com ajuste fino (+/-)",
            expanded = "combustivel" in expanded,
            onToggle = { onToggle("combustivel") },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                val precoReais = state.fuelPriceCents / 100.0
                FuelRow(
                    label = "Preço do Litro (R$)",
                    valueText = money(precoReais),
                    value = precoReais.toFloat(),
                    range = 3f..9f,
                    onMinus = { actions.onFuelPriceChange((state.fuelPriceCents - 5).coerceAtLeast(300)) },
                    onPlus = { actions.onFuelPriceChange((state.fuelPriceCents + 5).coerceAtMost(900)) },
                    onValue = { v -> actions.onFuelPriceChange((v * 20).roundToInt() * 5L) },
                )
                FuelRow(
                    label = "Autonomia do Carro (km/L)",
                    valueText = String.format(PT_BR, "%.1f km/L", state.fuelKmPerLiter),
                    value = state.fuelKmPerLiter.toFloat(),
                    range = 4f..25f,
                    onMinus = { actions.onFuelConsumptionChange((state.fuelKmPerLiter - 0.5).coerceAtLeast(4.0)) },
                    onPlus = { actions.onFuelConsumptionChange((state.fuelKmPerLiter + 0.5).coerceAtMost(25.0)) },
                    onValue = { v -> actions.onFuelConsumptionChange((v * 2).roundToInt() / 2.0) },
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(M.slate950)
                        .border(1.dp, M.slate800, RoundedCornerShape(8.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Custo do Combustível por KM:", color = M.slate400, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(money(custoPorKm) + " / km", color = M.emerald400, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }

        // 3. Telemetria completa (motor real: km da sessão + relógio + combustível)
        AccordionSection(
            icon = Icons.Filled.Speed,
            iconTint = M.emerald400,
            iconBg = M.emerald500.copy(alpha = 0.1f),
            title = "Telemetria & Consumo Real",
            subtitle = "Estatísticas dinâmicas em tempo real",
            expanded = "telemetriaSettings" in expanded,
            onToggle = { onToggle("telemetriaSettings") },
        ) {
            val telemetry = SessionTelemetry.compute(
                sessionKm = state.sessionKm,
                sessionStartEpochMs = state.sessionStartEpochMs,
                nowEpochMs = System.currentTimeMillis(),
                pricePerLiterCents = state.fuelPriceCents,
                kilometersPerLiter = state.fuelKmPerLiter,
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DarkStatRow(
                    "Taxa de Queima Atual:",
                    telemetry.burnRateLitersPerHour?.let { String.format(PT_BR, "%.1f L / hora", it) }
                        ?: "— (ligue o GPS e rode)",
                    M.white,
                )
                DarkStatRow(
                    "Velocidade Média da Sessão:",
                    telemetry.averageSpeedKmh?.let { String.format(PT_BR, "%.0f km/h", it) }
                        ?: "— (aguardando km)",
                    M.emerald400,
                )
                DarkStatRow(
                    "Combustível Usado na Sessão:",
                    telemetry.litersUsed?.let {
                        String.format(PT_BR, "%.1f L", it) +
                            (telemetry.fuelCostReais?.let { cost -> " (${money(cost)})" } ?: "")
                    } ?: "— (aguardando km)",
                    M.amber400,
                )
                DarkStatRow("Gasto da Viagem Média (10 km):", money(10 * custoPorKm), M.amber400)
                telemetry.elapsedMs?.let { elapsed ->
                    val minutes = elapsed / 60_000
                    DarkStatRow("Sessão ativa há:", "${minutes / 60}h ${minutes % 60}min", M.slate300)
                }
            }
        }

        // 4. Decisão (rigor + duração do card, funções reais)
        AccordionSection(
            icon = Icons.Filled.Rule,
            iconTint = M.emerald400,
            iconBg = M.emerald500.copy(alpha = 0.1f),
            title = "Rigor da Decisão",
            subtitle = "Quando aceitar, analisar ou recusar",
            expanded = "decisao" in expanded,
            onToggle = { onToggle("decisao") },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text(
                        "Perfil de Rigor",
                        color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val current = DecisionStrictness.forThresholds(state.acceptThreshold, state.analyzeThreshold)
                        DecisionStrictness.entries.forEach { preset ->
                            SelectButton(
                                text = preset.label,
                                selected = current == preset,
                                selectedBg = M.emerald600,
                                selectedBorder = M.emerald500,
                                modifier = Modifier.weight(1f),
                            ) {
                                actions.onDecisionThresholdsChange(preset.acceptThreshold, preset.analyzeThreshold)
                            }
                        }
                    }
                }
                Column {
                    Text(
                        "Duração do Card na Tela",
                        color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CARD_DURATION_OPTIONS.forEach { millis ->
                            val selected = CARD_DURATION_OPTIONS.minByOrNull {
                                kotlin.math.abs(it - state.cardDurationMs)
                            } == millis
                            SelectButton(
                                text = "${millis / 1000}s",
                                selected = selected,
                                selectedBg = M.indigo600,
                                selectedBorder = M.indigo500,
                                modifier = Modifier.weight(1f),
                            ) { actions.onCardDurationChange(millis) }
                        }
                    }
                }
            }
        }

        // 5. Leitura, voz e bloqueios (funções reais no traje do mockup)
        AccordionSection(
            icon = Icons.Filled.Settings,
            iconTint = M.blue400,
            iconBg = M.blue500.copy(alpha = 0.1f),
            title = "Leitura, Voz e Bloqueios",
            subtitle = "Acessibilidade, fala e lista de bloqueio",
            expanded = "leitura" in expanded,
            onToggle = { onToggle("leitura") },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (state.accessibilityEnabled) M.slate800 else M.emerald600)
                        .clickable(onClick = actions.onOpenAccessibility)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.accessibilityEnabled) "Acessibilidade ativa — abrir configurações" else "Ativar acessibilidade",
                        color = M.white, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Falar decisão da corrida", color = M.slate300, fontSize = 12.sp)
                    MockupSwitch(state.speakDecision, actions.onSpeakDecisionChange)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bloquear supermercados", color = M.slate300, fontSize = 12.sp)
                    MockupSwitch(state.blockSupermarkets, actions.onBlockSupermarketsChange)
                }
                if (state.showDebugTools) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .border(1.dp, M.emerald500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(onClick = actions.onTestGallery)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Testar imagem da galeria", color = M.emerald400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    state.galleryTestStatus?.let {
                        Text(it, color = M.slate400, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValue: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = M.amber400, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton(Icons.Filled.Remove, onMinus)
            ThemedSlider(value, onValue, range, M.amber500, modifier = Modifier.weight(1f))
            StepButton(Icons.Filled.Add, onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(M.slate800)
            .border(1.dp, M.slate700, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(icon, null, tint = M.white, modifier = Modifier.size(16.dp))
    }
}

// ============================================================================================
// Simulação do overlay sobre a "tela da Uber" (idêntica ao mockup)
// ============================================================================================

private val sampleMetricValues = mapOf(
    OverlayMetricField.RATE_PER_KM to "R$ 2,85",
    OverlayMetricField.RATE_PER_HOUR to "R$ 52,10",
    OverlayMetricField.RATE_PER_MINUTE to "R$ 1,54",
    OverlayMetricField.PASSENGER_RATING to "4,95",
    OverlayMetricField.PICKUP to "3min·1,2km",
    OverlayMetricField.TOTAL_DURATION to "25 min",
    OverlayMetricField.TOTAL_DISTANCE to "13,5 km",
    OverlayMetricField.PAYOUT to "R$ 38,50",
    OverlayMetricField.NET_PROFIT to "R$ 30,42",
    OverlayMetricField.NET_PROFIT_PERCENT to "78%",
    OverlayMetricField.NET_PROFIT_PER_HOUR to "R$ 40,90",
)

@Composable
private fun UberScreenSimulation(
    layout: OverlayLayoutStyle,
    fields: List<OverlayMetricField>,
    fontScale: Float,
    custoPorKm: Double,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(M.slate900)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).background(M.slate800))

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
                .clip(CircleShape)
                .background(M.red500.copy(alpha = 0.9f))
                .clickable(onClick = onClose)
                .padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, "Fechar", tint = M.white, modifier = Modifier.size(20.dp))
        }

        // Card falso da Uber
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(M.white)
                .padding(24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text("UberX", color = M.black, fontWeight = FontWeight.Black, fontSize = 30.sp)
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(M.blue100)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Dinâmico 1.4x", color = M.blue800, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Text("R$ 38,50", color = M.black, fontWeight = FontWeight.Black, fontSize = 30.sp)
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.clip(CircleShape).background(M.black).padding(8.dp)) {
                        Icon(Icons.Filled.Place, null, tint = M.white, modifier = Modifier.size(16.dp))
                    }
                    Text("3 min • 1.2 km de busca", color = M.black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.clip(CircleShape).background(M.slate200).padding(8.dp)) {
                        Icon(Icons.Filled.Route, null, tint = M.black, modifier = Modifier.size(16.dp))
                    }
                    Text("22 min • 12.3 km de viagem", color = M.black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Overlay Nexo simulado (usa as configurações reais)
        val topPad = if (layout == OverlayLayoutStyle.HORIZONTAL) 64.dp else 56.dp
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = topPad),
        ) {
            val cardModifier = when (layout) {
                OverlayLayoutStyle.VERTICAL -> Modifier.align(Alignment.TopEnd).padding(end = 16.dp).width(176.dp)
                OverlayLayoutStyle.HORIZONTAL -> Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp).fillMaxWidth()
                OverlayLayoutStyle.TOPO -> Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp).fillMaxWidth()
            }
            Column(
                cardModifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(M.slate950.copy(alpha = 0.95f))
                    .border(1.dp, M.emerald500.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(M.emerald500.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(M.emerald400))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (layout == OverlayLayoutStyle.VERTICAL) "NEXO" else "CORRIDA EXCELENTE",
                            color = M.emerald400, fontWeight = FontWeight.Black, fontSize = (12 * fontScale).sp,
                        )
                    }
                    Text("R$ 38,50", color = M.white, fontWeight = FontWeight.Black, fontSize = (13 * fontScale).sp)
                }

                if (layout != OverlayLayoutStyle.VERTICAL) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        fields.forEachIndexed { idx, field ->
                            Column(
                                Modifier.weight(1f).padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    field.label.uppercase(PT_BR),
                                    color = M.slate400, fontSize = (9 * fontScale).sp,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    sampleMetricValues[field] ?: "—",
                                    color = M.emerald400, fontWeight = FontWeight.Black,
                                    fontSize = (16 * fontScale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (idx < fields.size - 1) {
                                Box(Modifier.width(1.dp).height(28.dp).background(M.slate800))
                            }
                        }
                    }
                } else {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fields.forEach { field ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(M.slate900.copy(alpha = 0.9f))
                                    .border(1.dp, M.slate800, RoundedCornerShape(8.dp)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    field.label.uppercase(PT_BR),
                                    color = M.slate400, fontSize = (10 * fontScale).sp, fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    sampleMetricValues[field] ?: "—",
                                    color = M.emerald400, fontWeight = FontWeight.Black, fontSize = (14 * fontScale).sp,
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().background(M.slate900.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Person, null, tint = M.yellow500,
                            modifier = Modifier.size((12 * fontScale).dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("4.95", color = M.yellow500, fontWeight = FontWeight.Bold, fontSize = (11 * fontScale).sp)
                    }
                    Text("25 min • 13.5 km", color = M.slate300, fontSize = (11 * fontScale).sp)
                }
            }
        }
    }
}
