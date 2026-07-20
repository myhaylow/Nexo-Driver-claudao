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

enum class Metric(val label: String) {
    PAYOUT("Valor"),
    RATE_PER_KM("R$/km"),
    RATE_PER_HOUR("R$/h"),
    RATE_PER_MINUTE("R$/min"),
    NET_PROFIT("Lucro"),
    NET_PROFIT_PERCENT("Lucro %"),
    NET_PROFIT_PER_HOUR("Lucro/h"),
    PICKUP_DISTANCE("Dist. retirada"),
    PICKUP_DURATION("Tempo retirada"),
    TRIP_DISTANCE("Dist. viagem"),
    TRIP_DURATION("Tempo viagem"),
    TOTAL_DISTANCE("Dist. total"),
    TOTAL_DURATION("Tempo total"),
    PASSENGER_RATING("Avaliação"),
    HAS_MULTIPLE_STOPS("Paradas"),
    IS_LONG_TRIP("Viagem longa"),
    IS_TOWARD_DESTINATION("Sentido destino"),
    ENDS_NEAR_HOME("Termina perto de casa"),
    /** Set by the blocklist enricher so a blocked pickup is decided by the engine, not post-hoc. */
    PICKUP_IS_BLOCKED("Local bloqueado"),
    ;

    /**
     * False for metrics the engine maintains itself. [PICKUP_IS_BLOCKED] is driven by the
     * blocklist toggle in settings, so offering it a second time as a hand-editable filter rule
     * would let the two disagree.
     */
    val isUserConfigurable: Boolean get() = this != PICKUP_IS_BLOCKED
}

enum class Comparator { AT_LEAST, AT_MOST, IS_TRUE, IS_FALSE }

enum class EvaluationMode { SCORE, ELIMINATORY }

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

    /** Default absolute bands for metrics where a percentage band is not meaningful. */
    internal fun effectiveToleranceAbsolute(): Long? = toleranceAbsolute ?: when (metric) {
        Metric.PASSENGER_RATING -> DEFAULT_RATING_TOLERANCE
        else -> null
    }

    private companion object {
        /** Ratings are stored scaled by 100, so this is ±0.05 stars. */
        const val DEFAULT_RATING_TOLERANCE = 5L
    }
}

data class MetricEvaluation(
    val rule: FilterRule,
    val observedValue: Long?,
    val confidence: Float,
    val status: MetricStatus,
    val score: Int,
)

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
) {
    val hasIncompleteData: Boolean get() = metrics.any { it.status == MetricStatus.UNKNOWN }
}

class OfferEvaluator(
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val acceptThreshold: Int = DEFAULT_ACCEPT_THRESHOLD,
    private val analyzeThreshold: Int = DEFAULT_ANALYZE_THRESHOLD,
    private val minimumCoveragePercent: Int = DEFAULT_MINIMUM_COVERAGE_PERCENT,
) {
    init {
        require(confidenceThreshold in 0f..1f)
        require(acceptThreshold in analyzeThreshold..100)
        require(minimumCoveragePercent in 0..100)
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
        val decision = when {
            hardFailure -> OfferDecision.REJECT
            hardUnknown -> OfferDecision.ANALYZE
            // Too little of the card was readable to stand behind any verdict.
            coveragePercent < minimumCoveragePercent -> OfferDecision.ANALYZE
            weightedScore >= acceptThreshold && metrics.none { it.status == MetricStatus.UNKNOWN } -> OfferDecision.ACCEPT
            weightedScore >= analyzeThreshold -> OfferDecision.ANALYZE
            else -> OfferDecision.REJECT
        }
        return EvaluationResult(
            metrics = metrics,
            weightedScore = weightedScore,
            decision = decision,
            coveragePercent = coveragePercent,
            primaryReason = primaryReason(metrics, decision),
        )
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
    private fun primaryReason(metrics: List<MetricEvaluation>, decision: OfferDecision): MetricEvaluation? {
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
