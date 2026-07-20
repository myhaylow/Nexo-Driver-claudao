package br.com.nexo.driver.block

import br.com.nexo.driver.destination.GeoCoordinate
import java.text.Normalizer
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One blocked establishment from the offline supermarket package. */
data class SupermarketPoint(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val coordinate: GeoCoordinate?,
    val radiusMeters: Double,
)

enum class SupermarketMatchMethod { GEO_DISTANCE, ALIAS_TEXT, NONE }

data class SupermarketMatchResult(
    val matched: Boolean,
    val method: SupermarketMatchMethod,
    val point: SupermarketPoint? = null,
    val distanceMeters: Double? = null,
)

/**
 * Offline, deterministic supermarket/hypermarket blocklist. A match only marks the offer; it never
 * automates a tap. Per the driver's rule it considers the trip's PICKUP only ("corrida que começa
 * em supermercado"), by coordinate-within-radius first and a conservative alias-text fallback.
 */
class SupermarketBlocklist(
    val points: List<SupermarketPoint>,
) {
    private val aliasKeys: Set<String> = points
        .flatMap { point -> (listOf(point.name) + point.aliases) }
        .map(::normalize)
        .filter { it.length >= MIN_ALIAS_LENGTH }
        .toSet()

    fun matchPickup(pickup: GeoCoordinate?, pickupAddress: String?): SupermarketMatchResult {
        if (pickup != null && pickup.isValid) {
            var nearest: SupermarketPoint? = null
            var nearestDistance = Double.MAX_VALUE
            points.forEach { point ->
                val coordinate = point.coordinate
                if (coordinate != null && coordinate.isValid) {
                    val distance = distanceMeters(pickup, coordinate)
                    if (distance <= point.radiusMeters && distance < nearestDistance) {
                        nearest = point
                        nearestDistance = distance
                    }
                }
            }
            nearest?.let {
                return SupermarketMatchResult(true, SupermarketMatchMethod.GEO_DISTANCE, it, nearestDistance)
            }
        }

        // Text fallback only when there is no trusted coordinate hit. Matched on whole words, not
        // as a loose substring: a plain contains() made the alias "extra" (the Extra chain) fire on
        // "Rua Extrema", rejecting a good ride as a supermarket. normalize() collapses the address
        // to space-separated tokens, so padding both sides with a space anchors the alias to word
        // boundaries and still matches multi-word aliases like "supermercado jacomar".
        val normalizedAddress = pickupAddress?.let(::normalize).orEmpty()
        if (normalizedAddress.length >= MIN_ALIAS_LENGTH) {
            val paddedAddress = " $normalizedAddress "
            val hit = points.firstOrNull { point ->
                (listOf(point.name) + point.aliases)
                    .map(::normalize)
                    .any { alias -> alias.length >= MIN_ALIAS_LENGTH && paddedAddress.contains(" $alias ") }
            }
            if (hit != null) {
                return SupermarketMatchResult(true, SupermarketMatchMethod.ALIAS_TEXT, hit)
            }
        }
        return SupermarketMatchResult(false, SupermarketMatchMethod.NONE)
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun distanceMeters(from: GeoCoordinate, to: GeoCoordinate): Double {
        val latitudeDelta = (to.latitude - from.latitude).toRadians()
        val longitudeDelta = (to.longitude - from.longitude).toRadians()
        val fromLatitude = from.latitude.toRadians()
        val toLatitude = to.latitude.toRadians()
        val a = (sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(fromLatitude) * cos(toLatitude) * sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0))
            .coerceIn(0.0, 1.0)
        return EARTH_MEAN_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun Double.toRadians(): Double = this * Math.PI / 180.0

    companion object {
        const val MIN_ALIAS_LENGTH = 4
        private const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8

        val EMPTY = SupermarketBlocklist(emptyList())
    }
}

/**
 * Parses the reviewed TSV package. Comment lines (`#`) and the header are ignored; malformed rows
 * are skipped rather than failing the whole package.
 */
object SupermarketBlocklistTsvCodec {
    private const val EXPECTED_MIN_COLUMNS = 9

    fun decode(tsv: String): SupermarketBlocklist {
        val points = tsv.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filterNot { it.startsWith("id\t") }
            .mapNotNull(::parseRow)
            .toList()
        return SupermarketBlocklist(points)
    }

    private fun parseRow(line: String): SupermarketPoint? {
        val columns = line.split("\t")
        if (columns.size < EXPECTED_MIN_COLUMNS) return null
        val id = columns[0].trim()
        val name = columns[1].trim()
        if (id.isEmpty() || name.isEmpty()) return null
        val aliases = columns[2].split("|").map(String::trim).filter(String::isNotEmpty)
        val latitude = columns[6].trim().toDoubleOrNull()
        val longitude = columns[7].trim().toDoubleOrNull()
        val radius = columns[8].trim().toDoubleOrNull() ?: return null
        val coordinate = if (latitude != null && longitude != null) {
            GeoCoordinate(latitude, longitude)
        } else {
            null
        }
        return SupermarketPoint(
            id = id,
            name = name,
            aliases = aliases,
            coordinate = coordinate,
            radiusMeters = radius,
        )
    }
}
