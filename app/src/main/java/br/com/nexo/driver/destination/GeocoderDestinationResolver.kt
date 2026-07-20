package br.com.nexo.driver.destination

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

data class DestinationResolution(
    val input: String,
    val standardizedAddress: String?,
    val coordinate: GeoCoordinate?,
    val status: DestinationResolutionStatus,
    val preparedAtEpochMs: Long,
)

/** Resolves user text off the main thread and de-duplicates/retries failed lookups conservatively. */
class GeocoderDestinationResolver(
    context: Context,
    private val executor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "destination-geocoder").apply { isDaemon = true }
    },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val retryIntervalMs: Long = 30_000L,
    /** Persisting offer addresses or coordinates is prohibited; this is disabled by default. */
    private val cacheEnabled: Boolean = false,
) {
    private val appContext = context.applicationContext
    private val cache = GeocodedAddressCache.create(appContext)
    private val inFlight = mutableSetOf<String>()

    fun resolveAsync(input: String, callback: (DestinationResolution) -> Unit) {
        val query = input.trim()
        if (!isUseful(query)) {
            callback(failed(query, DestinationResolutionStatus.FAILED))
            return
        }
        val key = GeocodedAddressCache.normalize(query)
        synchronized(inFlight) {
            val cached = if (cacheEnabled) cache.get(query) else null
            if (cached != null && (cached.status == DestinationResolutionStatus.RESOLVED || nowMs() - cached.preparedAtEpochMs < retryIntervalMs)) {
                callback(cached.toResolution(query))
                return
            }
            if (!inFlight.add(key)) return
        }
        executor.execute {
            val result = resolveBlocking(query)
            if (cacheEnabled) {
                cache.put(GeocodedAddressCacheEntry(query, result.standardizedAddress, result.coordinate, result.status, result.preparedAtEpochMs))
            }
            synchronized(inFlight) { inFlight.remove(key) }
            callback(result)
        }
    }

    private fun resolveBlocking(input: String): DestinationResolution {
        val timestamp = nowMs()
        if (!Geocoder.isPresent()) return failed(input, DestinationResolutionStatus.UNAVAILABLE, timestamp)
        val query = if (input.contains("Brasil", ignoreCase = true)) input else "$input, Brasil"
        return runCatching {
            @Suppress("DEPRECATION")
            val address = Geocoder(appContext, Locale("pt", "BR")).getFromLocationName(query, 1)?.firstOrNull()
            if (address == null) failed(input, DestinationResolutionStatus.FAILED, timestamp)
            else DestinationResolution(input, address.toStandardizedAddress(), GeoCoordinate(address.latitude, address.longitude), DestinationResolutionStatus.RESOLVED, timestamp)
        }.getOrElse { failed(input, DestinationResolutionStatus.UNAVAILABLE, timestamp) }
    }

    private fun Address.toStandardizedAddress(): String = getAddressLine(0)?.trim()?.takeIf(String::isNotBlank)
        ?: listOfNotNull(thoroughfare, subThoroughfare, locality, adminArea, countryName).joinToString(", ")

    private fun failed(input: String, status: DestinationResolutionStatus, timestamp: Long = nowMs()) =
        DestinationResolution(input, null, null, status, timestamp)

    companion object {
        fun isUseful(value: String): Boolean {
            val normalized = GeocodedAddressCache.normalize(value)
            return normalized.length >= 4 && normalized !in setOf("destino", "detectado pelo android", "teste de overlay")
        }
    }
}

private fun GeocodedAddressCacheEntry.toResolution(input: String) = DestinationResolution(input, standardizedAddress, coordinate, status, preparedAtEpochMs)
