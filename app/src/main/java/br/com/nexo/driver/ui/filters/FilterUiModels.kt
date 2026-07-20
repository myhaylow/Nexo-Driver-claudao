package br.com.nexo.driver.ui.filters

import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import java.text.NumberFormat
import java.util.Locale

private val BRAZILIAN_PORTUGUESE = Locale.forLanguageTag("pt-BR")

/**
 * Presentation-only grouping for the configurable offer rules.  The evaluator remains the
 * source of truth; this model deliberately keeps the UI independent from persistence.
 */
enum class FilterSection(val label: String) {
    EARNINGS("Ganhos"),
    PICKUP("Retirada"),
    TRIP("Viagem"),
    PREFERENCES("Preferências"),
}

data class FiltersScreenState(
    val profileName: String,
    val isProfileEnabled: Boolean,
    val rules: List<FilterRule>,
)

/** Stable identity for a rule inside a profile. A numeric metric may have both bounds. */
data class FilterRuleId(
    val metric: Metric,
    val comparator: Comparator,
) {
    val stableKey: String get() = "${metric.name}:${comparator.name}"
}

val FilterRule.id: FilterRuleId
    get() = FilterRuleId(metric = metric, comparator = comparator)

data class FilterRulePresentation(
    val rule: FilterRule,
    val section: FilterSection,
    val title: String,
    val comparisonText: String,
    val valueText: String?,
) {
    val isBoolean: Boolean get() = valueText == null
}

fun FiltersScreenState.groupedRules(): Map<FilterSection, List<FilterRulePresentation>> =
    rules
        .map(::FilterRulePresentation)
        .groupBy(FilterRulePresentation::section)
        .mapValues { (_, items) ->
            items.sortedWith(
                compareBy<FilterRulePresentation> { it.rule.metric.displayOrder }
                    .thenBy { it.rule.comparator.displayOrder },
            )
        }

fun FilterRulePresentation(rule: FilterRule): FilterRulePresentation {
    val title = rule.metric.displayName
    return FilterRulePresentation(
        rule = rule,
        section = rule.metric.section,
        title = title,
        comparisonText = rule.naturalLanguageComparison(),
        valueText = rule.formattedTarget(),
    )
}

private val Metric.section: FilterSection
    get() = when (this) {
        Metric.PAYOUT, Metric.RATE_PER_KM, Metric.RATE_PER_HOUR, Metric.RATE_PER_MINUTE,
        Metric.NET_PROFIT, Metric.NET_PROFIT_PERCENT, Metric.NET_PROFIT_PER_HOUR -> FilterSection.EARNINGS
        Metric.PICKUP_DISTANCE, Metric.PICKUP_DURATION -> FilterSection.PICKUP
        Metric.TRIP_DISTANCE, Metric.TRIP_DURATION, Metric.TOTAL_DISTANCE, Metric.TOTAL_DURATION -> FilterSection.TRIP
        Metric.PASSENGER_RATING, Metric.HAS_MULTIPLE_STOPS, Metric.IS_LONG_TRIP,
        Metric.IS_TOWARD_DESTINATION, Metric.ENDS_NEAR_HOME, Metric.PICKUP_IS_BLOCKED ->
            FilterSection.PREFERENCES
    }

private val Metric.displayOrder: Int
    get() = when (this) {
        Metric.PAYOUT -> 0
        Metric.RATE_PER_HOUR -> 1
        Metric.RATE_PER_MINUTE -> 2
        Metric.RATE_PER_KM -> 3
        Metric.NET_PROFIT -> 4
        Metric.NET_PROFIT_PERCENT -> 5
        Metric.NET_PROFIT_PER_HOUR -> 6
        Metric.PICKUP_DURATION -> 0
        Metric.PICKUP_DISTANCE -> 1
        Metric.TRIP_DURATION -> 0
        Metric.TOTAL_DURATION -> 1
        Metric.TRIP_DISTANCE -> 2
        Metric.TOTAL_DISTANCE -> 3
        Metric.PASSENGER_RATING -> 0
        Metric.HAS_MULTIPLE_STOPS -> 1
        Metric.IS_LONG_TRIP -> 2
        Metric.IS_TOWARD_DESTINATION, Metric.ENDS_NEAR_HOME -> 3
        Metric.PICKUP_IS_BLOCKED -> 4
    }

private val Comparator.displayOrder: Int
    get() = when (this) {
        Comparator.AT_LEAST, Comparator.IS_TRUE -> 0
        Comparator.AT_MOST, Comparator.IS_FALSE -> 1
    }

private val Metric.displayName: String
    get() = when (this) {
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

private fun FilterRule.naturalLanguageComparison(): String = when (comparator) {
    Comparator.AT_LEAST -> "é no mínimo"
    Comparator.AT_MOST -> "é no máximo"
    Comparator.IS_TRUE -> "é uma oferta com"
    Comparator.IS_FALSE -> "não possui"
}

private fun FilterRule.formattedTarget(): String? = target?.let { raw ->
    when (metric) {
        Metric.PAYOUT -> raw.asBrl()
        Metric.RATE_PER_KM -> "${raw.asBrl()}/km"
        Metric.RATE_PER_HOUR -> "${raw.asBrl()}/h"
        Metric.RATE_PER_MINUTE -> "${raw.asBrl()}/min"
        Metric.NET_PROFIT -> raw.asBrl()
        Metric.NET_PROFIT_PERCENT -> "${raw / 100}%"
        Metric.NET_PROFIT_PER_HOUR -> "${raw.asBrl()}/h"
        Metric.PICKUP_DISTANCE, Metric.TRIP_DISTANCE, Metric.TOTAL_DISTANCE -> raw.asKilometres()
        Metric.PICKUP_DURATION, Metric.TRIP_DURATION, Metric.TOTAL_DURATION -> raw.asMinutes()
        Metric.PASSENGER_RATING -> "%.2f ★".format(BRAZILIAN_PORTUGUESE, raw / 100.0)
        Metric.HAS_MULTIPLE_STOPS, Metric.IS_LONG_TRIP, Metric.IS_TOWARD_DESTINATION,
        Metric.ENDS_NEAR_HOME, Metric.PICKUP_IS_BLOCKED -> null
    }
}

private fun Long.asBrl(): String = NumberFormat.getCurrencyInstance(BRAZILIAN_PORTUGUESE).format(this / 100.0)

private fun Long.asKilometres(): String {
    val kilometres = this / 1_000.0
    val digits = if (kilometres % 1.0 == 0.0) 0 else 1
    return "%1$.${digits}f km".format(BRAZILIAN_PORTUGUESE, kilometres)
}

private fun Long.asMinutes(): String = "${(this + 59) / 60} min"
