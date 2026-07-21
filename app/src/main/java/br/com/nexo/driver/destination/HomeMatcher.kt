package br.com.nexo.driver.destination

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A resolved end point of an offered trip. It deliberately represents the drop-off only: a
 * pickup location and the driver's live GPS position are not inputs to a home match.
 */
data class OfferDestination(
    val coordinate: GeoCoordinate? = null,
    val originalAddress: String? = null,
    val standardizedAddress: String? = null,
    val resolutionStatus: DestinationResolutionStatus = DestinationResolutionStatus.UNAVAILABLE,
) {
    val hasTrustedCoordinate: Boolean
        get() = resolutionStatus == DestinationResolutionStatus.RESOLVED && coordinate?.isValid == true
}

enum class HomeMatchStatus {
    ENDS_NEAR_HOME,
    ENDS_AWAY_FROM_HOME,
    UNKNOWN,
}

enum class HomeMatchMethod { GEO_DISTANCE, EXACT_ADDRESS_TEXT, NONE }

enum class HomeMatchReason {
    INSIDE_HOME_RADIUS,
    OUTSIDE_HOME_RADIUS,
    EXACT_ADDRESS_MATCH,
    HOME_DISABLED,
    HOME_UNRESOLVED,
    OFFER_DESTINATION_UNRESOLVED,
}

/** Immutable, explainable result for the `ends near home` rule. */
data class HomeMatchResult(
    val status: HomeMatchStatus,
    val method: HomeMatchMethod,
    val reason: HomeMatchReason,
    val distanceToHomeMeters: Double? = null,
    val homeRadiusMeters: Double? = null,
) {
    val endsNearHome: Boolean?
        get() = when (status) {
            HomeMatchStatus.ENDS_NEAR_HOME -> true
            HomeMatchStatus.ENDS_AWAY_FROM_HOME -> false
            HomeMatchStatus.UNKNOWN -> null
        }
}

/**
 * Matches only an offered drop-off against the driver's selected arrival radius. This is a
 * deterministic offline calculation and intentionally has no dependency on pickup or GPS data.
 */
class HomeMatcher {
    fun match(
        home: HomeDestination?,
        offerDestination: OfferDestination?,
    ): HomeMatchResult {
        if (home == null || !home.enabled) {
            return unknown(HomeMatchReason.HOME_DISABLED)
        }
        if (offerDestination == null) {
            return unknown(HomeMatchReason.OFFER_DESTINATION_UNRESOLVED)
        }

        // Trusted coordinates always win. Matching text must never override a measured endpoint
        // outside the configured radius.
        if (home.hasTrustedCoordinate && offerDestination.hasTrustedCoordinate) {
            val distance = distanceMeters(requireNotNull(offerDestination.coordinate), requireNotNull(home.coordinate))
            return HomeMatchResult(
                status = if (distance <= home.arrivalRadiusMeters) {
                    HomeMatchStatus.ENDS_NEAR_HOME
                } else {
                    HomeMatchStatus.ENDS_AWAY_FROM_HOME
                },
                method = HomeMatchMethod.GEO_DISTANCE,
                reason = if (distance <= home.arrivalRadiusMeters) {
                    HomeMatchReason.INSIDE_HOME_RADIUS
                } else {
                    HomeMatchReason.OUTSIDE_HOME_RADIUS
                },
                distanceToHomeMeters = distance,
                homeRadiusMeters = home.arrivalRadiusMeters,
            )
        }

        // Text is a conservative fallback only when one side has no trusted coordinate.
        if (addressesMatch(home, offerDestination)) {
            return HomeMatchResult(
                HomeMatchStatus.ENDS_NEAR_HOME,
                HomeMatchMethod.EXACT_ADDRESS_TEXT,
                HomeMatchReason.EXACT_ADDRESS_MATCH,
            )
        }
        if (!home.hasTrustedCoordinate) return unknown(HomeMatchReason.HOME_UNRESOLVED)
        return unknown(HomeMatchReason.OFFER_DESTINATION_UNRESOLVED)
    }

    private fun unknown(reason: HomeMatchReason) = HomeMatchResult(
        status = HomeMatchStatus.UNKNOWN,
        method = HomeMatchMethod.NONE,
        reason = reason,
    )

    private fun addressesMatch(home: HomeDestination, offer: OfferDestination): Boolean {
        val homeAddresses = listOfNotNull(home.originalAddress, home.standardizedAddress)
            .map(::addressSignature)
            .filter(AddressSignature::isSpecific)
        val offerAddresses = listOfNotNull(offer.originalAddress, offer.standardizedAddress)
            .map(::addressSignature)
            .filter(AddressSignature::isSpecific)
        return homeAddresses.any { homeAddress ->
            offerAddresses.any { offerAddress -> homeAddress.matches(offerAddress) }
        }
    }

    private fun addressSignature(value: String): AddressSignature {
        val normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        val rawTokens = normalized.split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
        // "..., São José dos Pinhais - PR, Brasil" no fim do endereço é localidade, não casa.
        // Sem essa remoção, os tokens da cidade ("sao", "jose", "pinhais") pontuam como se
        // fossem a rua e uma oferta para o centro da cidade casaria com uma casa qualquer.
        val tokens = CuritibaRegionLocalities.stripTrailingLocalities(rawTokens).ifEmpty { rawTokens }
        val hasStreetDesignator = tokens.any { it in ADDRESS_STRUCTURE_TOKENS }
        val relevant = tokens.filter { token ->
            token.length >= 2 && token !in ADDRESS_STRUCTURE_TOKENS && token !in LOCALITY_ONLY_TOKENS
        }.toSet()
        return AddressSignature(
            normalized = tokens.joinToString(separator = ""),
            relevantTokens = relevant,
            hasNumber = tokens.any { it.all(Char::isDigit) },
            hasStreetDesignator = hasStreetDesignator,
        )
    }

    private data class AddressSignature(
        val normalized: String,
        val relevantTokens: Set<String>,
        val hasNumber: Boolean,
        val hasStreetDesignator: Boolean,
    ) {
        val isSpecific: Boolean
            get() = normalized.length >= MIN_ADDRESS_NORMALIZED_LENGTH &&
                // A locality with several words (e.g. "São José dos Pinhais") must never
                // become a home address simply because it has two tokens. A number, or a real
                // street designator plus a non-locality token, is required before matching.
                (hasNumber || (hasStreetDesignator && relevantTokens.isNotEmpty()))

        fun matches(other: AddressSignature): Boolean {
            // Full normalized inclusion handles a complete address paired with its shortened
            // provider variant. The specificity gate prevents city/state-only false matches.
            if (normalized.contains(other.normalized) || other.normalized.contains(normalized)) return true
            val common = relevantTokens.intersect(other.relevantTokens).size
            if (common >= MIN_RELEVANT_TOKENS) return true
            val denominator = maxOf(relevantTokens.size, other.relevantTokens.size)
            return denominator > 0 && common.toDouble() / denominator >= MIN_TOKEN_SIMILARITY
        }
    }

    private fun distanceMeters(from: GeoCoordinate, to: GeoCoordinate): Double {
        val latitudeDelta = (to.latitude - from.latitude).toRadians()
        val longitudeDelta = (to.longitude - from.longitude).toRadians()
        val fromLatitude = from.latitude.toRadians()
        val toLatitude = to.latitude.toRadians()
        val a = (sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(fromLatitude) * cos(toLatitude) * sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
            ).coerceIn(0.0, 1.0)
        return EARTH_MEAN_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private companion object {
        const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8
        const val MIN_ADDRESS_NORMALIZED_LENGTH = 6
        const val MIN_RELEVANT_TOKENS = 2
        const val MIN_TOKEN_SIMILARITY = 0.70
        val ADDRESS_STRUCTURE_TOKENS = setOf(
            "rua", "r", "avenida", "av", "travessa", "tv", "estrada", "rodovia", "alameda",
            "praca", "largo", "bairro", "numero", "n", "s", "sn",
        )
        // City/state/country on their own never identify a home address. This list covers the
        // locale supported by the app and intentionally leaves street/neighbourhood tokens intact.
        val LOCALITY_ONLY_TOKENS = setOf(
            "brasil", "brazil", "pr", "parana", "curitiba", "sp", "saopaulo", "rj", "riodejaneiro",
            "sc", "santacatarina", "rs", "riograndedosul", "mg", "minasgerais", "go", "goias",
            "ba", "bahia", "pe", "pernambuco", "ce", "ceara", "df", "distritofederal",
        )
    }
}
