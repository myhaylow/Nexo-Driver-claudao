package br.com.nexo.driver.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import br.com.nexo.driver.BuildConfig
import br.com.nexo.driver.analysis.OfferAnalysisProcessor
import br.com.nexo.driver.analysis.ActiveOfferUpdateGate
import br.com.nexo.driver.ocr.OfferOcrPipeline
import br.com.nexo.driver.overlay.OverlayWindowBounds
import br.com.nexo.driver.overlay.WindowManagerOfferOverlay
import br.com.nexo.driver.speech.OfferDecisionSpeaker

/**
 * Primary read-only offer reader. It inspects text exposed by the active app's accessibility tree
 * and feeds the same local parser/evaluator/overlay pipeline used by OCR fallback.
 */
class DriverAccessibilityService : AccessibilityService() {
    private val reader = AccessibilityOfferReader()
    private val pipeline = OfferOcrPipeline()
    private lateinit var processor: OfferAnalysisProcessor
    private var overlayWindow: WindowManagerOfferOverlay? = null
    private var speaker: OfferDecisionSpeaker? = null
    private var screenshotFallback: AccessibilityScreenshotFallback? = null
    private val eventHandler = Handler(Looper.getMainLooper())
    private var pendingRead: Runnable? = null
    private var lastNoCardLogAtMs = Long.MIN_VALUE
    private val activeOfferUpdateGate = ActiveOfferUpdateGate()

    /**
     * Screen bounds of the ride app's window from the most recent read. The overlay uses it to
     * dock on the same side the app occupies instead of covering it in split-screen.
     */
    @Volatile
    private var lastProviderWindowBounds: OverlayWindowBounds? = null

    override fun onServiceConnected() {
        overlayWindow = WindowManagerOfferOverlay(this)
        speaker = OfferDecisionSpeaker(this)
        processor = OfferAnalysisProcessor(
            context = this,
            speaker = speaker,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            screenshotFallback = AccessibilityScreenshotFallback(this) { offer, _ ->
                val result = processor.analyze(offer, AnalysisSource.OCR)
                if (result != null) {
                    publishInitialOffer(offer, AnalysisSource.OCR, result)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::processor.isInitialized) return
        val packageName = event?.packageName?.toString()
        if (packageName == this.packageName || packageName?.startsWith("${this.packageName}.") == true) return
        val eventHint = accessibilityLayoutHint(packageName)
        val extraText = if (eventHint != null) {
            buildList {
                event?.text.orEmpty().mapNotNullTo(this) { value -> value?.toString() }
                event?.source?.let { source ->
                    try {
                        addAll(reader.collectTextFragments(source))
                    } finally {
                        source.recycle()
                    }
                }
            }
        } else {
            emptyList()
        }
        val receivedAtNanos = System.nanoTime()
        pendingRead?.let(eventHandler::removeCallbacks)
        pendingRead = Runnable {
            processActiveWindow(packageName, receivedAtNanos, extraText)
        }.also { read ->
            eventHandler.postDelayed(read, ACCESSIBILITY_SETTLE_DELAY_MS)
        }
    }

    private fun processActiveWindow(
        packageName: String?,
        receivedAtNanos: Long,
        extraText: List<String>,
    ) {
        val startedAtNanos = System.nanoTime()
        val windowInfos = windows.toList()
        val allRoots = windowInfos
            .sortedBy { window -> window.layer }
            .mapNotNull { window -> window.root }
        val providerRoots = allRoots.filter { root ->
            accessibilityLayoutHint(root.packageName) != null
        }
        lastProviderWindowBounds = windowInfos
            .firstOrNull { window -> accessibilityLayoutHint(window.root?.packageName) != null }
            ?.let { window ->
                val bounds = android.graphics.Rect().also(window::getBoundsInScreen)
                OverlayWindowBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    .takeIf(OverlayWindowBounds::isValid)
            }
        val eventHint = accessibilityLayoutHint(packageName)
        val relevantRoots = when {
            providerRoots.isNotEmpty() -> providerRoots
            eventHint != null -> allRoots.filter { root -> root.packageName?.toString() == packageName }
            else -> emptyList()
        }
        if (relevantRoots.isEmpty()) {
            if (eventHint != null) {
                // This branch was entirely silent, which hid the most common real-world outcome:
                // the ride app exposes no readable window and OCR quietly takes over. Rate-limited
                // by the same guard as the other no-card diagnostics.
                logNoReadableWindow(eventHint, allRoots.size, windowInfos.size)
                screenshotFallback?.request(eventHint)
            } else {
                screenshotFallback?.pause()
            }
            allRoots.forEach { root -> runCatching { root.recycle() } }
            windowInfos.forEach { window -> runCatching { window.recycle() } }
            return
        }
        val effectivePackage = relevantRoots.firstNotNullOfOrNull { root ->
            root.packageName?.toString()?.takeIf { accessibilityLayoutHint(it) != null }
        } ?: packageName
        val readResult = try {
            reader.readDetailed(
                roots = relevantRoots,
                layoutHint = accessibilityLayoutHint(effectivePackage),
                extraText = extraText,
            )
        } finally {
            allRoots.forEach { root -> runCatching { root.recycle() } }
            windowInfos.forEach { window -> runCatching { window.recycle() } }
        }
        val snapshot = readResult.snapshot
        if (snapshot == null) {
            logMissingCard(effectivePackage, readResult.diagnostics, receivedAtNanos)
            screenshotFallback?.request(accessibilityLayoutHint(effectivePackage))
            return
        }
        val output = pipeline.process(snapshot)
        output.unrecognizedLayoutSource?.let { source ->
            Log.w(TAG, "Accessibility saw $source offer marker but parser could not extract fields.")
        }
        val offer = output.offer
        if (offer == null) {
            if (BuildConfig.DEBUG && snapshot.looksLikeOffer()) {
                Log.d(
                    TAG,
                    "LIVE candidate_unparsed provider=${snapshot.layoutHint ?: "unknown"} " +
                        "windows=${providerRoots.size} blocks=${snapshot.blocks.size} " +
                        "latencyMs=${elapsedMs(startedAtNanos)}",
                )
            }
            screenshotFallback?.request(snapshot.layoutHint)
            return
        }
        if (output.isDuplicate) return
        if (!offer.isReadyForLiveAnalysis()) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "LIVE partial provider=${offer.source} hasPayout=${offer.payout.value != null} " +
                        "windows=${providerRoots.size} blocks=${snapshot.blocks.size} " +
                        "legs=${offer.knownLegCount()} latencyMs=${elapsedMs(startedAtNanos)}",
                )
            }
            screenshotFallback?.request(snapshot.layoutHint)
            return
        }
        val result = processor.analyze(offer, AnalysisSource.ACCESSIBILITY) ?: return
        Log.i(
            TAG,
            "LIVE tree_read blocks=${snapshot.blocks.size} readMs=${elapsedMs(startedAtNanos)} " +
                "sinceEventMs=${elapsedMs(receivedAtNanos)}",
        )
        // A complete accessibility read is authoritative. OCR is a fallback only and must not
        // keep a screenshot burst alive after the tree supplied a ready offer.
        screenshotFallback?.pause()
        // Accessibility can remain enabled while the separate overlay permission is revoked.
        // Keep reading, evaluating and speaking in that state instead of crashing on TYPE_OVERLAY.
        publishInitialOffer(offer, AnalysisSource.ACCESSIBILITY, result)
    }

    override fun onInterrupt() {
        speaker?.stop()
    }

    override fun onDestroy() {
        pendingRead?.let(eventHandler::removeCallbacks)
        pendingRead = null
        screenshotFallback?.close()
        screenshotFallback = null
        pipeline.reset()
        runCatching { overlayWindow?.close() }
        overlayWindow = null
        runCatching { speaker?.close() }
        speaker = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DriverAccessibility"
        const val ACCESSIBILITY_SETTLE_DELAY_MS = 100L
        const val NO_CARD_LOG_INTERVAL_MS = 1_000L

        /** Matches the capture-to-overlay budget documented for the capture pipeline. */
        const val LIVE_LATENCY_BUDGET_MS = 1_000L
    }

    private fun br.com.nexo.driver.ocr.OcrTextSnapshot.looksLikeOffer(): Boolean {
        val normalized = blocks.joinToString(" ") { it.text }.lowercase()
        return "r$" in normalized && "km" in normalized &&
            ("min" in normalized || "minuto" in normalized)
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L

    /**
     * One line per offer actually shown to the driver, emitted from the single point both readers
     * funnel through. Placing it on the accessibility branch alone left the OCR fallback -- which
     * turned out to be serving every offer on this device -- completely unlogged.
     *
     * Logged at INFO so it survives the BuildConfig.DEBUG gating the other diagnostics use, since
     * this is the line worth having when diagnosing latency on a real driver's phone.
     */
    private fun logServedOffer(
        offer: br.com.nexo.driver.offer.NormalizedOffer,
        source: AnalysisSource,
        result: br.com.nexo.driver.analysis.OfferAnalysisResult,
    ) {
        val ageMs = System.currentTimeMillis() - offer.detectedAtEpochMs
        Log.i(
            TAG,
            "LIVE served via=$source provider=${offer.source} decision=${result.overlay.status} " +
                "coverage=${result.overlay.coveragePercent}% reason=${result.overlay.decisionReason} " +
                "captureToOverlayMs=$ageMs budgetExceeded=${ageMs > LIVE_LATENCY_BUDGET_MS}",
        )
        if (ageMs > LIVE_LATENCY_BUDGET_MS) {
            Log.w(TAG, "LIVE latency budget exceeded: ${ageMs}ms > ${LIVE_LATENCY_BUDGET_MS}ms")
        }
    }

    /** Rate-limited so a burst of window events cannot flood the log the way code=3 once did. */
    private fun logNoReadableWindow(hint: String, rootCount: Int, windowCount: Int) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastNoCardLogAtMs < NO_CARD_LOG_INTERVAL_MS) return
        lastNoCardLogAtMs = nowMs
        Log.i(
            TAG,
            "LIVE no_readable_window provider=$hint roots=$rootCount windows=$windowCount " +
                "-> falling back to OCR",
        )
    }

    private fun logMissingCard(
        packageName: String?,
        diagnostics: AccessibilityReadDiagnostics,
        receivedAtNanos: Long,
    ) {
        if (!BuildConfig.DEBUG || accessibilityLayoutHint(packageName) == null) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastNoCardLogAtMs < NO_CARD_LOG_INTERVAL_MS) return
        lastNoCardLogAtMs = nowMs
        Log.d(
            TAG,
            "LIVE no_card windows=${diagnostics.rootCount} lines=${diagnostics.textLineCount} " +
                "anchors=${diagnostics.cardAnchorCount} payouts=${diagnostics.payoutTokenCount} " +
                "legs=${diagnostics.routeLegCount} eventAgeMs=${elapsedMs(receivedAtNanos)}",
        )
    }

    private fun showOverlay(result: br.com.nexo.driver.analysis.OfferAnalysisResult) {
        if (Settings.canDrawOverlays(this)) {
            runCatching {
                overlayWindow?.show(
                    model = result.overlay,
                    themeMode = result.appearance.themeMode,
                    fontScale = result.appearance.fontScale,
                    visualStyle = result.appearance.visualStyle,
                    colorVisionScheme = result.appearance.colorVisionScheme,
                    cardDurationMs = result.appearance.cardDurationMs,
                    appWindowBounds = lastProviderWindowBounds,
                )
            }.onFailure { failure ->
                Log.e(TAG, "Could not show offer overlay; continuing read-only analysis.", failure)
            }
        } else {
            Log.w(TAG, "Overlay permission is disabled; offer was analyzed without visual card.")
        }
    }

    private fun publishInitialOffer(
        offer: br.com.nexo.driver.offer.NormalizedOffer,
        source: AnalysisSource,
        result: br.com.nexo.driver.analysis.OfferAnalysisResult,
    ) {
        logServedOffer(offer, source, result)
        val generation = activeOfferUpdateGate.open(SystemClock.elapsedRealtime())
        showOverlay(result)
        processor.analyzeDestinationUpdateAsync(offer, source) { update ->
            eventHandler.post {
                if (
                    activeOfferUpdateGate.accepts(generation, SystemClock.elapsedRealtime()) &&
                    Settings.canDrawOverlays(this)
                ) {
                    runCatching {
                        overlayWindow?.update(
                            model = update.overlay,
                            themeMode = update.appearance.themeMode,
                            fontScale = update.appearance.fontScale,
                            visualStyle = update.appearance.visualStyle,
                            colorVisionScheme = update.appearance.colorVisionScheme,
                        )
                    }.onFailure { failure ->
                        Log.w(TAG, "Late destination enrichment could not update overlay.", failure)
                    }
                }
            }
        }
    }
}
