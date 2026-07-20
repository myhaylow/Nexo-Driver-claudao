package br.com.nexo.driver.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.permission.CaptureSessionId
import br.com.nexo.driver.permission.MediaProjectionConsent
import br.com.nexo.driver.permission.PermissionGrant
import br.com.nexo.driver.permission.PermissionReadiness
import br.com.nexo.driver.permission.PermissionReadinessEvaluator
import br.com.nexo.driver.permission.PermissionState
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode

/** Callbacks are intentionally declarative so this UI does not own Android permission APIs. */
data class PermissionOnboardingActions(
    val requestAccessibility: () -> Unit,
    val requestOverlay: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestCaptureSession: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * Explains the minimum permissions required for a single reader session. Direction to home
 * uses the driver's saved destination and offline address package, with no GPS permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingSheet(
    state: PermissionState,
    readiness: PermissionReadiness,
    accessibilityServiceEnabled: Boolean = false,
    actions: PermissionOnboardingActions,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = actions.dismiss,
        modifier = modifier,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Preparar o leitor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (readiness.canAnalyzeOffers) {
                        "A sessão está pronta. Você pode revisar permissões opcionais abaixo."
                    } else {
                        "Ative a acessibilidade e a sobreposição. A captura fica disponível só como fallback."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PermissionSetupItem(
                title = "Serviço de acessibilidade",
                description = "Lê o texto visível dos cards da Uber e 99 sem tocar, aceitar ou recusar corridas.",
                stateLabel = if (accessibilityServiceEnabled) "Ativado no Android" else "Ative manualmente nas configurações do Android",
                isComplete = accessibilityServiceEnabled,
                actionLabel = "Abrir acessibilidade",
                onAction = actions.requestAccessibility,
                required = true,
            )
            HorizontalDivider()
            PermissionSetupItem(
                title = "Sobreposição sobre outros apps",
                description = "Mostra o card do Driver Inteligente acima da oferta, sem tocar nos botões do app.",
                stateLabel = grantLabel(state.overlay),
                isComplete = state.overlay == PermissionGrant.GRANTED,
                actionLabel = "Permitir",
                onAction = actions.requestOverlay,
                required = true,
            )
            HorizontalDivider()
            PermissionSetupItem(
                title = "Captura de tela de fallback",
                description = "Usa OCR local quando o app de corrida não expõe texto suficiente para a acessibilidade.",
                stateLabel = captureSessionLabel(state.mediaProjection),
                isComplete = state.mediaProjection is MediaProjectionConsent.Granted,
                actionLabel = "Iniciar fallback",
                onAction = actions.requestCaptureSession,
                required = false,
            )
            HorizontalDivider()
            PermissionSetupItem(
                title = "Notificações",
                description = "Exibe o aviso de que o leitor está ativo em segundo plano.",
                stateLabel = grantLabel(state.notifications),
                isComplete = state.notifications == PermissionGrant.GRANTED,
                actionLabel = "Permitir",
                onAction = actions.requestNotifications,
                required = false,
            )
            Button(
                onClick = actions.dismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = readiness.canAnalyzeOffers,
            ) { Text(if (readiness.canAnalyzeOffers) "Concluir" else "Conceda as permissões necessárias") }
        }
    }
}

@Composable
private fun PermissionSetupItem(
    title: String,
    description: String,
    stateLabel: String,
    isComplete: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    required: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (required) "Necessária" else "Opcional",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isComplete) {
                Text("Pronta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun grantLabel(grant: PermissionGrant): String = when (grant) {
    PermissionGrant.GRANTED -> "Autorizada"
    PermissionGrant.DENIED -> "Não autorizada"
    PermissionGrant.NOT_REQUESTED -> "Ainda não solicitada"
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PermissionOnboardingSheetPreview() {
    val session = CaptureSessionId("preview-session")
    val state = PermissionState(overlay = PermissionGrant.GRANTED)
    DriverInteligenteTheme(DriverThemeMode.LIGHT) {
        PermissionOnboardingSheet(
            state = state,
            readiness = PermissionReadinessEvaluator().evaluate(state, session),
            accessibilityServiceEnabled = true,
            actions = PermissionOnboardingActions({}, {}, {}, {}, {}),
        )
    }
}
