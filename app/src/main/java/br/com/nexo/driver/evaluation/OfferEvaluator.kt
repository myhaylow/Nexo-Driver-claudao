package br.com.nexo.driver.evaluation

import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.DerivedMetrics
import br.com.nexo.driver.offer.Distance
import br.com.nexo.driver.offer.Duration
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.NormalizedOffer

private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.85f

const val DEFAULT_ACCEPT_THRESHOLD = 80
const val DEFAULT_ANALYZE_THRESHOLD = 50

/**
 * Below this share of readable rule weight the engine refuses to commit to ACCEPT or REJECT.
 * It is the guard that keeps a barely-read card from being scored as if it were fully understood.
 */
const val DEFAULT_MINIMUM_COVERAGE_PERCENT = 40

/** How far a R$/km must clear its target to compensate a below-target R$/h. */
const val DEFAULT_STRONG_KM_BONUS = 0.10f

/**
 * How a metric's raw [Long] value relates to what a driver reads and types.
 *
 * Every metric is stored as a scaled integer (cents, metres, seconds, rating x100) so the engine
 * never touches floating point. That scale, the unit symbol, and the input precision are three
 * facets of one fact, and they were previously re-derived independently in four places — the
 * tolerance default, the overlay's reason line, the filter list, and the rule editor's
 * parse/format pair. Keeping them together means a new metric cannot be half-taught its own unit.
 */
enum class MetricUnit(
    val symbol: String,
    /** Multiplier between the displayed number and the stored value. */
    val scale: Long,
    /** Fraction digits offered when the driver types a target. */
    val inputFractionDigits: Int,
    val isNumeric: Boolean = true,
) {
    MONEY_CENTS(symbol = "R$", scale = 100L, inputFractionDigits = 2),
    PERCENT_SCALED(symbol = "%", scale = 100L, inputFractionDigits = 0),
    DISTANCE_METERS(symbol = "km", scale = 1_000L, inputFractionDigits = 1),
    DURATION_SECONDS(symbol = "min", scale = 60L, inputFractionDigits = 1),
    RATING_SCALED(symbol = "★", scale = 100L, inputFractionDigits = 2),

    /** A yes/no signal: it has no target to type and no unit to render. */
    FLAG(symbol = "", scale = 1L, inputFractionDigits = 0, isNumeric = false),
}

/** Grouping used to organise the filter screen. */
enum class MetricGroup { EARNINGS, PICKUP, TRIP, PREFERENCES }

/**
 * Everything the app needs to know about a metric travels with the metric.
 *
 * Adding a metric used to mean editing roughly fifteen exhaustive `when` expressions spread over
 * the evaluator, the overlay presenter and four filter screens — with the compiler only catching
 * the ones that were exhaustive, and silent `else` branches swallowing the rest. Now a new entry
 * declares its own label, unit and placement, and every consumer reads those properties.
 *
 * @param label short form, used where space is tight (overlay reason line, profile summary).
 * @param displayName full form, used as a heading in the filter screens.
 */
enum class Metric(
    val label: String,
    val displayName: String,
    val unit: MetricUnit,
    val group: MetricGroup,
    val order: Int,
    /**
     * False for signals the engine maintains itself. [PICKUP_IS_BLOCKED] is driven by the
     * blocklist toggle in settings, so offering it a second time as a hand-editable filter rule
     * would let the two disagree.
     */
    val isUserConfigurable: Boolean = true,
    /**
     * Near-threshold band in the metric's own stored unit, for metrics where a percentage band is
     * meaningless. 10% under a 4.70 rating target reaches 4.23, which is not "almost passing".
     */
    val defaultToleranceAbsolute: Long? = null,
) {
    PAYOUT("Valor", "Pagamento total", MetricUnit.MONEY_CENTS, MetricGroup.EARNINGS, 0),
    RATE_PER_HOUR("R$/h", "Valor por hora", MetricUnit.MONEY_CENTS, MetricGroup.EARNINGS, 1),
    RATE_PER_MINUTE("R$/min", "Valor por minuto", MetricUnit.MONEY_CENTS, MetricGroup.EARNINGS, 2),
    RATE_PER_KM("R$/km", "Valor por km", MetricUnit.MONEY_CENTS, MetricGroup.EARNINGS, 3),
    NET_PROFIT("Lucro", "Lucro líquido", MetricUnit.MONEY_CENTS, MetricGroup.EARNINGS, 4),
    NET_PROFIT_PERCENT("Lucro %", "Lucro percentual", MetricUnit.PERCENT_SCALED, MetricGroup.EARNINGS, 5),
    NET_PROFIT_PER_HOUR("Lucro/h", "Lucro por hora", MetricUnit.MONEY_CENTS, MetricGroup.EARNINGS, 6),

    PICKUP_DURATION("Tempo retirada", "Tempo de retirada", MetricUnit.DURATION_SECONDS, MetricGroup.PICKUP, 0),
    PICKUP_DISTANCE("Dist. retirada", "Distância de retirada", MetricUnit.DISTANCE_METERS, MetricGroup.PICKUP, 1),

    TRIP_DURATION("Tempo viagem", "Tempo de viagem", MetricUnit.DURATION_SECONDS, MetricGroup.TRIP, 0),
    TOTAL_DURATION("Tempo total", "Tempo total", MetricUnit.DURATION_SECONDS, MetricGroup.TRIP, 1),
    TRIP_DISTANCE("Dist. viagem", "Distância da viagem", MetricUnit.DISTANCE_METERS, MetricGroup.TRIP, 2),
    TOTAL_DISTANCE("Dist. total", "Distância total", MetricUnit.DISTANCE_METERS, MetricGroup.TRIP, 3),

    PASSENGER_RATING(
        "Avaliação",
        "Nota do passageiro",
        MetricUnit.RATING_SCALED,
        MetricGroup.PREFERENCES,
        0,
        // Ratings are stored scaled by 100, so this is +/-0.05 stars.
        defaultToleranceAbsolute = 5L,
    ),
    HAS_MULTIPLE_STOPS("Paradas", "Múltiplas paradas", MetricUnit.FLAG, MetricGroup.PREFERENCES, 1),
    IS_LONG_TRIP("Viagem longa", "Viagem longa", MetricUnit.FLAG, MetricGroup.PREFERENCES, 2),
    IS_TOWARD_DESTINATION("Sentido destino", "Aproxima da casa", MetricUnit.FLAG, MetricGroup.PREFERENCES, 3),
    // Previously shared order 3 with IS_TOWARD_DESTINATION, which left the filter list's sort
    // unstable: the two could swap places between recompositions with nothing to explain it.
    ENDS_NEAR_HOME("Termina perto de casa", "Destino próximo de casa", MetricUnit.FLAG, MetricGroup.PREFERENCES, 4),

    /** Set by the blocklist enricher so a blocked pickup is decided by the engine, not post-hoc. */
    PICKUP_IS_BLOCKED(
        "Local bloqueado",
        "Local bloqueado",
        MetricUnit.FLAG,
        MetricGroup.PREFERENCES,
        5,
        isUserConfigurable = false,
    ),
}

enum class Comparator { AT_LEAST, AT_MOST, IS_TRUE, IS_FALSE }

enum class EvaluationMode { SCORE, ELIMINATORY }

/**
 * The weight and mode the system assigns a metric, so the driver never has to. The editor used to
 * expose a 1-10 weight slider and a score/eliminatory toggle -- engineer concepts a driver should
 * not have to reason about. Now the driver only picks the bound and the value; policy decides the
 * rest. Earnings and rating carry the most weight, trip totals the least, and only a blocked pickup
 * is a hard veto -- everything else scores, letting the weighted total and the km-compensates-hour
 * rule ([[DecisionReason.KM_COMPENSATES_HOUR]]) do the nuanced work.
 */
val Metric.systemWeight: Int
    get() = when (this) {
        Metric.PAYOUT, Metric.RATE_PER_KM, Metric.RATE_PER_HOUR, Metric.RATE_PER_MINUTE,
        Metric.NET_PROFIT, Metric.NET_PROFIT_PERCENT, Metric.NET_PROFIT_PER_HOUR,
        Metric.PASSENGER_RATING -> 3
        Metric.PICKUP_DISTANCE, Metric.PICKUP_DURATION,
        Metric.HAS_MULTIPLE_STOPS, Metric.IS_LONG_TRIP,
        Metric.IS_TOWARD_DESTINATION, Metric.ENDS_NEAR_HOME -> 2
        Metric.TRIP_DISTANCE, Metric.TRIP_DURATION,
        Metric.TOTAL_DISTANCE, Metric.TOTAL_DURATION -> 1
        Metric.PICKUP_IS_BLOCKED -> 1
    }

/** Only a blocked pickup vetoes outright; every other metric contributes to the score. */
val Metric.systemMode: EvaluationMode
    get() = if (this == Metric.PICKUP_IS_BLOCKED) EvaluationMode.ELIMINATORY else EvaluationMode.SCORE

/** Re-stamps a rule with the system's weight, mode and tolerance, discarding any hand-set values. */
fun FilterRule.withSystemPolicy(): FilterRule = copy(
    weight = metric.systemWeight,
    mode = metric.systemMode,
    tolerancePercent = SYSTEM_TOLERANCE_PERCENT,
    toleranceAbsolute = null,
)

const val SYSTEM_TOLERANCE_PERCENT = 10

enum class MetricStatus { PASS, NEAR, FAIL, UNKNOWN }

enum class OfferDecision { ACCEPT, ANALYZE, REJECT }

data class FilterRule(
    val metric: Metric,
    val comparator: Comparator,
    val target: Long? = null,
    val tolerancePercent: Int = 10,
    /**
     * Absolute near-threshold band, in the metric's own unit. It takes precedence over
     * [tolerancePercent] when set, because a multiplicative band is meaningless for bounded
     * metrics: 10% under a 4.70 rating target reaches 4.23, which is not "almost passing".
     */
    val toleranceAbsolute: Long? = null,
    val weight: Int = 1,
    val mode: EvaluationMode = EvaluationMode.SCORE,
    val enabled: Boolean = true,
) {
    init {
        require(tolerancePercent in 0..100)
        require(toleranceAbsolute == null || toleranceAbsolute >= 0)
        require(weight > 0)
        if (comparator == Comparator.AT_LEAST || comparator == Comparator.AT_MOST) {
            require(target != null) { "Numeric rules require a target." }
        }
    }

    /** An explicit band wins; otherwise the metric's own default applies. */
    internal fun effectiveToleranceAbsolute(): Long? = toleranceAbsolute ?: metric.defaultToleranceAbsolute
}

data class MetricEvaluation(
    val rule: FilterRule,
    val observedValue: Long?,
    val confidence: Float,
    val status: MetricStatus,
    val score: Int,
)

/**
 * Why the engine reached its verdict, decided at the point of decision rather than derived after.
 *
 * Adopted from the sibling project's decision engine, where every outcome carries an explicit
 * reason instead of one being reconstructed from the metrics. It names the *kind* of decision;
 * [EvaluationResult.primaryReason] still carries the specific rule and number to show alongside.
 */
enum class DecisionReason {
    /** No rules are configured, so the engine defers rather than judging. */
    NO_RULES,
    /** An eliminatory rule failed outright; it alone decided the reject. */
    ELIMINATORY_BLOCK,
    /** An eliminatory rule could not be read, so no confident verdict is possible. */
    ELIMINATORY_UNKNOWN,
    /** Too little of the card was readable to stand behind accept or reject. */
    LOW_COVERAGE,
    /** Score cleared the accept bar with every rule readable. */
    MEETS_TARGETS,
    /**
     * A strong R$/km rescued an offer whose R$/h was close but under target. The relational rule
     * the sibling project encodes that a weighted average cannot: a good per-km rate compensates a
     * slightly weak per-hour one.
     */
    KM_COMPENSATES_HOUR,
    /** Score landed in the middle band: neither clearly good nor clearly bad. */
    BORDERLINE,
    /** Score fell below the analyze bar. */
    BELOW_TARGET,
}

data class EvaluationResult(
    val metrics: List<MetricEvaluation>,
    val weightedScore: Int,
    val decision: OfferDecision,
    /**
     * Share of the configured rule weight that produced a usable reading, 0..100. The weighted
     * score is computed only over the readable rules, so this is what separates "a genuinely
     * middling offer" from "an offer whose fields could not be read".
     */
    val coveragePercent: Int = 100,
    /** The rule that most explains [decision], used for the overlay's one-line justification. */
    val primaryReason: MetricEvaluation? = null,
    /** The kind of decision, set where the decision is made. */
    val reason: DecisionReason = DecisionReason.NO_RULES,
) {
    val hasIncompleteData: Boolean get() = metrics.any { it.status == MetricStatus.UNKNOWN }
}

class OfferEvaluator(
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val acceptThreshold: Int = DEFAULT_ACCEPT_THRESHOLD,
    private val analyzeThreshold: Int = DEFAULT_ANALYZE_THRESHOLD,
    private val minimumCoveragePercent: Int = DEFAULT_MINIMUM_COVERAGE_PERCENT,
    /**
     * How far above its target an R$/km rule must sit to compensate a below-target R$/h, as a
     * fraction. Mirrors the sibling engine's `strongKmBonus`; 0.10 means the per-km must clear its
     * minimum by 10%.
     */
    private val strongKmBonus: Float = DEFAULT_STRONG_KM_BONUS,
) {
    init {
        require(confidenceThreshold in 0f..1f)
        require(acceptThreshold in analyzeThreshold..100)
        require(minimumCoveragePercent in 0..100)
        require(strongKmBonus >= 0f)
    }

    fun derive(offer: NormalizedOffer): DerivedMetrics {
        val totalDistance = combineDistance(offer.pickup.distance, offer.trip.distance)
        val totalDuration = combineDuration(offer.pickup.duration, offer.trip.duration)
        val payout = offer.payout
        val totalMeters = totalDistance.value?.meters
        val totalSeconds = totalDuration.value?.seconds
        val rateConfidence = minOf(payout.score, totalDistance.score)
        val hourConfidence = minOf(payout.score, totalDuration.score)
        val ratePerKm = if (payout.value != null && (totalMeters ?: 0) > 0) {
            payout.value.cents * 1_000 / requireNotNull(totalMeters)
        } else {
            null
        }
        val ratePerHour = if (payout.value != null && (totalSeconds ?: 0) > 0) {
            payout.value.cents * 3_600 / requireNotNull(totalSeconds)
        } else {
            null
        }
        val ratePerMinute = if (payout.value != null && (totalSeconds ?: 0) > 0) {
            payout.value.cents * 60 / requireNotNull(totalSeconds)
        } else null
        return DerivedMetrics(
            totalDistance = totalDistance,
            totalDuration = totalDuration,
            ratePerKm = Confidence(ratePerKm, rateConfidence, FieldSource.DERIVED),
            ratePerHour = Confidence(ratePerHour, hourConfidence, FieldSource.DERIVED),
            ratePerMinute = Confidence(ratePerMinute, hourConfidence, FieldSource.DERIVED),
        )
    }

    fun evaluate(
        offer: NormalizedOffer,
        rules: List<FilterRule>,
        netProfit: Confidence<Long> = Confidence(null, 0f, FieldSource.DERIVED),
        netProfitPercent: Confidence<Long> = Confidence(null, 0f, FieldSource.DERIVED),
        netProfitPerHour: Confidence<Long> = Confidence(null, 0f, FieldSource.DERIVED),
    ): EvaluationResult {
        val derived = derive(offer)
        val activeRules = rules.filter { it.enabled }
        if (activeRules.isEmpty()) {
            return EvaluationResult(emptyList(), weightedScore = 0, decision = OfferDecision.ANALYZE)
        }
        val metrics = activeRules.map { rule ->
            evaluateMetric(
                rule,
                valueFor(
                    metric = rule.metric,
                    offer = offer,
                    derived = derived,
                    netProfit = netProfit,
                    netProfitPercent = netProfitPercent,
                    netProfitPerHour = netProfitPerHour,
                ),
            )
        }
        // An unreadable metric carries no evidence in either direction, so it is excluded from the
        // average entirely rather than contributing a neutral 50. Folding it in made a half-parsed
        // offer score the same as a genuinely borderline one, which then let a weak offer drift up
        // from REJECT into ANALYZE purely because fields were missing.
        val readable = metrics.filter { it.status != MetricStatus.UNKNOWN }
        // Field confidence scales the weight, so an OCR reading naturally counts for slightly less
        // than the same field taken from the accessibility tree without needing a dedicated rule.
        val scoringWeight = readable.sumOf { it.effectiveWeight() }
        val weightedScore = if (scoringWeight == 0L) {
            0
        } else {
            (readable.sumOf { it.score.toLong() * it.effectiveWeight() } / scoringWeight).toInt()
        }
        // Coverage deliberately uses the nominal rule weights. An unreadable metric has zero
        // confidence and therefore zero effective weight, so measuring coverage with the scaled
        // weights would erase the very rules it exists to count and always report 100%.
        val configuredWeight = metrics.sumOf { it.rule.weight.toLong() }
        val readableWeight = readable.sumOf { it.rule.weight.toLong() }
        val coveragePercent =
            if (configuredWeight == 0L) 0 else (readableWeight * 100 / configuredWeight).toInt()
        val hardFailure = metrics.any { it.rule.mode == EvaluationMode.ELIMINATORY && it.status == MetricStatus.FAIL }
        val hardUnknown = metrics.any { it.rule.mode == EvaluationMode.ELIMINATORY && it.status == MetricStatus.UNKNOWN }
        // Base decision, then a reason to match it. Kept as one when so the two never drift apart.
        val base: Pair<OfferDecision, DecisionReason> = when {
            hardFailure -> OfferDecision.REJECT to DecisionReason.ELIMINATORY_BLOCK
            hardUnknown -> OfferDecision.ANALYZE to DecisionReason.ELIMINATORY_UNKNOWN
            coveragePercent < minimumCoveragePercent -> OfferDecision.ANALYZE to DecisionReason.LOW_COVERAGE
            weightedScore >= acceptThreshold && metrics.none { it.status == MetricStatus.UNKNOWN } ->
                OfferDecision.ACCEPT to DecisionReason.MEETS_TARGETS
            weightedScore >= analyzeThreshold -> OfferDecision.ANALYZE to DecisionReason.BORDERLINE
            else -> OfferDecision.REJECT to DecisionReason.BELOW_TARGET
        }
        // A strong R$/km can lift an offer whose R$/h is only just short. Applied only to a scored
        // ANALYZE -- never over a hard block, a missing read, or low coverage -- so it can rescue a
        // borderline offer but never a genuinely bad one. This is the relational judgement the
        // weighted average cannot make on its own; unlike the sibling engine it carries no traffic
        // guard, because the ride apps do not expose a traffic signal we could trust.
        val (decision, reason) = if (base.first == OfferDecision.ANALYZE &&
            base.second == DecisionReason.BORDERLINE &&
            kmCompensatesHour(metrics)
        ) {
            OfferDecision.ACCEPT to DecisionReason.KM_COMPENSATES_HOUR
        } else {
            base
        }
        return EvaluationResult(
            metrics = metrics,
            weightedScore = weightedScore,
            decision = decision,
            coveragePercent = coveragePercent,
            primaryReason = primaryReason(metrics, decision, reason),
            reason = reason,
        )
    }

    /**
     * True when an R$/km rule passes strongly while an R$/h rule is within tolerance but under
     * target -- the "strong per-km compensates a weak per-hour" case. Requires both rules to be
     * present and configured as minimums, so it only fires when the driver actually filters on
     * both. The per-km must clear its own target by [strongKmBonus]; the per-hour must be NEAR
     * (inside its band), never FAIL.
     */
    private fun kmCompensatesHour(metrics: List<MetricEvaluation>): Boolean {
        val km = metrics.firstOrNull {
            it.rule.metric == Metric.RATE_PER_KM && it.rule.comparator == Comparator.AT_LEAST
        } ?: return false
        val hour = metrics.firstOrNull {
            it.rule.metric == Metric.RATE_PER_HOUR && it.rule.comparator == Comparator.AT_LEAST
        } ?: return false
        if (hour.status != MetricStatus.NEAR) return false
        if (km.status != MetricStatus.PASS) return false
        val kmTarget = km.rule.target ?: return false
        val kmValue = km.observedValue ?: return false
        return kmValue >= kmTarget + (kmTarget * strongKmBonus).toLong()
    }

    /**
     * Picks the single rule that best explains the verdict.
     *
     * Ordering matters more than it looks. An eliminatory failure always wins, since it alone
     * decided the outcome. For a scored ANALYZE the useful answer is what is *holding the offer
     * back* -- the heaviest failing rule -- not an unrelated field that happened to be unreadable.
     * Reporting the unknown first (as an earlier version did) told the driver "ends near home:
     * could not read" on offers whose amber verdict actually came from a failed rate rule, which
     * points at the wrong thing to fix. An unknown is only the headline when nothing else explains
     * the verdict, or when it is what blocked a decision outright.
     */
    private fun primaryReason(
        metrics: List<MetricEvaluation>,
        decision: OfferDecision,
        reason: DecisionReason,
    ): MetricEvaluation? {
        // The compensation accepted on the strength of the per-km rule, so that is the number worth
        // showing -- not the per-hour that fell short.
        if (reason == DecisionReason.KM_COMPENSATES_HOUR) {
            metrics.firstOrNull { it.rule.metric == Metric.RATE_PER_KM }?.let { return it }
        }
        metrics.firstOrNull { it.rule.mode == EvaluationMode.ELIMINATORY && it.status == MetricStatus.FAIL }
            ?.let { return it }
        metrics.firstOrNull { it.rule.mode == EvaluationMode.ELIMINATORY && it.status == MetricStatus.UNKNOWN }
            ?.let { return it }
        val heaviest = { status: MetricStatus ->
            metrics.filter { it.status == status }.maxByOrNull { it.effectiveWeight() }
        }
        return when (decision) {
            OfferDecision.ACCEPT -> heaviest(MetricStatus.PASS)
            OfferDecision.REJECT -> heaviest(MetricStatus.FAIL)
            // Prefer a real shortfall over a missing reading.
            OfferDecision.ANALYZE -> heaviest(MetricStatus.FAIL)
                ?: heaviest(MetricStatus.NEAR)
        } ?: metrics.firstOrNull { it.status == MetricStatus.UNKNOWN }
    }

    private fun evaluateMetric(rule: FilterRule, metric: Confidence<Long>): MetricEvaluation {
        if (!metric.isUsable(confidenceThreshold)) {
            return MetricEvaluation(rule, metric.value, metric.score, MetricStatus.UNKNOWN, 50)
        }
        val value = requireNotNull(metric.value)
        val status = when (rule.comparator) {
            Comparator.AT_LEAST -> numericAtLeast(value, requireNotNull(rule.target), rule.nearBand())
            Comparator.AT_MOST -> numericAtMost(value, requireNotNull(rule.target), rule.nearBand())
            Comparator.IS_TRUE -> if (value == 1L) MetricStatus.PASS else MetricStatus.FAIL
            Comparator.IS_FALSE -> if (value == 0L) MetricStatus.PASS else MetricStatus.FAIL
        }
        return MetricEvaluation(rule, value, metric.score, status, status.toScore())
    }

    private fun valueFor(
        metric: Metric,
        offer: NormalizedOffer,
        derived: DerivedMetrics,
        netProfit: Confidence<Long>,
        netProfitPercent: Confidence<Long>,
        netProfitPerHour: Confidence<Long>,
    ): Confidence<Long> = when (metric) {
        Metric.PAYOUT -> offer.payout.map { it.cents }
        Metric.RATE_PER_KM -> derived.ratePerKm
        Metric.RATE_PER_HOUR -> derived.ratePerHour
        Metric.RATE_PER_MINUTE -> derived.ratePerMinute
        Metric.NET_PROFIT -> netProfit
        Metric.NET_PROFIT_PERCENT -> netProfitPercent
        Metric.NET_PROFIT_PER_HOUR -> netProfitPerHour
        Metric.PICKUP_DISTANCE -> offer.pickup.distance.map { it.meters }
        Metric.PICKUP_DURATION -> offer.pickup.duration.map { it.seconds }
        Metric.TRIP_DISTANCE -> offer.trip.distance.map { it.meters }
        Metric.TRIP_DURATION -> offer.trip.duration.map { it.seconds }
        Metric.TOTAL_DISTANCE -> derived.totalDistance.map { it.meters }
        Metric.TOTAL_DURATION -> derived.totalDuration.map { it.seconds }
        Metric.PASSENGER_RATING -> offer.passenger.rating
        Metric.HAS_MULTIPLE_STOPS -> offer.stopCount.map { if (it > 1) 1L else 0L }
        Metric.IS_LONG_TRIP -> offer.longTripHint.map { if (it) 1L else 0L }
        Metric.IS_TOWARD_DESTINATION -> offer.destinationDirectionHint.map { if (it) 1L else 0L }
        Metric.ENDS_NEAR_HOME -> offer.endsNearHome.map { if (it) 1L else 0L }
        Metric.PICKUP_IS_BLOCKED -> offer.pickupIsBlocked.map { if (it) 1L else 0L }
    }

    private fun numericAtLeast(value: Long, target: Long, band: Long): MetricStatus = when {
        value >= target -> MetricStatus.PASS
        value >= target - band -> MetricStatus.NEAR
        else -> MetricStatus.FAIL
    }

    private fun numericAtMost(value: Long, target: Long, band: Long): MetricStatus = when {
        value <= target -> MetricStatus.PASS
        value <= target + band -> MetricStatus.NEAR
        else -> MetricStatus.FAIL
    }

    private fun combineDistance(first: Confidence<Distance>, second: Confidence<Distance>): Confidence<Distance> =
        Confidence(
            value = if (first.value != null && second.value != null) Distance(first.value.meters + second.value.meters) else null,
            score = minOf(first.score, second.score),
            source = FieldSource.DERIVED,
        )

    private fun combineDuration(first: Confidence<Duration>, second: Confidence<Duration>): Confidence<Duration> =
        Confidence(
            value = if (first.value != null && second.value != null) Duration(first.value.seconds + second.value.seconds) else null,
            score = minOf(first.score, second.score),
            source = FieldSource.DERIVED,
        )
}

private fun MetricStatus.toScore() = when (this) {
    MetricStatus.PASS -> 100
    MetricStatus.NEAR, MetricStatus.UNKNOWN -> 50
    MetricStatus.FAIL -> 0
}

/** Rule weight scaled by field confidence, in centi-weight so the maths stays in integers. */
internal fun MetricEvaluation.effectiveWeight(): Long {
    val scaledConfidence = (confidence * 100f).toLong().coerceIn(0L, 100L)
    return rule.weight.toLong() * scaledConfidence
}

/** Resolves the near-threshold band to the metric's own unit. */
private fun FilterRule.nearBand(): Long {
    effectiveToleranceAbsolute()?.let { return it }
    val reference = target ?: return 0L
    return reference * tolerancePercent / 100
}

private fun <T, R> Confidence<T>.map(transform: (T) -> R): Confidence<R> =
    Confidence(value?.let(transform), score, source)
