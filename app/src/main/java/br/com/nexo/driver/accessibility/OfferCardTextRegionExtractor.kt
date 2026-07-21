package br.com.nexo.driver.accessibility

import br.com.nexo.driver.parser.OfferLayoutSignatures

/** Selects only the offer card text and discards map labels, balances and unrelated controls. */
internal object OfferCardTextRegionExtractor {
    /** The single best card window on screen, or null when none qualifies. */
    fun extract(rawLines: List<String>, layoutHint: String?): List<String>? =
        extractAll(rawLines, layoutHint).firstOrNull()

    /**
     * Every qualifying card window on screen, ranked best-first. Uber's job board (`CardsTrayV2`)
     * can show several upfront offers at once; reading all of them lets the overlay annotate and
     * rank the tray instead of surfacing only the top card. The first element is exactly what
     * [extract] returns, so single-card callers are unaffected.
     *
     * Windows that overlap an already-accepted (higher-scoring) window are dropped, so trailing
     * noise after one card cannot masquerade as a second offer; genuinely separate cards -- each
     * with its own service anchor and terminal action -- survive as distinct windows.
     */
    fun extractAll(rawLines: List<String>, layoutHint: String?): List<List<String>> {
        val lines = rawLines
            .flatMap { value -> value.lines() }
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (lines.isEmpty()) return emptyList()

        val candidates = candidateStarts(lines, layoutHint)
        // routeLegCount is the expensive part of both the completeness test and the score, so it is
        // computed once per candidate window and carried, rather than rescanned by each.
        val scored = candidates
            .map { start -> ScoredWindow(start, cardWindow(lines, start)) }
            .map { candidate -> candidate.copy(routeLegs = routeLegCount(candidate.window)) }
            .filter { it.routeLegs >= REQUIRED_ROUTE_LEGS && it.window.any(::hasPayout) }
            .sortedByDescending { cardScore(it.window, it.routeLegs) }

        val accepted = mutableListOf<ScoredWindow>()
        for (candidate in scored) {
            val overlaps = accepted.any { existing ->
                candidate.start in existing.start until (existing.start + existing.window.size)
            }
            if (!overlaps) accepted += candidate
        }
        return accepted.map { it.window }
    }

    private fun candidateStarts(lines: List<String>, layoutHint: String?): List<Int> =
        when (layoutHint?.lowercase()) {
            "uber" -> lines.indices.filter { index -> isUberService(lines[index]) }
            "99" -> lines.indices.filter { index -> isNinetyNineAnchor(lines[index]) }
            else -> lines.indices.filter { index ->
                isUberService(lines[index]) || isNinetyNineAnchor(lines[index])
            }
        }.ifEmpty {
            if (layoutHint == null) {
                emptyList()
            } else {
                lines.indices.filter { index -> hasPayout(lines[index]) }
            }
        }

    private data class ScoredWindow(
        val start: Int,
        val window: List<String>,
        val routeLegs: Int = 0,
    )

    private fun cardWindow(lines: List<String>, start: Int): List<String> {
        val bounded = lines.drop(start).take(MAX_CARD_LINES)
        val actionIndex = bounded.indexOfFirst(::isTerminalAction)
        return if (actionIndex >= 0) bounded.take(actionIndex + 1) else bounded
    }

    private fun cardScore(lines: List<String>, routeLegs: Int): Int =
        routeLegs * 10 +
            lines.count(::hasPayout) * 4 +
            lines.count(::hasRatingSignal) * 2 +
            lines.count(::isTerminalAction)

    private fun isUberService(line: String): Boolean = OfferLayoutSignatures.isUberServiceLine(line)

    private fun isNinetyNineAnchor(line: String): Boolean = OfferLayoutSignatures.isNinetyNineAnchor(line)

    private fun hasPayout(line: String): Boolean = OfferLayoutSignatures.hasPayout(line)

    private fun hasRouteLeg(line: String): Boolean = ROUTE_LEG_REGEX.containsMatchIn(line)

    private fun routeLegCount(lines: List<String>): Int {
        var count = 0
        var index = 0
        while (index < lines.size) {
            if (hasRouteLeg(lines[index])) {
                count += 1
                index += 1
            } else if (
                index + 1 < lines.size &&
                LEG_DURATION_ONLY_REGEX.containsMatchIn(lines[index]) &&
                LEG_DISTANCE_ONLY_REGEX.containsMatchIn(lines[index + 1])
            ) {
                count += 1
                index += 2
            } else {
                index += 1
            }
        }
        return count
    }

    private fun hasRatingSignal(line: String): Boolean =
        line.contains("corridas", ignoreCase = true) || RATING_REGEX.containsMatchIn(line)

    private fun isTerminalAction(line: String): Boolean = OfferLayoutSignatures.isTerminalAction(line)

    private val ROUTE_LEG_REGEX = OfferLayoutSignatures.ROUTE_LEG
    private val LEG_DURATION_ONLY_REGEX = OfferLayoutSignatures.LEG_DURATION_ONLY
    private val LEG_DISTANCE_ONLY_REGEX = OfferLayoutSignatures.LEG_DISTANCE_ONLY
    private val RATING_REGEX = OfferLayoutSignatures.RATING
    private const val REQUIRED_ROUTE_LEGS = 2
    private const val MAX_CARD_LINES = 40
}
