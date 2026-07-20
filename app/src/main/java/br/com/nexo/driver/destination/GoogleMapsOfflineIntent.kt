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
        val preferred = Intent(intent).setPackage(GOOGLE_MAPS_PACKAGE)
        val chosen = if (preferred.resolveActivity(context.packageManager) != null) preferred else intent
        if (chosen.resolveActivity(context.packageManager) == null) return false
        context.startActivity(chosen)
        return true
    }
}
