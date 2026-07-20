package br.com.nexo.driver.offer

enum class OfferSource { UBER, NINETY_NINE }

enum class OfferKind { UBER_STANDARD, NINETY_NINE_STANDARD, NINETY_NINE_NEGOCIA }

enum class FieldSource { OCR, ACCESSIBILITY, DERIVED }

enum class OfferField {
    PAYOUT,
    PICKUP_DISTANCE,
    PICKUP_DURATION,
    TRIP_DISTANCE,
    TRIP_DURATION,
    PASSENGER_RATING,
    STOP_COUNT,
    LONG_TRIP,
    DESTINATION_DIRECTION,
    ENDS_NEAR_HOME,
}

data class Confidence<T>(
    val value: T?,
    val score: Float,
    val source: FieldSource,
) {
    init {
        require(score in 0f..1f) { "Confidence must be between 0 and 1." }
    }

    fun isUsable(minimum: Float) = value != null && score >= minimum
}

@JvmInline
value class Money(val cents: Long) {
    init {
        require(cents >= 0) { "Money cannot be negative: $cents" }
    }
}

@JvmInline
value class Distance(val meters: Long) {
    init {
        require(meters >= 0) { "Distance cannot be negative: $meters" }
    }
}

@JvmInline
value class Duration(val seconds: Long) {
    init {
        require(seconds >= 0) { "Duration cannot be negative: $seconds" }
    }
}

data class GeoText(
    val address: String?,
    val locality: String?,
    /** Filled by the optional asynchronous Geocoder enrichment; never contains map tiles. */
    val coordinate: br.com.nexo.driver.destination.GeoCoordinate? = null,
)

data class OfferLeg(
    val duration: Confidence<Duration>,
    val distance: Confidence<Distance>,
    val location: Confidence<GeoText>,
)

data class Passenger(
    /** Rating scaled by 100, e.g. 4.95 is stored as 495. */
    val rating: Confidence<Long>,
    val tripCount: Confidence<Long>,
    val profile: Confidence<String>,
)

data class LayoutMetadata(
    val hasVerificationBadge: Boolean? = null,
    val hasDynamicFare: Boolean? = null,
    val hasLongWaitBonus: Boolean? = null,
    val negotiationAlternatives: List<Money> = emptyList(),
    val isTripRadar: Boolean? = null,
)

data class NormalizedOffer(
    val source: OfferSource,
    val kind: OfferKind,
    val detectedAtEpochMs: Long,
    val payout: Confidence<Money>,
    val displayedRatePerKm: Confidence<Money>,
    val bonus: Confidence<Money>,
    val pickup: OfferLeg,
    val trip: OfferLeg,
    val passenger: Passenger,
    val serviceType: Confidence<String>,
    val stopCount: Confidence<Long>,
    val longTripHint: Confidence<Boolean>,
    /** Exact offline match of the offered drop-off to the driver's selected home radius. */
    val endsNearHome: Confidence<Boolean> = Confidence(null, 0f, FieldSource.DERIVED),
    /**
     * Our own offline geometry result: the trip meaningfully moves toward the driver's home even if
     * it does not end inside the home radius. Combined with [endsNearHome] to drive the "sentido
     * casa" card theme. Distinct from [destinationDirectionHint], which is the platform's own badge.
     */
    val headingTowardHome: Confidence<Boolean> = Confidence(null, 0f, FieldSource.DERIVED),
    /** Informative platform/direction signal. It is intentionally distinct from [endsNearHome]. */
    val destinationDirectionHint: Confidence<Boolean>,
    /**
     * True when the driver's blocklist matched this offer's pickup. It is carried on the offer so
     * the evaluator can reject it as a normal eliminatory rule, keeping the verdict explainable
     * and testable alongside every other rule instead of being patched onto the overlay after the
     * fact.
     */
    val pickupIsBlocked: Confidence<Boolean> = Confidence(null, 0f, FieldSource.DERIVED),
    val rawLayoutVersion: String,
    val fieldConfidence: Map<OfferField, Float>,
    val metadata: LayoutMetadata = LayoutMetadata(),
)

data class DerivedMetrics(
    val totalDistance: Confidence<Distance>,
    val totalDuration: Confidence<Duration>,
    /** BRL cents per kilometre. */
    val ratePerKm: Confidence<Long>,
    /** BRL cents per hour. */
    val ratePerHour: Confidence<Long>,
    /** BRL cents per minute. */
    val ratePerMinute: Confidence<Long>,
)
