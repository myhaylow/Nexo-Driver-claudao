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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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

// Paleta do redesign (mockup): fundo preto sólido e cores vivas idênticas às do Tailwind enviado,
// para não "desbotar" como as cores atenuadas do tema.
private object Overlay {
    val bg = Color(0xFF000000)          // preto sólido
    val slate900 = Color(0xFF0F172A)
    val slate800 = Color(0xFF1E293B)
    val slate500 = Color(0xFF64748B)
    val slate400 = Color(0xFF94A3B8)
    val slate300 = Color(0xFFCBD5E1)
    val white = Color(0xFFF1F5F9)
    val yellow = Color(0xFFEAB308)      // yellow-500 (nota, igual ao mockup)

    // Cores de status vivas (Tailwind): emerald-400 / amber-400 / red-400.
    val emerald = Color(0xFF34D399)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFF87171)
}

private val OverlayShape = RoundedCornerShape(16.dp)

/**
 * Overlay compacto no visual aprovado (mockup): barra-título esmeralda com o veredito e o valor da
 * corrida, grade de métricas configuráveis e rodapé com nota + duração/distância. Continua dirigido
 * pelos dados reais de [OfferOverlayUiModel]; [fontScale] aumenta só o texto, sem mudar o card.
 *
 * Quando a corrida vai no sentido de casa, o realce vira roxo ("sentido casa"), preservando esse
 * sinal do card original.
 */
@Composable
fun OfferOverlayCard(
    model: OfferOverlayUiModel,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    layout: OverlayLayoutStyle = OverlayLayoutStyle.TOPO,
) {
    val accent = if (model.isTowardHome) DriverInteligenteTheme.statusColors.home else vividStatusColor(model.status)
    val verdict = if (model.isTowardHome) "SENTIDO CASA" else decisionLabel(model.status)

    Box(modifier = modifier.fillMaxWidth()) {
        val cardModifier = when (layout) {
            // Card estreito ancorado à direita, métricas empilhadas — como o "Vertical" do mockup.
            OverlayLayoutStyle.VERTICAL -> Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 8.dp)
                .width(190.dp)
            // Topo/Horizontal: card em largura total (o mockup varia só a margem lateral).
            OverlayLayoutStyle.HORIZONTAL -> Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
            OverlayLayoutStyle.TOPO -> Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        }
        Column(
            modifier = cardModifier
                .clip(OverlayShape)
                .background(Overlay.bg)
                .border(BorderStroke(1.5.dp, accent.copy(alpha = 0.5f)), OverlayShape),
        ) {
            OverlayHeader(
                verdict = if (layout == OverlayLayoutStyle.VERTICAL) "NEXO" else verdict,
                accent = accent,
                model = model,
                fontScale = fontScale,
            )
            if (layout == OverlayLayoutStyle.VERTICAL) {
                OverlayMetricsVertical(model = model, fontScale = fontScale)
            } else {
                OverlayMetrics(model = model, fontScale = fontScale)
            }
            OverlayFooter(model = model, fontScale = fontScale)
        }
    }
}

/** Métricas empilhadas (uma por linha), usadas pelo formato Vertical do mockup. */
@Composable
private fun OverlayMetricsVertical(model: OfferOverlayUiModel, fontScale: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        model.gridFields.forEach { field ->
            val metric = model.metricFor(field)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Overlay.slate900.copy(alpha = 0.9f))
                    .border(1.dp, Overlay.slate800, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = field.label.uppercase(),
                    color = Overlay.slate400,
                    fontWeight = FontWeight.Medium,
                    fontSize = (10 * fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (metric.isAvailable) metric.value else "—",
                    color = if (metric.isAvailable) valueColor(metric.status) else Overlay.slate500,
                    fontWeight = FontWeight.Black,
                    fontSize = (14 * fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OverlayHeader(
    verdict: String,
    accent: Color,
    model: OfferOverlayUiModel,
    fontScale: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(6.dp))
            Text(
                text = verdict,
                color = accent,
                fontWeight = FontWeight.Black,
                fontSize = (12 * fontScale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Valor da corrida no lugar antes ocupado pelo rótulo/semáforo.
        Text(
            text = if (model.isPayoutAvailable) model.payout else "—",
            color = if (model.isPayoutAvailable) Overlay.white else Overlay.slate400,
            fontWeight = FontWeight.Black,
            fontSize = (13 * fontScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverlayMetrics(model: OfferOverlayUiModel, fontScale: Float) {
    val fields = model.gridFields
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        fields.forEachIndexed { index, field ->
            OverlayMetricCell(
                field = field,
                metric = model.metricFor(field),
                fontScale = fontScale,
                modifier = Modifier.weight(1f),
            )
            if (index != fields.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height((28 * fontScale).dp)
                        .background(Overlay.slate800),
                )
            }
        }
    }
}

@Composable
private fun OverlayMetricCell(
    field: OverlayMetricField,
    metric: OverlayMetricUi,
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = field.label.uppercase(),
            color = Overlay.slate400,
            fontWeight = FontWeight.Medium,
            fontSize = (9 * fontScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (metric.isAvailable) metric.value else "—",
            color = if (metric.isAvailable) valueColor(metric.status) else Overlay.slate500,
            fontWeight = FontWeight.Black,
            fontSize = (16 * fontScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverlayFooter(model: OfferOverlayUiModel, fontScale: Float) {
    val distance = if (model.totalDistance.isAvailable) model.totalDistance.value else null
    val footerRight = listOfNotNull(model.totalDuration.takeIf { it.isNotBlank() }, distance)
        .joinToString(" • ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Overlay.slate900.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Overlay.yellow,
                modifier = Modifier.size((12 * fontScale).dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (model.passengerRating.isAvailable) model.passengerRating.value else "—",
                color = Overlay.yellow,
                fontWeight = FontWeight.Bold,
                fontSize = (11 * fontScale).sp,
            )
        }
        Text(
            text = footerRight,
            color = Overlay.slate300,
            fontSize = (11 * fontScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun decisionLabel(status: OverlayStatus): String = when (status) {
    OverlayStatus.ACCEPT -> "CORRIDA EXCELENTE"
    OverlayStatus.ANALYZE -> "EM ANÁLISE"
    OverlayStatus.REJECT -> "RECUSAR CORRIDA"
    OverlayStatus.UNKNOWN -> "DADOS PARCIAIS"
}

// Cores vivas do mockup (não usa as cores atenuadas do tema, para manter a fidelidade pedida).
private fun vividStatusColor(status: OverlayStatus): Color = when (status) {
    OverlayStatus.ACCEPT -> Overlay.emerald
    OverlayStatus.ANALYZE -> Overlay.amber
    OverlayStatus.REJECT -> Overlay.red
    OverlayStatus.UNKNOWN -> Overlay.white
}

// Valores neutros (sem regra observando) ficam claros; os demais herdam a cor viva do status.
private fun valueColor(status: OverlayStatus): Color = vividStatusColor(status)

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
