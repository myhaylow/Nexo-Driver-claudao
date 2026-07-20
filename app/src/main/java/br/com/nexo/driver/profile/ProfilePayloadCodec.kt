package br.com.nexo.driver.profile

import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.EvaluationMode
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Internal, versioned codec intentionally based on Kotlin/JDK only (no JSON dependency required). */
internal object ProfilePayloadCodec {
    private const val SCHEMA = "driver-profile-v2"
    private const val LEGACY_SCHEMA = "driver-profile-v1"
    private const val PROFILE = "p"
    private const val RULE = "r"

    fun encode(profiles: List<DriverProfile>): String = buildString {
        append(SCHEMA)
        profiles.forEach { profile ->
            append('\n')
            append(PROFILE).append('\t')
            append(encodeText(profile.id)).append('\t')
            append(encodeText(profile.name)).append('\t')
            append(if (profile.isEnabled) '1' else '0').append('\t')
            append(profile.createdAtEpochMs).append('\t')
            append(profile.updatedAtEpochMs)
            profile.rules.forEach { rule ->
                append('\n')
                append(RULE).append('\t')
                append(encodeText(profile.id)).append('\t')
                append(rule.metric.name).append('\t')
                append(rule.comparator.name).append('\t')
                append(rule.target ?: "").append('\t')
                append(rule.tolerancePercent).append('\t')
                append(rule.weight).append('\t')
                append(rule.mode.name).append('\t')
                append(if (rule.enabled) '1' else '0')
            }
        }
    }

    fun decode(payload: String?): List<DriverProfile> {
        if (payload.isNullOrBlank()) return emptyList()
        val lines = payload.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() !in setOf(LEGACY_SCHEMA, SCHEMA)) return emptyList()

        val profiles = linkedMapOf<String, ProfileBuilder>()
        val pendingRules = linkedMapOf<String, MutableList<FilterRule>>()
        while (lines.hasNext()) {
            val parts = lines.next().split('\t')
            when (parts.firstOrNull()) {
                PROFILE -> parseProfile(parts)?.let { builder ->
                    profiles[builder.id] = builder
                    pendingRules.getOrPut(builder.id) { mutableListOf() }
                }
                RULE -> parseRule(parts)?.let { (profileId, rule) ->
                    pendingRules.getOrPut(profileId) { mutableListOf() }.add(rule)
                }
            }
        }
        return profiles.values.mapNotNull { builder ->
            runCatching {
                DriverProfile(
                    id = builder.id,
                    name = builder.name,
                    rules = pendingRules[builder.id].orEmpty()
                        .distinctBy { it.metric to it.comparator },
                    isEnabled = builder.isEnabled,
                    createdAtEpochMs = builder.createdAtEpochMs,
                    updatedAtEpochMs = builder.updatedAtEpochMs,
                )
            }.getOrNull()
        }
    }

    private fun parseProfile(parts: List<String>): ProfileBuilder? = runCatching {
        require(parts.size == 6)
        ProfileBuilder(
            id = decodeText(parts[1]),
            name = decodeText(parts[2]),
            isEnabled = parts[3] == "1",
            createdAtEpochMs = parts[4].toLong(),
            updatedAtEpochMs = parts[5].toLong(),
        )
    }.getOrNull()

    private fun parseRule(parts: List<String>): Pair<String, FilterRule>? = runCatching {
        require(parts.size == 9)
        val target = parts[4].takeIf { it.isNotEmpty() }?.toLong()
        decodeText(parts[1]) to FilterRule(
            metric = migrateMetric(parts[2]),
            comparator = Comparator.valueOf(parts[3]),
            target = target,
            tolerancePercent = parts[5].toInt(),
            weight = parts[6].toInt(),
            mode = EvaluationMode.valueOf(parts[7]),
            enabled = parts[8] == "1",
        )
    }.getOrNull()

    /** The old directional signal was platform-derived; v2 uses the exact home-arrival rule. */
    private fun migrateMetric(name: String): Metric = when (name) {
        "IS_TOWARD_DESTINATION" -> Metric.ENDS_NEAR_HOME
        else -> Metric.valueOf(name)
    }

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(UTF_8))

    private fun decodeText(value: String): String = String(Base64.getUrlDecoder().decode(value), UTF_8)

    private data class ProfileBuilder(
        val id: String,
        val name: String,
        val isEnabled: Boolean,
        val createdAtEpochMs: Long,
        val updatedAtEpochMs: Long,
    )
}
