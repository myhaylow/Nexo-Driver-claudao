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
                        Text(metric.pickerLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(comparator.pickerLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun Metric.availableComparators(): List<Comparator> = when (this) {
    Metric.HAS_MULTIPLE_STOPS,
    Metric.IS_LONG_TRIP,
    Metric.ENDS_NEAR_HOME -> listOf(Comparator.IS_TRUE, Comparator.IS_FALSE)
    else -> listOf(Comparator.AT_LEAST, Comparator.AT_MOST)
}

private fun Metric.defaultRule(comparator: Comparator): FilterRule = FilterRule(
    metric = this,
    comparator = comparator,
    target = defaultTarget(),
)

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

private fun Metric.pickerLabel(): String = when (this) {
    Metric.PAYOUT -> "Pagamento total"
    Metric.RATE_PER_KM -> "Valor por km"
    Metric.RATE_PER_HOUR -> "Valor por hora"
    Metric.RATE_PER_MINUTE -> "Valor por minuto"
    Metric.NET_PROFIT -> "Lucro líquido"
    Metric.NET_PROFIT_PERCENT -> "Lucro percentual"
    Metric.NET_PROFIT_PER_HOUR -> "Lucro por hora"
    Metric.PICKUP_DISTANCE -> "Distância de retirada"
    Metric.PICKUP_DURATION -> "Tempo de retirada"
    Metric.TRIP_DISTANCE -> "Distância da viagem"
    Metric.TRIP_DURATION -> "Tempo de viagem"
    Metric.TOTAL_DISTANCE -> "Distância total"
    Metric.TOTAL_DURATION -> "Tempo total"
    Metric.PASSENGER_RATING -> "Nota do passageiro"
    Metric.HAS_MULTIPLE_STOPS -> "Múltiplas paradas"
    Metric.IS_LONG_TRIP -> "Viagem longa"
    Metric.IS_TOWARD_DESTINATION -> "Aproxima da casa"
    Metric.ENDS_NEAR_HOME -> "Destino próximo de casa"
    Metric.PICKUP_IS_BLOCKED -> "Local bloqueado"
}

private fun Comparator.pickerLabel(): String = when (this) {
    Comparator.AT_LEAST -> "No mínimo"
    Comparator.AT_MOST -> "No máximo"
    Comparator.IS_TRUE -> "Deve possuir"
    Comparator.IS_FALSE -> "Não deve possuir"
}
