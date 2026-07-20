package br.com.nexo.driver.destination.offline

import java.text.Normalizer
import java.util.Locale

/**
 * Common OCR and Portuguese street-form variations which can be canonicalised without fuzzy
 * matching. Everything else must match exactly after normalisation.
 */
object OfflineAddressNormalizer {
    private val leadingStreetAbbreviations = mapOf(
        "r" to "rua",
        "av" to "avenida",
        "rod" to "rodovia",
        "estr" to "estrada",
        "trav" to "travessa",
    )

    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutMarks = decomposed.filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        val rawTokens = withoutMarks
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
        val tokens = rawTokens.filterIndexed { index, token ->
            // "nº 12" becomes "n 12" after punctuation removal. It is a number marker, not
            // part of the street name. Keep a standalone n in every other context.
            !(token == "n" && rawTokens.getOrNull(index + 1)?.all(Char::isDigit) == true)
        }.toMutableList()
        if (tokens.isNotEmpty()) tokens[0] = leadingStreetAbbreviations[tokens[0]] ?: tokens[0]
        return tokens.joinToString(" ")
    }
}

data class OfflineAddressResolution(
    val place: OfflineAddressPlace,
    /** The canonical query key which identified this place; useful only for local diagnostics. */
    val normalizedQuery: String,
)

/**
 * Exact offline address resolver. It has no approximation or distance fallback: a query with
 * zero matches or multiple distinct places resolves to null. That conservative behaviour is
 * essential when the result affects an offer's "direction to home" colour.
 */
class OfflineAddressResolver private constructor(
    private val addressPackage: OfflineAddressPackage,
    private val placesByNormalizedAddress: Map<String, List<OfflineAddressPlace>>,
) {
    fun resolve(query: String?): OfflineAddressResolution? {
        val normalized = query?.let(OfflineAddressNormalizer::normalize).orEmpty()
        if (normalized.isEmpty()) return null
        val matches = placesByNormalizedAddress[normalized].orEmpty()
            .distinctBy { it.id.trim().lowercase(Locale.ROOT) }
        return matches.singleOrNull()?.let { OfflineAddressResolution(it, normalized) }
    }

    fun packageMetadata(): OfflineAddressPackageMetadata = addressPackage.metadata

    companion object {
        /**
         * A malformed package cannot be used. Warnings (such as a duplicate alias) are allowed,
         * because the lookup remains safe: an ambiguous key is never returned as a location.
         */
        fun create(addressPackage: OfflineAddressPackage): OfflineAddressResolver? {
            if (!addressPackage.validate().isValid) return null
            val byKey = linkedMapOf<String, MutableList<OfflineAddressPlace>>()
            addressPackage.places.forEach { place ->
                (listOf(place.label) + place.aliases).forEach { name ->
                    OfflineAddressNormalizer.normalize(name)
                        .takeIf { it.isNotEmpty() }
                        ?.let { key -> byKey.getOrPut(key) { mutableListOf() }.add(place) }
                }
            }
            return OfflineAddressResolver(addressPackage, byKey)
        }
    }
}
