package br.com.nexo.driver.overlay

import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.preferences.OverlayPreferences

/** The evaluation state used by the compact on-screen offer overlay. */
enum class OverlayStatus {
    ACCEPT,
    ANALYZE,
    REJECT,
    UNKNOWN,
}

/**
 * Presentation-ready data for one offer. Values are already formatted so this
 * small composable can be rendered immediately after the evaluator finishes.
 */
data class OfferOverlayUiModel(
    val appName: String = "Driver inteligente",
    val status: OverlayStatus,
    val totalDuration: String,
    val payout: String,
    /**
     * The payout remains the single primary value in the header (it is not
     * repeated in the 2×2 grid), but still receives its own filter colour.
     */
    val payoutStatus: OverlayStatus = OverlayStatus.UNKNOWN,
    val isPayoutAvailable: Boolean = true,
    val ratePerKm: OverlayMetricUi,
    val ratePerHour: OverlayMetricUi,
    val ratePerMinute: OverlayMetricUi = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
    val passengerRating: OverlayMetricUi,
    val pickup: OverlayMetricUi,
    val totalDurationMetric: OverlayMetricUi = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
    val totalDistance: OverlayMetricUi = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
    /** The offered value as a selectable cell for legacy/custom saved grids. */
    val payoutMetric: OverlayMetricUi = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
    val netProfit: OverlayMetricUi = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
    val netProfitPercent: OverlayMetricUi = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
    val netProfitPerHour: OverlayMetricUi = OverlayMetricUi("—", OverlayStatus.UNKNOWN, isAvailable = false),
    val gridFields: List<OverlayMetricField> = OverlayPreferences.DEFAULT.fields,
    val isTowardHome: Boolean = false,
    /** True when a supermarket-blocklist match forced the reject; drives the spoken phrase. */
    val isBlockedSupermarket: Boolean = false,
    /**
     * One line naming the rule that drove the verdict, e.g. "R$/km 1,42 (mín. 1,80)". Without it
     * the driver sees a colour and has to guess which filter fired, which erodes trust in the
     * card. Null when no rule is configured or the reason adds nothing.
     */
    val decisionReason: String? = null,
    /** Share of configured rule weight that was actually readable, 0..100. */
    val coveragePercent: Int = 100,
) {
    init {
        require(gridFields.size == 4 && gridFields.distinct().size == 4) {
            "The overlay grid must contain exactly four distinct fields."
        }
    }

    fun metricFor(field: OverlayMetricField): OverlayMetricUi = when (field) {
        OverlayMetricField.RATE_PER_KM -> ratePerKm
        OverlayMetricField.RATE_PER_HOUR -> ratePerHour
        OverlayMetricField.RATE_PER_MINUTE -> ratePerMinute
        OverlayMetricField.PASSENGER_RATING -> passengerRating
        OverlayMetricField.PICKUP -> pickup
        OverlayMetricField.TOTAL_DURATION -> totalDurationMetric
        OverlayMetricField.TOTAL_DISTANCE -> totalDistance
        OverlayMetricField.PAYOUT -> payoutMetric
        OverlayMetricField.NET_PROFIT -> netProfit
        OverlayMetricField.NET_PROFIT_PERCENT -> netProfitPercent
        OverlayMetricField.NET_PROFIT_PER_HOUR -> netProfitPerHour
    }
}

data class OverlayMetricUi(
    val value: String,
    val status: OverlayStatus,
    val isAvailable: Boolean = true,
)
