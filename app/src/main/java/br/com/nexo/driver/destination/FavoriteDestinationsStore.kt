package br.com.nexo.driver.destination

import android.content.Context
import android.content.SharedPreferences

/**
 * Lista de destinos favoritos (Casa, Base, Faculdade...) com um ativo.
 *
 * O caminho de análise continua lendo o [HomeDestinationStore] único: quem troca o favorito ativo
 * é responsável por espelhá-lo lá. Assim a regra "termina perto de casa" não muda de forma.
 */
data class FavoriteDestinations(
    val destinations: List<HomeDestination> = emptyList(),
    val activeIndex: Int = 0,
) {
    val active: HomeDestination? get() = destinations.getOrNull(activeIndex)

    fun withActive(index: Int): FavoriteDestinations =
        copy(activeIndex = index.coerceIn(0, (destinations.size - 1).coerceAtLeast(0)))

    fun withUpdatedActive(destination: HomeDestination): FavoriteDestinations = when {
        destinations.isEmpty() -> FavoriteDestinations(listOf(destination), 0)
        else -> copy(
            destinations = destinations.mapIndexed { index, existing ->
                if (index == activeIndex) destination else existing
            },
        )
    }

    fun withAdded(destination: HomeDestination): FavoriteDestinations = FavoriteDestinations(
        destinations = (destinations + destination).take(MAX_FAVORITES),
        activeIndex = minOf(destinations.size, MAX_FAVORITES - 1),
    )

    fun withRemovedActive(): FavoriteDestinations {
        if (destinations.isEmpty()) return this
        val remaining = destinations.filterIndexed { index, _ -> index != activeIndex }
        return FavoriteDestinations(remaining, activeIndex.coerceIn(0, (remaining.size - 1).coerceAtLeast(0)))
    }

    companion object {
        const val MAX_FAVORITES = 5
    }
}

class SharedPreferencesFavoriteDestinationsStore private constructor(
    private val preferences: SharedPreferences,
) {
    private val lock = Any()

    fun load(): FavoriteDestinations = synchronized(lock) {
        val payload = preferences.getString(KEY_FAVORITES, null) ?: return FavoriteDestinations()
        val lines = payload.split('\n')
        val header = lines.firstOrNull()?.takeIf { it.startsWith("$SCHEMA:") } ?: return FavoriteDestinations()
        val activeIndex = header.removePrefix("$SCHEMA:").toIntOrNull() ?: 0
        val destinations = lines.drop(1).mapNotNull(DestinationPayloadCodec::decode)
        FavoriteDestinations(destinations, activeIndex.coerceIn(0, (destinations.size - 1).coerceAtLeast(0)))
    }

    fun save(favorites: FavoriteDestinations): FavoriteDestinations = synchronized(lock) {
        val valid = favorites.destinations.mapNotNull { it.validatedOrNull() }
        val sanitized = FavoriteDestinations(
            destinations = valid.take(FavoriteDestinations.MAX_FAVORITES),
            activeIndex = favorites.activeIndex.coerceIn(0, (valid.size - 1).coerceAtLeast(0)),
        )
        val payload = buildString {
            append("$SCHEMA:${sanitized.activeIndex}")
            sanitized.destinations.forEach { destination ->
                append('\n')
                append(DestinationPayloadCodec.encode(destination))
            }
        }
        preferences.edit().putString(KEY_FAVORITES, payload).apply()
        sanitized
    }

    companion object {
        private const val PREFERENCES_NAME = "driver_favorite_destinations"
        private const val KEY_FAVORITES = "favorites_v1"
        private const val SCHEMA = "favorites-v1"

        fun create(context: Context): SharedPreferencesFavoriteDestinationsStore =
            SharedPreferencesFavoriteDestinationsStore(
                context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
    }
}
