package br.com.nexo.driver.location

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Android-only adapter for the optional foreground GPS service. It uses no network SDK, does not
 * persist coordinates, and never asks for ACCESS_BACKGROUND_LOCATION. Consumers receive only the
 * current in-memory state through [onStateChanged].
 */
class CurrentLocationTracker(
    context: Context,
    private val locationManager: LocationManager = context.applicationContext.getSystemService(LocationManager::class.java),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val accumulator: LocationFixAccumulator = LocationFixAccumulator(),
    private val movementAccumulator: LocationMovementAccumulator = LocationMovementAccumulator(),
    private val onStateChanged: (CurrentLocationState) -> Unit = {},
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var started = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val fix = location.toFix(isLastKnown = false)
            val movement = movementAccumulator.offer(fix)
            if (movement.rejection != null) {
                publish(CurrentLocationState.MovementRejected(movement.rejection))
            } else {
                publish(accumulator.offer(fix, nowEpochMs()).withSessionDistance(movement.sessionDistanceMeters))
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (started && usableProviders().isEmpty()) publish(CurrentLocationState.ProviderUnavailable)
        }
    }

    @SuppressLint("MissingPermission") // Guarded immediately below; every framework call also catches revocation races.
    fun start(): CurrentLocationState {
        if (!hasLocationPermission()) return publish(CurrentLocationState.PermissionMissing)
        val providers = usableProviders()
        if (providers.isEmpty()) return publish(CurrentLocationState.ProviderUnavailable)

        started = true
        val lastKnown = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider)?.toFix(isLastKnown = true) }.getOrNull()
        }
        val seeded = LastKnownLocationSelector().select(lastKnown, nowEpochMs())
        seeded?.let(movementAccumulator::offer)
        publish(accumulator.seed(seeded, nowEpochMs()).withSessionDistance(0.0))
        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MS,
                    UPDATE_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper(),
                )
            }.onFailure { failure ->
                if (failure is SecurityException) publish(CurrentLocationState.PermissionMissing)
            }
        }
        return accumulator.current(nowEpochMs())
    }

    fun currentState(): CurrentLocationState = when {
        !started -> CurrentLocationState.Idle
        !hasLocationPermission() -> CurrentLocationState.PermissionMissing
        usableProviders().isEmpty() -> CurrentLocationState.ProviderUnavailable
        else -> accumulator.current(nowEpochMs())
    }

    override fun close() {
        started = false
        runCatching { locationManager.removeUpdates(listener) }
        accumulator.clear()
        movementAccumulator.clear()
        publish(CurrentLocationState.Idle)
    }

    private fun usableProviders(): List<String> {
        val providers = buildList {
            if (hasFineLocationPermission()) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.distinct()
        return providers.filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
    }

    private fun hasLocationPermission(): Boolean = hasFineLocationPermission() ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toFix(isLastKnown: Boolean): LocationFix = LocationFix(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
        capturedAtEpochMs = time,
        provider = provider ?: "android",
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
        isLastKnown = isLastKnown,
    )

    private fun publish(state: CurrentLocationState): CurrentLocationState {
        CurrentLocationStateRepository.update(state)
        onStateChanged(state)
        return state
    }

    private fun CurrentLocationState.withSessionDistance(sessionDistanceMeters: Double): CurrentLocationState = when (this) {
        is CurrentLocationState.Available -> copy(sessionDistanceMeters = sessionDistanceMeters)
        else -> this
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 2_000L
        const val UPDATE_DISTANCE_METERS = 5f
    }
}
