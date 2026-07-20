package br.com.nexo.driver.overlay.preferences

/** Dependency-free persistence boundary for overlay metric choices. */
interface OverlayPreferenceStore {
    fun load(): OverlayPreferences

    fun save(preferences: OverlayPreferences): OverlayPreferences
}

/** Deterministic implementation for previews and unit tests. */
class InMemoryOverlayPreferenceStore(
    initial: OverlayPreferences = OverlayPreferences.DEFAULT,
) : OverlayPreferenceStore {
    private var preferences = initial

    @Synchronized
    override fun load(): OverlayPreferences = preferences

    @Synchronized
    override fun save(preferences: OverlayPreferences): OverlayPreferences {
        this.preferences = preferences
        return preferences
    }
}
