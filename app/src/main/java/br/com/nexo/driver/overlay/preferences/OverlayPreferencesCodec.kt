package br.com.nexo.driver.overlay.preferences

/** Small versioned payload that avoids adding a JSON dependency for six enum values. */
internal object OverlayPreferencesCodec {
    private const val SCHEMA = "overlay-preferences-v1"

    fun encode(preferences: OverlayPreferences): String =
        "$SCHEMA:${preferences.fields.joinToString(",") { it.name }}"

    /** Invalid or obsolete data falls back to the safe default configuration. */
    fun decode(payload: String?): OverlayPreferences {
        val names = payload
            ?.takeIf { it.startsWith("$SCHEMA:") }
            ?.removePrefix("$SCHEMA:")
            ?.split(',')
            ?: return OverlayPreferences.DEFAULT
        val fields = runCatching { names.map(OverlayMetricField::valueOf) }.getOrNull()
            ?: return OverlayPreferences.DEFAULT
        return runCatching { OverlayPreferences(fields) }.getOrDefault(OverlayPreferences.DEFAULT)
    }
}
