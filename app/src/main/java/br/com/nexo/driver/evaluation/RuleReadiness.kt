package br.com.nexo.driver.evaluation

/**
 * Why an enabled rule cannot currently produce a value.
 *
 * A rule in this state is not neutral. The evaluator excludes unreadable metrics from the score,
 * so an inoperative rule silently drags coverage down and pushes offers toward ANALYZE — and the
 * driver has no way to tell that from a genuinely borderline ride. This was observed live: the
 * "termina perto de casa" rule was enabled with a home destination set, but no offline address
 * package had been imported, so it never resolved on a single offer.
 *
 * The blocker names the missing prerequisite rather than the symptom, so the screen can offer the
 * action that fixes it.
 */
enum class RuleBlocker(val summary: String, val actionLabel: String?) {
    /** [Metric.ENDS_NEAR_HOME] needs the offline address package to resolve a drop-off. */
    MISSING_OFFLINE_ADDRESS_PACKAGE(
        summary = "Falta o pacote de endereços offline.",
        actionLabel = "Importar",
    ),

    /** The same rule also needs somewhere to compare the drop-off against. */
    MISSING_HOME_DESTINATION(
        summary = "Defina o destino de casa para esta regra valer.",
        actionLabel = "Definir",
    ),

    /** The blocklist toggle is off, so a pickup-blocking rule can never fire. */
    BLOCKLIST_DISABLED(
        summary = "A lista de bloqueio está desligada nos ajustes.",
        actionLabel = "Ativar",
    ),
}

/** Prerequisites the app can currently satisfy, used to derive [RuleBlocker]s. */
data class RulePrerequisites(
    val hasHomeDestination: Boolean,
    val hasOfflineAddressPackage: Boolean,
    val isBlocklistEnabled: Boolean,
)

/**
 * The blocker for [metric], or null when the rule can evaluate.
 *
 * Only enabled rules are worth reporting: a disabled rule already reads as inactive, so flagging
 * it would be noise. Home destination is checked before the address package because it is the more
 * fundamental of the two — telling someone to import a package when they have not chosen a
 * destination yet points at the wrong step.
 */
fun FilterRule.blocker(prerequisites: RulePrerequisites): RuleBlocker? {
    if (!enabled) return null
    return when (metric) {
        Metric.ENDS_NEAR_HOME, Metric.IS_TOWARD_DESTINATION -> when {
            !prerequisites.hasHomeDestination -> RuleBlocker.MISSING_HOME_DESTINATION
            !prerequisites.hasOfflineAddressPackage -> RuleBlocker.MISSING_OFFLINE_ADDRESS_PACKAGE
            else -> null
        }
        Metric.PICKUP_IS_BLOCKED ->
            if (prerequisites.isBlocklistEnabled) null else RuleBlocker.BLOCKLIST_DISABLED
        else -> null
    }
}
