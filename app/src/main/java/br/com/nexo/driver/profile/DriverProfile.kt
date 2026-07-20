package br.com.nexo.driver.profile

import br.com.nexo.driver.evaluation.FilterRule
import java.util.UUID

/** A named, independently persisted collection of offer-evaluation rules. */
data class DriverProfile(
    val id: String,
    val name: String,
    val rules: List<FilterRule>,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "Profile id cannot be blank." }
        require(name.isNotBlank()) { "Profile name cannot be blank." }
        require(name.length <= MAX_NAME_LENGTH) { "Profile name is too long." }
        require(createdAtEpochMs > 0) { "Creation time must be positive." }
        require(updatedAtEpochMs >= createdAtEpochMs) { "Update time cannot precede creation time." }
        require(rules.map { it.metric to it.comparator }.distinct().size == rules.size) {
            "A profile can contain only one rule for each metric and comparator pair."
        }
    }

    fun updated(
        name: String = this.name,
        rules: List<FilterRule> = this.rules,
        isEnabled: Boolean = this.isEnabled,
        updatedAtEpochMs: Long,
    ) = copy(
        name = name.trim(),
        rules = rules,
        isEnabled = isEnabled,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        const val MAX_NAME_LENGTH = 80

        fun create(
            name: String,
            rules: List<FilterRule> = emptyList(),
            nowEpochMs: Long,
            id: String = UUID.randomUUID().toString(),
        ) = DriverProfile(
            id = id,
            name = name.trim(),
            rules = rules,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
    }
}

data class ProfileSnapshot(
    val profiles: List<DriverProfile>,
    val activeProfileId: String?,
) {
    init {
        require(activeProfileId == null || profiles.any { it.id == activeProfileId }) {
            "The active profile must exist in the snapshot."
        }
    }

    val activeProfile: DriverProfile? get() = profiles.firstOrNull { it.id == activeProfileId }
}
