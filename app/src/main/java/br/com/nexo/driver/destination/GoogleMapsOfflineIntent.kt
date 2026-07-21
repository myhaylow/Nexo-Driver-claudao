package br.com.nexo.driver.destination

import android.content.Context
import android.content.Intent
import android.net.Uri

object GoogleMapsOfflineIntent {
    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    fun create(coordinate: GeoCoordinate, label: String?): Intent {
        require(coordinate.isValid)
        val encodedLabel = Uri.encode(label?.ifBlank { null } ?: "Destino")
        return Intent(Intent.ACTION_VIEW, Uri.parse("geo:${coordinate.latitude},${coordinate.longitude}?q=${coordinate.latitude},${coordinate.longitude}($encodedLabel)"))
    }

    fun open(context: Context, coordinate: GeoCoordinate, label: String?): Boolean {
        val intent = create(coordinate, label)
        // resolveActivity() mente no Android 11+ sem <queries> no manifest (filtro de visibilidade
        // de pacotes): retornava null com o Google Maps instalado e o botão desistia em silêncio.
        // Tentar abrir e capturar a ausência real de app é o caminho confiável.
        val preferred = Intent(intent).setPackage(GOOGLE_MAPS_PACKAGE)
        return runCatching { context.startActivity(preferred) }.isSuccess ||
            runCatching { context.startActivity(intent) }.isSuccess
    }
}
