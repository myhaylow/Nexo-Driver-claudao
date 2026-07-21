package br.com.nexo.driver.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.nexo.driver.capture.OfferCaptureService
import br.com.nexo.driver.analysis.OfferHistoryRepository
import br.com.nexo.driver.analysis.OfferSessionMetricsRepository
import br.com.nexo.driver.analysis.SessionTelemetryRepository
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.FavoriteDestinations
import br.com.nexo.driver.destination.SharedPreferencesDriverDestinationStore
import br.com.nexo.driver.destination.SharedPreferencesFavoriteDestinationsStore
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.profile.DriverProfile
import br.com.nexo.driver.profile.SharedPreferencesProfileStore
import br.com.nexo.driver.overlay.preferences.SharedPreferencesOverlayPreferenceStore
import br.com.nexo.driver.offline.SharedPreferencesOfflineMapPackageStore
import br.com.nexo.driver.permission.CaptureSessionId
import br.com.nexo.driver.permission.PermissionGrant
import br.com.nexo.driver.permission.PermissionReadinessEvaluator
import br.com.nexo.driver.permission.PermissionState
import br.com.nexo.driver.permission.PermissionStateReducer
import br.com.nexo.driver.ui.filters.FilterRuleEditorSheet
import br.com.nexo.driver.ui.filters.FilterPickerSheet
import br.com.nexo.driver.ui.filters.FilterRuleId
import br.com.nexo.driver.ui.filters.id
import br.com.nexo.driver.ui.destination.HomeDestinationScreen
import br.com.nexo.driver.ui.permission.PermissionOnboardingActions
import br.com.nexo.driver.ui.permission.PermissionOnboardingSheet
import br.com.nexo.driver.ui.settings.AppSettings
import br.com.nexo.driver.ui.settings.AppSettingsStore
import br.com.nexo.driver.block.SharedPreferencesBlockSettingsStore
import br.com.nexo.driver.cost.SharedPreferencesFuelSettingsStore
import br.com.nexo.driver.speech.SharedPreferencesSpeechSettingsStore
import br.com.nexo.driver.speech.SpeechSettings
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.NexoAppTheme
import br.com.nexo.driver.ui.mockup.MockupShellActions
import br.com.nexo.driver.ui.mockup.MockupShellState
import br.com.nexo.driver.ui.mockup.MockupTab
import br.com.nexo.driver.ui.mockup.NexoMockupShell
import br.com.nexo.driver.location.CurrentLocationServiceStatus
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import br.com.nexo.driver.accessibility.DriverAccessibilityService
import br.com.nexo.driver.gallery.GalleryOfferTester
import br.com.nexo.driver.gallery.message
import br.com.nexo.driver.BuildConfig
import br.com.nexo.driver.location.CurrentLocationService
import br.com.nexo.driver.location.CurrentLocationState
import br.com.nexo.driver.location.CurrentLocationStateRepository
import java.util.UUID

private enum class AppDestination(
    val label: String,
    val showInBottomBar: Boolean = true,
) {
    HOME("Início"),
    FILTERS("Filtros"),
    SETTINGS("Ajustes"),
    HOME_DESTINATION("Destino casa", showInBottomBar = false),
}

@Composable
fun NexoApp() {
    val context = LocalContext.current
    val overlayPreferencesStore = remember(context) {
        SharedPreferencesOverlayPreferenceStore.create(context)
    }
    var overlayPreferences by remember(overlayPreferencesStore) {
        mutableStateOf(overlayPreferencesStore.load())
    }
    val speechSettingsStore = remember(context) { SharedPreferencesSpeechSettingsStore.create(context) }
    var speechSettings by remember(speechSettingsStore) {
        mutableStateOf(speechSettingsStore.load())
    }
    val blockSettingsStore = remember(context) { SharedPreferencesBlockSettingsStore.create(context) }
    var blockSettings by remember(blockSettingsStore) { mutableStateOf(blockSettingsStore.load()) }
    val fuelSettingsStore = remember(context) { SharedPreferencesFuelSettingsStore.create(context) }
    var fuelSettings by remember(fuelSettingsStore) { mutableStateOf(fuelSettingsStore.load()) }
    var accessibilityServiceEnabled by remember(context) {
        mutableStateOf(context.isDriverAccessibilityServiceEnabled())
    }
    val galleryOfferTester = remember(context) { if (BuildConfig.DEBUG) GalleryOfferTester(context) else null }
    var galleryTestStatus by remember { mutableStateOf<String?>(null) }
    DisposableEffect(galleryOfferTester) {
        onDispose { galleryOfferTester?.close() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityServiceEnabled = context.isDriverAccessibilityServiceEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Single source of truth shared with OfferAnalysisProcessor, which reads the same settings from
    // the capture thread. Seven hand-rolled reader extensions used to live here alongside a second
    // copy of the key strings in the processor.
    val appSettingsStore = remember(context) { AppSettingsStore.create(context) }
    var appSettings by remember(appSettingsStore) { mutableStateOf(appSettingsStore.load()) }
    val updateSettings: ((AppSettings) -> AppSettings) -> Unit = { transform ->
        appSettings = appSettingsStore.save(transform(appSettings))
    }
    val profileStore = remember(context) { SharedPreferencesProfileStore.create(context) }
    val homeDestinationStore = remember(context) { SharedPreferencesDriverDestinationStore.create(context) }
    var homeDestination by remember(homeDestinationStore) { mutableStateOf(homeDestinationStore.load()) }
    val favoritesStore = remember(context) { SharedPreferencesFavoriteDestinationsStore.create(context) }
    var favoriteDestinations by remember(favoritesStore) {
        val loaded = favoritesStore.load()
        // Migração: quem já tinha um destino único ganha ele como primeiro favorito.
        val seeded = homeDestination?.takeIf { loaded.destinations.isEmpty() }
            ?.let { favoritesStore.save(FavoriteDestinations(listOf(it), 0)) }
            ?: loaded
        mutableStateOf(seeded)
    }
    val offlineMapPackageStore = remember(context) { SharedPreferencesOfflineMapPackageStore.create(context) }
    var offlineMapPackage by remember(offlineMapPackageStore) {
        mutableStateOf(offlineMapPackageStore.load())
    }
    var profileSnapshot by remember(profileStore) {
        val stored = profileStore.load()
        mutableStateOf(
            stored.takeIf { it.activeProfile != null } ?: profileStore.save(
                DriverProfile.create(
                    name = "Dia a dia",
                    rules = defaultRules(),
                    nowEpochMs = System.currentTimeMillis(),
                ),
            ),
        )
    }
    val activeProfile = requireNotNull(profileSnapshot.activeProfile)
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var editingRuleId by remember { mutableStateOf<FilterRuleId?>(null) }
    var showFilterPicker by remember { mutableStateOf(false) }
    var readerEnabled by remember { mutableStateOf(OfferCaptureService.isActive(context)) }
    var locationSnapshot by remember { mutableStateOf(CurrentLocationStateRepository.current()) }
    var sessionMetrics by remember { mutableStateOf(OfferSessionMetricsRepository.current()) }
    var offerHistory by remember { mutableStateOf(OfferHistoryRepository.current()) }
    var showPermissionOnboarding by remember { mutableStateOf(false) }
    var captureSessionId by remember { mutableStateOf(newCaptureSessionId()) }
    val permissionReducer = remember { PermissionStateReducer() }
    val readinessEvaluator = remember { PermissionReadinessEvaluator() }
    var permissionState by remember { mutableStateOf(initialPermissionState(context)) }
    var pendingLocationStart by remember { mutableStateOf(false) }
    val readiness = readinessEvaluator.evaluate(permissionState, captureSessionId, accessibilityServiceEnabled)

    DisposableEffect(Unit) {
        val locationSubscription = CurrentLocationStateRepository.subscribe { snapshot -> locationSnapshot = snapshot }
        val offerSubscription = OfferSessionMetricsRepository.subscribe { metrics -> sessionMetrics = metrics }
        val historySubscription = OfferHistoryRepository.subscribe { entries -> offerHistory = entries }
        onDispose {
            locationSubscription.close()
            offerSubscription.close()
            historySubscription.close()
        }
    }

    BackHandler(
        enabled = editingRuleId != null || showFilterPicker || showPermissionOnboarding ||
            destination == AppDestination.FILTERS || destination == AppDestination.HOME_DESTINATION ||
            destination == AppDestination.SETTINGS,
    ) {
        when {
            editingRuleId != null -> editingRuleId = null
            showFilterPicker -> showFilterPicker = false
            showPermissionOnboarding -> showPermissionOnboarding = false
            else -> destination = AppDestination.HOME
        }
    }

    // The service is the source of truth: projection can be stopped by Android at any time (for
    // example from the system privacy controls), without a tap in this Activity.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != OfferCaptureService.ACTION_READER_STATE_CHANGED) return
                val active = intent.getBooleanExtra(OfferCaptureService.EXTRA_READER_ACTIVE, false)
                readerEnabled = active
                if (!active) {
                    permissionState = permissionReducer.clearMediaProjection(permissionState)
                    captureSessionId = newCaptureSessionId()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(OfferCaptureService.ACTION_READER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Covers Activity recreation while a valid foreground capture continues.
        readerEnabled = OfferCaptureService.isActive(context)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionState = permissionReducer.setOverlay(
            permissionState,
            context.overlayPermissionGrant(),
        )
    }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionState = permissionReducer.setNotifications(
            permissionState,
            if (granted) PermissionGrant.GRANTED else PermissionGrant.DENIED,
        )
        if (pendingLocationStart) {
            pendingLocationStart = false
            if (granted) {
                CurrentLocationService.start(context)
            } else {
                CurrentLocationStateRepository.update(CurrentLocationState.PermissionMissing)
            }
        }
    }
    val locationPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingLocationStart = true
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                CurrentLocationService.start(context)
            }
        }
    }
    val mediaProjectionManager = remember(context) {
        context.getSystemService(MediaProjectionManager::class.java)
    }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            permissionState = permissionReducer.grantMediaProjection(permissionState, captureSessionId)
            OfferCaptureService.start(context, result.resultCode, data)
            // Wait for the service to create the virtual display and publish its active state.
            // This avoids showing an enabled switch when Android rejects a one-shot consent.
            readerEnabled = false
            showPermissionOnboarding = false
        } else {
            permissionState = permissionReducer.denyMediaProjection(permissionState)
            readerEnabled = false
        }
    }
    val galleryImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            galleryTestStatus = "Nenhuma imagem selecionada."
        } else {
            galleryTestStatus = "Lendo imagem e calculando oferta…"
            galleryOfferTester?.test(uri) { result ->
                galleryTestStatus = result.message()
            } ?: run {
                galleryTestStatus = "Teste de galeria disponível apenas em build de debug."
            }
        }
    }

    fun updateActiveProfile(transform: (DriverProfile) -> DriverProfile) {
        profileSnapshot = profileStore.save(transform(activeProfile))
    }

    DriverInteligenteTheme(
        mode = appSettings.themeMode,
        fontScale = appSettings.fontScale.multiplier,
        visualStyle = appSettings.visualStyle,
        colorVisionScheme = appSettings.colorVisionScheme,
    ) {
      NexoAppTheme {
        when (destination) {
            AppDestination.HOME_DESTINATION -> HomeDestinationScreen(
                currentDestination = homeDestination,
                currentOfflineMapPackage = offlineMapPackage,
                onNavigateBack = { destination = AppDestination.HOME },
                onSave = { selected ->
                    homeDestination = homeDestinationStore.save(selected)
                    favoriteDestinations = favoritesStore.save(favoriteDestinations.withUpdatedActive(selected))
                    destination = AppDestination.HOME
                },
                onDraftChanged = { draft ->
                    homeDestination = homeDestinationStore.save(draft)
                    favoriteDestinations = favoritesStore.save(favoriteDestinations.withUpdatedActive(draft))
                },
                onClear = {
                    favoriteDestinations = favoritesStore.save(favoriteDestinations.withRemovedActive())
                    homeDestination = favoriteDestinations.active?.let(homeDestinationStore::save)
                        ?: run {
                            homeDestinationStore.clear()
                            null
                        }
                    if (favoriteDestinations.destinations.isEmpty()) destination = AppDestination.HOME
                },
                favorites = favoriteDestinations,
                onSelectFavorite = { index ->
                    favoriteDestinations = favoritesStore.save(favoriteDestinations.withActive(index))
                    favoriteDestinations.active?.let { homeDestination = homeDestinationStore.save(it) }
                },
                onAddFavorite = {
                    val novo = DriverDestination(label = "Novo destino", enabled = false)
                    favoriteDestinations = favoritesStore.save(favoriteDestinations.withAdded(novo))
                    favoriteDestinations.active?.let { homeDestination = homeDestinationStore.save(it) }
                },
                onOfflineMapImported = { selected ->
                    offlineMapPackage
                        ?.takeIf { previous -> previous.contentUri != selected.contentUri }
                        ?.let { previous -> context.releaseOfflineMapReadPermission(previous.contentUri) }
                    offlineMapPackage = offlineMapPackageStore.save(selected)
                },
                onOfflineMapRemoved = { removed ->
                    context.releaseOfflineMapReadPermission(removed.contentUri)
                    offlineMapPackageStore.clear()
                    offlineMapPackage = null
                },
            )

            AppDestination.HOME, AppDestination.FILTERS, AppDestination.SETTINGS -> NexoMockupShell(
                tab = when (destination) {
                    AppDestination.FILTERS -> MockupTab.FILTERS
                    AppDestination.SETTINGS -> MockupTab.SETTINGS
                    else -> MockupTab.HOME
                },
                onTabChange = { selected ->
                    destination = when (selected) {
                        MockupTab.HOME -> AppDestination.HOME
                        MockupTab.FILTERS -> AppDestination.FILTERS
                        MockupTab.SETTINGS -> AppDestination.SETTINGS
                    }
                },
                state = MockupShellState(
                    readerEnabled = accessibilityServiceEnabled || readerEnabled,
                    readerStatusText = readerStatusText(accessibilityServiceEnabled, readerEnabled, Settings.canDrawOverlays(context)),
                    profileName = activeProfile.name,
                    profileEnabled = activeProfile.isEnabled,
                    rules = activeProfile.rules,
                    sessionKm = locationSnapshot.sessionDistanceMeters / 1_000.0,
                    locationEnabled = locationSnapshot.status in setOf(
                        CurrentLocationServiceStatus.ACQUIRING,
                        CurrentLocationServiceStatus.ACTIVE,
                        CurrentLocationServiceStatus.FIX_REJECTED,
                        CurrentLocationServiceStatus.MOVEMENT_REJECTED,
                    ),
                    fuelPriceCents = fuelSettings.pricePerLiterCents,
                    fuelKmPerLiter = fuelSettings.kilometersPerLiter,
                    overlayFields = overlayPreferences.fields,
                    overlayLayout = appSettings.overlayLayout,
                    overlayFontScale = appSettings.overlayFontScale,
                    homeDestinationName = homeDestination?.displayName(),
                    galleryTestStatus = galleryTestStatus,
                    showDebugTools = BuildConfig.DEBUG,
                    accessibilityEnabled = accessibilityServiceEnabled,
                    speakDecision = speechSettings.speakDecision,
                    blockSupermarkets = blockSettings.blockSupermarkets,
                    history = offerHistory,
                    readMetrics = sessionMetrics,
                    overlayPermissionGranted = Settings.canDrawOverlays(context),
                    sessionStartEpochMs = SessionTelemetryRepository.currentSessionStart(),
                    acceptThreshold = appSettings.acceptThreshold,
                    analyzeThreshold = appSettings.analyzeThreshold,
                    cardDurationMs = appSettings.cardDurationMs,
                ),
                actions = MockupShellActions(
                    onReaderToggle = { enabled ->
                        if (!enabled) {
                            OfferCaptureService.stop(context)
                            readerEnabled = false
                            permissionState = permissionReducer.clearMediaProjection(permissionState)
                            captureSessionId = newCaptureSessionId()
                            if (accessibilityServiceEnabled) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        } else {
                            captureSessionId = newCaptureSessionId()
                            permissionState = permissionReducer.clearMediaProjection(permissionState)
                            showPermissionOnboarding = true
                        }
                    },
                    onProfileEnabledChange = { enabled ->
                        updateActiveProfile { profile ->
                            profile.updated(isEnabled = enabled, updatedAtEpochMs = System.currentTimeMillis())
                        }
                    },
                    onRuleTargetChange = { metric, comparator, target ->
                        updateActiveProfile { profile ->
                            val exists = profile.rules.any { it.metric == metric && it.comparator == comparator }
                            profile.updated(
                                rules = if (exists) {
                                    profile.rules.map { rule ->
                                        if (rule.metric == metric && rule.comparator == comparator) {
                                            rule.copy(target = target)
                                        } else {
                                            rule
                                        }
                                    }
                                } else {
                                    profile.rules + FilterRule(metric, comparator, target = target)
                                },
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                        }
                    },
                    onRuleEnabledChange = { metric, comparator, enabled ->
                        updateActiveProfile { profile ->
                            profile.updated(
                                rules = profile.rules.map { rule ->
                                    if (rule.metric == metric && rule.comparator == comparator) {
                                        rule.copy(enabled = enabled)
                                    } else {
                                        rule
                                    }
                                },
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                        }
                    },
                    onEditRule = { metric, comparator -> editingRuleId = FilterRuleId(metric, comparator) },
                    onAddFilter = { showFilterPicker = true },
                    onLocationToggle = { enabled ->
                        if (!enabled) {
                            CurrentLocationService.stop(context)
                        } else {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted || coarseGranted) {
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingLocationStart = true
                                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    CurrentLocationService.start(context)
                                }
                            } else {
                                locationPermissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        }
                    },
                    onFuelPriceChange = { cents ->
                        fuelSettings = fuelSettingsStore.save(fuelSettings.copy(pricePerLiterCents = cents))
                    },
                    onFuelConsumptionChange = { kmPerLiter ->
                        fuelSettings = fuelSettingsStore.save(fuelSettings.copy(kilometersPerLiter = kmPerLiter))
                    },
                    onOverlayFieldsChange = { fields ->
                        runCatching { OverlayPreferences(fields) }.getOrNull()?.let { selected ->
                            overlayPreferences = overlayPreferencesStore.save(selected)
                        }
                    },
                    onOverlayLayoutChange = { style -> updateSettings { it.copy(overlayLayout = style) } },
                    onOverlayFontScaleChange = { scale -> updateSettings { it.copy(overlayFontScale = scale) } },
                    onConfigureHomeDestination = { destination = AppDestination.HOME_DESTINATION },
                    onOpenAccessibility = {
                        accessibilityServiceEnabled = context.isDriverAccessibilityServiceEnabled()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onSpeakDecisionChange = { enabled ->
                        speechSettings = speechSettingsStore.save(speechSettings.copy(speakDecision = enabled))
                    },
                    onBlockSupermarketsChange = { enabled ->
                        blockSettings = blockSettingsStore.save(blockSettings.copy(blockSupermarkets = enabled))
                    },
                    onTestGallery = {
                        if (!Settings.canDrawOverlays(context)) {
                            galleryTestStatus = "Autorize a sobreposição antes do teste para visualizar o card."
                            overlaySettingsLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                            )
                        } else {
                            galleryImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                    onDecisionThresholdsChange = { accept, analyze ->
                        // Written together so the analyze <= accept invariant can never be
                        // observed half-applied by the capture thread.
                        updateSettings { it.copy(acceptThreshold = accept, analyzeThreshold = analyze) }
                    },
                    onCardDurationChange = { millis -> updateSettings { it.copy(cardDurationMs = millis) } },
                ),
            )
        }
        if (showPermissionOnboarding) {
            PermissionOnboardingSheet(
                state = permissionState,
                readiness = readiness,
                accessibilityServiceEnabled = accessibilityServiceEnabled,
                actions = PermissionOnboardingActions(
                    requestAccessibility = {
                        accessibilityServiceEnabled = context.isDriverAccessibilityServiceEnabled()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    requestOverlay = {
                        overlaySettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    requestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            permissionState = permissionReducer.setNotifications(permissionState, PermissionGrant.GRANTED)
                        }
                    },
                    requestCaptureSession = {
                        if (permissionState.overlay != PermissionGrant.GRANTED) {
                            overlaySettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        } else {
                            captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                        }
                    },
                    dismiss = {
                        showPermissionOnboarding = false
                        if (!readerEnabled) permissionState = permissionReducer.clearMediaProjection(permissionState)
                    },
                ),
            )
        }
        val editingRule = editingRuleId?.let { ruleId ->
            activeProfile.rules.firstOrNull { it.id == ruleId }
        }
        if (editingRule != null) {
            FilterRuleEditorSheet(
                rule = editingRule,
                onSave = { updatedRule ->
                    updateActiveProfile { profile ->
                        val originalId = editingRule.id
                        profile.updated(
                            rules = profile.rules.replaceRule(originalId, updatedRule),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                    editingRuleId = null
                },
                onDismiss = { editingRuleId = null },
            )
        }
        if (showFilterPicker) {
            FilterPickerSheet(
                existingRules = activeProfile.rules,
                onSelect = { addedRule ->
                    updateActiveProfile { profile ->
                        profile.updated(
                            rules = profile.rules + addedRule,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                    editingRuleId = addedRule.id
                    showFilterPicker = false
                },
                onDismiss = { showFilterPicker = false },
            )
        }
      }
    }
}

private fun readerStatusText(
    accessibilityServiceEnabled: Boolean,
    captureFallbackEnabled: Boolean,
    overlayGranted: Boolean,
): String = when {
    accessibilityServiceEnabled && overlayGranted -> "Ativo"
    accessibilityServiceEnabled -> "Falta sobreposição"
    captureFallbackEnabled && overlayGranted -> "Ativo com OCR"
    captureFallbackEnabled -> "OCR sem sobreposição"
    else -> "Pausado"
}
private fun newCaptureSessionId() = CaptureSessionId(UUID.randomUUID().toString())

private fun initialPermissionState(context: android.content.Context): PermissionState = PermissionState(
    overlay = context.overlayPermissionGrant(),
    notifications = if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) PermissionGrant.GRANTED else PermissionGrant.NOT_REQUESTED,
)

private fun android.content.Context.overlayPermissionGrant(): PermissionGrant =
    if (Settings.canDrawOverlays(this)) PermissionGrant.GRANTED else PermissionGrant.NOT_REQUESTED

private fun android.content.Context.isDriverAccessibilityServiceEnabled(): Boolean {
    val expected = ComponentName(this, DriverAccessibilityService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    return splitter.any { service -> service.equals(expected, ignoreCase = true) }
}

private fun List<FilterRule>.conciseProfileSummary(): String {
    val enabledRules = filter(FilterRule::enabled)
    if (enabledRules.isEmpty()) return "Sem filtros ativos"
    val labels = enabledRules
        .take(3)
        .joinToString(" · ") { it.metric.label }
    val remaining = enabledRules.size - 3
    return if (remaining > 0) "$labels · +$remaining" else labels
}

private fun DriverDestination.displayName(): String = label ?: originalAddress ?: "Casa"

private fun DriverDestination.displayDetails(): String = coordinate?.let {
    "Raio ${arrivalRadiusMeters.toInt()} m · offline"
} ?: "Raio ${arrivalRadiusMeters.toInt()} m · texto"

private fun Context.releaseOfflineMapReadPermission(contentUri: String) {
    runCatching {
        contentResolver.releasePersistableUriPermission(
            Uri.parse(contentUri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun defaultRules(): List<FilterRule> = listOf(
    FilterRule(Metric.PAYOUT, Comparator.AT_LEAST, target = 800),
    FilterRule(Metric.RATE_PER_HOUR, Comparator.AT_LEAST, target = 4_000),
    FilterRule(Metric.RATE_PER_KM, Comparator.AT_LEAST, target = 175),
    FilterRule(Metric.NET_PROFIT_PER_HOUR, Comparator.AT_LEAST, target = 3_000),
    FilterRule(Metric.PICKUP_DURATION, Comparator.AT_MOST, target = 360),
    FilterRule(Metric.PICKUP_DISTANCE, Comparator.AT_MOST, target = 2_500),
    FilterRule(Metric.PASSENGER_RATING, Comparator.AT_LEAST, target = 480),
    FilterRule(Metric.HAS_MULTIPLE_STOPS, Comparator.IS_FALSE),
    FilterRule(Metric.ENDS_NEAR_HOME, Comparator.IS_TRUE, enabled = false),
)


/**
 * Replaces the rule selected by its original identity. If the comparator was changed to an
 * already configured bound, the edited value wins and the obsolete duplicate is removed.
 */
private fun List<FilterRule>.replaceRule(originalId: FilterRuleId, replacement: FilterRule): List<FilterRule> =
    mapNotNull { rule ->
        when {
            rule.id == originalId -> replacement
            replacement.id != originalId && rule.id == replacement.id -> null
            else -> rule
        }
    }

// The seven SharedPreferences reader extensions and their key constants moved to
// ui/settings/AppSettingsStore.kt, which OfferAnalysisProcessor also reads from.
