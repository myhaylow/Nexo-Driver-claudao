package br.com.nexo.driver.speech

import android.content.Context

data class SpeechSettings(
    val speakDecision: Boolean = true,
)

class SharedPreferencesSpeechSettingsStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): SpeechSettings = synchronized(lock) {
        SpeechSettings(
            speakDecision = preferences.getBoolean(KEY_SPEAK_DECISION, true),
        )
    }

    fun save(settings: SpeechSettings): SpeechSettings = synchronized(lock) {
        preferences.edit()
            .putBoolean(KEY_SPEAK_DECISION, settings.speakDecision)
            .remove(KEY_SAVE_HISTORY)
            .apply()
        settings
    }

    companion object {
        private const val PREFERENCES = "driver_inteligente_speech_settings"
        private const val KEY_SPEAK_DECISION = "speak_decision"
        private const val KEY_SAVE_HISTORY = "save_offer_history"

        fun create(context: Context): SharedPreferencesSpeechSettingsStore =
            SharedPreferencesSpeechSettingsStore(context.applicationContext)
    }
}
