package br.com.nexo.driver.offline

import android.content.Context
import android.content.SharedPreferences

/**
 * A persisted reference to a map package selected by the driver. The app deliberately stores a
 * content URI, not a duplicate of a potentially multi-gigabyte map file. The URI is granted by
 * Android's Storage Access Framework and can be consumed by the future offline map engine.
 */
data class OfflineMapPackage(
    val contentUri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val importedAtEpochMs: Long,
) {
    val isValid: Boolean
        get() = contentUri.startsWith("content://") && displayName.isNotBlank() && importedAtEpochMs > 0
}

interface OfflineMapPackageStore {
    fun load(): OfflineMapPackage?
    fun save(mapPackage: OfflineMapPackage): OfflineMapPackage
    fun clear()
}

class InMemoryOfflineMapPackageStore(
    initialPackage: OfflineMapPackage? = null,
) : OfflineMapPackageStore {
    private var stored = initialPackage?.takeIf { it.isValid }

    override fun load(): OfflineMapPackage? = stored

    override fun save(mapPackage: OfflineMapPackage): OfflineMapPackage {
        require(mapPackage.isValid) { "Pacote de mapa offline inválido." }
        return mapPackage.also { stored = it }
    }

    override fun clear() {
        stored = null
    }
}

class SharedPreferencesOfflineMapPackageStore private constructor(
    private val preferences: SharedPreferences,
) : OfflineMapPackageStore {
    private val lock = Any()

    override fun load(): OfflineMapPackage? = synchronized(lock) {
        val contentUri = preferences.getString(KEY_CONTENT_URI, null) ?: return@synchronized null
        val displayName = preferences.getString(KEY_DISPLAY_NAME, null) ?: return@synchronized null
        OfflineMapPackage(
            contentUri = contentUri,
            displayName = displayName,
            sizeBytes = preferences.getLong(KEY_SIZE_BYTES, UNKNOWN_SIZE).takeIf { it >= 0L },
            importedAtEpochMs = preferences.getLong(KEY_IMPORTED_AT, 0L),
        ).takeIf { it.isValid }
    }

    override fun save(mapPackage: OfflineMapPackage): OfflineMapPackage = synchronized(lock) {
        require(mapPackage.isValid) { "Pacote de mapa offline inválido." }
        preferences.edit()
            .putString(KEY_CONTENT_URI, mapPackage.contentUri)
            .putString(KEY_DISPLAY_NAME, mapPackage.displayName)
            .putLong(KEY_SIZE_BYTES, mapPackage.sizeBytes ?: UNKNOWN_SIZE)
            .putLong(KEY_IMPORTED_AT, mapPackage.importedAtEpochMs)
            .apply()
        mapPackage
    }

    override fun clear() = synchronized(lock) {
        preferences.edit()
            .remove(KEY_CONTENT_URI)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_SIZE_BYTES)
            .remove(KEY_IMPORTED_AT)
            .apply()
    }

    companion object {
        fun create(context: Context): SharedPreferencesOfflineMapPackageStore =
            SharedPreferencesOfflineMapPackageStore(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )

        private const val PREFERENCES_NAME = "driver_inteligente_offline_map"
        private const val KEY_CONTENT_URI = "content_uri"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_SIZE_BYTES = "size_bytes"
        private const val KEY_IMPORTED_AT = "imported_at"
        private const val UNKNOWN_SIZE = -1L
    }
}
