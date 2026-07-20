package br.com.nexo.driver.ui.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import br.com.nexo.driver.analysis.OfferSessionMetricsRepository
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.evaluation.MetricUnit
import br.com.nexo.driver.evaluation.withSystemPolicy
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Editor independente de uma regra. O valor que a pessoa vê usa a unidade natural da métrica
 * (por exemplo, km e minutos); [onSave] recebe o valor normalizado que o avaliador utiliza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRuleEditorSheet(
    rule: FilterRule,
    onSave: (FilterRule) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numericRule = rule.metric.isNumeric
    var comparator by rememberSaveable(rule.metric, rule.comparator) {
        mutableStateOf(rule.comparator)
    }
    var targetText by rememberSaveable(rule.metric, rule.target) {
        mutableStateOf(rule.target?.toDisplayInput(rule.metric).orEmpty())
    }

    val parsedTarget = remember(targetText, rule.metric) {
        targetText.toNormalizedTarget(rule.metric)
    }
    val targetIsValid = !numericRule || parsedTarget != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(rule.metric.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (numericRule) "Defina como esta oferta deve ser avaliada." else "Defina quando esta condição deve valer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (numericRule) {
                NumericComparatorSelector(
                    comparator = comparator,
                    onComparatorChange = { comparator = it },
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(rule.metric.targetLabel) },
                    suffix = { Text(rule.metric.inputUnit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(
                            if (targetIsValid) rule.metric.targetHint
                            else "Informe um valor válido maior ou igual a zero.",
                        )
                    },
                    isError = !targetIsValid,
                )

                // Immediate answer to "what does this target actually filter out?" against the
                // offers already seen this session -- so calibrating is not blind. Session-only and
                // numbers-only; nothing about the ride is kept.
                val impact = remember(parsedTarget, comparator, rule.metric) {
                    parsedTarget?.let {
                        OfferSessionMetricsRepository.impactOf(
                            metric = rule.metric,
                            target = it,
                            atLeast = comparator == Comparator.AT_LEAST,
                        )
                    }
                }
                RuleImpactPreview(impact)
            } else {
                BooleanComparatorSelector(
                    metric = rule.metric,
                    comparator = comparator,
                    onComparatorChange = { comparator = it },
                )
            }

            // Weight, mode and tolerance are no longer shown: the system sets them (see
            // withSystemPolicy). The driver decides only the bound and the value.

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            FilterRule(
                                metric = rule.metric,
                                comparator = comparator,
                                target = if (numericRule) parsedTarget else null,
                                enabled = rule.enabled,
                                // Weight, mode and tolerance are the system's to set.
                            ).withSystemPolicy(),
                        )
                    },
                    enabled = targetIsValid,
                ) {
                    Text("Salvar regra")
                }
            }
        }
    }
}

/**
 * "Nas ofertas desta sessão, X de N passariam" for the target being typed.
 *
 * A driver setting "R$/km ≥ 1,75" otherwise has no way to know if that rejects a tenth of rides or
 * most of them until they drive. When no offers have been seen yet, it says so rather than showing
 * a hollow "0 de 0" that would read as a failure.
 */
@Composable
private fun RuleImpactPreview(impact: br.com.nexo.driver.analysis.ImpactSample?) {
    val text = when {
        impact == null -> "Sem ofertas nesta sessão para prever o impacto ainda."
        else -> "Nesta sessão, ${impact.passing} de ${impact.total} ofertas passariam (${impact.percent}%)."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NumericComparatorSelector(
    comparator: Comparator,
    onComparatorChange: (Comparator) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Comparação", style = MaterialTheme.typography.labelLarge)
        ChoiceRow {
            ChoiceButton(
                selected = comparator == Comparator.AT_LEAST,
                label = "Ao menos",
                onClick = { onComparatorChange(Comparator.AT_LEAST) },
            )
            ChoiceButton(
                selected = comparator == Comparator.AT_MOST,
                label = "No máximo",
                onClick = { onComparatorChange(Comparator.AT_MOST) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BooleanComparatorSelector(
    metric: Metric,
    comparator: Comparator,
    onComparatorChange: (Comparator) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Condição", style = MaterialTheme.typography.labelLarge)
        ChoiceRow {
            ChoiceButton(
                selected = comparator == Comparator.IS_TRUE,
                label = metric.booleanTrueLabel,
                onClick = { onComparatorChange(Comparator.IS_TRUE) },
            )
            ChoiceButton(
                selected = comparator == Comparator.IS_FALSE,
                label = metric.booleanFalseLabel,
                onClick = { onComparatorChange(Comparator.IS_FALSE) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.ChoiceButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val buttonModifier = Modifier.weight(1f)
    if (selected) {
        Button(onClick = onClick, modifier = buttonModifier) { Text(label, maxLines = 1) }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier) { Text(label, maxLines = 1) }
    }
}

private val Metric.isNumeric: Boolean get() = unit.isNumeric

// Flag metrics read as a sentence about the offer, so each phrases its own yes/no rather than
// falling back to a bare "Sim"/"Não". This is genuine per-metric copy, not unit knowledge.
private val Metric.booleanTrueLabel: String
    get() = when (this) {
        Metric.HAS_MULTIPLE_STOPS -> "Possui"
        Metric.IS_LONG_TRIP -> "É longa"
        Metric.IS_TOWARD_DESTINATION -> "Aproxima"
        Metric.ENDS_NEAR_HOME -> "Termina perto"
        else -> "Sim"
    }

private val Metric.booleanFalseLabel: String
    get() = when (this) {
        Metric.HAS_MULTIPLE_STOPS -> "Não possui"
        Metric.IS_LONG_TRIP -> "Não é longa"
        Metric.IS_TOWARD_DESTINATION -> "Não aproxima"
        Metric.ENDS_NEAR_HOME -> "Não termina perto"
        else -> "Não"
    }

/** The symbol shown as the field suffix, e.g. "R$", "km", "min". */
private val Metric.inputUnit: String get() = unit.symbol

private val Metric.targetLabel: String
    get() = when (unit) {
        MetricUnit.MONEY_CENTS -> "Valor desejado"
        MetricUnit.PERCENT_SCALED -> "Percentual desejado"
        MetricUnit.RATING_SCALED -> "Nota desejada"
        MetricUnit.DISTANCE_METERS, MetricUnit.DURATION_SECONDS, MetricUnit.FLAG -> "Limite"
    }

private val Metric.targetHint: String
    get() = when (unit) {
        MetricUnit.MONEY_CENTS -> "Use vírgula para os centavos."
        MetricUnit.PERCENT_SCALED -> "Percentual inteiro, por exemplo 60."
        MetricUnit.DISTANCE_METERS -> "Em quilómetros, por exemplo 2,5."
        MetricUnit.DURATION_SECONDS -> "Em minutos, por exemplo 6."
        MetricUnit.RATING_SCALED -> "De 0 a 5, por exemplo 4,80."
        MetricUnit.FLAG -> ""
    }

/** Stored value to the number the driver reads, using the metric's own scale. */
private fun Long.toDisplayInput(metric: Metric): String {
    if (!metric.unit.isNumeric) return ""
    return (toDouble() / metric.unit.scale).formatForInput(metric.unit.inputFractionDigits)
}

/** The inverse of [toDisplayInput]; null when the text is not a usable target for this metric. */
private fun String.toNormalizedTarget(metric: Metric): Long? {
    if (!metric.unit.isNumeric) return null
    val number = trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (number < BigDecimal.ZERO) return null
    return number
        .multiply(metric.unit.scale.toBigDecimal())
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
}

private fun Double.formatForInput(maximumFractionDigits: Int): String =
    "%1$.${maximumFractionDigits}f".format(Locale("pt", "BR"), this)
        .trimEnd('0')
        .trimEnd(',')

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun NumericFilterRuleEditorPreview() {
    DriverInteligenteTheme {
        FilterRuleEditorSheet(
            rule = FilterRule(
                metric = Metric.RATE_PER_KM,
                comparator = Comparator.AT_LEAST,
                target = 175,
            ),
            onSave = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 840)
@Composable
private fun BooleanFilterRuleEditorPreview() {
    DriverInteligenteTheme {
        FilterRuleEditorSheet(
            rule = FilterRule(
                metric = Metric.HAS_MULTIPLE_STOPS,
                comparator = Comparator.IS_FALSE,
            ),
            onSave = {},
            onDismiss = {},
        )
    }
}
