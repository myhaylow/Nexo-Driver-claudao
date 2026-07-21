package br.com.nexo.driver.ui.destination

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.nexo.driver.destination.CuritibaPlace
import br.com.nexo.driver.destination.CuritibaPlacesCatalog
import br.com.nexo.driver.destination.DestinationResolutionStatus
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.FavoriteDestinations
import br.com.nexo.driver.destination.GeoCoordinate
import br.com.nexo.driver.destination.GeocoderDestinationResolver
import br.com.nexo.driver.destination.GoogleMapsOfflineIntent
import br.com.nexo.driver.destination.OneShotLocation
import br.com.nexo.driver.destination.offline.OfflineAddressPackageTsvCodec
import br.com.nexo.driver.offline.OfflineMapPackage
import br.com.nexo.driver.ui.mockup.AccordionSection
import br.com.nexo.driver.ui.mockup.M
import br.com.nexo.driver.ui.mockup.SelectButton
import br.com.nexo.driver.ui.mockup.SliderRow
import br.com.nexo.driver.ui.mockup.ThemedSlider
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Configuração do Destino Casa no visual do mockup: favoritos com troca rápida, endereço com
 * sugestões offline de Curitiba/RMC, GPS de um toque, raio com slider e presets, e as opções
 * avançadas (coordenadas manuais + pacote TSV) recolhidas numa sanfona.
 */
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
    favorites: FavoriteDestinations = FavoriteDestinations(),
    onSelectFavorite: (Int) -> Unit = {},
    onAddFavorite: () -> Unit = {},
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
    var radiusMeters by remember(currentDestination) {
        mutableDoubleStateOf(currentDestination?.arrivalRadiusMeters ?: 2_000.0)
    }
    var resolutionStatus by remember(currentDestination) {
        mutableStateOf(currentDestination?.resolutionStatus ?: DestinationResolutionStatus.UNAVAILABLE)
    }
    var addressEdited by remember(currentDestination) { mutableStateOf(false) }
    var resolutionGeneration by remember { mutableIntStateOf(0) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var offlineImportMessage by remember { mutableStateOf<String?>(null) }
    var geocodeMessage by remember { mutableStateOf<String?>(null) }
    var expandedAdvanced by remember { mutableStateOf(false) }
    var pendingGpsCapture by remember { mutableStateOf(false) }
    var showMiniMap by remember { mutableStateOf(false) }
    val importScope = rememberCoroutineScope()
    val geocoder = remember(context) { GeocoderDestinationResolver(context) }

    // Sugestões offline (Curitiba/RMC) enquanto digita.
    val suggestions = remember(addressInput, addressEdited) {
        if (addressEdited) CuritibaPlacesCatalog.search(addressInput) else emptyList()
    }

    fun applyPlace(place: CuritibaPlace) {
        addressInput = place.displayName
        standardizedAddress = place.displayName
        latitude = place.coordinate.latitude.toInput()
        longitude = place.coordinate.longitude.toInput()
        resolutionStatus = DestinationResolutionStatus.RESOLVED
        addressEdited = false
        if (label.isBlank()) label = place.name
        geocodeMessage = "Coordenada aproximada de ${place.displayName} aplicada (precisão de bairro). " +
            "Para o ponto exato use o GPS ou resolva o endereço completo."
    }

    fun captureGps() {
        geocodeMessage = "Buscando sua localização atual…"
        OneShotLocation.request(context) { coordinate ->
            if (coordinate != null) {
                latitude = coordinate.latitude.toInput()
                longitude = coordinate.longitude.toInput()
                resolutionStatus = DestinationResolutionStatus.RESOLVED
                addressEdited = false
                if (label.isBlank()) label = "Casa"
                if (addressInput.isBlank()) addressInput = "Minha localização atual"
                geocodeMessage = "Localização capturada! Confira o raio e salve."
            } else {
                geocodeMessage = "Não foi possível obter a localização. Verifique o GPS e a permissão."
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (pendingGpsCapture) {
            pendingGpsCapture = false
            if (granted) captureGps() else geocodeMessage = "Permissão de localização negada."
        }
    }

    LaunchedEffect(addressInput, addressEdited) {
        if (!addressEdited) return@LaunchedEffect
        delay(400L)
        parseDestination(
            label = label,
            address = addressInput,
            standardizedAddress = null,
            latitudeInput = "",
            longitudeInput = "",
            radiusMeters = radiusMeters,
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

    Column(modifier = modifier.fillMaxSize().background(M.slate950)) {
        // ---------------- Header ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .background(M.slate900)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(M.slate800)
                    .clickable(onClick = onNavigateBack)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = M.white, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Destino Casa", color = M.white, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "Corridas que terminam perto daqui viram \"sentido casa\"",
                    color = M.slate400,
                    fontSize = 11.sp,
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------- Favoritos ----------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(M.slate900)
                    .border(1.dp, M.slate800, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text("Meus Destinos", color = M.slate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    favorites.destinations.forEachIndexed { index, favorite ->
                        val selected = index == favorites.activeIndex
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) M.emerald600 else M.slate800)
                                .border(
                                    1.dp,
                                    if (selected) M.emerald500 else M.slate700,
                                    RoundedCornerShape(20.dp),
                                )
                                .clickable { onSelectFavorite(index) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Home, null,
                                tint = if (selected) M.white else M.slate400,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                favorite.label ?: favorite.originalAddress ?: "Destino ${index + 1}",
                                color = if (selected) M.white else M.slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (favorites.destinations.size < FavoriteDestinations.MAX_FAVORITES) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(M.slate950)
                                .border(1.dp, M.emerald500.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .clickable(onClick = onAddFavorite)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Add, null, tint = M.emerald400, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Novo", color = M.emerald400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ---------------- Onde fica ----------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(M.slate900)
                    .border(1.dp, M.slate800, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Onde fica?", color = M.white, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                // GPS de 1 toque — o caminho mais rápido.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(M.emerald600)
                        .clickable {
                            if (OneShotLocation.hasPermission(context)) {
                                captureGps()
                            } else {
                                pendingGpsCapture = true
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.MyLocation, null, tint = M.white, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Usar minha localização atual",
                        color = M.white,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }

                DarkTextField(
                    value = addressInput,
                    onValueChange = {
                        addressInput = it
                        standardizedAddress = null
                        latitude = ""
                        longitude = ""
                        resolutionStatus = DestinationResolutionStatus.FAILED
                        addressEdited = true
                        resolutionGeneration++
                        geocodeMessage = null
                    },
                    label = "Ou digite o endereço / bairro",
                    placeholder = "Ex.: Água Verde, Sítio Cercado, Rua XV…",
                )

                // Sugestões offline de Curitiba/RMC
                if (suggestions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { place ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(M.slate950)
                                    .border(1.dp, M.emerald500.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { applyPlace(place) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Place, null, tint = M.emerald400, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(place.displayName, color = M.slate200, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Resolver via geocoder (online), para endereço exato com número.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, M.blue500.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable(enabled = GeocoderDestinationResolver.isUseful(addressInput)) {
                            val requestGeneration = ++resolutionGeneration
                            geocodeMessage = "Resolvendo endereço…"
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
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Resolver endereço exato (internet)",
                        color = if (GeocoderDestinationResolver.isUseful(addressInput)) M.blue400 else M.slate500,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }

                geocodeMessage?.let { Text(it, color = M.slate400, fontSize = 11.sp) }
                if (latitude.isNotBlank()) {
                    Text(
                        "✓ Coordenada definida ($latitude, $longitude)",
                        color = M.emerald400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                DarkTextField(
                    value = label,
                    onValueChange = { label = it; validationError = null },
                    label = "Nome do destino (opcional)",
                    placeholder = "Ex.: Casa",
                )
            }

            // ---------------- Raio de chegada ----------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(M.slate900)
                    .border(1.dp, M.slate800, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SliderRow(
                    "Raio de Chegada",
                    radiusMeters.toRadiusText(),
                    M.emerald400,
                ) {
                    ThemedSlider(
                        value = radiusMeters.toFloat().coerceIn(200f, 5_000f),
                        onValue = { radiusMeters = ((it / 100).roundToInt() * 100).toDouble() },
                        range = 200f..5_000f,
                        accent = M.emerald500,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500.0, 1_000.0, 2_000.0, 5_000.0).forEach { preset ->
                        SelectButton(
                            text = preset.toRadiusText(),
                            selected = radiusMeters == preset,
                            selectedBg = M.emerald600,
                            selectedBorder = M.emerald500,
                            modifier = Modifier.weight(1f),
                        ) { radiusMeters = preset }
                    }
                }
                Text(
                    "Corridas que terminam até ${radiusMeters.toRadiusText()} do destino contam como \"sentido casa\".",
                    color = M.slate400,
                    fontSize = 11.sp,
                )
            }

            // ---------------- Avançado ----------------
            AccordionSection(
                icon = Icons.Outlined.Tune,
                iconTint = M.indigo400,
                iconBg = M.indigo500.copy(alpha = 0.1f),
                title = "Avançado",
                subtitle = "Coordenadas manuais e pacote offline TSV",
                expanded = expandedAdvanced,
                onToggle = { expandedAdvanced = !expandedAdvanced },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DarkTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = "Latitude",
                        placeholder = "-25.4284",
                    )
                    DarkTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = "Longitude",
                        placeholder = "-49.2733",
                    )
                    Text(
                        "Pacote offline de endereços (TSV): o arquivo local tem prioridade na leitura das ofertas; nada é enviado.",
                        color = M.slate400,
                        fontSize = 11.sp,
                    )
                    if (currentOfflineMapPackage == null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, M.emerald500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable {
                                    offlineMapLauncher.launch(arrayOf("text/tab-separated-values", "text/plain"))
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Selecionar pacote offline", color = M.emerald400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            "${currentOfflineMapPackage.displayName} • ${currentOfflineMapPackage.sizeBytes.toFileSizeText()}",
                            color = M.slate200,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, M.emerald500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        offlineMapLauncher.launch(arrayOf("text/tab-separated-values", "text/plain"))
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Trocar", color = M.emerald400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, M.red500.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        onOfflineMapRemoved(currentOfflineMapPackage)
                                        offlineImportMessage = "Pacote offline removido deste aplicativo."
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Remover", color = M.red400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    offlineImportMessage?.let { Text(it, color = M.slate400, fontSize = 11.sp) }
                }
            }

            validationError?.let {
                Text(it, color = M.red400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // ---------------- Ações ----------------
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(M.emerald600)
                    .clickable {
                        val parsed = parseDestination(
                            label = label,
                            address = addressInput,
                            standardizedAddress = standardizedAddress,
                            latitudeInput = latitude,
                            longitudeInput = longitude,
                            radiusMeters = radiusMeters,
                            resolutionStatus = resolutionStatus,
                        )
                        if (parsed == null) {
                            validationError =
                                "Defina uma localização (GPS, sugestão ou endereço) antes de salvar."
                        } else {
                            validationError = null
                            onSave(parsed)
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Salvar destino", color = M.white, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }

            // O mapa embutido usa a coordenada em edição (GPS/sugestão) mesmo antes de salvar.
            val mapCoordinate = latitude.toDecimalOrNull()?.let { lat ->
                longitude.toDecimalOrNull()?.let { lng -> GeoCoordinate(lat, lng) }
            }?.takeIf(GeoCoordinate::isValid) ?: currentDestination?.coordinate

            mapCoordinate?.let { coordinate ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, M.blue500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { showMiniMap = true }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Map, null, tint = M.blue400, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ver no mapa", color = M.blue400, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (showMiniMap) {
                    DestinationMiniMapDialog(
                        coordinate = coordinate,
                        radiusMeters = radiusMeters,
                        label = label.takeIf { it.isNotBlank() }
                            ?: currentDestination?.label
                            ?: currentDestination?.standardizedAddress,
                        onDismiss = { showMiniMap = false },
                    )
                }
            }

            if (currentDestination != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, M.red500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onClear)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Remover este destino", color = M.red400, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = M.slate400, fontSize = 12.sp) },
        placeholder = { Text(placeholder, color = M.slate500, fontSize = 13.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = M.white,
            unfocusedTextColor = M.slate200,
            focusedBorderColor = M.emerald500,
            unfocusedBorderColor = M.slate700,
            cursorColor = M.emerald400,
            focusedContainerColor = M.slate950,
            unfocusedContainerColor = M.slate950,
        ),
    )
}

private fun Double.toRadiusText(): String =
    if (this >= 1_000.0) String.format(PT_BR, "%.1f km", this / 1_000.0).replace(",0", "") else "${toInt()} m"

private fun parseDestination(
    label: String,
    address: String,
    standardizedAddress: String?,
    latitudeInput: String,
    longitudeInput: String,
    radiusMeters: Double,
    resolutionStatus: DestinationResolutionStatus,
): DriverDestination? {
    if (radiusMeters !in DriverDestination.MIN_HOME_RADIUS_METERS..DriverDestination.MAX_HOME_RADIUS_METERS) return null
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
        arrivalRadiusMeters = radiusMeters,
        standardizedAddress = standardizedAddress,
        preparedAtEpochMs = System.currentTimeMillis(),
        resolutionStatus = trustedStatus,
        enabled = true,
        originalAddress = original,
    )
}

private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun Double.toInput(): String = "%.6f".format(Locale.US, this).trimEnd('0').trimEnd('.')

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
    this < 1_024L * 1_024L * 1_024L -> "${"%.1f".format(Locale.US, this / (1_024.0 * 1_024.0))} MB"
    else -> "${"%.2f".format(Locale.US, this / (1_024.0 * 1_024.0 * 1_024.0))} GB"
}

private const val MAX_OFFLINE_ADDRESS_PACKAGE_BYTES = 16 * 1024 * 1024
