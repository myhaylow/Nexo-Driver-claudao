package br.com.nexo.driver.location

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS-84 point kept only in memory by the optional foreground location service. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class LocationFix(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val capturedAtEpochMs: Long,
    val provider: String,
    /** Monotonic Android elapsed-realtime clock; movement calculations must use this, not epoch. */
    val elapsedRealtimeNanos: Long,
    val speedMps: Double? = null,
    val bearingDegrees: Double? = null,
    val isLastKnown: Boolean = false,
) {
    val isStructurallyValid: Boolean
        get() = point.isValid && accuracyMeters.isFinite() && accuracyMeters >= 0.0 &&
            capturedAtEpochMs > 0L && elapsedRealtimeNanos > 0L && provider.isNotBlank() &&
            (speedMps == null || speedMps.isFinite() && speedMps >= 0.0) &&
            (bearingDegrees == null || bearingDegrees.isFinite() && bearingDegrees in 0.0..360.0)
}

data class LocationFixPolicy(
    val maxAgeMs: Long = 90_000L,
    val maxFutureSkewMs: Long = 5_000L,
    val maxAccuracyMeters: Double = 60.0,
) {
    init {
        require(maxAgeMs >= 0L)
        require(maxFutureSkewMs >= 0L)
        require(maxAccuracyMeters.isFinite() && maxAccuracyMeters >= 0.0)
    }
}

enum class LocationFixRejection {
    INVALID,
    STALE,
    FUTURE,
    INACCURATE,
}

/** Pure validation rule shared by last-known selection and live updates. */
class LocationFixValidator(
    private val policy: LocationFixPolicy = LocationFixPolicy(),
) {
    fun rejectionFor(fix: LocationFix, nowEpochMs: Long): LocationFixRejection? = when {
        !fix.isStructurallyValid -> LocationFixRejection.INVALID
        fix.capturedAtEpochMs > nowEpochMs + policy.maxFutureSkewMs -> LocationFixRejection.FUTURE
        nowEpochMs - fix.capturedAtEpochMs > policy.maxAgeMs -> LocationFixRejection.STALE
        fix.accuracyMeters > policy.maxAccuracyMeters -> LocationFixRejection.INACCURATE
        else -> null
    }

    fun isUsable(fix: LocationFix, nowEpochMs: Long): Boolean = rejectionFor(fix, nowEpochMs) == null
}

/**
 * Chooses last-known location by a deliberate stability rule: a point more than 10 seconds newer
 * wins; otherwise the most accurate wins (then newest for an exact accuracy tie).
 */
class LastKnownLocationSelector(
    private val validator: LocationFixValidator = LocationFixValidator(),
) {
    fun select(candidates: Iterable<LocationFix>, nowEpochMs: Long): LocationFix? = candidates
        .filter { validator.isUsable(it, nowEpochMs) }
        .fold<LocationFix, LocationFix?>(null) { selected, candidate ->
            when {
                selected == null -> candidate
                candidate.capturedAtEpochMs > selected.capturedAtEpochMs + NEWER_FIX_WINDOW_MS -> candidate
                selected.capturedAtEpochMs > candidate.capturedAtEpochMs + NEWER_FIX_WINDOW_MS -> selected
                candidate.accuracyMeters < selected.accuracyMeters -> candidate
                candidate.accuracyMeters > selected.accuracyMeters -> selected
                candidate.capturedAtEpochMs > selected.capturedAtEpochMs -> candidate
                else -> selected
            }
        }

    private companion object {
        const val NEWER_FIX_WINDOW_MS = 10_000L
    }
}

sealed interface CurrentLocationState {
    data object Idle : CurrentLocationState
    data object Acquiring : CurrentLocationState
    data object PermissionMissing : CurrentLocationState
    data object ProviderUnavailable : CurrentLocationState
    data class Available(val fix: LocationFix, val sessionDistanceMeters: Double = 0.0) : CurrentLocationState
    data class Rejected(val reason: LocationFixRejection) : CurrentLocationState
    data class MovementRejected(val reason: LocationMovementRejection) : CurrentLocationState
}

enum class LocationMovementRejection {
    NON_MONOTONIC_CLOCK,
    REPORTED_SPEED_OUTLIER,
    CALCULATED_SPEED_OUTLIER,
}

data class LocationMovementUpdate(
    val sessionDistanceMeters: Double,
    val addedDistanceMeters: Double = 0.0,
    val calculatedSpeedMps: Double? = null,
    val ignoredAsJitter: Boolean = false,
    val rejection: LocationMovementRejection? = null,
)

/**
 * Accumulates only live, plausible movement. Last-known locations can seed position but never add
 * to session distance. Segment timing uses elapsedRealtimeNanos exclusively to avoid wall-clock
 * changes and spoofed timestamps.
 */
class LocationMovementAccumulator(
    private val maximumSpeedKmh: Double = MAXIMUM_SPEED_KMH,
) {
    init {
        require(maximumSpeedKmh.isFinite() && maximumSpeedKmh > 0.0)
    }

    private var previousLiveFix: LocationFix? = null
    private var sessionDistanceMeters = 0.0

    fun offer(fix: LocationFix): LocationMovementUpdate {
        if (fix.isLastKnown) {
            previousLiveFix = null
            return snapshot()
        }
        if (fix.speedMps?.times(KMH_PER_MPS)?.let { it > maximumSpeedKmh } == true) {
            return snapshot(rejection = LocationMovementRejection.REPORTED_SPEED_OUTLIER)
        }
        val previous = previousLiveFix
        if (previous == null) {
            previousLiveFix = fix
            return snapshot()
        }
        val elapsedNanos = fix.elapsedRealtimeNanos - previous.elapsedRealtimeNanos
        if (elapsedNanos <= 0L) return snapshot(rejection = LocationMovementRejection.NON_MONOTONIC_CLOCK)
        val distance = haversineMeters(previous.point, fix.point)
        val calculatedSpeed = distance / (elapsedNanos / NANOS_PER_SECOND.toDouble())
        if (calculatedSpeed * KMH_PER_MPS > maximumSpeedKmh) {
            return snapshot(calculatedSpeedMps = calculatedSpeed, rejection = LocationMovementRejection.CALCULATED_SPEED_OUTLIER)
        }
        previousLiveFix = fix
        val jitter = max(MIN_JITTER_METERS, min(MAX_JITTER_METERS, (previous.accuracyMeters + fix.accuracyMeters) * JITTER_ACCURACY_FACTOR))
        if (distance <= jitter) return snapshot(calculatedSpeedMps = calculatedSpeed, ignoredAsJitter = true)
        sessionDistanceMeters += distance
        return snapshot(addedDistanceMeters = distance, calculatedSpeedMps = calculatedSpeed)
    }

    fun clear() {
        previousLiveFix = null
        sessionDistanceMeters = 0.0
    }

    private fun snapshot(
        addedDistanceMeters: Double = 0.0,
        calculatedSpeedMps: Double? = null,
        ignoredAsJitter: Boolean = false,
        rejection: LocationMovementRejection? = null,
    ) = LocationMovementUpdate(sessionDistanceMeters, addedDistanceMeters, calculatedSpeedMps, ignoredAsJitter, rejection)

    private fun haversineMeters(from: GeoPoint, to: GeoPoint): Double {
        val latitudeDelta = (to.latitude - from.latitude) * PI / 180.0
        val longitudeDelta = (to.longitude - from.longitude) * PI / 180.0
        val fromLatitude = from.latitude * PI / 180.0
        val toLatitude = to.latitude * PI / 180.0
        val a = (sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(fromLatitude) * cos(toLatitude) * sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)).coerceIn(0.0, 1.0)
        return EARTH_MEAN_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private companion object {
        const val MAXIMUM_SPEED_KMH = 170.0
        const val KMH_PER_MPS = 3.6
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MIN_JITTER_METERS = 5.0
        const val MAX_JITTER_METERS = 25.0
        const val JITTER_ACCURACY_FACTOR = 0.35
        const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8
    }
}

/**
 * In-memory only accumulator. It never writes coordinates, and never replaces a usable fix with
 * a live fix with an older one. Live location is intentionally most-recent-usable; accuracy is
 * used only to select last-known candidates. Call [current] before using a cached value so data
 * expires.
 */
class LocationFixAccumulator(
    private val validator: LocationFixValidator = LocationFixValidator(),
) {
    private var bestFix: LocationFix? = null

    fun offer(fix: LocationFix, nowEpochMs: Long): CurrentLocationState {
        validator.rejectionFor(fix, nowEpochMs)?.let { return CurrentLocationState.Rejected(it) }
        val existing = bestFix
        if (existing == null || !fix.isLastKnown || (existing.isLastKnown && isBetterLastKnown(fix, existing))) {
            bestFix = fix
        }
        return CurrentLocationState.Available(requireNotNull(bestFix))
    }

    fun seed(fix: LocationFix?, nowEpochMs: Long): CurrentLocationState =
        fix?.let { offer(it, nowEpochMs) } ?: CurrentLocationState.Acquiring

    fun current(nowEpochMs: Long): CurrentLocationState = bestFix
        ?.takeIf { validator.isUsable(it, nowEpochMs) }
        ?.let(CurrentLocationState::Available)
        ?: CurrentLocationState.Acquiring

    fun clear() {
        bestFix = null
    }

    private fun isBetterLastKnown(candidate: LocationFix, existing: LocationFix): Boolean = when {
        candidate.accuracyMeters < existing.accuracyMeters -> true
        candidate.accuracyMeters > existing.accuracyMeters -> false
        else -> candidate.capturedAtEpochMs > existing.capturedAtEpochMs
    }
}
