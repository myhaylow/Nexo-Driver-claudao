package br.com.nexo.driver.ui.filters

import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.evaluation.RuleBlocker
import br.com.nexo.driver.evaluation.RulePrerequisites
import br.com.nexo.driver.evaluation.blocker
import br.com.nexo.driver.evaluation.MetricGroup
import br.com.nexo.driver.evaluation.MetricUnit
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
    /**
     * What the app can currently satisfy. Rules whose prerequisites are missing are shown as
     * inactive with the fix, instead of looking identical to working ones while quietly dragging
     * every offer toward "em análise".
     */
    val prerequisites: RulePrerequisites = RulePrerequisites(
        hasHomeDestination = true,
        hasOfflineAddressPackage = true,
        isBlocklistEnabled = true,
    ),
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
    /** Set when the rule is on but cannot evaluate; drives the inline warning on its row. */
    val blocker: RuleBlocker? = null,
) {
    val isBoolean: Boolean get() = valueText == null
}

fun FiltersScreenState.groupedRules(): Map<FilterSection, List<FilterRulePresentation>> =
    rules
        .map { rule -> FilterRulePresentation(rule, prerequisites) }
        .groupBy(FilterRulePresentation::section)
        .mapValues { (_, items) ->
            items.sortedWith(
                compareBy<FilterRulePresentation> { it.rule.metric.displayOrder }
                    .thenBy { it.rule.comparator.displayOrder },
            )
        }

/** Count of enabled-but-inoperative rules, for the summary at the top of the screen. */
fun FiltersScreenState.blockedRuleCount(): Int =
    rules.count { rule -> rule.blocker(prerequisites) != null }

fun FilterRulePresentation(
    rule: FilterRule,
    prerequisites: RulePrerequisites = RulePrerequisites(true, true, true),
): FilterRulePresentation = FilterRulePresentation(
    rule = rule,
    section = rule.metric.section,
    title = rule.metric.displayName,
    comparisonText = rule.naturalLanguageComparison(),
    valueText = rule.formattedTarget(),
    blocker = rule.blocker(prerequisites),
)

/** Section labels are UI copy; the grouping itself belongs to the metric. */
private val Metric.section: FilterSection
    get() = when (group) {
        MetricGroup.EARNINGS -> FilterSection.EARNINGS
        MetricGroup.PICKUP -> FilterSection.PICKUP
        MetricGroup.TRIP -> FilterSection.TRIP
        MetricGroup.PREFERENCES -> FilterSection.PREFERENCES
    }

private val Metric.displayOrder: Int get() = order

private val Comparator.displayOrder: Int
    get() = when (this) {
        Comparator.AT_LEAST, Comparator.IS_TRUE -> 0
        Comparator.AT_MOST, Comparator.IS_FALSE -> 1
    }

private fun FilterRule.naturalLanguageComparison(): String = when (comparator) {
    Comparator.AT_LEAST -> "é no mínimo"
    Comparator.AT_MOST -> "é no máximo"
    Comparator.IS_TRUE -> "é uma oferta com"
    Comparator.IS_FALSE -> "não possui"
}

private fun FilterRule.formattedTarget(): String? = target?.let { raw ->
    when (metric.unit) {
        MetricUnit.MONEY_CENTS -> raw.asBrl() + metric.rateSuffix()
        MetricUnit.PERCENT_SCALED -> "${raw / MetricUnit.PERCENT_SCALED.scale}%"
        MetricUnit.DISTANCE_METERS -> raw.asKilometres()
        MetricUnit.DURATION_SECONDS -> raw.asMinutes()
        MetricUnit.RATING_SCALED -> "%.2f ★".format(BRAZILIAN_PORTUGUESE, raw / MetricUnit.RATING_SCALED.scale.toDouble())
        MetricUnit.FLAG -> null
    }
}

/**
 * Money metrics that express a rate carry their denominator in the label, so the target reads
 * "R$ 1,75/km" rather than a bare amount. A plain payout has no denominator.
 */
private fun Metric.rateSuffix(): String = when (this) {
    Metric.RATE_PER_KM -> "/km"
    Metric.RATE_PER_HOUR, Metric.NET_PROFIT_PER_HOUR -> "/h"
    Metric.RATE_PER_MINUTE -> "/min"
    else -> ""
}

private fun Long.asBrl(): String = NumberFormat.getCurrencyInstance(BRAZILIAN_PORTUGUESE).format(this / 100.0)

private fun Long.asKilometres(): String {
    val kilometres = this / 1_000.0
    val digits = if (kilometres % 1.0 == 0.0) 0 else 1
    return "%1$.${digits}f km".format(BRAZILIAN_PORTUGUESE, kilometres)
}

private fun Long.asMinutes(): String = "${(this + 59) / 60} min"
