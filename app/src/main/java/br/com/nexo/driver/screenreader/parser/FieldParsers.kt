package br.com.nexo.driver.screenreader.parser

import java.math.BigDecimal
import java.math.RoundingMode

object CurrencyParser {
    private val token = Regex("(?i)(?:r\\$\\s*)?([\\d.]+(?:,[\\d]{1,2})?)")
    fun parse(value: String): BigDecimal? = token.find(value)?.groupValues?.get(1)?.let { raw ->
        val normalized = if (',' in raw) raw.replace(".", "").replace(',', '.') else raw
        normalized.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    }
}

object DistanceParser {
    private val token = Regex("(?i)([\\d.,]+)\\s*(km|m)\\b")
    fun parseKilometres(value: String): Double? = token.find(value)?.let { match ->
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        when (match.groupValues[2].lowercase()) { "m" -> amount / 1_000.0; else -> amount }
    }
}

object DurationParser {
    fun parseMinutes(value: String): Int? {
        val hours = Regex("(?i)(\\d+)\\s*h(?:ora)?s?").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(?i)(\\d+)\\s*(?:min|minutos?)").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }
}

object RatingParser {
    private val token = Regex("(?:★|⭐)?\\s*([0-5][,.][0-9]{1,2})")
    fun parse(value: String): Double? = token.find(value)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
}
