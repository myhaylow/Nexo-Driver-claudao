package br.com.nexo.driver.overlay.preferences

import android.content.Context
import br.com.nexo.driver.overlay.OverlayPosition

/** Keeps placement independent from the four-metric configuration schema. */
class SharedPreferencesOverlayPositionStore private constructor(
    private val context: Context,
) {
    fun load(): OverlayPosition = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(KEY_POSITION, null)
        ?.let { saved -> OverlayPosition.entries.firstOrNull { it.name == saved } }
        ?: OverlayPosition.BOTTOM

    fun save(position: OverlayPosition): OverlayPosition {
        check(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_POSITION, position.name)
                .commit(),
        ) { "Could not persist overlay position." }
        return position
    }

    companion object {
        private const val PREFERENCES = "overlay_position_preferences"
        private const val KEY_POSITION = "overlay_position_v1"

        fun create(context: Context) = SharedPreferencesOverlayPositionStore(context.applicationContext)
    }
}
