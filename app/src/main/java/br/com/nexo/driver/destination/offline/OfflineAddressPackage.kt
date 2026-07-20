package br.com.nexo.driver.destination.offline

import br.com.nexo.driver.destination.GeoCoordinate

/**
 * Metadata that travels with a locally downloaded address package. It deliberately contains no
 * provider URL or network contract: importing/downloading the bytes is an Android/UI concern.
 */
data class OfflineAddressPackageMetadata(
    val name: String,
    val version: String,
    val city: String,
)

/** One known address, landmark, or map feature in an offline city package. */
data class OfflineAddressPlace(
    val id: String,
    val label: String,
    val coordinate: GeoCoordinate,
    val aliases: Set<String> = emptySet(),
)

/** A self-contained, network-free set of locations for one city. */
data class OfflineAddressPackage(
    val metadata: OfflineAddressPackageMetadata,
    val places: List<OfflineAddressPlace>,
) {
    fun validate(): OfflineAddressPackageValidation = OfflineAddressPackageValidator.validate(this)
}

enum class OfflinePackageIssueSeverity { ERROR, WARNING }

enum class OfflinePackageIssueCode {
    BLANK_PACKAGE_NAME,
    INVALID_VERSION,
    BLANK_CITY,
    EMPTY_PLACES,
    BLANK_PLACE_ID,
    DUPLICATE_PLACE_ID,
    BLANK_PLACE_LABEL,
    INVALID_COORDINATE,
    BLANK_ALIAS,
    AMBIGUOUS_ADDRESS_KEY,
}

data class OfflinePackageIssue(
    val severity: OfflinePackageIssueSeverity,
    val code: OfflinePackageIssueCode,
    val placeIds: Set<String> = emptySet(),
)

data class OfflineAddressPackageValidation(
    val issues: List<OfflinePackageIssue>,
) {
    val isValid: Boolean
        get() = issues.none { it.severity == OfflinePackageIssueSeverity.ERROR }

    val errors: List<OfflinePackageIssue>
        get() = issues.filter { it.severity == OfflinePackageIssueSeverity.ERROR }

    val warnings: List<OfflinePackageIssue>
        get() = issues.filter { it.severity == OfflinePackageIssueSeverity.WARNING }
}

/**
 * Validates data without changing it, so an importer can show precise errors before replacing an
 * existing offline package. Colliding aliases are warnings: they are safe because the resolver
 * returns null for them, but package authors should fix them for better coverage.
 */
object OfflineAddressPackageValidator {
    private val semanticVersion = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$")

    fun validate(addressPackage: OfflineAddressPackage): OfflineAddressPackageValidation {
        val issues = mutableListOf<OfflinePackageIssue>()
        val metadata = addressPackage.metadata
        if (metadata.name.isBlank()) issues += error(OfflinePackageIssueCode.BLANK_PACKAGE_NAME)
        if (!semanticVersion.matches(metadata.version.trim())) issues += error(OfflinePackageIssueCode.INVALID_VERSION)
        if (metadata.city.isBlank()) issues += error(OfflinePackageIssueCode.BLANK_CITY)
        if (addressPackage.places.isEmpty()) issues += error(OfflinePackageIssueCode.EMPTY_PLACES)

        val ids = mutableMapOf<String, MutableList<String>>()
        val addressKeys = mutableMapOf<String, MutableSet<String>>()
        addressPackage.places.forEach { place ->
            val displayId = place.id.trim()
            if (displayId.isEmpty()) {
                issues += error(OfflinePackageIssueCode.BLANK_PLACE_ID)
            } else {
                ids.getOrPut(displayId.lowercase()) { mutableListOf() }.add(displayId)
            }
            if (place.label.isBlank()) issues += error(OfflinePackageIssueCode.BLANK_PLACE_LABEL, setOf(displayId))
            if (!place.coordinate.isValid) issues += error(OfflinePackageIssueCode.INVALID_COORDINATE, setOf(displayId))

            val rawNames = listOf(place.label) + place.aliases
            place.aliases.filter { it.isBlank() }.forEach {
                issues += error(OfflinePackageIssueCode.BLANK_ALIAS, setOf(displayId))
            }
            rawNames.asSequence()
                .map(OfflineAddressNormalizer::normalize)
                .filter { it.isNotEmpty() }
                .forEach { key -> addressKeys.getOrPut(key) { linkedSetOf() }.add(displayId) }
        }

        ids.values.filter { it.size > 1 }.forEach { duplicateIds ->
            issues += error(OfflinePackageIssueCode.DUPLICATE_PLACE_ID, duplicateIds.toSet())
        }
        addressKeys.values.filter { it.size > 1 }.forEach { conflictingIds ->
            issues += OfflinePackageIssue(
                severity = OfflinePackageIssueSeverity.WARNING,
                code = OfflinePackageIssueCode.AMBIGUOUS_ADDRESS_KEY,
                placeIds = conflictingIds,
            )
        }
        return OfflineAddressPackageValidation(issues)
    }

    private fun error(code: OfflinePackageIssueCode, ids: Set<String> = emptySet()) =
        OfflinePackageIssue(OfflinePackageIssueSeverity.ERROR, code, ids)
}
