package br.com.nexo.driver.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme

private val OverlayShape = RoundedCornerShape(20.dp)
private val StarGold = Color(0xFFFFCF2B)

/**
 * Compact, non-interactive offer overlay matching the reference "neon" identity:
 * a neon frame with the decision label sitting on the top border, a traffic light that shows the
 * real decision, the payout as the primary value, configurable metrics, and a distance/duration footer.
 *
 * When the trip ends near or heads toward the driver's home, the frame and label turn purple
 * ("sentido casa") while the traffic light keeps signalling accept/analyze/reject so a poor ride
 * that happens to go home is never disguised as a good one.
 */
@Composable
fun OfferOverlayCard(
    model: OfferOverlayUiModel,
    modifier: Modifier = Modifier,
) {
    val decisionColor = statusColor(model.status)
    val frameColor = if (model.isTowardHome) DriverInteligenteTheme.statusColors.home else decisionColor
    val background = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f)

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = OverlayShape,
            color = background,
            border = BorderStroke(2.5.dp, frameColor),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                Spacer(Modifier.height(4.dp))
                PayoutHeader(model)
                Spacer(Modifier.height(10.dp))
                OverlayMetricRow(model)
                Spacer(Modifier.height(12.dp))
                MetricDivider()
                Spacer(Modifier.height(10.dp))
                OverlayFooter(model)
                DecisionReason(model, decisionColor)
                AlternativesStrip(model.alternatives)
            }
        }

        DecisionLabel(
            text = if (model.isTowardHome) "SENTIDO CASA" else decisionLabel(model.status),
            color = frameColor,
            background = background,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp),
        )

        TrafficLight(
            decision = model.status,
            outline = frameColor,
            background = background,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-14).dp),
        )
    }
}

@Composable
private fun PayoutHeader(model: OfferOverlayUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Valor",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (model.isPayoutAvailable) model.payout else "—",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (model.isPayoutAvailable) statusColor(model.payoutStatus) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
@Composable
private fun OverlayMetricRow(model: OfferOverlayUiModel) {
    val fields = model.gridFields
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        fields.forEachIndexed { index, field ->
            OverlayMetricCell(
                field = field,
                metric = model.metricFor(field),
                modifier = Modifier.weight(1f),
            )
            if (index != fields.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

@Composable
private fun OverlayMetricCell(
    field: OverlayMetricField,
    metric: OverlayMetricUi,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (metric.isAvailable) metric.value else "—",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetricIndicator(field = field, metric = metric)
        }
    }
}

/**
 * Trailing glyph per the reference: a gold star for the rating and a coloured ▲/▼ trend
 * arrow for pass/fail metrics.
 */
@Composable
private fun MetricIndicator(field: OverlayMetricField, metric: OverlayMetricUi) {
    if (!metric.isAvailable) return
    when (field) {
        OverlayMetricField.PASSENGER_RATING -> {
            Spacer(Modifier.width(3.dp))
            Text(text = "★", color = StarGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        // A neutral glyph is drawn for ANALYZE so "in the tolerance band" stays distinguishable
        // from "no rule is watching this metric", which previously looked identical.
        else -> when (metric.status) {
            OverlayStatus.ACCEPT -> TrendArrow("▲", statusColor(OverlayStatus.ACCEPT))
            OverlayStatus.REJECT -> TrendArrow("▼", statusColor(OverlayStatus.REJECT))
            OverlayStatus.ANALYZE -> TrendArrow("=", statusColor(OverlayStatus.ANALYZE))
            OverlayStatus.UNKNOWN -> Unit
        }
    }
}

@Composable
private fun TrendArrow(glyph: String, color: Color) {
    Spacer(Modifier.width(4.dp))
    Text(text = glyph, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
    )
}

@Composable
private fun OverlayFooter(model: OfferOverlayUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterStat(
            label = "Distância:",
            value = if (model.totalDistance.isAvailable) model.totalDistance.value else "—",
        )
        FooterStat(label = "Duração:", value = model.totalDuration)
    }
}

/**
 * The one-line justification for the verdict. A low-coverage read is called out explicitly so a
 * cautious amber card is never mistaken for a genuinely middling offer.
 */
@Composable
private fun DecisionReason(model: OfferOverlayUiModel, color: Color) {
    val lowCoverage = model.coveragePercent < LOW_COVERAGE_PERCENT
    val text = when {
        model.isBlockedSupermarket -> "Embarque em local bloqueado"
        lowCoverage -> "Leitura parcial da tela (${model.coveragePercent}%)"
        else -> model.decisionReason ?: return
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (lowCoverage) MaterialTheme.colorScheme.onSurfaceVariant else color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The other offers on screen at the same time (Uber's tray), ranked best-first under a divider.
 * Each row is a coloured dot (the same accept/analyze/reject signal), the provider, its payout and
 * R$/km. Purely informative -- it never adds a tap target, matching the window's FLAG_NOT_TOUCHABLE.
 */
@Composable
private fun AlternativesStrip(alternatives: List<OverlayAlternativeUi>) {
    if (alternatives.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    MetricDivider()
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Outras ofertas na tela",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    alternatives.forEach { alternative ->
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(statusColor(alternative.status)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = alternative.provider,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = alternative.ratePerKm,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = alternative.payout,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor(alternative.status),
                maxLines = 1,
            )
        }
    }
}

private const val LOW_COVERAGE_PERCENT = 60

@Composable
private fun FooterStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DecisionLabel(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    // A background-filled chip sits on the frame, visually breaking the border line like the mockup.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.16f))
                .border(BorderStroke(1.5.dp, color), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun TrafficLight(
    decision: OverlayStatus,
    outline: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF0D0D0F))
                .border(BorderStroke(1.5.dp, outline), RoundedCornerShape(9.dp))
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Bulb(on = decision == OverlayStatus.REJECT, onColor = statusColor(OverlayStatus.REJECT), dim = dim)
            Bulb(on = decision == OverlayStatus.ANALYZE, onColor = statusColor(OverlayStatus.ANALYZE), dim = dim)
            Bulb(on = decision == OverlayStatus.ACCEPT, onColor = statusColor(OverlayStatus.ACCEPT), dim = dim)
        }
    }
}

@Composable
private fun Bulb(on: Boolean, onColor: Color, dim: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) onColor else dim),
    )
}

private fun decisionLabel(status: OverlayStatus): String = when (status) {
    OverlayStatus.ACCEPT -> "ACEITAR CORRIDA"
    OverlayStatus.ANALYZE -> "EM ANÁLISE"
    OverlayStatus.REJECT -> "RECUSAR CORRIDA"
    OverlayStatus.UNKNOWN -> "DADOS PARCIAIS"
}

@Composable
private fun statusColor(status: OverlayStatus): Color = when (status) {
    OverlayStatus.ACCEPT -> DriverInteligenteTheme.statusColors.accept
    OverlayStatus.ANALYZE -> DriverInteligenteTheme.statusColors.analyze
    OverlayStatus.REJECT -> DriverInteligenteTheme.statusColors.reject
    OverlayStatus.UNKNOWN -> DriverInteligenteTheme.statusColors.unknown
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun OfferOverlayCardAcceptPreview() {
    DriverInteligenteTheme {
        OfferOverlayCard(
            model = OfferOverlayUiModel(
                status = OverlayStatus.ACCEPT,
                totalDuration = "32 min",
                payout = "R$ 48,30",
                ratePerKm = OverlayMetricUi("1,95", OverlayStatus.ACCEPT),
                ratePerHour = OverlayMetricUi("48,30", OverlayStatus.ACCEPT),
                passengerRating = OverlayMetricUi("4,95", OverlayStatus.ACCEPT),
                pickup = OverlayMetricUi("3 min · 1,2 km", OverlayStatus.ACCEPT),
                netProfit = OverlayMetricUi("R$ 40,90", OverlayStatus.UNKNOWN),
                totalDistance = OverlayMetricUi("14,8 km", OverlayStatus.ACCEPT),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun OfferOverlayCardTowardHomePreview() {
    DriverInteligenteTheme {
        OfferOverlayCard(
            model = OfferOverlayUiModel(
                status = OverlayStatus.ACCEPT,
                totalDuration = "36 min",
                payout = "R$ 55,40",
                ratePerKm = OverlayMetricUi("2,10", OverlayStatus.ACCEPT),
                ratePerHour = OverlayMetricUi("55,40", OverlayStatus.ACCEPT),
                passengerRating = OverlayMetricUi("4,98", OverlayStatus.ACCEPT),
                pickup = OverlayMetricUi("4 min · 1,8 km", OverlayStatus.ACCEPT),
                netProfit = OverlayMetricUi("R$ 46,35", OverlayStatus.UNKNOWN),
                totalDistance = OverlayMetricUi("18,1 km", OverlayStatus.ACCEPT),
                isTowardHome = true,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun OfferOverlayCardTrayPreview() {
    DriverInteligenteTheme {
        OfferOverlayCard(
            model = OfferOverlayUiModel(
                status = OverlayStatus.ACCEPT,
                totalDuration = "28 min",
                payout = "R$ 32,10",
                ratePerKm = OverlayMetricUi("1,90", OverlayStatus.ACCEPT),
                ratePerHour = OverlayMetricUi("42,10", OverlayStatus.ACCEPT),
                passengerRating = OverlayMetricUi("4,92", OverlayStatus.ACCEPT),
                pickup = OverlayMetricUi("3 min · 1,1 km", OverlayStatus.ACCEPT),
                totalDistance = OverlayMetricUi("12,4 km", OverlayStatus.ACCEPT),
                alternatives = listOf(
                    OverlayAlternativeUi("Comfort", "R$ 24,00", "R$ 1,55/km", OverlayStatus.ANALYZE),
                    OverlayAlternativeUi("UberX", "R$ 15,80", "R$ 1,20/km", OverlayStatus.REJECT),
                ),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun OfferOverlayCardRejectPreview() {
    DriverInteligenteTheme {
        OfferOverlayCard(
            model = OfferOverlayUiModel(
                status = OverlayStatus.REJECT,
                totalDuration = "48 min",
                payout = "R$ 35,70",
                ratePerKm = OverlayMetricUi("1,50", OverlayStatus.REJECT),
                ratePerHour = OverlayMetricUi("35,70", OverlayStatus.REJECT),
                passengerRating = OverlayMetricUi("4,70", OverlayStatus.ANALYZE),
                pickup = OverlayMetricUi("6 min · 3,1 km", OverlayStatus.REJECT),
                netProfit = OverlayMetricUi("R$ 25,05", OverlayStatus.UNKNOWN),
                totalDistance = OverlayMetricUi("21,3 km", OverlayStatus.REJECT),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
