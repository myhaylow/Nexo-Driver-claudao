package br.com.nexo.driver.destination

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A geographic coordinate expressed in WGS-84 decimal degrees. */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * The driver's selected home destination. The coordinates come from an offline geocoder/map;
 * this module never calls a map, a network API, or the device GPS.
 */
data class HomeDestination(
    val coordinate: GeoCoordinate? = null,
    val label: String? = null,
    val arrivalRadiusMeters: Double = DEFAULT_HOME_RADIUS_METERS,
    val standardizedAddress: String? = null,
    val preparedAtEpochMs: Long? = null,
    val resolutionStatus: DestinationResolutionStatus = DestinationResolutionStatus.RESOLVED,
    /** A disabled destination is retained for editing but must never influence a decision. */
    val enabled: Boolean = coordinate != null,
    /** Exact text supplied by the driver, preserved independently from a geocoder's normalization. */
    val originalAddress: String? = null,
) {
    val hasTrustedCoordinate: Boolean
        get() = enabled && resolutionStatus == DestinationResolutionStatus.RESOLVED && coordinate?.isValid == true

    companion object {
        const val MIN_HOME_RADIUS_METERS = 200.0
        const val DEFAULT_HOME_RADIUS_METERS = 2_000.0
        const val MAX_HOME_RADIUS_METERS = 20_000.0
    }
}

/** Source-compatible name used by the first destination-home implementation. */
typealias DriverDestination = HomeDestination

enum class DestinationResolutionStatus {
    RESOLVED,
    FAILED,
    UNAVAILABLE,
}

/**
 * Coordinates available for an offered trip. The pickup is preferred as the trip origin because
 * it represents the point where the passenger journey starts; the current position is a fallback.
 */
data class DirectionEvaluationInput(
    val currentPosition: GeoCoordinate?,
    val pickupPosition: GeoCoordinate?,
    val dropoffPosition: GeoCoordinate?,
    val destination: DriverDestination?,
)

enum class DirectionOrigin {
    PICKUP,
    CURRENT_POSITION,
}

enum class DestinationDirectionStatus {
    /** The trip endpoint meaningfully reduces the remaining geodesic distance. */
    TOWARDS_DESTINATION,

    /** The endpoint is effectively lateral to the configured destination. */
    NEUTRAL,

    /** The endpoint meaningfully increases the remaining geodesic distance. */
    AWAY_FROM_DESTINATION,

    /** A reliable calculation cannot be made from the data available. */
    UNKNOWN,
}

/**
 * Guardrails against GPS/geocoding noise. The default requires both 500 m and 10% progress to
 * label a trip as toward the destination. The dead band avoids treating a few inaccurate meters
 * as a directional signal.
 */
data class DestinationDirectionConfig(
    val minimumReductionMeters: Double = 500.0,
    val minimumReductionRatio: Double = 0.10,
    val neutralToleranceMeters: Double = 150.0,
) {
    init {
        require(minimumReductionMeters >= 0.0) { "minimumReductionMeters must be non-negative" }
        require(minimumReductionRatio in 0.0..1.0) { "minimumReductionRatio must be between 0 and 1" }
        require(neutralToleranceMeters >= 0.0) { "neutralToleranceMeters must be non-negative" }
    }
}

data class DestinationDirectionResult(
    val status: DestinationDirectionStatus,
    val origin: DirectionOrigin? = null,
    val startDistanceMeters: Double? = null,
    val endDistanceMeters: Double? = null,
    /** Positive values indicate that the trip ends closer to the driver's destination. */
    val distanceReductionMeters: Double? = null,
    val distanceReductionRatio: Double? = null,
) {
    val isTowardsDestination: Boolean
        get() = status == DestinationDirectionStatus.TOWARDS_DESTINATION
}

/**
 * Pure, deterministic direction classifier.
 *
 * It compares the great-circle (Haversine) distance from the trip origin to the driver's chosen
 * destination with the distance from the offered drop-off to that destination. Great-circle
 * distance is deliberately used instead of straight projected-map distance: it is stable offline,
 * works across map providers, and makes the rule explainable. It is an approximation of driving
 * distance, so the configurable absolute/relative thresholds prevent fragile decisions.
 */
class DestinationDirectionEvaluator(
    private val config: DestinationDirectionConfig = DestinationDirectionConfig(),
) {
    fun evaluate(input: DirectionEvaluationInput): DestinationDirectionResult {
        val destination = input.destination ?: return unknown()
        val dropoff = input.dropoffPosition ?: return unknown()
        val origin = when {
            input.pickupPosition?.isValid == true -> DirectionOrigin.PICKUP to input.pickupPosition
            input.currentPosition?.isValid == true -> DirectionOrigin.CURRENT_POSITION to input.currentPosition
            else -> return unknown()
        }

        val destinationCoordinate = destination.coordinate
        if (!destination.hasTrustedCoordinate || destinationCoordinate == null || !dropoff.isValid ||
            destination.arrivalRadiusMeters < 0.0 || !destination.arrivalRadiusMeters.isFinite()
        ) {
            return unknown()
        }

        val start = distanceMeters(origin.second!!, destinationCoordinate)
        val end = distanceMeters(dropoff, destinationCoordinate)
        val reduction = start - end
        val ratio = if (start == 0.0) 0.0 else reduction / start

        val status = when {
            // Entering the configured arrival area is always helpful, including when already close.
            end <= destination.arrivalRadiusMeters -> DestinationDirectionStatus.TOWARDS_DESTINATION
            reduction <= -config.neutralToleranceMeters -> DestinationDirectionStatus.AWAY_FROM_DESTINATION
            reduction >= config.minimumReductionMeters && ratio >= config.minimumReductionRatio ->
                DestinationDirectionStatus.TOWARDS_DESTINATION
            else -> DestinationDirectionStatus.NEUTRAL
        }

        return DestinationDirectionResult(
            status = status,
            origin = origin.first,
            startDistanceMeters = start,
            endDistanceMeters = end,
            distanceReductionMeters = reduction,
            distanceReductionRatio = ratio,
        )
    }

    private fun unknown() = DestinationDirectionResult(DestinationDirectionStatus.UNKNOWN)

    /** Haversine great-circle distance in meters using the IUGG mean Earth radius. */
    private fun distanceMeters(from: GeoCoordinate, to: GeoCoordinate): Double {
        val latitudeDelta = (to.latitude - from.latitude).toRadians()
        val longitudeDelta = (to.longitude - from.longitude).toRadians()
        val fromLatitude = from.latitude.toRadians()
        val toLatitude = to.latitude.toRadians()
        val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(fromLatitude) * cos(toLatitude) * sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
        return EARTH_MEAN_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private companion object {
        const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8
    }
}
