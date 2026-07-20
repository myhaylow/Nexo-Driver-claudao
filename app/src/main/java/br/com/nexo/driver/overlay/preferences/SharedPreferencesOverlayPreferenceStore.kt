package br.com.nexo.driver.overlay.preferences

import android.content.Context
import android.content.SharedPreferences

/** Production local storage for a single four-cell overlay configuration. */
class SharedPreferencesOverlayPreferenceStore private constructor(
    private val preferences: SharedPreferences,
) : OverlayPreferenceStore {
    private val lock = Any()

    override fun load(): OverlayPreferences = synchronized(lock) {
        OverlayPreferencesCodec.decode(preferences.getString(KEY_CONFIGURATION, null))
    }

    override fun save(preferences: OverlayPreferences): OverlayPreferences = synchronized(lock) {
        check(
            this.preferences.edit()
                .putString(KEY_CONFIGURATION, OverlayPreferencesCodec.encode(preferences))
                .commit(),
        ) { "Could not persist overlay preferences." }
        preferences
    }

    companion object {
        private const val PREFERENCES_NAME = "overlay_preferences"
        private const val KEY_CONFIGURATION = "metric_fields_v1"

        fun create(context: Context): SharedPreferencesOverlayPreferenceStore =
            SharedPreferencesOverlayPreferenceStore(
                context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
    }
}
