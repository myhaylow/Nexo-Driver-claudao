package br.com.nexo.driver.analysis

import android.content.Context
import br.com.nexo.driver.accessibility.AnalysisSource
import br.com.nexo.driver.destination.SharedPreferencesDriverDestinationStore
import br.com.nexo.driver.destination.GeocoderDestinationResolver
import br.com.nexo.driver.destination.GeocodedAddressCache
import br.com.nexo.driver.destination.OfferDestinationGeocoder
import br.com.nexo.driver.destination.offline.DestinationOfferEnricher
import br.com.nexo.driver.destination.offline.OfflineAddressPackageTsvCodec
import br.com.nexo.driver.destination.offline.OfflineAddressResolver
import br.com.nexo.driver.evaluation.Comparator
import br.com.nexo.driver.evaluation.DEFAULT_ACCEPT_THRESHOLD
import br.com.nexo.driver.evaluation.DEFAULT_ANALYZE_THRESHOLD
import br.com.nexo.driver.evaluation.EvaluationMode
import br.com.nexo.driver.evaluation.FilterRule
import br.com.nexo.driver.evaluation.Metric
import br.com.nexo.driver.evaluation.OfferEvaluator
import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferField
import br.com.nexo.driver.offline.SharedPreferencesOfflineMapPackageStore
import br.com.nexo.driver.overlay.OfferOverlayPresenter
import br.com.nexo.driver.overlay.OfferOverlayUiModel
import br.com.nexo.driver.overlay.preferences.SharedPreferencesOverlayPreferenceStore
import br.com.nexo.driver.profile.SharedPreferencesProfileStore
import br.com.nexo.driver.block.SharedPreferencesBlockSettingsStore
import br.com.nexo.driver.block.SupermarketBlocklistLoader
import br.com.nexo.driver.cost.SharedPreferencesFuelSettingsStore
import br.com.nexo.driver.cost.NetProfitCalculator
import br.com.nexo.driver.overlay.OverlayStatus
import br.com.nexo.driver.speech.OfferDecisionSpeaker
import br.com.nexo.driver.speech.SharedPreferencesSpeechSettingsStore
import br.com.nexo.driver.ui.settings.AppSettingsStore
import br.com.nexo.driver.ui.theme.ColorVisionScheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.ui.theme.DriverVisualStyle
import java.util.concurrent.Executors

/**
 * Shared final stage for Accessibility and OCR readers. It keeps analysis behaviour identical
 * once either source has produced a parsed offer.
 */
class OfferAnalysisProcessor(
    private val context: Context,
    private val evaluator: OfferEvaluator = OfferEvaluator(),
    private val presenter: OfferOverlayPresenter = OfferOverlayPresenter(evaluator),
    private val speaker: OfferDecisionSpeaker? = null,
) {
    private val appContext = context.applicationContext
    private val offlinePackageStore by lazy { SharedPreferencesOfflineMapPackageStore.create(appContext) }
    private val offlineLoader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "driver-offline-address-loader").apply { isDaemon = true }
    }
    private val offlineResolverLock = Any()
    @Volatile private var cachedOfflineKey: String? = null
    @Volatile private var cachedOfflineResolver: OfflineAddressResolver? = null
    private var loadingOfflineKey: String? = null
    private val offerDestinationGeocoder by lazy {
        OfferDestinationGeocoder(GeocoderDestinationResolver(appContext))
    }
    private val supermarketBlocklistLoader by lazy { SupermarketBlocklistLoader(appContext) }
    private val appSettingsStore by lazy { AppSettingsStore.create(appContext) }

    init {
        val migration = appContext.getSharedPreferences(PRIVACY_MIGRATION_PREFERENCES, Context.MODE_PRIVATE)
        if (!migration.getBoolean(KEY_OLD_GEOCODE_CACHE_CLEARED, false)) {
            GeocodedAddressCache.create(appContext).clear()
            appContext.getSharedPreferences(LEGACY_HISTORY_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit()
            migration.edit().putBoolean(KEY_OLD_GEOCODE_CACHE_CLEARED, true).commit()
        }
        refreshOfflineResolverAsync()
    }

    fun analyze(
        offer: NormalizedOffer,
        source: AnalysisSource,
        allowSideEffects: Boolean = true,
    ): OfferAnalysisResult? {
        val destinationEnriched = currentDestinationOfferEnricher()?.enrich(offer) ?: offer.withUnknownHomeMatch()
        val enrichedOffer = destinationEnriched.withBlocklistMatch()
        val evaluator = currentEvaluator()
        val profile = SharedPreferencesProfileStore.create(appContext).load().activeProfile
        val rules = profile?.takeIf { it.isEnabled }?.rules.orEmpty() + blocklistRule(enrichedOffer)
        val overlayPreferences = SharedPreferencesOverlayPreferenceStore.create(appContext).load()
        val fuelSettings = SharedPreferencesFuelSettingsStore.create(appContext).load()
        val derived = evaluator.derive(enrichedOffer)
        val profitConfidence = minOf(enrichedOffer.payout.score, derived.totalDistance.score)
        val profitEstimate = NetProfitCalculator(fuelSettings).estimate(
                enrichedOffer.payout.value?.cents,
                derived.totalDistance.value?.meters,
            )
        val netProfit = Confidence(
            value = profitEstimate?.netProfitCents,
            score = profitConfidence,
            source = FieldSource.DERIVED,
        )
        val netProfitPercent = Confidence(
            value = profitEstimate?.netProfitPercentScaled,
            score = minOf(profitConfidence, enrichedOffer.payout.score),
            source = FieldSource.DERIVED,
        )
        val duration = derived.totalDuration.value?.seconds
        val netProfitPerHour = Confidence(
            value = if (profitEstimate != null && duration != null && duration > 0) {
                profitEstimate.netProfitCents * 3_600 / duration
            } else {
                null
            },
            score = minOf(profitConfidence, derived.totalDuration.score),
            source = FieldSource.DERIVED,
        )
        val evaluation = evaluator.evaluate(
            offer = enrichedOffer,
            rules = rules,
            netProfit = netProfit,
            netProfitPercent = netProfitPercent,
            netProfitPerHour = netProfitPerHour,
        )
        val baseOverlay = presenter.present(enrichedOffer, evaluation, overlayPreferences.fields, fuelSettings)
        // The blocklist is now an eliminatory rule, so the evaluator has already forced the reject.
        // The model only needs the flag that drives the spoken phrase and the card's reason line.
        val blocked = enrichedOffer.pickupIsBlocked.value == true
        val overlay = if (blocked) {
            baseOverlay.copy(isTowardHome = false, isBlockedSupermarket = true)
        } else {
            baseOverlay
        }
        val settings = SharedPreferencesSpeechSettingsStore.create(appContext).load()

        if (allowSideEffects) {
            OfferSessionMetricsRepository.record(enrichedOffer)
        }
        if (allowSideEffects && settings.speakDecision) {
            speaker?.speak(overlay)
        }

        return OfferAnalysisResult(
            offer = enrichedOffer,
            overlay = overlay,
            appearance = currentOverlayAppearance(),
        )
    }

    /**
     * Resolves the driver's blocklist against this offer's pickup and records the outcome on the
     * offer itself. Feeding it through the evaluator as a normal eliminatory rule (rather than
     * rewriting the overlay afterwards) keeps the block visible in [EvaluationResult], explainable
     * by the same reason line as every other rule, and testable alongside them. It only changes
     * what is displayed and spoken — it never automates accepting or refusing.
     */
    private fun NormalizedOffer.withBlocklistMatch(): NormalizedOffer {
        val blockEnabled = SharedPreferencesBlockSettingsStore.create(appContext).load().blockSupermarkets
        if (!blockEnabled) return this
        val pickup = pickup.location.value
        val match = supermarketBlocklistLoader.load().matchPickup(pickup?.coordinate, pickup?.address)
        return copy(
            pickupIsBlocked = Confidence(match.matched, 1f, FieldSource.DERIVED),
        )
    }

    /** Present only while the blocklist has actually resolved, so it never votes on unknown data. */
    private fun blocklistRule(offer: NormalizedOffer): List<FilterRule> =
        if (offer.pickupIsBlocked.value == null) {
            emptyList()
        } else {
            listOf(
                FilterRule(
                    metric = Metric.PICKUP_IS_BLOCKED,
                    comparator = Comparator.IS_FALSE,
                    mode = EvaluationMode.ELIMINATORY,
                ),
            )
        }

    /**
     * Resolves an uncached textual drop-off after the first overlay is already visible. The
     * returned analysis deliberately disables speech/session counters; the caller also verifies that the
     * same offer is still active before replacing the card.
     */
    fun analyzeDestinationUpdateAsync(
        offer: NormalizedOffer,
        source: AnalysisSource,
        callback: (OfferAnalysisResult) -> Unit,
    ) {
        if (offer.trip.location.value?.coordinate?.isValid == true) return
        refreshOfflineResolverAsync {
            val offlineResult = analyze(offer, source, allowSideEffects = false)
            if (offlineResult?.offer?.endsNearHome?.value != null) {
                callback(offlineResult)
                return@refreshOfflineResolverAsync
            }
            offerDestinationGeocoder.resolveAsync(offer) { enriched ->
                if (enriched.trip.location.value?.coordinate?.isValid != true) return@resolveAsync
                if (enriched.trip.location.value?.coordinate == offer.trip.location.value?.coordinate) return@resolveAsync
                analyze(enriched, source, allowSideEffects = false)?.let(callback)
            }
        }
    }

    /**
     * Fast path used for every offer. It only reads small preferences and an already-decoded,
     * in-memory resolver. Selecting or replacing a TSV schedules decoding away from the capture
     * thread, so disk I/O can never delay the first overlay.
     */
    private fun currentDestinationOfferEnricher(): DestinationOfferEnricher? = runCatching {
        val destination = SharedPreferencesDriverDestinationStore.create(appContext).load() ?: return null
        val selectedPackage = offlinePackageStore.load()
        val key = selectedPackage?.cacheKey()
        val resolver = cachedOfflineResolver.takeIf { key != null && cachedOfflineKey == key }
        if (key != cachedOfflineKey) refreshOfflineResolverAsync()
        DestinationOfferEnricher(resolver, destination)
    }.getOrNull()

    private fun refreshOfflineResolverAsync(onReady: (() -> Unit)? = null) {
        val selectedPackage = offlinePackageStore.load()
        val key = selectedPackage?.cacheKey()
        synchronized(offlineResolverLock) {
            if (key == cachedOfflineKey) {
                onReady?.invoke()
                return
            }
            if (loadingOfflineKey == key) {
                if (onReady != null) offlineLoader.execute {
                    while (synchronized(offlineResolverLock) { loadingOfflineKey == key }) Thread.yield()
                    onReady()
                }
                return
            }
            loadingOfflineKey = key
        }
        offlineLoader.execute {
            val resolver = selectedPackage?.let { mapPackage ->
                runCatching {
                    appContext.contentResolver.openInputStream(android.net.Uri.parse(mapPackage.contentUri))
                        ?.use(::readBoundedAddressPackage)
                        ?.let(OfflineAddressPackageTsvCodec::decode)
                        ?.let(OfflineAddressResolver::create)
                }.getOrNull()
            }
            synchronized(offlineResolverLock) {
                if (offlinePackageStore.load()?.cacheKey() == key) {
                    cachedOfflineKey = key
                    cachedOfflineResolver = resolver
                }
                if (loadingOfflineKey == key) loadingOfflineKey = null
            }
            onReady?.invoke()
        }
    }

    private fun br.com.nexo.driver.offline.OfflineMapPackage.cacheKey(): String =
        "$contentUri|$importedAtEpochMs|${sizeBytes ?: -1L}"

    private fun readBoundedAddressPackage(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_ADDRESS_PACKAGE_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_ADDRESS_PACKAGE_BYTES) {
                throw IllegalArgumentException("Offline address package is too large.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun NormalizedOffer.withUnknownHomeMatch(): NormalizedOffer {
        val homeMatch = Confidence<Boolean>(value = null, score = 0f, source = FieldSource.DERIVED)
        return copy(
            endsNearHome = homeMatch,
            fieldConfidence = fieldConfidence + (OfferField.ENDS_NEAR_HOME to homeMatch.score),
        )
    }

    private fun currentOverlayAppearance(): OverlayAppearance {
        val settings = appSettingsStore.load()
        return OverlayAppearance(
            themeMode = settings.themeMode,
            fontScale = settings.fontScale.multiplier,
            visualStyle = settings.visualStyle,
            colorVisionScheme = settings.colorVisionScheme,
            cardDurationMs = settings.cardDurationMs,
        )
    }

    /** User-tunable decision thresholds, mirroring what comparable apps expose as good/bad bands. */
    private fun currentEvaluator(): OfferEvaluator {
        val settings = appSettingsStore.load()
        if (
            settings.acceptThreshold == DEFAULT_ACCEPT_THRESHOLD &&
            settings.analyzeThreshold == DEFAULT_ANALYZE_THRESHOLD
        ) {
            return evaluator
        }
        return OfferEvaluator(
            acceptThreshold = settings.acceptThreshold,
            analyzeThreshold = settings.analyzeThreshold,
        )
    }

    private companion object {
        private const val APP_SETTINGS_PREFERENCES = "driver_inteligente_app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_VISUAL_STYLE = "visual_style"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_COLOR_VISION = "color_vision_scheme"
        private const val KEY_CARD_DURATION_MS = "overlay_card_duration_ms"
        private const val KEY_ACCEPT_THRESHOLD = "decision_accept_threshold"
        private const val KEY_ANALYZE_THRESHOLD = "decision_analyze_threshold"
        private const val DEFAULT_ADDRESS_PACKAGE_BUFFER_BYTES = 16 * 1024
        private const val MAX_ADDRESS_PACKAGE_BYTES = 16 * 1024 * 1024
        private const val PRIVACY_MIGRATION_PREFERENCES = "driver_privacy_migrations"
        private const val KEY_OLD_GEOCODE_CACHE_CLEARED = "old_offer_geocode_cache_cleared_v1"
        private const val LEGACY_HISTORY_PREFERENCES = "driver_inteligente_offer_history"
    }
}

data class OfferAnalysisResult(
    val offer: NormalizedOffer,
    val overlay: OfferOverlayUiModel,
    val appearance: OverlayAppearance,
)

data class OverlayAppearance(
    val themeMode: DriverThemeMode,
    val fontScale: Float,
    val visualStyle: DriverVisualStyle,
    val colorVisionScheme: ColorVisionScheme = ColorVisionScheme.NORMAL,
    /** How long the card stays on screen. Uber's own offer sheet can outlast the old fixed 8s. */
    val cardDurationMs: Long = DEFAULT_CARD_DURATION_MS,
)

const val DEFAULT_CARD_DURATION_MS = 12_000L
const val MIN_CARD_DURATION_MS = 4_000L
const val MAX_CARD_DURATION_MS = 20_000L
