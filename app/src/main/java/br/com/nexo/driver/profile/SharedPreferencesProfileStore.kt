package br.com.nexo.driver.profile

import android.content.Context
import android.content.SharedPreferences

/**
 * Small, dependency-free local store for driver profiles.
 *
 * One versioned payload and the selected id are committed in a single SharedPreferences editor
 * transaction. Corrupt or stale entries are ignored individually rather than preventing the app
 * from loading valid profiles.
 */
class SharedPreferencesProfileStore private constructor(
    private val preferences: SharedPreferences,
) : DriverProfileStore {
    private val lock = Any()

    override fun load(): ProfileSnapshot = synchronized(lock) { readSnapshot() }

    override fun save(profile: DriverProfile): ProfileSnapshot = synchronized(lock) {
        val current = readSnapshot()
        val profiles = (current.profiles.filterNot { it.id == profile.id } + profile).sortedBy { it.createdAtEpochMs }
        val next = ProfileSnapshot(profiles, current.activeProfileId ?: profile.id)
        writeSnapshot(next)
        next
    }

    override fun delete(profileId: String): ProfileSnapshot = synchronized(lock) {
        val current = readSnapshot()
        val next = ProfileSnapshot(
            profiles = current.profiles.filterNot { it.id == profileId },
            activeProfileId = current.activeProfileId?.takeUnless { it == profileId },
        )
        writeSnapshot(next)
        next
    }

    override fun setActiveProfile(profileId: String?): ProfileSnapshot = synchronized(lock) {
        val current = readSnapshot()
        require(profileId == null || current.profiles.any { it.id == profileId }) {
            "Cannot activate a profile that is not stored."
        }
        val next = current.copy(activeProfileId = profileId)
        writeSnapshot(next)
        next
    }

    private fun readSnapshot(): ProfileSnapshot {
        val profiles = ProfilePayloadCodec.decode(preferences.getString(KEY_PROFILES, null)).sortedBy { it.createdAtEpochMs }
        val activeId = preferences.getString(KEY_ACTIVE_PROFILE, null)?.takeIf { selected ->
            profiles.any { it.id == selected }
        }
        return ProfileSnapshot(profiles, activeId)
    }

    private fun writeSnapshot(snapshot: ProfileSnapshot) {
        check(
            preferences.edit()
                .putString(KEY_PROFILES, ProfilePayloadCodec.encode(snapshot.profiles))
                .putString(KEY_ACTIVE_PROFILE, snapshot.activeProfileId)
                .commit(),
        ) { "Could not persist driver profiles." }
    }

    companion object {
        private const val PREFERENCES_NAME = "driver_profiles"
        private const val KEY_PROFILES = "profiles_v1"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"

        fun create(context: Context): SharedPreferencesProfileStore = SharedPreferencesProfileStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }
}
