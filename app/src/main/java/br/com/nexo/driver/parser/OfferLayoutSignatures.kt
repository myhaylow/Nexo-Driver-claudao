package br.com.nexo.driver.parser

import br.com.nexo.driver.offer.OfferSource

/**
 * Single source of truth for every hardcoded Uber/99 screen marker.
 *
 * These strings and patterns are the app's most drift-prone surface: they describe someone else's
 * UI copy, which can change without warning. They previously lived duplicated across the parser,
 * the accessibility reader and the card-region extractor, so a layout fix could be applied to one
 * and silently missed in the others -- and the resulting failure is quiet (the card is located but
 * not parsed, or parsed but not located) rather than loud.
 *
 * Keeping them here means a drift fix is a single-file diff, and every consumer moves together.
 */
internal object OfferLayoutSignatures {

    /**
     * Uber's service tier line ("UberX", "2 Comfort", "Priority exclusivo", ...).
     *
     * The `\b` after the tier keyword is load-bearing: without it, "x" followed by `.*` matched any
     * word starting with a tier letter -- a Curitiba neighbourhood named "XAXIM" at the top of the
     * card counted as a service line, which pushed the offer region up to include the earnings-chip
     * "R$ 0,00" and made the parser read a zero fare. Longer alternatives precede shorter so "XL"
     * is not consumed as "X".
     */
    val UBER_SERVICE_LINE = Regex(
        "(?i)\\s*(?:\\d+\\s*)?(?:uber\\s*)?(?:xl|x|black|comfort|flash|moto|priority)\\b.*",
    )

    /** Uber markers that identify the card without naming a service tier. */
    private val UBER_SCREEN_MARKERS = listOf("trip radar", "radar de viagens")

    /** 99 markers. The card never shows a service tier line the way Uber's does. */
    private val NINETY_NINE_MARKERS = listOf(
        "pgto. no app",
        "pagamento no app",
        "negocia",
        "perfil essencial",
        "perfil premium",
    )

    /** Money on the card. Deliberately excludes the R$/km line, which is a rate and not a payout. */
    val PAYOUT = Regex("(?i)r\\$\\s*[\\d.]+(?:,[\\d]{1,2})?")

    /** A route leg: "5 min (1,6 km)" and the several spacings the platforms use for it. */
    val ROUTE_LEG = Regex("(?i)\\d+\\s*(?:min|minutos)\\s*(?:\\(\\s*)?[\\d.,]+\\s*km")

    /** OCR and Compose both split a leg across two lines; these match each half. */
    val LEG_DURATION_ONLY = Regex("(?i)^\\s*\\(?\\s*\\d+\\s*(?:min|minutos)\\b.*$")
    val LEG_DISTANCE_ONLY = Regex("(?i)^\\s*\\(?\\s*[\\d.,]+\\s*km\\s*\\)?\\s*$")

    val RATING = Regex("(?i)\\b[345][,.]\\d{1,2}\\b")

    /** The button row that ends an offer card, used to bound the extracted region. */
    private val TERMINAL_ACTIONS = listOf("selecionar", "aceitar por")

    fun isUberServiceLine(line: String): Boolean = UBER_SERVICE_LINE.matches(line)

    fun hasUberMarker(text: String): Boolean {
        val normalized = text.lowercase()
        return UBER_SCREEN_MARKERS.any(normalized::contains) ||
            text.lineSequence().any(::isUberServiceLine)
    }

    fun hasNinetyNineMarker(text: String): Boolean {
        val normalized = text.lowercase()
        return NINETY_NINE_MARKERS.any(normalized::contains)
    }

    fun isNinetyNineAnchor(line: String): Boolean = hasNinetyNineMarker(line)

    fun isTerminalAction(line: String): Boolean {
        val normalized = line.lowercase()
        return TERMINAL_ACTIONS.any(normalized::contains)
    }

    fun hasPayout(line: String): Boolean =
        PAYOUT.containsMatchIn(line) && !line.contains("/km", ignoreCase = true)

    /** Resolves the platform from screen text alone, when the package name is not conclusive. */
    fun inferSource(text: String): OfferSource? = when {
        hasNinetyNineMarker(text) -> OfferSource.NINETY_NINE
        hasUberMarker(text) -> OfferSource.UBER
        else -> null
    }

    /** Maps a layout hint string onto its source, keeping the hint vocabulary in one place. */
    fun sourceForHint(hint: String?): OfferSource? = when (hint?.lowercase()) {
        HINT_UBER -> OfferSource.UBER
        HINT_NINETY_NINE -> OfferSource.NINETY_NINE
        else -> null
    }

    fun hintFor(source: OfferSource): String = when (source) {
        OfferSource.UBER -> HINT_UBER
        OfferSource.NINETY_NINE -> HINT_NINETY_NINE
    }

    /** Package names of the ride apps whose windows we read. */
    fun hintForPackage(packageName: CharSequence?): String? {
        val normalized = packageName?.toString()?.lowercase().orEmpty()
        return when {
            normalized == "com.ubercab.driver" || normalized.startsWith("com.ubercab.driver.") -> HINT_UBER
            normalized == "com.app99.driver" || normalized.startsWith("com.app99.driver.") ||
                normalized.contains("taxis99") ||
                normalized.contains("99driver") ||
                normalized.contains("didiglobal.driver") -> HINT_NINETY_NINE
            else -> null
        }
    }

    const val HINT_UBER = "uber"
    const val HINT_NINETY_NINE = "99"
}
