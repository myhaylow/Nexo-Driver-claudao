package br.com.nexo.driver.ui.destination

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.nexo.driver.block.SupermarketBlocklistLoader
import br.com.nexo.driver.destination.CuritibaPlacesCatalog
import br.com.nexo.driver.destination.GeoCoordinate
import br.com.nexo.driver.destination.GoogleMapsOfflineIntent
import br.com.nexo.driver.ui.mockup.M
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

private const val METERS_PER_DEGREE_LAT = 111_320.0

/**
 * Mapa offline do destino: um Canvas com o ponto salvo no centro, o raio de chegada em escala,
 * anéis de distância de 1 km, os bairros/cidades do catálogo por perto e os supermercados da lista
 * de bloqueio na vizinhança. Sempre mostra um mapa — mesmo sem nenhum app de mapas instalado —
 * e oferece abrir o Google Maps por cima.
 */
@Composable
fun DestinationMiniMapDialog(
    coordinate: GeoCoordinate,
    radiusMeters: Double,
    label: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    // Janela do mapa: mostra pelo menos 2,5 km de raio, ou 2,5x o raio de chegada.
    val viewportHalfMeters = max(2_500.0, radiusMeters * 2.5)
    val nearbyPlaces = remember(coordinate, viewportHalfMeters) {
        CuritibaPlacesCatalog.nearby(coordinate, viewportHalfMeters * 0.95, limit = 8)
    }
    val nearbySupermarkets = remember(coordinate, viewportHalfMeters) {
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians(coordinate.latitude))
        SupermarketBlocklistLoader(context).load().points
            .mapNotNull { point -> point.coordinate?.let { point.name to it } }
            .filter { (_, spot) ->
                val dLat = (spot.latitude - coordinate.latitude) * METERS_PER_DEGREE_LAT
                val dLng = (spot.longitude - coordinate.longitude) * metersPerDegreeLng
                kotlin.math.sqrt(dLat * dLat + dLng * dLng) <= viewportHalfMeters * 0.95
            }
            .take(12)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(M.slate900)
                .border(1.dp, M.slate700, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                label?.takeIf { it.isNotBlank() } ?: "Destino Casa",
                color = M.white,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(M.slate950)
                    .border(1.dp, M.slate800, RoundedCornerShape(12.dp)),
            ) {
                val half = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val pxPerMeter = half / viewportHalfMeters.toFloat()
                val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians(coordinate.latitude))

                fun toOffset(lat: Double, lng: Double): Offset {
                    val dxMeters = (lng - coordinate.longitude) * metersPerDegreeLng
                    val dyMeters = (lat - coordinate.latitude) * METERS_PER_DEGREE_LAT
                    return Offset(
                        x = center.x + (dxMeters * pxPerMeter).toFloat(),
                        y = center.y - (dyMeters * pxPerMeter).toFloat(),
                    )
                }

                // Anéis de distância a cada 1 km + eixos, para o mapa ter escala legível.
                var ringMeters = 1_000.0
                while (ringMeters <= viewportHalfMeters) {
                    drawCircle(
                        color = M.slate800,
                        radius = (ringMeters * pxPerMeter).toFloat(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    ringMeters += 1_000.0
                }
                drawLine(M.slate800, Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
                drawLine(M.slate800, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())

                // Raio de chegada em escala.
                val radiusPx = (radiusMeters * pxPerMeter).toFloat()
                drawCircle(M.emerald500.copy(alpha = 0.15f), radiusPx, center)
                drawCircle(M.emerald500, radiusPx, center, style = Stroke(width = 2.dp.toPx()))

                // Bairros/cidades próximos do catálogo.
                nearbyPlaces.forEach { place ->
                    val position = toOffset(place.coordinate.latitude, place.coordinate.longitude)
                    drawCircle(M.blue400, 4.dp.toPx(), position)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = place.name,
                        style = TextStyle(color = M.slate300, fontSize = 9.sp),
                        topLeft = position + Offset(6.dp.toPx(), -(6).dp.toPx()),
                    )
                }

                // Supermercados da lista de bloqueio na vizinhança.
                nearbySupermarkets.forEach { (_, spot) ->
                    val position = toOffset(spot.latitude, spot.longitude)
                    drawCircle(M.red400, 3.5.dp.toPx(), position)
                }

                // O destino, por cima de tudo.
                drawCircle(M.emerald400, 8.dp.toPx(), center)
                drawCircle(Color.White, 3.5.dp.toPx(), center)

                // Barra de escala (1 km) no canto inferior esquerdo.
                val scaleStart = Offset(12.dp.toPx(), size.height - 16.dp.toPx())
                val scaleEnd = scaleStart + Offset((1_000.0 * pxPerMeter).toFloat(), 0f)
                drawLine(M.slate300, scaleStart, scaleEnd, 2.dp.toPx())
                drawText(
                    textMeasurer = textMeasurer,
                    text = "1 km",
                    style = TextStyle(color = M.slate300, fontSize = 9.sp),
                    topLeft = Offset(scaleStart.x, scaleStart.y - 14.dp.toPx()),
                )
                // Norte.
                drawText(
                    textMeasurer = textMeasurer,
                    text = "N ↑",
                    style = TextStyle(color = M.slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset(size.width - 34.dp.toPx(), 10.dp.toPx()),
                )
            }

            // Legenda
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(M.emerald400, "Destino")
                LegendDot(M.emerald500.copy(alpha = 0.6f), "Raio ${radiusMeters.toLegendText()}")
                LegendDot(M.blue400, "Bairros")
                if (nearbySupermarkets.isNotEmpty()) LegendDot(M.red400, "Bloqueados")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, M.blue500.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { GoogleMapsOfflineIntent.open(context, coordinate, label) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Abrir no Google Maps", color = M.blue400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(M.slate800)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Fechar", color = M.white, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = M.slate400, fontSize = 10.sp)
    }
}

private fun Double.toLegendText(): String =
    if (this >= 1_000.0) String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", this / 1_000.0).replace(",0", "")
    else "${toInt()} m"
