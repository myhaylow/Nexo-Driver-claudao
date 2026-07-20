package br.com.nexo.driver.ui.permission

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.permission.CaptureSessionId
import br.com.nexo.driver.permission.MediaProjectionConsent
import br.com.nexo.driver.permission.PermissionGrant
import br.com.nexo.driver.permission.PermissionReadiness
import br.com.nexo.driver.permission.PermissionReadinessEvaluator
import br.com.nexo.driver.permission.PermissionState
import br.com.nexo.driver.permission.ReadinessStatus
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode

/**
 * Compact status card for the home screen. It deliberately presents permission status
 * without making Android permission calls; those calls stay in the activity boundary.
 */
@Composable
fun PermissionReadinessCard(
    readiness: PermissionReadiness,
    permissionState: PermissionState,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = when (readiness.status) {
        ReadinessStatus.READY -> "Pronto para analisar ofertas"
        ReadinessStatus.READY_WITH_LIMITATIONS -> "Pronto, com avisos"
        ReadinessStatus.NOT_READY -> "Ação necessária para iniciar"
    }
    val statusColor = when (readiness.status) {
        ReadinessStatus.READY -> MaterialTheme.colorScheme.primary
        ReadinessStatus.READY_WITH_LIMITATIONS -> MaterialTheme.colorScheme.tertiary
        ReadinessStatus.NOT_READY -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusColor)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Prontidão do leitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onOpenOnboarding) { Text("Revisar") }
            }
            PermissionSummaryRow("Sobreposição", permissionState.overlay == PermissionGrant.GRANTED, "necessária")
            PermissionSummaryRow(
                "Captura de tela",
                readiness.canAnalyzeOffers,
                captureSessionLabel(permissionState.mediaProjection),
            )
            PermissionSummaryRow(
                "Notificações",
                permissionState.notifications == PermissionGrant.GRANTED,
                "recomendada",
            )
        }
    }
}

@Composable
private fun PermissionSummaryRow(label: String, enabled: Boolean, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

internal fun captureSessionLabel(consent: MediaProjectionConsent): String = when (consent) {
    MediaProjectionConsent.NotRequested -> "opcional como fallback"
    MediaProjectionConsent.Denied -> "não autorizada"
    is MediaProjectionConsent.Granted -> "ativa nesta sessão"
}

@Preview(showBackground = true)
@Composable
private fun PermissionReadinessCardPreview() {
    val session = CaptureSessionId("preview-session")
    val state = PermissionState(
        overlay = PermissionGrant.GRANTED,
        notifications = PermissionGrant.GRANTED,
        mediaProjection = MediaProjectionConsent.Granted(session),
    )
    DriverInteligenteTheme(DriverThemeMode.LIGHT) {
        PermissionReadinessCard(
            readiness = PermissionReadinessEvaluator().evaluate(state, session),
            permissionState = state,
            onOpenOnboarding = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
