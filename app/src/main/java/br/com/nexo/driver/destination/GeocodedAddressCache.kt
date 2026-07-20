package br.com.nexo.driver.destination

import android.content.Context
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

data class GeocodedAddressCacheEntry(
    val query: String,
    val standardizedAddress: String?,
    val coordinate: GeoCoordinate?,
    val status: DestinationResolutionStatus,
    val preparedAtEpochMs: Long,
)

/** Small, private cache of address resolutions; it never stores map tiles or raw offer text. */
class GeocodedAddressCache private constructor(private val preferences: android.content.SharedPreferences) {
    @Synchronized fun get(query: String): GeocodedAddressCacheEntry? = decode(preferences.getString(key(query), null))

    @Synchronized fun put(entry: GeocodedAddressCacheEntry) {
        preferences.edit().putString(key(entry.query), encode(entry)).apply()
    }

    @Synchronized fun clear() {
        preferences.edit().clear().commit()
    }

    private fun key(query: String) = "address_${normalize(query)}"

    private fun encode(entry: GeocodedAddressCacheEntry): String = listOf(
        entry.standardizedAddress?.let(::b64).orEmpty(),
        entry.coordinate?.latitude?.toString().orEmpty(),
        entry.coordinate?.longitude?.toString().orEmpty(),
        entry.status.name,
        entry.preparedAtEpochMs.toString(),
    ).joinToString("\t")

    private fun decode(value: String?): GeocodedAddressCacheEntry? = runCatching {
        val fields = value?.split('\t') ?: return null
        require(fields.size == 5)
        GeocodedAddressCacheEntry(
            query = "",
            standardizedAddress = fields[0].takeIf(String::isNotEmpty)?.let(::unb64),
            coordinate = fields[1].toDoubleOrNull()?.let { lat -> fields[2].toDoubleOrNull()?.let { GeoCoordinate(lat, it) } },
            status = DestinationResolutionStatus.valueOf(fields[3]),
            preparedAtEpochMs = fields[4].toLong(),
        )
    }.getOrNull()

    companion object {
        fun create(context: Context) = GeocodedAddressCache(
            context.applicationContext.getSharedPreferences("geocoded_address_cache", Context.MODE_PRIVATE),
        )

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
            .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

        private fun b64(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        private fun unb64(value: String) = String(Base64.getUrlDecoder().decode(value))
    }
}
