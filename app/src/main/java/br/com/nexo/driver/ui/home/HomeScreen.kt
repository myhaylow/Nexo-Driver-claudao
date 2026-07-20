package br.com.nexo.driver.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.analysis.OfferReadPath
import br.com.nexo.driver.analysis.OfferSessionMetrics
import br.com.nexo.driver.location.CurrentLocationServiceSnapshot
import br.com.nexo.driver.location.CurrentLocationServiceStatus

data class HomeScreenState(
    val readerEnabled: Boolean = false,
    val readerStatusText: String = "Pausado",
    val activeProfileName: String = "Dia a dia",
    val activeProfileSummary: String = "R$/km ≥ 1,75 · R$/h ≥ 40",
    val homeDestination: String? = null,
    val homeDestinationDetails: String? = null,
    val kilometresAnalyzed: Double = 0.0,
    val offersEvaluated: Int = 0,
    /** Read-health of this session; drives the "últimas ofertas" panel. */
    val readMetrics: OfferSessionMetrics = OfferSessionMetrics(),
    val location: CurrentLocationServiceSnapshot = CurrentLocationServiceSnapshot(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeScreenState,
    onReaderEnabledChanged: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onConfigureHome: () -> Unit,
    onLocationEnabledChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Driver Inteligente", fontWeight = FontWeight.SemiBold) },
            actions = {
                ReaderIndicator(isActive = state.readerEnabled)
                Spacer(Modifier.width(20.dp))
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReaderCard(state.readerEnabled, state.readerStatusText, onReaderEnabledChanged)
            if (state.readerEnabled) {
                ReadHealthCard(state.readMetrics)
            }
            CurrentLocationCard(state.location, onLocationEnabledChanged)
            ProfileCard(state.activeProfileName, state.activeProfileSummary, onOpenFilters)
            HomeDestinationCard(state.homeDestination, state.homeDestinationDetails, onConfigureHome)
            TodaySummary(state.kilometresAnalyzed, state.offersEvaluated)
        }
    }
}

@Composable
private fun CurrentLocationCard(
    snapshot: CurrentLocationServiceSnapshot,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val enabled = snapshot.status in setOf(
        CurrentLocationServiceStatus.ACQUIRING,
        CurrentLocationServiceStatus.ACTIVE,
        CurrentLocationServiceStatus.FIX_REJECTED,
        CurrentLocationServiceStatus.MOVEMENT_REJECTED,
    )
    val description = when (snapshot.status) {
        CurrentLocationServiceStatus.IDLE -> "Desligado"
        CurrentLocationServiceStatus.ACQUIRING -> "Procurando uma localização precisa…"
        CurrentLocationServiceStatus.ACTIVE -> buildString {
            append(if (snapshot.isLastKnown) "Última localização conhecida" else "Ativo")
            snapshot.accuracyMeters?.let { append(" · precisão ${it.toInt()} m") }
            snapshot.fixEpochMs?.let { timestamp ->
                val ageSeconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1_000L)
                append(" · há ${ageSeconds}s")
            }
        }
        CurrentLocationServiceStatus.PERMISSION_MISSING -> "Permissão de localização necessária."
        CurrentLocationServiceStatus.PROVIDER_UNAVAILABLE -> "Ative GPS ou localização do celular."
        CurrentLocationServiceStatus.FIX_REJECTED -> "Aguardando uma localização mais precisa."
        CurrentLocationServiceStatus.MOVEMENT_REJECTED -> "Movimento descartado por segurança; tentando novamente."
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Localização GPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (snapshot.status != CurrentLocationServiceStatus.IDLE) {
                    Text(
                        "Sessão: ${"%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), snapshot.sessionDistanceMeters / 1_000.0)} km",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
    }
}

/**
 * A glanceable read-health panel, shown only while the reader is on.
 *
 * The plain "Ativo" state hid the fact that offers can be served entirely through the OCR fallback
 * while the accessibility tree delivers nothing — visible before only in logcat. This surfaces the
 * three numbers from the served-offer log: how the last card was read, how long capture→overlay
 * took, and how much of it was readable. An all-OCR session is called out in an amber tone because
 * it means the fast path stopped working; it never relies on colour alone, the text says so.
 *
 * Before the first offer of a session it shows a waiting state rather than empty zeros, so an idle
 * driver is not misled into thinking something failed.
 */
@Composable
private fun ReadHealthCard(metrics: OfferSessionMetrics) {
    SectionCard {
        Text(
            "Últimas ofertas",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (metrics.totalReads == 0) {
            Text(
                "Aguardando a primeira oferta desta sessão.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        MetricRow("Lidas", metrics.totalReads.toString())
        metrics.lastLatencyMs?.let {
            Spacer(Modifier.height(10.dp))
            MetricRow("Tempo até o card", "$it ms")
        }
        metrics.lastCoveragePercent?.let {
            Spacer(Modifier.height(10.dp))
            MetricRow("Dados lidos", "$it%")
        }
        metrics.lastReadPath?.let { path ->
            Spacer(Modifier.height(10.dp))
            MetricRow(
                "Última leitura",
                when (path) {
                    OfferReadPath.ACCESSIBILITY -> "acessibilidade"
                    OfferReadPath.OCR -> "OCR"
                },
            )
        }
        if (metrics.isOcrOnly) {
            Spacer(Modifier.height(12.dp))
            val tone = MaterialTheme.colorScheme.tertiary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(tone.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Só por OCR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "A leitura rápida por acessibilidade não está funcionando neste app. " +
                        "O OCR consome mais bateria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReaderIndicator(isActive: Boolean) {
    val label = if (isActive) "●" else "○"
    Text(label, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
}

@Composable
private fun ReaderCard(enabled: Boolean, statusText: String, onEnabledChanged: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Leitor de ofertas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
    }
}

@Composable
private fun ProfileCard(name: String, summary: String, onOpenFilters: () -> Unit) {
    SectionCard(onClick = onOpenFilters) {
        Text("Perfil ativo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeDestinationCard(
    destination: String?,
    details: String?,
    onConfigureHome: () -> Unit,
) {
    SectionCard(onClick = onConfigureHome) {
        Text("Destino casa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            destination ?: "Configurar destino",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            details ?: "Endereço, raio e pacote offline opcional.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodaySummary(kilometres: Double, offers: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Hoje", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SectionCard {
            MetricRow("km da sessão", "%.1f".format(java.util.Locale.forLanguageTag("pt-BR"), kilometres))
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            MetricRow("ofertas avaliadas", offers.toString())
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = { content() })
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(HomeScreenState(readerEnabled = true), {}, {}, {}, {})
    }
}
