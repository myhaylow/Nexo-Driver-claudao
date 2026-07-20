package br.com.nexo.driver.ui.filters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.withSystemPolicy
import br.com.nexo.driver.evaluation.Metric

/** Explicit rule picker so drivers choose the next limit rather than receiving a hidden sequence. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPickerSheet(
    existingRules: List<FilterRule>,
    onSelect: (FilterRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val existingIds = existingRules.map { it.id }.toSet()
    val options = Metric.entries.filter { it.isUserConfigurable }
        .filterNot { it == Metric.IS_TOWARD_DESTINATION }
        .flatMap { metric -> metric.availableComparators().map { comparator -> metric to comparator } }
        .filterNot { (metric, comparator) -> FilterRuleId(metric, comparator) in existingIds }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Adicionar filtro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Escolha a condição que deve entrar no perfil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            LazyColumn {
                items(options, key = { (metric, comparator) -> "${metric.name}:${comparator.name}" }) { (metric, comparator) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(metric.defaultRule(comparator)) }
                            .padding(vertical = 14.dp),
                    ) {
                        Text(metric.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(comparator.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Which comparators the metric can even express. This follows from the unit: a flag is yes/no, a
 * measured value takes a bound.
 *
 * The previous version listed the flag metrics by hand and sent everything else to AT_LEAST /
 * AT_MOST, so any flag missing from that list would have been offered a numeric bound it cannot
 * satisfy -- FilterRule requires a non-null target for AT_LEAST/AT_MOST and would have thrown.
 * IS_TOWARD_DESTINATION was in exactly that state and only escaped because the picker filters it
 * out one line above.
 */
private fun Metric.availableComparators(): List<Comparator> = if (unit.isNumeric) {
    listOf(Comparator.AT_LEAST, Comparator.AT_MOST)
} else {
    listOf(Comparator.IS_TRUE, Comparator.IS_FALSE)
}

private fun Metric.defaultRule(comparator: Comparator): FilterRule = FilterRule(
    metric = this,
    comparator = comparator,
    target = defaultTarget(),
).withSystemPolicy()

private fun Metric.defaultTarget(): Long? = when (this) {
    Metric.PAYOUT -> 800L
    Metric.RATE_PER_KM -> 175L
    Metric.RATE_PER_HOUR -> 4_000L
    Metric.RATE_PER_MINUTE -> 70L
    Metric.NET_PROFIT -> 800L
    Metric.NET_PROFIT_PERCENT -> 60_00L
    Metric.NET_PROFIT_PER_HOUR -> 3_000L
    Metric.PICKUP_DISTANCE, Metric.TRIP_DISTANCE, Metric.TOTAL_DISTANCE -> 2_000L
    Metric.PICKUP_DURATION, Metric.TRIP_DURATION, Metric.TOTAL_DURATION -> 300L
    Metric.PASSENGER_RATING -> 480L
    else -> null
}

private val Comparator.displayName: String
    get() = when (this) {
        Comparator.AT_LEAST -> "No mínimo"
        Comparator.AT_MOST -> "No máximo"
        Comparator.IS_TRUE -> "Deve possuir"
        Comparator.IS_FALSE -> "Não deve possuir"
    }
