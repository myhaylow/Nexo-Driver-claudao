package br.com.nexo.driver.destination.offline

import br.com.nexo.driver.destination.DestinationDirectionEvaluator
import br.com.nexo.driver.destination.DirectionEvaluationInput
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.GeoCoordinate
import br.com.nexo.driver.destination.HomeMatchStatus
import br.com.nexo.driver.destination.HomeMatcher
import br.com.nexo.driver.destination.OfferDestination
import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.GeoText
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferField

/**
 * Adds a deterministic offline home-arrival result to an offer.
 *
 * A platform badge such as Uber's "em dire\u00e7\u00e3o ao seu destino" is deliberately never used here:
 * the app's destination is independent from the platform destination quota. The platform's
 * [NormalizedOffer.destinationDirectionHint] is retained as informative data; it never drives
 * the `ends near home` filter. A Boolean is emitted only from an exact, unambiguous drop-off
 * match in the active offline package. Pickup and device GPS are deliberately never consulted.
 */
class DestinationOfferEnricher(
    private val addressResolver: OfflineAddressResolver?,
    private val driverDestination: DriverDestination?,
    private val matcher: HomeMatcher = HomeMatcher(),
    private val directionEvaluator: DestinationDirectionEvaluator = DestinationDirectionEvaluator(),
) {
    fun enrich(offer: NormalizedOffer): NormalizedOffer {
        val match = matcher.match(driverDestination, offer.trip.location.resolveOfferDestination())
        val endsNearHome = when (match.status) {
            HomeMatchStatus.ENDS_NEAR_HOME -> knownHomeMatch(true)
            HomeMatchStatus.ENDS_AWAY_FROM_HOME -> knownHomeMatch(false)
            HomeMatchStatus.UNKNOWN -> unknownHomeMatch()
        }
        val headingTowardHome = evaluateHeadingTowardHome(offer)

        return offer.copy(
            endsNearHome = endsNearHome,
            headingTowardHome = headingTowardHome,
            fieldConfidence = offer.fieldConfidence +
                (OfferField.ENDS_NEAR_HOME to endsNearHome.score),
        )
    }

    /**
     * Complements [HomeMatcher] with a directional signal: does the offered trip meaningfully move
     * toward the driver's home, even when it does not end inside the home radius? Unlike the
     * ends-near-home rule, this deliberately consults the pickup as the trip origin so the "sentido
     * casa" theme can also light up for a ride that is simply heading the right way.
     */
    private fun evaluateHeadingTowardHome(offer: NormalizedOffer): Confidence<Boolean> {
        val result = directionEvaluator.evaluate(
            DirectionEvaluationInput(
                currentPosition = null,
                pickupPosition = offer.pickup.location.value?.coordinate?.takeIf(GeoCoordinate::isValid),
                dropoffPosition = offer.trip.location.value?.coordinate?.takeIf(GeoCoordinate::isValid),
                destination = driverDestination,
            ),
        )
        return when (result.status) {
            br.com.nexo.driver.destination.DestinationDirectionStatus.TOWARDS_DESTINATION -> knownHomeMatch(true)
            br.com.nexo.driver.destination.DestinationDirectionStatus.NEUTRAL,
            br.com.nexo.driver.destination.DestinationDirectionStatus.AWAY_FROM_DESTINATION,
            -> knownHomeMatch(false)
            br.com.nexo.driver.destination.DestinationDirectionStatus.UNKNOWN -> unknownHomeMatch()
        }
    }

    private fun Confidence<GeoText>.resolveOfferDestination(): OfferDestination? = value?.let { text ->
        text.coordinate?.takeIf(GeoCoordinate::isValid)?.let { coordinate ->
            OfferDestination(
                coordinate = coordinate,
                originalAddress = text.address,
                standardizedAddress = text.address,
                resolutionStatus = br.com.nexo.driver.destination.DestinationResolutionStatus.RESOLVED,
            )
        } ?: text.address?.let { address ->
            addressResolver?.resolve(address)?.place?.let { place ->
                OfferDestination(
                    coordinate = place.coordinate,
                    originalAddress = address,
                    standardizedAddress = place.label,
                    resolutionStatus = br.com.nexo.driver.destination.DestinationResolutionStatus.RESOLVED,
                )
            } ?: OfferDestination(
                originalAddress = address,
                standardizedAddress = address,
                resolutionStatus = br.com.nexo.driver.destination.DestinationResolutionStatus.UNAVAILABLE,
            )
        }
    }

    private fun unknownHomeMatch() = Confidence<Boolean>(
        value = null,
        score = 0f,
        source = FieldSource.DERIVED,
    )

    private fun knownHomeMatch(value: Boolean) = Confidence(
        value = value,
        score = DERIVED_HOME_MATCH_CONFIDENCE,
        source = FieldSource.DERIVED,
    )

    private companion object {
        // Exact package lookup plus deterministic geometry makes this a high-confidence result.
        const val DERIVED_HOME_MATCH_CONFIDENCE = 1f
    }
}
