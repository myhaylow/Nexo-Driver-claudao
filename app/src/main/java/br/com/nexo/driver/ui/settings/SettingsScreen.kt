package br.com.nexo.driver.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import br.com.nexo.driver.overlay.preferences.OverlaySlot
import br.com.nexo.driver.overlay.OverlayPosition
import br.com.nexo.driver.analysis.DEFAULT_CARD_DURATION_MS
import br.com.nexo.driver.ui.theme.ColorVisionScheme
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.ui.theme.DriverVisualStyle

/**
 * Appearance controls for the driver's app. The host owns persistence and applies the selected
 * theme/font scale at app level through the callbacks, avoiding a split visual state.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onThemeModeChanged: (DriverThemeMode) -> Unit,
    onVisualStyleChanged: (DriverVisualStyle) -> Unit = {},
    onFontScaleChanged: (AppFontScale) -> Unit,
    onOverlayPreferencesChanged: (OverlayPreferences) -> Unit = {},
    onOverlayPositionChanged: (OverlayPosition) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onSpeakDecisionChanged: (Boolean) -> Unit = {},
    onTestGalleryImage: () -> Unit = {},
    onBlockSupermarketsChanged: (Boolean) -> Unit = {},
    onFuelPriceChanged: (Long) -> Unit = {},
    onFuelConsumptionChanged: (Double) -> Unit = {},
    onColorVisionSchemeChanged: (ColorVisionScheme) -> Unit = {},
    onCardDurationChanged: (Long) -> Unit = {},
    onDecisionThresholdsChanged: (accept: Int, analyze: Int) -> Unit = { _, _ -> },
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Ajustes", fontWeight = FontWeight.SemiBold) })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Aparência",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Escolha como o Driver Inteligente aparece no seu celular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PreferenceCard(title = "Tema", description = "Define claro, escuro ou acompanha o Android.") {
                ChoiceGroup(
                    options = DriverThemeMode.entries,
                    selected = state.themeMode,
                    label = DriverThemeMode::displayName,
                    onSelected = onThemeModeChanged,
                )
            }

            PreferenceCard(title = "Estilo visual", description = "Escolha entre o visual atual e três novas variantes do app e overlay.") {
                ChoiceGroup(
                    options = DriverVisualStyle.entries,
                    selected = state.visualStyle,
                    label = DriverVisualStyle::displayName,
                    onSelected = onVisualStyleChanged,
                )
            }

            PreferenceCard(title = "Tamanho da fonte", description = "Aumente a leitura sem alterar os filtros.") {
                ChoiceGroup(
                    options = AppFontScale.entries,
                    selected = state.fontScale,
                    label = AppFontScale::label,
                    onSelected = onFontScaleChanged,
                )
            }

            PreferenceCard(
                title = "Visão de cores",
                description = "Ajusta as cores de aceitar e recusar para daltonismo. O semáforo e as " +
                    "setas continuam indicando a decisão pela posição e pelo símbolo.",
            ) {
                ChoiceGroup(
                    options = ColorVisionScheme.entries,
                    selected = state.colorVisionScheme,
                    label = ColorVisionScheme::displayName,
                    onSelected = onColorVisionSchemeChanged,
                )
            }

            PreferenceCard(
                title = "Duração do card",
                description = "Por quanto tempo a análise fica na tela antes de sumir sozinha.",
            ) {
                ChoiceGroup(
                    options = CARD_DURATION_OPTIONS,
                    selected = CARD_DURATION_OPTIONS.minByOrNull {
                        kotlin.math.abs(it - state.cardDurationMs)
                    } ?: DEFAULT_CARD_DURATION_MS,
                    label = { millis -> "${millis / 1000}s" },
                    onSelected = onCardDurationChanged,
                )
            }

            PreferenceCard(
                title = "Rigor da decisão",
                description = "Define o quanto uma corrida precisa pontuar para aparecer como aceitar " +
                    "ou como análise. Mais exigente recusa mais; mais tolerante aceita mais.",
            ) {
                ChoiceGroup(
                    options = DecisionStrictness.entries,
                    selected = DecisionStrictness.forThresholds(state.acceptThreshold, state.analyzeThreshold),
                    label = DecisionStrictness::label,
                    onSelected = { strictness ->
                        onDecisionThresholdsChanged(strictness.acceptThreshold, strictness.analyzeThreshold)
                    },
                )
            }

            OverlayPreferencesCard(
                position = state.overlayPosition,
                preferences = state.overlayPreferences,
                onPositionChanged = onOverlayPositionChanged,
                onPreferencesChanged = onOverlayPreferencesChanged,
            )
            Text(
                text = "Leitura e voz",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A acessibilidade lê os cards quando o app da corrida expõe texto. O OCR por tela continua como fallback.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PreferenceCard(
                title = "Serviço de acessibilidade",
                description = if (state.accessibilityServiceEnabled) {
                    "Ativo: leitura principal por acessibilidade habilitada."
                } else {
                    "Inativo: toque para abrir as configurações do Android e ativar manualmente."
                },
            ) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenAccessibilitySettings) {
                    Text(if (state.accessibilityServiceEnabled) "Abrir acessibilidade" else "Ativar acessibilidade")
                }
            }
            if (state.showDebugTools) {
                PreferenceCard(
                    title = "Testar imagem da galeria",
                    description = "Escolha uma captura da Uber ou 99. A imagem passa pelo mesmo OCR, filtros e overlay, sem ser salva pelo app.",
                ) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onTestGalleryImage) {
                        Text("Selecionar captura")
                    }
                    state.galleryTestStatus?.let { status ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PreferenceCard(
                title = "Falar decisão da corrida",
                description = "Fala uma vez por oferta nova: aceitar, analisar ou recusar, junto com o valor.",
            ) {
                ToggleRow(
                    label = if (state.speakDecision) "Fala ligada" else "Fala desligada",
                    checked = state.speakDecision,
                    onCheckedChange = onSpeakDecisionChanged,
                )
            }

            Text(
                text = "Custos e bloqueios",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Lucro pode participar dos filtros quando você ativar regras. O app nunca toca, aceita ou recusa sozinho.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PreferenceCard(
                title = "Combustível",
                description = "Usado para estimar o lucro líquido da corrida (valor − combustível).",
            ) {
                StepperRow(
                    label = "Preço do litro",
                    value = "R$ %.2f".format(state.fuelPricePerLiterCents / 100.0),
                    onDecrement = { onFuelPriceChanged((state.fuelPricePerLiterCents - 10).coerceAtLeast(0)) },
                    onIncrement = { onFuelPriceChanged(state.fuelPricePerLiterCents + 10) },
                )
                Spacer(Modifier.height(8.dp))
                StepperRow(
                    label = "Consumo",
                    value = "%.1f km/L".format(state.fuelKilometersPerLiter),
                    onDecrement = { onFuelConsumptionChanged((state.fuelKilometersPerLiter - 0.5).coerceAtLeast(1.0)) },
                    onIncrement = { onFuelConsumptionChanged(state.fuelKilometersPerLiter + 0.5) },
                )
            }
            PreferenceCard(
                title = "Bloquear supermercados",
                description = "Quando ligado, corridas que começam em supermercado viram recusa (card vermelho) e a voz fala \"supermercado\".",
            ) {
                ToggleRow(
                    label = if (state.blockSupermarkets) "Bloqueio ligado" else "Bloqueio desligado",
                    checked = state.blockSupermarkets,
                    onCheckedChange = onBlockSupermarketsChanged,
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDecrement) { Text("−") }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            OutlinedButton(onClick = onIncrement) { Text("+") }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OverlayPreferencesCard(
    position: OverlayPosition,
    preferences: OverlayPreferences,
    onPositionChanged: (OverlayPosition) -> Unit,
    onPreferencesChanged: (OverlayPreferences) -> Unit,
) {
    PreferenceCard(
        title = "Overlay",
        description = "Posição e quatro métricas do card exibido sobre a corrida.",
    ) {
        Text("Posição", style = MaterialTheme.typography.labelLarge)
        ChoiceGroup(
            options = OverlayPosition.entries,
            selected = position,
            label = OverlayPosition::label,
            onSelected = onPositionChanged,
        )
        Spacer(Modifier.height(8.dp))
        Text("Campos do card", style = MaterialTheme.typography.labelLarge)
        OverlaySlot.entries.forEach { slot ->
            val selected = preferences[slot]
            val availableFields = preferences.availableFieldsFor(slot)
            Text(
                text = slot.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            ChoiceGroup(
                options = availableFields,
                selected = selected,
                label = OverlayMetricField::label,
                onSelected = { field ->
                    onPreferencesChanged(preferences.withField(slot, field))
                },
            )
        }
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun FontPreview() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Prévia",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "R$ 2,18/km",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Valor por quilômetro da corrida",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    DriverInteligenteTheme(mode = DriverThemeMode.LIGHT) {
        SettingsScreen(
            state = SettingsScreenState(),
            onThemeModeChanged = {},
            onVisualStyleChanged = {},
            onFontScaleChanged = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    DriverInteligenteTheme(mode = DriverThemeMode.DARK) {
        SettingsScreen(
            state = SettingsScreenState(
                themeMode = DriverThemeMode.DARK,
                fontScale = AppFontScale.LARGE,
            ),
            onThemeModeChanged = {},
            onVisualStyleChanged = {},
            onFontScaleChanged = {},
        )
    }
}
