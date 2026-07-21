package br.com.nexo.driver.destination

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Leitura única e sob demanda da localização, para o botão "usar minha localização atual como
 * casa". Nada é rastreado ou retido: uma amostra, entregue ao chamador, e nada mais.
 */
object OneShotLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Pede uma localização atual ao provedor disponível (GPS, depois rede). O callback recebe null
     * quando não há permissão, provedor, ou o pedido expira (~15s). Sempre chamado na main thread.
     */
    fun request(context: Context, onResult: (GeoCoordinate?) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        fun deliver(location: Location?) {
            val coordinate = location
                ?.let { GeoCoordinate(it.latitude, it.longitude) }
                ?.takeIf(GeoCoordinate::isValid)
            mainHandler.post { onResult(coordinate) }
        }

        if (!hasPermission(context)) {
            deliver(null)
            return
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: run {
            deliver(null)
            return
        }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull(manager.allProviders::contains)
            ?.takeIf(manager::isProviderEnabled)
        if (provider == null) {
            deliver(null)
            return
        }

        runCatching {
            var finished = false
            fun finishOnce(location: Location?) {
                if (finished) return
                finished = true
                deliver(location)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellation = CancellationSignal()
                manager.getCurrentLocation(
                    provider,
                    cancellation,
                    ContextCompat.getMainExecutor(context),
                ) { location -> finishOnce(location) }
                mainHandler.postDelayed({
                    if (!finished) {
                        cancellation.cancel()
                        finishOnce(manager.lastKnownOrNull(provider))
                    }
                }, TIMEOUT_MS)
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = finishOnce(location)

                    @Deprecated("Android 10 callback member.")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                @Suppress("MissingPermission", "DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                mainHandler.postDelayed({
                    if (!finished) {
                        runCatching { manager.removeUpdates(listener) }
                        finishOnce(manager.lastKnownOrNull(provider))
                    }
                }, TIMEOUT_MS)
            }
        }.onFailure { deliver(null) }
    }

    private fun LocationManager.lastKnownOrNull(provider: String): Location? = runCatching {
        @Suppress("MissingPermission")
        getLastKnownLocation(provider)
    }.getOrNull()

    private const val TIMEOUT_MS = 15_000L
}
