package br.com.nexo.driver.overlay.preferences

/**
 * The values available in the four-cell overlay grid.
 *
 * The compact layout keeps payout as the primary header value. [PAYOUT] remains selectable
 * only for older saved preferences; the default grid favours derived decision metrics.
 */
enum class OverlayMetricField(
    val label: String,
) {
    RATE_PER_KM("R$/km"),
    RATE_PER_HOUR("R$/h"),
    RATE_PER_MINUTE("R$/min"),
    PASSENGER_RATING("Avaliação"),
    PICKUP("Retirada"),
    TOTAL_DURATION("Duração total"),
    TOTAL_DISTANCE("Distância total"),
    PAYOUT("Valor"),
    NET_PROFIT("Lucro"),
    NET_PROFIT_PERCENT("Lucro %"),
    NET_PROFIT_PER_HOUR("Lucro/h"),
}

/** Fixed positions make a saved configuration stable even when the UI changes. */
enum class OverlaySlot {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

/**
 * User configuration for the 2x2 metric grid.
 *
 * Exactly four different values are required, so every cell adds information
 * at a glance and the header remains the only location for the offer payment.
 */
data class OverlayPreferences(
    val fields: List<OverlayMetricField> = DEFAULT_FIELDS,
) {
    init {
        require(fields.size in MIN_FIELDS..MAX_FIELDS) {
            "An overlay must have between $MIN_FIELDS and $MAX_FIELDS fields."
        }
        require(fields.distinct().size == fields.size) {
            "Overlay fields must be unique."
        }
    }

    operator fun get(slot: OverlaySlot): OverlayMetricField =
        fields.getOrElse(slot.ordinal) { fields.last() }

    /** Replaces one cell while preserving the distinct-fields invariant. */
    fun withField(slot: OverlaySlot, field: OverlayMetricField): OverlayPreferences {
        if (slot.ordinal >= fields.size) return this
        val next = fields.toMutableList().also { it[slot.ordinal] = field }
        return OverlayPreferences(next)
    }

    companion object {
        /** O mockup permite escolher entre 3 e 4 campos exibidos. */
        const val MIN_FIELDS = 3
        const val MAX_FIELDS = 4

        // Default card (visual do mockup): valor no header, nota no rodapé; a grade traz R$/km,
        // R$/h, Lucro % e Lucro — a avaliação não se repete numa célula.
        val DEFAULT_FIELDS = listOf(
            OverlayMetricField.RATE_PER_KM,
            OverlayMetricField.RATE_PER_HOUR,
            OverlayMetricField.NET_PROFIT_PERCENT,
            OverlayMetricField.NET_PROFIT,
        )

        val DEFAULT = OverlayPreferences(DEFAULT_FIELDS)
    }
}
