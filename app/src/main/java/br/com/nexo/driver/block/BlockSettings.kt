package br.com.nexo.driver.block

import android.content.Context

/** Whether the offline supermarket blocklist is active. Off by default; the driver opts in. */
data class BlockSettings(
    val blockSupermarkets: Boolean = false,
)

class SharedPreferencesBlockSettingsStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): BlockSettings = synchronized(lock) {
        BlockSettings(
            blockSupermarkets = preferences.getBoolean(KEY_BLOCK_SUPERMARKETS, false),
        )
    }

    fun save(settings: BlockSettings): BlockSettings = synchronized(lock) {
        preferences.edit()
            .putBoolean(KEY_BLOCK_SUPERMARKETS, settings.blockSupermarkets)
            .apply()
        settings
    }

    companion object {
        private const val PREFERENCES = "driver_inteligente_block_settings"
        private const val KEY_BLOCK_SUPERMARKETS = "block_supermarkets"

        fun create(context: Context): SharedPreferencesBlockSettingsStore =
            SharedPreferencesBlockSettingsStore(context.applicationContext)
    }
}

/**
 * Loads and caches the bundled supermarket blocklist asset once. Decoding is cheap, but reading the
 * asset off the capture thread keeps the first overlay fast, so callers should prime it early.
 */
class SupermarketBlocklistLoader(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    @Volatile private var cached: SupermarketBlocklist? = null

    fun load(): SupermarketBlocklist {
        cached?.let { return it }
        return synchronized(lock) {
            cached ?: decodeAsset().also { cached = it }
        }
    }

    private fun decodeAsset(): SupermarketBlocklist = runCatching {
        appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            SupermarketBlocklistTsvCodec.decode(reader.readText())
        }
    }.getOrDefault(SupermarketBlocklist.EMPTY)

    companion object {
        const val ASSET_NAME = "supermarket-blocklist.tsv"
    }
}
