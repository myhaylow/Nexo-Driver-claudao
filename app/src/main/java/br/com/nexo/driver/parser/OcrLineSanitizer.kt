package br.com.nexo.driver.parser

/**
 * Repairs the character confusions ML Kit makes on Uber/99 offer cards, before any regex runs.
 *
 * This only applies to the OCR path. Text taken from the accessibility tree is already exact, and
 * running these substitutions over it could only corrupt it. OCR is the degraded path used when
 * the tree yields nothing, and it is precisely there that a single `O` read where a `0` belongs
 * makes the payout unparseable and costs the driver the whole card.
 *
 * Every substitution is **contextual**: a digit-lookaround guards each one, so "Rua Ipiranga" and
 * "Bloco G" survive untouched. A global replace -- as seen in comparable apps -- corrupts street
 * names, which then poison the destination matching downstream.
 */
internal object OcrLineSanitizer {

    fun sanitize(line: String): String {
        if (line.isBlank()) return line
        var result = line
        DIGIT_CONFUSIONS.forEach { (pattern, replacement) ->
            result = pattern.replace(result, replacement)
        }
        WORD_CONFUSIONS.forEach { (wrong, right) ->
            result = result.replace(wrong, right, ignoreCase = true)
        }
        return result
            .let(::normalizeCurrencySpacing)
            .let(::normalizeUnitSpacing)
            .trim()
    }

    fun sanitize(lines: List<String>): List<String> = lines.map(::sanitize)

    /**
     * Letters mistaken for digits, corrected only when surrounded by digits. Each entry needs both
     * a leading and trailing digit context so isolated letters in words are never touched.
     */
    private val DIGIT_CONFUSIONS: List<Pair<Regex, String>> = listOf(
        // The right-hand context accepts a decimal separator too, so the last digit of an amount
        // ("R$ 1B,50") is repaired the same way an interior one is.
        Regex("(?<=\\d)[lLIi|](?=\\d|[.,]\\d)") to "1",
        Regex("(?<=\\d)[Oo](?=\\d|[.,]\\d)") to "0",
        Regex("(?<=\\d)[Gg](?=\\d|[.,]\\d)") to "6",
        Regex("(?<=\\d)[Ss](?=\\d|[.,]\\d)") to "5",
        Regex("(?<=\\d)[Bb](?=\\d|[.,]\\d)") to "8",
        Regex("(?<=\\d)&(?=\\d|[.,]\\d)") to "8",
        // A leading letter directly before a decimal group: "l,60 km" -> "1,60 km".
        Regex("(?<![\\p{L}\\d])[lLIi|](?=[.,]\\d)") to "1",
        Regex("(?<![\\p{L}\\d])[Oo](?=[.,]\\d)") to "0",
        // A leading letter starting a measurement: "l2 min" -> "12 min". Deliberately requires the
        // unit to follow, so an address like "Quadra L2" is not rewritten into "Quadra 12".
        Regex("(?<![\\p{L}\\d])[lLIi|](?=[\\d.,]*\\s*(?:km|min|minutos)\\b)") to "1",
        Regex("(?<![\\p{L}\\d])[Oo](?=[\\d.,]*\\s*(?:km|min|minutos)\\b)") to "0",
        // A leading letter starting a currency amount: "R$ l8,50" -> "R$ 18,50".
        Regex("(?<=R\\$)(\\s*)[lLIi|](?=\\d)") to "$1" + "1",
        Regex("(?<=R\\$)(\\s*)[Oo](?=\\d)") to "$1" + "0",
    )

    /** Whole-token OCR errors on the unit words that the leg regex depends on. */
    private val WORD_CONFUSIONS: List<Pair<String, String>> = listOf(
        "mnin" to "min",
        "rnin" to "min",
        "mim" to "min",
        "rnin" to "min",
        "kn1" to "km",
        "krn" to "km",
    )

    /**
     * "R $ 24,50" / "R\$24,50" all collapse to the single spelling the money regex expects.
     * The lambda form is required: a literal `$` in a replacement string is parsed as a group
     * reference and throws.
     */
    private fun normalizeCurrencySpacing(line: String): String =
        CURRENCY_SPACING.replace(line) { "R$ " }

    private val CURRENCY_SPACING = Regex("(?i)\\bR\\s*\\$\\s*")

    /** "12,3km" and "5min" get the separating space the leg regex tolerates but reads better with. */
    private fun normalizeUnitSpacing(line: String): String =
        Regex("(?i)(\\d)(km|min)\\b").replace(line) { match ->
            "${match.groupValues[1]} ${match.groupValues[2].lowercase()}"
        }
}
