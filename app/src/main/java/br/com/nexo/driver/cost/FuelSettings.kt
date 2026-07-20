package br.com.nexo.driver.cost

import android.content.Context

/** Minimal, local fuel-cost configuration used to estimate offer profit from fuel cost. */
data class FuelSettings(
    /** Fuel price in BRL cents per litre, e.g. R$ 4,00 -> 400. */
    val pricePerLiterCents: Long = DEFAULT_PRICE_PER_LITER_CENTS,
    /** Vehicle consumption in kilometres per litre, e.g. 8.0. */
    val kilometersPerLiter: Double = DEFAULT_KILOMETERS_PER_LITER,
    /** When false, the overlay shows "—" for profit instead of an estimate. */
    val enabled: Boolean = true,
) {
    init {
        require(pricePerLiterCents >= 0) { "Fuel price cannot be negative." }
        require(kilometersPerLiter > 0.0 && kilometersPerLiter.isFinite()) {
            "Consumption must be a positive, finite number."
        }
    }

    /** Estimated fuel cost in BRL cents per kilometre. R$ 4,00/L at 8 km/L -> 50 cents/km. */
    val costPerKilometerCents: Double
        get() = pricePerLiterCents / kilometersPerLiter

    companion object {
        const val DEFAULT_PRICE_PER_LITER_CENTS = 400L
        const val DEFAULT_KILOMETERS_PER_LITER = 8.0
    }
}

class SharedPreferencesFuelSettingsStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): FuelSettings = synchronized(lock) {
        FuelSettings(
            pricePerLiterCents = preferences.getLong(KEY_PRICE, FuelSettings.DEFAULT_PRICE_PER_LITER_CENTS),
            kilometersPerLiter = preferences.getFloat(
                KEY_CONSUMPTION,
                FuelSettings.DEFAULT_KILOMETERS_PER_LITER.toFloat(),
            ).toDouble(),
            enabled = preferences.getBoolean(KEY_ENABLED, true),
        )
    }

    fun save(settings: FuelSettings): FuelSettings = synchronized(lock) {
        preferences.edit()
            .putLong(KEY_PRICE, settings.pricePerLiterCents)
            .putFloat(KEY_CONSUMPTION, settings.kilometersPerLiter.toFloat())
            .putBoolean(KEY_ENABLED, settings.enabled)
            .apply()
        settings
    }

    companion object {
        private const val PREFERENCES = "driver_inteligente_fuel_settings"
        private const val KEY_PRICE = "fuel_price_per_liter_cents"
        private const val KEY_CONSUMPTION = "fuel_km_per_liter"
        private const val KEY_ENABLED = "fuel_estimate_enabled"

        fun create(context: Context): SharedPreferencesFuelSettingsStore =
            SharedPreferencesFuelSettingsStore(context.applicationContext)
    }
}
