package br.com.nexo.driver.destination

/**
 * Local persistence contract for the optional "destination home" feature.
 *
 * The stored coordinate is intentionally only a driver-selected destination; no offer, route, or
 * location-history data belongs in this store. Callers receive an immutable value on every read.
 */
interface HomeDestinationStore {
    fun load(): HomeDestination?

    /** Replaces the current destination after validating the coordinate and arrival radius. */
    fun save(destination: HomeDestination): HomeDestination

    /** Removes the destination completely. */
    fun clear()
}

/** Useful for previews and deterministic unit tests without Android framework dependencies. */
class InMemoryDriverDestinationStore(
    initialDestination: HomeDestination? = null,
) : HomeDestinationStore {
    private var destination = initialDestination?.validatedOrNull()

    @Synchronized
    override fun load(): HomeDestination? = destination

    @Synchronized
    override fun save(destination: HomeDestination): HomeDestination {
        val validated = requireNotNull(destination.validatedOrNull()) {
            "A destination must have valid coordinates and a non-negative finite arrival radius."
        }
        this.destination = validated
        return validated
    }

    @Synchronized
    override fun clear() {
        destination = null
    }
}

/** Normalizes optional presentation text and rejects values unsafe for distance calculations. */
/** Source-compatible store name retained while callers migrate to [HomeDestinationStore]. */
typealias DriverDestinationStore = HomeDestinationStore

internal fun HomeDestination.validatedOrNull(): HomeDestination? {
    if (!arrivalRadiusMeters.isFinite() ||
        arrivalRadiusMeters !in HomeDestination.MIN_HOME_RADIUS_METERS..HomeDestination.MAX_HOME_RADIUS_METERS ||
        coordinate?.isValid == false
    ) {
        return null
    }
    if (enabled && resolutionStatus == DestinationResolutionStatus.RESOLVED && coordinate == null) return null
    // A failed/unavailable geocode must never leave a stale coordinate available for matching.
    val safeCoordinate = coordinate.takeIf { resolutionStatus == DestinationResolutionStatus.RESOLVED }
    return copy(
        coordinate = safeCoordinate,
        label = label?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LABEL_LENGTH),
        originalAddress = originalAddress?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_ADDRESS_LENGTH),
        standardizedAddress = standardizedAddress?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_ADDRESS_LENGTH),
    )
}

private const val MAX_LABEL_LENGTH = 240
private const val MAX_ADDRESS_LENGTH = 500
