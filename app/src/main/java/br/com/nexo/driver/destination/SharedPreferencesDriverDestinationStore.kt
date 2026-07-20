package br.com.nexo.driver.destination

import android.content.Context
import android.content.SharedPreferences

/**
 * Private on-device storage for the driver's selected destination.
 *
 * Values are committed synchronously because a destination change must be visible to the capture
 * service immediately. Invalid or corrupt persisted data is treated as absent instead of crashing
 * the offer-reading flow.
 */
class SharedPreferencesDriverDestinationStore private constructor(
    private val preferences: SharedPreferences,
) : HomeDestinationStore {
    private val lock = Any()

    override fun load(): HomeDestination? = synchronized(lock) {
        val currentPayload = preferences.getString(KEY_DESTINATION_V3, null)
        DestinationPayloadCodec.decode(currentPayload)?.also { return@synchronized it }
        val migrated = DestinationPayloadCodec.decode(preferences.getString(KEY_LEGACY_DESTINATION, null))
        if (migrated != null) {
            check(
                preferences.edit()
                    .putString(KEY_DESTINATION_V3, DestinationPayloadCodec.encode(migrated))
                    .remove(KEY_LEGACY_DESTINATION)
                    .commit(),
            ) { "Could not migrate home destination." }
        }
        migrated
    }

    override fun save(destination: HomeDestination): HomeDestination = synchronized(lock) {
        val validated = requireNotNull(destination.validatedOrNull()) {
            "A destination must have valid coordinates and a non-negative finite arrival radius."
        }
        check(
            preferences.edit()
                .putString(KEY_DESTINATION_V3, DestinationPayloadCodec.encode(validated))
                .remove(KEY_LEGACY_DESTINATION)
                .commit(),
        ) { "Could not persist driver destination." }
        validated
    }

    override fun clear() = synchronized(lock) {
        check(preferences.edit().remove(KEY_DESTINATION_V3).remove(KEY_LEGACY_DESTINATION).commit()) {
            "Could not clear driver destination."
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "driver_destination"
        private const val KEY_DESTINATION_V3 = "destination_v3"
        private const val KEY_LEGACY_DESTINATION = "destination_v1"

        fun create(context: Context): SharedPreferencesDriverDestinationStore =
            SharedPreferencesDriverDestinationStore(
                context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
    }
}
