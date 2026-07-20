package br.com.nexo.driver.ui.destination

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.GeoCoordinate
import br.com.nexo.driver.destination.GeocoderDestinationResolver
import br.com.nexo.driver.destination.DestinationResolutionStatus
import br.com.nexo.driver.destination.GoogleMapsOfflineIntent
import br.com.nexo.driver.destination.offline.OfflineAddressPackageTsvCodec
import br.com.nexo.driver.offline.OfflineMapPackage
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A deliberately small, offline-first destination setup. Coordinates can be copied from any map
 * the driver already uses; no address is sent to a server by this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDestinationScreen(
    currentDestination: DriverDestination?,
    currentOfflineMapPackage: OfflineMapPackage?,
    onNavigateBack: () -> Unit,
    onSave: (DriverDestination) -> Unit,
    onDraftChanged: (DriverDestination) -> Unit = {},
    onClear: () -> Unit,
    onOfflineMapImported: (OfflineMapPackage) -> Unit,
    onOfflineMapRemoved: (OfflineMapPackage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var label by remember(currentDestination) { mutableStateOf(currentDestination?.label.orEmpty()) }
    var addressInput by remember(currentDestination) {
        mutableStateOf(currentDestination?.originalAddress ?: currentDestination?.standardizedAddress.orEmpty())
    }
    var standardizedAddress by remember(currentDestination) { mutableStateOf(currentDestination?.standardizedAddress) }
    var latitude by remember(currentDestination) {
        mutableStateOf(currentDestination?.coordinate?.latitude?.toInput().orEmpty())
    }
    var longitude by remember(currentDestination) {
        mutableStateOf(currentDestination?.coordinate?.longitude?.toInput().orEmpty())
    }
    var radius by remember(currentDestination) {
        mutableStateOf(currentDestination?.arrivalRadiusMeters?.toInput() ?: "2000")
    }
    var resolutionStatus by remember(currentDestination) {
        mutableStateOf(currentDestination?.resolutionStatus ?: DestinationResolutionStatus.UNAVAILABLE)
    }
    var addressEdited by remember(currentDestination) { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var resolutionGeneration by remember { mutableIntStateOf(0) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var offlineImportMessage by remember { mutableStateOf<String?>(null) }
    var geocodeMessage by remember { mutableStateOf<String?>(null) }
    val importScope = rememberCoroutineScope()
    val geocoder = remember(context) { GeocoderDestinationResolver(context) }
    LaunchedEffect(addressInput, addressEdited) {
        if (!addressEdited) return@LaunchedEffect
        delay(400L)
        parseDestination(
            label = label,
            address = addressInput,
            standardizedAddress = null,
            latitudeInput = "",
            longitudeInput = "",
            radiusInput = radius,
            resolutionStatus = DestinationResolutionStatus.FAILED,
        )?.let(onDraftChanged)
    }
    val offlineMapLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            offlineImportMessage = "Validando pacote TSV…"
            importScope.launch {
                val result = withContext(Dispatchers.IO) { context.persistOfflineMapPackage(uri) }
                result.fold(
                    onSuccess = { mapPackage ->
                        onOfflineMapImported(mapPackage)
                        offlineImportMessage = "Pacote TSV validado e vinculado para uso offline."
                    },
                    onFailure = { failure ->
                        offlineImportMessage = when (failure) {
                            is SecurityException -> "O Android não concedeu acesso permanente ao arquivo. Selecione-o novamente."
                            is OfflinePackageTooLargeException -> "O pacote excede o limite de 16 MB. Use somente o índice TSV de endereços."
                            is IllegalArgumentException -> "TSV inválido: confira cabeçalho, coordenadas e codificação UTF-8."
                            else -> "Não foi possível ler o pacote TSV selecionado."
                        }
                    },
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Destino casa", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Voltar")
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Defina Casa para destacar ofertas que terminam perto ou aproximam você do destino.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resolução offline primeiro", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "O TSV local tem prioridade. O Geocoder do Android só entra quando você resolver o endereço.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            OutlinedTextField(
                value = addressInput,
                onValueChange = {
                    addressInput = it
                    standardizedAddress = null
                    latitude = ""
                    longitude = ""
                    resolutionStatus = DestinationResolutionStatus.FAILED
                    addressEdited = true
                    resolutionGeneration++
                    geocodeMessage = "Coordenadas anteriores removidas. Resolva novamente ou use a comparação textual."
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endereço do destino") },
                placeholder = { Text("Ex.: Rua XV de Novembro, Curitiba") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = GeocoderDestinationResolver.isUseful(addressInput),
                onClick = {
                    val requestGeneration = ++resolutionGeneration
                    geocodeMessage = "Resolvendo endereço..."
                    geocoder.resolveAsync(addressInput) { resolution ->
                        Handler(Looper.getMainLooper()).post {
                            if (requestGeneration != resolutionGeneration) return@post
                            if (resolution.coordinate != null) {
                                standardizedAddress = resolution.standardizedAddress
                                latitude = resolution.coordinate.latitude.toInput()
                                longitude = resolution.coordinate.longitude.toInput()
                                resolutionStatus = DestinationResolutionStatus.RESOLVED
                                addressEdited = false
                                geocodeMessage = "Endereço resolvido. Confira o raio e salve."
                            } else {
                                standardizedAddress = null
                                latitude = ""
                                longitude = ""
                                resolutionStatus = resolution.status
                                geocodeMessage = "Não foi possível resolver agora. A comparação funcionará somente pelo texto."
                            }
                        }
                    }
                },
            ) { Text("Resolver endereço") }
            Text(
                "O pacote offline é um TSV selecionado por você; nada é enviado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            geocodeMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OfflineMapPackageCard(
                mapPackage = currentOfflineMapPackage,
                statusMessage = offlineImportMessage,
                onImport = { offlineMapLauncher.launch(arrayOf("text/tab-separated-values", "text/plain")) },
                onRemove = {
                    currentOfflineMapPackage?.let(onOfflineMapRemoved)
                    offlineImportMessage = "Pacote offline removido deste aplicativo."
                },
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it; validationError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome do destino (opcional)") },
                placeholder = { Text("Ex.: Casa") },
                singleLine = true,
            )
            NumericField(radius, { radius = it; validationError = null }, "Raio da casa (metros)", "2000")
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Ocultar coordenadas avançadas" else "Informar coordenadas manualmente")
            }
            if (showAdvanced) {
                NumericField(latitude, {
                    latitude = it
                    resolutionStatus = DestinationResolutionStatus.RESOLVED
                    validationError = null
                }, "Latitude", "Ex.: -25,4284")
                NumericField(longitude, {
                    longitude = it
                    resolutionStatus = DestinationResolutionStatus.RESOLVED
                    validationError = null
                }, "Longitude", "Ex.: -49,2733")
            }
            validationError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val parsed = parseDestination(
                        label, addressInput, standardizedAddress, latitude, longitude, radius, resolutionStatus,
                    )
                    if (parsed == null) validationError = "Informe um endereço específico e um raio entre 200 m e 20 km."
                    else onSave(parsed)
                },
            ) { Text("Salvar destino") }
            currentDestination?.coordinate?.let { coordinate ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!GoogleMapsOfflineIntent.open(context, coordinate, currentDestination.standardizedAddress ?: currentDestination.label)) {
                            geocodeMessage = "Nenhum aplicativo de mapas compatível foi encontrado."
                        }
                    },
                ) { Text("Abrir no Google Maps / mapa") }
            }
            if (currentDestination != null) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClear) { Text("Remover destino") }
            }
        }
    }
}

@Composable
private fun OfflineMapPackageCard(
    mapPackage: OfflineMapPackage?,
    statusMessage: String?,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Pacote offline de endereços", fontWeight = FontWeight.SemiBold)
            if (mapPackage == null) {
                Text(
                    "Selecione um arquivo TSV do Driver Inteligente já baixado. O Android mantém apenas a permissão de leitura; o arquivo não é copiado nem enviado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(modifier = Modifier.fillMaxWidth(), onClick = onImport) {
                    Text("Selecionar pacote offline")
                }
            } else {
                Text(mapPackage.displayName, fontWeight = FontWeight.Medium)
                Text(
                    "${mapPackage.sizeBytes.toFileSizeText()} • acesso salvo neste celular",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(modifier = Modifier.fillMaxWidth(), onClick = onImport) {
                    Text("Trocar pacote")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRemove) {
                    Text("Remover pacote")
                }
            }
            statusMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumericField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        // Some decimal keyboards omit the minus sign; text input accepts Brazilian negative
        // coordinates and comma decimals while validation remains strict on save.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        singleLine = true,
    )
}

private fun parseDestination(
    label: String,
    address: String,
    standardizedAddress: String?,
    latitudeInput: String,
    longitudeInput: String,
    radiusInput: String,
    resolutionStatus: DestinationResolutionStatus,
): DriverDestination? {
    val radius = radiusInput.toDecimalOrNull() ?: return null
    if (radius !in DriverDestination.MIN_HOME_RADIUS_METERS..DriverDestination.MAX_HOME_RADIUS_METERS) return null
    val latitude = latitudeInput.toDecimalOrNull()
    val longitude = longitudeInput.toDecimalOrNull()
    if ((latitude == null) != (longitude == null)) return null
    val coordinate = if (latitude != null && longitude != null) GeoCoordinate(latitude, longitude) else null
    if (coordinate?.isValid == false) return null
    val original = address.trim().takeIf(String::isNotBlank)
    if (coordinate == null && !GeocoderDestinationResolver.isUseful(original.orEmpty())) return null
    val trustedStatus = if (coordinate != null) DestinationResolutionStatus.RESOLVED else resolutionStatus
    return DriverDestination(
        coordinate = coordinate,
        label = label.trim().takeIf(String::isNotBlank),
        arrivalRadiusMeters = radius,
        standardizedAddress = standardizedAddress,
        preparedAtEpochMs = System.currentTimeMillis(),
        resolutionStatus = trustedStatus,
        enabled = true,
        originalAddress = original,
    )
}

private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun Double.toInput(): String = "%.6f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')

private fun Context.persistOfflineMapPackage(uri: Uri): Result<OfflineMapPackage> = runCatching {
    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The same persisted document URI can be reopened by the in-memory resolver after validation.
    val packageBytes = checkNotNull(contentResolver.openInputStream(uri)).use(::readBoundedOfflineAddressPackage)
    OfflineAddressPackageTsvCodec.decode(packageBytes)
    val metadata = contentResolver.readOfflineMapMetadata(uri)
    OfflineMapPackage(
        contentUri = uri.toString(),
        displayName = metadata.displayName,
        sizeBytes = metadata.sizeBytes,
        importedAtEpochMs = System.currentTimeMillis(),
    )
}

private fun readBoundedOfflineAddressPackage(input: java.io.InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (output.size() + read > MAX_OFFLINE_ADDRESS_PACKAGE_BYTES) {
            throw OfflinePackageTooLargeException()
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private class OfflinePackageTooLargeException : IllegalArgumentException()

private data class OfflineMapMetadata(
    val displayName: String,
    val sizeBytes: Long?,
)

private fun android.content.ContentResolver.readOfflineMapMetadata(uri: Uri): OfflineMapMetadata {
    var displayName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "mapa-offline"
    var sizeBytes: Long? = null
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { column ->
                    cursor.getString(column)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
            cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { sizeBytes = cursor.getLong(it).takeIf { value -> value >= 0L } }
        }
    }
    return OfflineMapMetadata(displayName = displayName, sizeBytes = sizeBytes)
}

private fun Long?.toFileSizeText(): String = when {
    this == null -> "tamanho não informado"
    this < 1_024L -> "$this B"
    this < 1_024L * 1_024L -> "${this / 1_024L} KB"
    this < 1_024L * 1_024L * 1_024L -> "${"%.1f".format(java.util.Locale.US, this / (1_024.0 * 1_024.0))} MB"
    else -> "${"%.2f".format(java.util.Locale.US, this / (1_024.0 * 1_024.0 * 1_024.0))} GB"
}

private const val MAX_OFFLINE_ADDRESS_PACKAGE_BYTES = 16 * 1024 * 1024

@Preview(showBackground = true)
@Composable
private fun HomeDestinationScreenPreview() {
    DriverInteligenteTheme {
        HomeDestinationScreen(
            currentDestination = null,
            currentOfflineMapPackage = null,
            onNavigateBack = {},
            onSave = {},
            onClear = {},
            onOfflineMapImported = {},
            onOfflineMapRemoved = {},
        )
    }
}
