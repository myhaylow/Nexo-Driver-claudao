package br.com.nexo.driver.accessibility

import br.com.nexo.driver.offer.NormalizedOffer

/**
 * Prevents transient Uber/99 window states (for example, an earnings chip rendered before the
 * offer card) from producing a misleading overlay. A live decision requires the monetary value
 * and both route legs; rating remains optional because providers do not expose it on every offer.
 */
internal fun NormalizedOffer.isReadyForLiveAnalysis(): Boolean =
    payout.value != null && knownLegCount() == REQUIRED_LEG_COUNT

internal fun NormalizedOffer.knownLegCount(): Int = listOf(pickup, trip).count { leg ->
    leg.duration.value != null && leg.distance.value != null
}

private const val REQUIRED_LEG_COUNT = 2
