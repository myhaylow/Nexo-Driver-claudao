package br.com.nexo.driver.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import br.com.nexo.driver.capture.visual.BitmapVisualPixelFrame
import br.com.nexo.driver.capture.visual.FrameSimilarityGate
import br.com.nexo.driver.capture.visual.OfferCardVisualDetector
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.ocr.OfferOcrPipeline
import br.com.nexo.driver.ocr.OcrTextBlock
import br.com.nexo.driver.ocr.OcrTextSnapshot
import br.com.nexo.driver.ocr.mlkit.MlKitBitmapOcrEngine
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Automatic local screenshot/OCR complement when Accessibility text is absent or incomplete. */
internal class AccessibilityScreenshotFallback(
    private val service: AccessibilityService,
    private val onOffer: (NormalizedOffer, List<NormalizedOffer>, ScreenshotReadMetrics) -> Unit,
) : Closeable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AccessibilityScreenshotOcr").apply { isDaemon = true }
    }
    private val inFlight = AtomicBoolean(false)
    private val detector = OfferCardVisualDetector()
    private val similarityGate = FrameSimilarityGate()
    private val ocrEngine = MlKitBitmapOcrEngine(timeoutMillis = SCREENSHOT_OCR_TIMEOUT_MS)
    private val pipeline = OfferOcrPipeline()
    private var providerHint: String? = null
    private var active = false
    private var burstDeadlineMs = 0L
    // Zero permits the first wide retry. Long.MIN_VALUE overflows when subtracted
    // from elapsedRealtime(), which used to suppress every wide retry forever.
    private var lastWideRetryMs = 0L
    private var scheduled = false
    private var lastDiagnosticText: String? = null

    /**
     * Wall-clock guard for the platform's own rate limit on `takeScreenshot`. The reschedule
     * delays alone are not enough: several paths schedule the next capture, and the previous
     * 150ms cadence meant the framework rejected roughly every other request with
     * ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT, spinning without ever succeeding.
     */
    private var lastScreenshotRequestAtMs = 0L

    /** Grows on repeated interval rejections and resets once a screenshot lands. */
    private var screenshotIntervalMs = MIN_SCREENSHOT_INTERVAL_MS
    private var throttledRejections = 0
    @Volatile private var closed = false
    @Volatile private var captureGeneration = 0L

    fun request(provider: String?) {
        if (closed || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        providerHint = provider
        active = true
        burstDeadlineMs = SystemClock.elapsedRealtime() + CAPTURE_BURST_MAX_MS
        schedule(delayMs = 0L, wide = false)
    }

    fun pause() {
        captureGeneration += 1
        active = false
        scheduled = false
        // A new burst must never be gated against a frame from the previous one.
        similarityGate.reset()
        throttledRejections = 0
        screenshotIntervalMs = MIN_SCREENSHOT_INTERVAL_MS
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun schedule(delayMs: Long, wide: Boolean) {
        if (closed || scheduled) return
        scheduled = true
        mainHandler.postDelayed(
            {
                scheduled = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !closed && active) {
                    capture(wide)
                }
            },
            delayMs,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capture(wide: Boolean) {
        // Accessibility is global, but screenshots must never outlive the supported provider
        // window that justified the fallback request (for example after an app switch).
        if (!hasSupportedWindow()) {
            pause()
            return
        }
        // Hold the request until the platform's minimum gap has elapsed, instead of issuing it and
        // letting the framework reject it. A rejected request costs the same round trip as a real
        // one and returns nothing.
        val nowMs = SystemClock.elapsedRealtime()
        val sinceLastMs = nowMs - lastScreenshotRequestAtMs
        if (sinceLastMs < screenshotIntervalMs) {
            schedule(screenshotIntervalMs - sinceLastMs, wide)
            return
        }
        if (!inFlight.compareAndSet(false, true)) return
        lastScreenshotRequestAtMs = nowMs
        val generation = captureGeneration
        val requestedAtNanos = System.nanoTime()
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            worker,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    processScreenshot(result, requestedAtNanos, wide, generation)
                }

                override fun onFailure(errorCode: Int) {
                    inFlight.set(false)
                    mainHandler.post {
                        // An interval rejection means our estimate of the platform's rate limit is
                        // still too eager, so widen it rather than retrying at the same cadence.
                        if (errorCode == ERROR_INTERVAL_TIME_SHORT) {
                            throttledRejections += 1
                            screenshotIntervalMs = (screenshotIntervalMs * 2)
                                .coerceAtMost(MAX_SCREENSHOT_INTERVAL_MS)
                            // Logged once per burst: the old code emitted this on every rejection
                            // and buried the rest of the diagnostics.
                            if (throttledRejections == 1) {
                                Log.w(TAG, "screenshot_throttled backoff=${screenshotIntervalMs}ms")
                            }
                        } else {
                            Log.w(TAG, "screenshot_failed code=$errorCode")
                        }
                        if (isCurrentGeneration(generation)) schedule(screenshotIntervalMs, wide = false)
                    }
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun hasSupportedWindow(): Boolean {
        var supported = false
        service.windows.forEach { window ->
            try {
                if (!supported) {
                    supported = window.root?.let { root ->
                        try {
                            accessibilityLayoutHint(root.packageName) != null
                        } finally {
                            root.recycle()
                        }
                    } == true
                }
            } finally {
                window.recycle()
            }
        }
        return supported
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun processScreenshot(
        result: AccessibilityService.ScreenshotResult,
        requestedAtNanos: Long,
        wide: Boolean,
        generation: Long,
    ) {
        val buffer = result.hardwareBuffer
        var screenBitmap: Bitmap? = null
        var cropBitmap: Bitmap? = null
        try {
            // A screenshot landed, so the current interval estimate is workable again.
            throttledRejections = 0
            screenshotIntervalMs = MIN_SCREENSHOT_INTERVAL_MS
            if (!isCurrentGeneration(generation)) return
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                ?: error("Screenshot HardwareBuffer could not be wrapped.")
            screenBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBitmap.recycle()

            val region = if (wide) {
                detector.fallback(screenBitmap.width, screenBitmap.height, providerHint).copy(
                    left = 0,
                    top = (screenBitmap.height * WIDE_RETRY_TOP_FRACTION).toInt(),
                    right = screenBitmap.width,
                )
            } else {
                detector.detect(screenBitmap, providerHint)
            }
            cropBitmap = Bitmap.createBitmap(
                screenBitmap,
                region.left,
                region.top,
                region.width,
                region.height,
            )
            if (!isCurrentGeneration(generation)) return
            // A visually unchanged crop would only re-derive the offer the pipeline already
            // deduplicates, after paying the full recognition cost. Stop before OCR instead.
            if (!similarityGate.shouldProcess(BitmapVisualPixelFrame(cropBitmap))) return
            val ocrStartedAtNanos = System.nanoTime()
            val blocks = ocrEngine.recognize(cropBitmap)
            val ocrMs = elapsedMs(ocrStartedAtNanos)
            val output = pipeline.process(
                OcrTextSnapshot(
                    blocks = blocks,
                    capturedAtEpochMs = System.currentTimeMillis(),
                    layoutHint = providerHint,
                ),
            )
            val offer = output.offer
            val metrics = ScreenshotReadMetrics(
                totalMs = elapsedMs(requestedAtNanos),
                ocrMs = ocrMs,
                cropTop = region.top,
                cropBottom = region.bottom,
                visualConfidence = region.confidence,
                cropStrategy = if (wide) "WIDE_RETRY" else region.strategy.name,
            )
            if (offer != null && output.isDuplicate) {
                // Keep the bounded burst alive; the next frame may already contain a new radar card.
            } else if (
                offer != null &&
                offer.isReadyForLiveAnalysis() &&
                offer.isPlausible()
            ) {
                // A successful read used to be the only silent outcome here, which made the OCR
                // path look dead in logs while it was in fact serving every offer.
                Log.i(
                    TAG,
                    "ocr_served provider=${providerHint ?: "unknown"} blocks=${blocks.size} " +
                        "strategy=${metrics.cropStrategy} crop=${metrics.cropTop}-${metrics.cropBottom} " +
                        "ocrMs=${metrics.ocrMs} totalMs=${metrics.totalMs}",
                )
                // If the OCR'd region actually held more than one card (Uber's tray, most likely on a
                // wide-retry crop), the extra windows ride along as read-only alternatives. On the
                // common single-card crop extractAll returns one window, so this is an empty list.
                val alternatives = OfferCardTextRegionExtractor.extractAll(blocks.map { it.text }, providerHint)
                    .drop(1)
                    .mapNotNull { window ->
                        pipeline.parse(
                            OcrTextSnapshot(
                                blocks = window.mapIndexed { index, text -> OcrTextBlock(text, index) },
                                capturedAtEpochMs = System.currentTimeMillis(),
                                layoutHint = providerHint,
                            ),
                        )
                    }
                    .filter { it.isReadyForLiveAnalysis() && it.isPlausible() }
                // Keep a bounded OCR burst alive after the first valid card: Uber can replace
                // radar cards without sending another accessibility event with usable text.
                mainHandler.post {
                    if (isCurrentGeneration(generation)) onOffer(offer, alternatives, metrics)
                }
            } else {
                logChangedDiagnostic(output.raw.text, offer)
                Log.d(
                    TAG,
                    "ocr_incomplete provider=${providerHint ?: "unknown"} blocks=${blocks.size} " +
                        "strategy=${metrics.cropStrategy} confidence=${metrics.visualConfidence} " +
                        "crop=${metrics.cropTop}-${metrics.cropBottom} ocrMs=${metrics.ocrMs} totalMs=${metrics.totalMs}",
                )
                val nowMs = SystemClock.elapsedRealtime()
                if (!wide && nowMs - lastWideRetryMs >= WIDE_RETRY_COOLDOWN_MS) {
                    lastWideRetryMs = nowMs
                    mainHandler.post { schedule(WIDE_RETRY_DELAY_MS, wide = true) }
                }
            }
        } catch (failure: Exception) {
            Log.w(TAG, "screenshot_ocr_failed reason=${failure.javaClass.simpleName}")
        } finally {
            runCatching { buffer.close() }
            cropBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            screenBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            inFlight.set(false)
            if (isCurrentGeneration(generation) && SystemClock.elapsedRealtime() < burstDeadlineMs) {
                mainHandler.post { schedule(screenshotIntervalMs, wide = false) }
            } else {
                active = false
            }
        }
    }

    override fun close() {
        captureGeneration += 1
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        runCatching { ocrEngine.close() }
    }

    private fun NormalizedOffer.isPlausible(): Boolean {
        val payoutCents = payout.value?.cents ?: return false
        val durations = listOfNotNull(pickup.duration.value?.seconds, trip.duration.value?.seconds)
        val distances = listOfNotNull(pickup.distance.value?.meters, trip.distance.value?.meters)
        val rating = passenger.rating.value
        return payoutCents in 200L..50_000L &&
            durations.all { it in 60L..36_000L } &&
            distances.all { it in 100L..350_000L } &&
            (rating == null || rating in 400L..500L)
    }

    private fun logChangedDiagnostic(rawText: String, offer: NormalizedOffer?) {
        val diagnostic = "chars=${rawText.length} lines=${rawText.lineSequence().count()} " +
            "parsed=${offer != null} knownLegs=${offer?.knownLegCount() ?: 0}"
        if (diagnostic == lastDiagnosticText) return
        lastDiagnosticText = diagnostic
        Log.d(TAG, "ocr_diagnostic $diagnostic")
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L

    private fun isCurrentGeneration(generation: Long): Boolean =
        !closed && active && captureGeneration == generation

    /** Internal rather than private so the screenshot pacing policy can be asserted in tests. */
    internal companion object {
        const val TAG = "AccessibilityOcr"
        /**
         * Floor between screenshot requests. The framework rejects anything faster with
         * ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT; measured on a Galaxy S23 / Android 16, a
         * 150ms cadence produced 82 consecutive rejections and zero usable frames.
         */
        const val MIN_SCREENSHOT_INTERVAL_MS = 1_000L
        const val MAX_SCREENSHOT_INTERVAL_MS = 4_000L

        /** AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT. */
        const val ERROR_INTERVAL_TIME_SHORT = 3
        const val CAPTURE_BURST_MAX_MS = 12_000L
        const val WIDE_RETRY_DELAY_MS = 120L
        const val WIDE_RETRY_COOLDOWN_MS = 2_500L
        const val WIDE_RETRY_TOP_FRACTION = 0.12f
        const val SCREENSHOT_OCR_TIMEOUT_MS = 1_600L
    }
}

internal data class ScreenshotReadMetrics(
    val totalMs: Long,
    val ocrMs: Long,
    val cropTop: Int,
    val cropBottom: Int,
    val visualConfidence: Float,
    val cropStrategy: String,
)
