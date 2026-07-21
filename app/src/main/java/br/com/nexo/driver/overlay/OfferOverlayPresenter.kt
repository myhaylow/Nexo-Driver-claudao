package br.com.nexo.driver.overlay

import br.com.nexo.driver.cost.FuelSettings
import br.com.nexo.driver.cost.NetProfitCalculator
import br.com.nexo.driver.evaluation.EvaluationResult
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.evaluation.MetricStatus
import br.com.nexo.driver.evaluation.MetricUnit
import br.com.nexo.driver.evaluation.OfferDecision
import br.com.nexo.driver.evaluation.OfferEvaluator
import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import java.text.NumberFormat
import java.util.Locale

/** Converts evaluator output to the small, presentation-ready overlay model. */
class OfferOverlayPresenter(
    private val evaluator: OfferEvaluator = OfferEvaluator(),
) {
    fun present(
        offer: NormalizedOffer,
        result: EvaluationResult,
        gridFields: List<OverlayMetricField> = OverlayPreferences.DEFAULT.fields,
        fuelSettings: FuelSettings = FuelSettings(),
    ): OfferOverlayUiModel {
        val derived = evaluator.derive(offer)
        val payoutAvailable = offer.payout.isUsable()
        val totalDistanceAvailable = derived.totalDistance.isUsable()
        val netProfitEstimate = NetProfitCalculator(fuelSettings).estimate(
            grossPayoutCents = offer.payout.value?.cents.takeIf { payoutAvailable },
            totalDistanceMeters = derived.totalDistance.value?.meters.takeIf { totalDistanceAvailable },
        )
        val netProfitConfidence = minOf(offer.payout.score, derived.totalDistance.score)
        val netProfit = Confidence(netProfitEstimate?.netProfitCents, netProfitConfidence, FieldSource.DERIVED)
        val netProfitPercent = Confidence(netProfitEstimate?.netProfitPercentScaled, netProfitConfidence, FieldSource.DERIVED)
        val netProfitPerHour = Confidence(
            value = if (netProfitEstimate != null && (derived.totalDuration.value?.seconds ?: 0L) > 0L) {
                netProfitEstimate.netProfitCents * 3_600 / requireNotNull(derived.totalDuration.value).seconds
            } else {
                null
            },
            score = minOf(netProfitConfidence, derived.totalDuration.score),
            source = FieldSource.DERIVED,
        )
        val ratePerKmAvailable = derived.ratePerKm.isUsable()
        val ratePerHourAvailable = derived.ratePerHour.isUsable()
        val ratePerMinuteAvailable = derived.ratePerMinute.isUsable()
        val passengerRatingAvailable = offer.passenger.rating.isUsable()
        val pickupAvailable = offer.pickup.duration.isUsable() && offer.pickup.distance.isUsable()
        return OfferOverlayUiModel(
            status = result.decision.toOverlayStatus(),
            totalDuration = derived.totalDuration.value?.seconds?.formatDuration() ?: "—",
            payout = if (payoutAvailable) offer.payout.value?.cents?.formatBrl() ?: "—" else "—",
            payoutStatus = result.statusFor(Metric.PAYOUT, payoutAvailable),
            isPayoutAvailable = payoutAvailable,
            ratePerKm = OverlayMetricUi(
                value = if (ratePerKmAvailable) derived.ratePerKm.value?.formatBrl() ?: "—" else "—",
                status = result.statusFor(Metric.RATE_PER_KM, ratePerKmAvailable),
                isAvailable = ratePerKmAvailable,
            ),
            ratePerHour = OverlayMetricUi(
                value = if (ratePerHourAvailable) derived.ratePerHour.value?.formatBrl() ?: "—" else "—",
                status = result.statusFor(Metric.RATE_PER_HOUR, ratePerHourAvailable),
                isAvailable = ratePerHourAvailable,
            ),
            ratePerMinute = OverlayMetricUi(
                value = if (ratePerMinuteAvailable) derived.ratePerMinute.value?.formatBrl() ?: "—" else "—",
                status = result.statusFor(Metric.RATE_PER_MINUTE, ratePerMinuteAvailable),
                isAvailable = ratePerMinuteAvailable,
            ),
            passengerRating = OverlayMetricUi(
                value = if (passengerRatingAvailable) offer.passenger.rating.value?.formatRating() ?: "—" else "—",
                status = result.statusFor(Metric.PASSENGER_RATING, passengerRatingAvailable),
                isAvailable = passengerRatingAvailable,
            ),
            pickup = OverlayMetricUi(
                value = if (pickupAvailable) {
                    listOfNotNull(
                        offer.pickup.duration.value?.seconds?.formatDuration(),
                        offer.pickup.distance.value?.meters?.formatDistance(),
                    ).joinToString(" · ").ifBlank { "—" }
                } else {
                    "—"
                },
                status = pickupStatus(result, pickupAvailable),
                isAvailable = pickupAvailable,
            ),
            totalDurationMetric = OverlayMetricUi(
                value = derived.totalDuration.value?.seconds?.formatDuration() ?: "—",
                status = result.statusFor(Metric.TOTAL_DURATION, derived.totalDuration.isUsable()),
                isAvailable = derived.totalDuration.isUsable(),
            ),
            totalDistance = OverlayMetricUi(
                value = derived.totalDistance.value?.meters?.formatDistance() ?: "—",
                status = result.statusFor(Metric.TOTAL_DISTANCE, derived.totalDistance.isUsable()),
                isAvailable = totalDistanceAvailable,
            ),
            payoutMetric = OverlayMetricUi(
                value = if (payoutAvailable) offer.payout.value?.cents?.formatBrl() ?: "—" else "—",
                status = result.statusFor(Metric.PAYOUT, payoutAvailable),
                isAvailable = payoutAvailable,
            ),
            netProfit = OverlayMetricUi(
                value = netProfit.value?.formatBrl() ?: "—",
                status = result.statusFor(Metric.NET_PROFIT, netProfit.isUsable()),
                isAvailable = netProfit.isUsable(),
            ),
            netProfitPercent = OverlayMetricUi(
                value = netProfitPercent.value?.formatPercent() ?: "—",
                status = result.statusFor(Metric.NET_PROFIT_PERCENT, netProfitPercent.isUsable()),
                isAvailable = netProfitPercent.isUsable(),
            ),
            netProfitPerHour = OverlayMetricUi(
                value = netProfitPerHour.value?.formatBrl() ?: "—",
                status = result.statusFor(Metric.NET_PROFIT_PER_HOUR, netProfitPerHour.isUsable()),
                isAvailable = netProfitPerHour.isUsable(),
            ),
            gridFields = gridFields,
            isTowardHome = offer.endsNearHome.value == true || offer.headingTowardHome.value == true,
            decisionReason = result.explain(),
            coveragePercent = result.coveragePercent,
        )
    }

    /**
     * Renders the deciding rule as "<metric> <observed> (<bound> <target>)". Values are formatted
     * in the metric's own unit so the line reads the same way as the grid cell it refers to.
     */
    private fun EvaluationResult.explain(): String? {
        val metric = primaryReason ?: return null
        // The compensation is a relationship between two rules, so it reads better as a sentence
        // than as a single "R$/km 2,10 (mín. 1,80)" bound.
        if (this.reason == br.com.nexo.driver.evaluation.DecisionReason.KM_COMPENSATES_HOUR) {
            val km = metric.observedValue?.let { metric.rule.metric.formatValue(it) }
            return if (km != null) "R$/km $km compensa a hora" else "R$/km forte compensa a hora"
        }
        if (this.reason == br.com.nexo.driver.evaluation.DecisionReason.PAYOUT_COMPENSATES_PICKUP) {
            val payout = metric.observedValue?.let { metric.rule.metric.formatValue(it) }
            return if (payout != null) "$payout compensa a retirada" else "Valor compensa a retirada"
        }
        if (metric.status == MetricStatus.UNKNOWN) {
            return "${metric.rule.metric.label}: não foi possível ler"
        }
        val observed = metric.observedValue?.let { metric.rule.metric.formatValue(it) } ?: return null
        val target = metric.rule.target?.let { metric.rule.metric.formatValue(it) }
        val bound = when (metric.rule.comparator) {
            br.com.nexo.driver.evaluation.Comparator.AT_LEAST -> "mín."
            br.com.nexo.driver.evaluation.Comparator.AT_MOST -> "máx."
            else -> null
        }
        return if (target != null && bound != null) {
            "${metric.rule.metric.label} $observed ($bound $target)"
        } else {
            "${metric.rule.metric.label} $observed"
        }
    }

    /** Formats a raw metric value using the unit the metric itself declares. */
    private fun Metric.formatValue(raw: Long): String = when (unit) {
        MetricUnit.MONEY_CENTS -> raw.formatBrl()
        MetricUnit.PERCENT_SCALED -> raw.formatPercent()
        MetricUnit.DISTANCE_METERS -> raw.formatDistance()
        MetricUnit.DURATION_SECONDS -> raw.formatDuration()
        MetricUnit.RATING_SCALED -> raw.formatRating()
        MetricUnit.FLAG -> if (raw == 1L) "sim" else "não"
    }

    private fun pickupStatus(result: EvaluationResult, isAvailable: Boolean): OverlayStatus = when {
        !isAvailable -> OverlayStatus.UNKNOWN
        result.metrics.any { it.rule.metric == Metric.PICKUP_DURATION && it.status == MetricStatus.FAIL } -> OverlayStatus.REJECT
        result.metrics.any { it.rule.metric == Metric.PICKUP_DISTANCE && it.status == MetricStatus.FAIL } -> OverlayStatus.REJECT
        result.metrics.any { it.rule.metric in PICKUP_METRICS && it.status in setOf(MetricStatus.NEAR, MetricStatus.UNKNOWN) } -> OverlayStatus.ANALYZE
        result.metrics.any { it.rule.metric in PICKUP_METRICS && it.status == MetricStatus.PASS } -> OverlayStatus.ACCEPT
        else -> OverlayStatus.UNKNOWN
    }

    /**
     * A metric may have both a minimum and a maximum rule. The cell must communicate the most
     * restrictive outcome across them: failure wins, then near-threshold, then unknown, then pass.
     */
    private fun EvaluationResult.statusFor(metric: Metric, isAvailable: Boolean): OverlayStatus {
        if (!isAvailable) return OverlayStatus.UNKNOWN
        val statuses = metrics.filter { it.rule.metric == metric }.map { it.status }
        return when {
            statuses.isEmpty() -> OverlayStatus.UNKNOWN
            MetricStatus.FAIL in statuses -> OverlayStatus.REJECT
            MetricStatus.NEAR in statuses -> OverlayStatus.ANALYZE
            MetricStatus.UNKNOWN in statuses -> OverlayStatus.UNKNOWN
            else -> OverlayStatus.ACCEPT
        }
    }

    private fun MetricStatus.toOverlayStatus(): OverlayStatus = when (this) {
        MetricStatus.PASS -> OverlayStatus.ACCEPT
        MetricStatus.NEAR -> OverlayStatus.ANALYZE
        MetricStatus.FAIL -> OverlayStatus.REJECT
        MetricStatus.UNKNOWN -> OverlayStatus.UNKNOWN
    }

    private fun OfferDecision.toOverlayStatus(): OverlayStatus = when (this) {
        OfferDecision.ACCEPT -> OverlayStatus.ACCEPT
        OfferDecision.ANALYZE -> OverlayStatus.ANALYZE
        OfferDecision.REJECT -> OverlayStatus.REJECT
    }

    private fun Long.formatBrl(): String = NumberFormat.getCurrencyInstance(BRAZIL).format(this / 100.0)
    private fun Long.formatPercent(): String = "%.0f%%".format(BRAZIL, this / 100.0)
    private fun Long.formatRating(): String = "%.2f".format(BRAZIL, this / 100.0)
    private fun Long.formatDuration(): String = "${(this + 59) / 60} min"
    private fun Long.formatDistance(): String = "%.1f km".format(BRAZIL, this / 1_000.0)

    private fun <T> br.com.nexo.driver.offer.Confidence<T>.isUsable() = value != null && score >= 0.85f

    private companion object {
        val BRAZIL = Locale.forLanguageTag("pt-BR")
        val PICKUP_METRICS = setOf(Metric.PICKUP_DURATION, Metric.PICKUP_DISTANCE)
    }
}
