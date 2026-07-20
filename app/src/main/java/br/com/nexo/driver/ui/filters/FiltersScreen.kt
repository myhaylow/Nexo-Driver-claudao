package br.com.nexo.driver.ui.filters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.EvaluationMode
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.R

/**
 * Read-only presentation of a profile's rules. The hosting screen owns persistence and opens
 * the rule editor through [onRuleClick] and [onAddFilter].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    state: FiltersScreenState,
    onNavigateBack: () -> Unit,
    onProfileEnabledChange: (Boolean) -> Unit,
    onRuleEnabledChange: (FilterRuleId, Boolean) -> Unit,
    onRuleClick: (FilterRuleId) -> Unit,
    onAddFilter: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val groupedRules = state.groupedRules()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Filtros") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Voltar")
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProfileSwitchCard(
                    profileName = state.profileName,
                    enabled = state.isProfileEnabled,
                    onEnabledChange = onProfileEnabledChange,
                )
            }

            FilterSection.entries.forEach { section ->
                val sectionRules = groupedRules[section].orEmpty()
                if (sectionRules.isNotEmpty()) {
                    item(key = "section-${section.name}") {
                        FilterSectionCard(
                            section = section,
                            rules = sectionRules,
                            onRuleEnabledChange = onRuleEnabledChange,
                            onRuleClick = onRuleClick,
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onAddFilter,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("+ Adicionar filtro")
                }
            }
        }
    }
}

@Composable
private fun FilterSectionCard(
    section: FilterSection,
    rules: List<FilterRulePresentation>,
    onRuleEnabledChange: (FilterRuleId, Boolean) -> Unit,
    onRuleClick: (FilterRuleId) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(
                text = section.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            rules.forEachIndexed { index, item ->
                FilterRuleRow(
                    item = item,
                    onEnabledChange = { onRuleEnabledChange(item.rule.id, it) },
                    onClick = { onRuleClick(item.rule.id) },
                )
                if (index != rules.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileSwitchCard(
    profileName: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Aplicar filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Perfil ativo: $profileName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun FilterRuleRow(
    item: FilterRulePresentation,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item.rule.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.comparisonText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                item.valueText?.let { target ->
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = onClick,
                        label = { Text(target) },
                        border = null,
                    )
                }
            }
            if (item.rule.mode == EvaluationMode.ELIMINATORY) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Eliminatória",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = item.rule.enabled, onCheckedChange = onEnabledChange)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun FiltersScreenPreview() {
    MaterialTheme {
        FiltersScreen(
            state = FiltersScreenState(
                profileName = "Curitiba — padrão",
                isProfileEnabled = true,
                rules = sampleRules(),
            ),
            onNavigateBack = {},
            onProfileEnabledChange = {},
            onRuleEnabledChange = { _, _ -> },
            onRuleClick = {},
            onAddFilter = {},
        )
    }
}

private fun sampleRules(): List<FilterRule> = listOf(
    FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 800),
    FilterRule(Metric.RATE_PER_HOUR, Comparator.AT_LEAST, target = 4_000),
    FilterRule(Metric.RATE_PER_KM, Comparator.AT_LEAST, target = 175),
    FilterRule(Metric.PICKUP_DURATION, Comparator.AT_MOST, target = 360),
    FilterRule(Metric.PICKUP_DISTANCE, Comparator.AT_MOST, target = 2_500),
    FilterRule(Metric.TRIP_DURATION, Comparator.AT_MOST, target = 1_800),
    FilterRule(Metric.TRIP_DURATION, Comparator.AT_LEAST, target = 300),
    FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_MOST, target = 12_000),
    FilterRule(Metric.TOTAL_DISTANCE, Comparator.AT_LEAST, target = 2_000),
    FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 480),
    FilterRule(Metric.HAS_MULTIPLE_STOPS, Comparator.IS_FALSE),
    FilterRule(Metric.ENDS_NEAR_HOME, Comparator.IS_TRUE, enabled = false),
)
