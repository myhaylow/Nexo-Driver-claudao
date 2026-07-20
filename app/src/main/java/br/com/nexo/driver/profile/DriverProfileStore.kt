package br.com.nexo.driver.profile

/**
 * Local persistence contract for profile configuration.
 *
 * Implementations return whole snapshots so callers never need to combine a profile list and
 * active-profile value from separate reads.
 */
interface DriverProfileStore {
    fun load(): ProfileSnapshot

    /** Creates or replaces a profile, making the first saved profile active. */
    fun save(profile: DriverProfile): ProfileSnapshot

    /** Removes a profile and clears the active selection if it was selected. */
    fun delete(profileId: String): ProfileSnapshot

    /** Selects an existing profile; pass null to clear the selection. */
    fun setActiveProfile(profileId: String?): ProfileSnapshot
}

/** Useful for previews and deterministic unit tests. Production code should use SharedPreferencesProfileStore. */
class InMemoryDriverProfileStore(initial: ProfileSnapshot = ProfileSnapshot(emptyList(), null)) : DriverProfileStore {
    private var snapshot = initial

    @Synchronized
    override fun load(): ProfileSnapshot = snapshot

    @Synchronized
    override fun save(profile: DriverProfile): ProfileSnapshot {
        val updated = snapshot.profiles.filterNot { it.id == profile.id } + profile
        snapshot = ProfileSnapshot(updated, snapshot.activeProfileId ?: profile.id)
        return snapshot
    }

    @Synchronized
    override fun delete(profileId: String): ProfileSnapshot {
        val updated = snapshot.profiles.filterNot { it.id == profileId }
        snapshot = ProfileSnapshot(updated, snapshot.activeProfileId?.takeUnless { it == profileId })
        return snapshot
    }

    @Synchronized
    override fun setActiveProfile(profileId: String?): ProfileSnapshot {
        require(profileId == null || snapshot.profiles.any { it.id == profileId }) {
            "Cannot activate a profile that is not stored."
        }
        snapshot = snapshot.copy(activeProfileId = profileId)
        return snapshot
    }
}
