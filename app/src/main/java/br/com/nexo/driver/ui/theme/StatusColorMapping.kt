package br.com.nexo.driver.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import br.com.nexo.driver.evaluation.MetricStatus
import br.com.nexo.driver.evaluation.OfferDecision

/** A stable visual role for an offer decision or an individual filter result. */
enum class StatusTone {
    ACCEPT,
    ANALYZE,
    REJECT,
    UNKNOWN,
}

fun MetricStatus.toStatusTone(): StatusTone = when (this) {
    MetricStatus.PASS -> StatusTone.ACCEPT
    MetricStatus.NEAR -> StatusTone.ANALYZE
    MetricStatus.FAIL -> StatusTone.REJECT
    MetricStatus.UNKNOWN -> StatusTone.UNKNOWN
}

fun OfferDecision.toStatusTone(): StatusTone = when (this) {
    OfferDecision.ACCEPT -> StatusTone.ACCEPT
    OfferDecision.ANALYZE -> StatusTone.ANALYZE
    OfferDecision.REJECT -> StatusTone.REJECT
}

@Composable
@ReadOnlyComposable
fun StatusTone.color(): Color = when (this) {
    StatusTone.ACCEPT -> DriverInteligenteTheme.statusColors.accept
    StatusTone.ANALYZE -> DriverInteligenteTheme.statusColors.analyze
    StatusTone.REJECT -> DriverInteligenteTheme.statusColors.reject
    StatusTone.UNKNOWN -> DriverInteligenteTheme.statusColors.unknown
}

@Composable
@ReadOnlyComposable
fun StatusTone.contentColor(): Color = when (this) {
    StatusTone.ACCEPT -> DriverInteligenteTheme.statusColors.onAccept
    StatusTone.ANALYZE -> DriverInteligenteTheme.statusColors.onAnalyze
    StatusTone.REJECT -> DriverInteligenteTheme.statusColors.onReject
    StatusTone.UNKNOWN -> DriverInteligenteTheme.statusColors.onUnknown
}
