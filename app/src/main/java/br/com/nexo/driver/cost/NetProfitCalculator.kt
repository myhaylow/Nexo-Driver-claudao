package br.com.nexo.driver.cost

import kotlin.math.roundToLong

/** Estimates the net value a driver keeps for an offer: gross payout minus estimated fuel cost. */
class NetProfitCalculator(
    private val settings: FuelSettings,
) {
    /**
     * @param grossPayoutCents the offered payout in BRL cents, or null when unavailable.
     * @param totalDistanceMeters the full trip distance in metres, or null when unavailable.
     * @return the estimated net profit in BRL cents, or null when it cannot be estimated.
     */
    fun estimateCents(grossPayoutCents: Long?, totalDistanceMeters: Long?): Long? {
        return estimate(grossPayoutCents, totalDistanceMeters)?.netProfitCents
    }

    fun estimate(grossPayoutCents: Long?, totalDistanceMeters: Long?): NetProfitEstimate? {
        if (!settings.enabled) return null
        if (grossPayoutCents == null || totalDistanceMeters == null) return null
        if (totalDistanceMeters < 0L) return null
        val kilometers = totalDistanceMeters / 1_000.0
        val fuelCostCents = (kilometers * settings.costPerKilometerCents).roundToLong()
        return NetProfitEstimate(
            grossPayoutCents = grossPayoutCents,
            totalDistanceMeters = totalDistanceMeters,
            fuelCostCents = fuelCostCents,
            netProfitCents = grossPayoutCents - fuelCostCents,
        )
    }
}

data class NetProfitEstimate(
    val grossPayoutCents: Long,
    val totalDistanceMeters: Long,
    val fuelCostCents: Long,
    val netProfitCents: Long,
) {
    /** Profit percentage scaled by 100, e.g. 84.68% is stored as 8468. */
    val netProfitPercentScaled: Long?
        get() = if (grossPayoutCents > 0L) netProfitCents * 10_000 / grossPayoutCents else null
}
