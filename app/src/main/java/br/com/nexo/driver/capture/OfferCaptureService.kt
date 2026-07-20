package br.com.nexo.driver.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.nexo.driver.accessibility.AnalysisSource
import br.com.nexo.driver.analysis.OfferAnalysisProcessor
import br.com.nexo.driver.analysis.ActiveOfferUpdateGate
import br.com.nexo.driver.capture.service.CaptureStopReason
import br.com.nexo.driver.capture.service.CaptureSessionGuard
import br.com.nexo.driver.capture.service.CapturedBitmapConsumer
import br.com.nexo.driver.capture.service.MediaProjectionCaptureConfig
import br.com.nexo.driver.capture.service.boundedCaptureSize
import br.com.nexo.driver.capture.service.MediaProjectionFrameOrchestrator
import br.com.nexo.driver.capture.performance.OfferResponseLatencyTracker
import br.com.nexo.driver.capture.performance.CaptureLatencyDiagnostics
import br.com.nexo.driver.capture.performance.CaptureLatencyDiagnosticsStore
import br.com.nexo.driver.ocr.OcrTextSnapshot
import br.com.nexo.driver.ocr.OfferOcrPipeline
import br.com.nexo.driver.ocr.mlkit.DEFAULT_RECOGNITION_TIMEOUT_MILLIS
import br.com.nexo.driver.ocr.mlkit.MlKitBitmapOcrEngine
import br.com.nexo.driver.overlay.WindowManagerOfferOverlay
import br.com.nexo.driver.speech.OfferDecisionSpeaker
import java.util.concurrent.Executors

/**
 * Foreground service for one user-authorized screen-capture session. The service never stores
 * frames or raw text: it recognizes, evaluates and immediately discards each frame locally.
 */
class OfferCaptureService : Service() {
    private val captureThread = HandlerThread("driver-inteligente-capture")
    private val conversionExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var captureConfig: MediaProjectionCaptureConfig? = null
    private var frameOrchestrator: MediaProjectionFrameOrchestrator? = null
    private var overlayWindow: WindowManagerOfferOverlay? = null
    private var ocrEngine: MlKitBitmapOcrEngine? = null
    private var isStopping = false
    private var isForeground = false
    private val sessionGuard = CaptureSessionGuard()

    private val pipeline = OfferOcrPipeline()
    private lateinit var analysisProcessor: OfferAnalysisProcessor
    private var speaker: OfferDecisionSpeaker? = null
    /** Rolling in-memory diagnostics for the one-second frame-to-overlay target. */
    private val responseLatency = OfferResponseLatencyTracker()
    /**
     * Rolling in-memory diagnostics for everything after OCR returns (parse, evaluate, enrich,
     * overlay render). ML Kit's own timeout already bounds the OCR stage at
     * [DEFAULT_RECOGNITION_TIMEOUT_MILLIS]; this tracker bounds what remains of the one-second
     * budget so a slow parser/evaluator/enrichment step is visible instead of only showing up as
     * an unexplained total-latency violation.
     */
    private val postOcrLatency = OfferResponseLatencyTracker(targetMillis = POST_OCR_BUDGET_MILLIS)
    /** Aggregate-only local evidence for checking p95 and worst case during or after a session. */
    private val latencyDiagnostics by lazy { CaptureLatencyDiagnosticsStore.create(this) }
    private val activeOfferUpdateGate = ActiveOfferUpdateGate()
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                stopCapture(CaptureStopReason.SCREEN_LOCKED)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        captureThread.start()
        overlayWindow = WindowManagerOfferOverlay(this)
        speaker = OfferDecisionSpeaker(this)
        analysisProcessor = OfferAnalysisProcessor(this, speaker = speaker)
        ocrEngine = MlKitBitmapOcrEngine()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            // A stray start must not tear down a healthy capture session.
            if (mediaProjection == null && frameOrchestrator == null) rejectStart()
            return START_NOT_STICKY
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            rejectStart()
            return START_NOT_STICKY
        }

        // A consent Intent and a MediaProjection instance are single-use on Android 14+. Ignore
        // duplicate starts while the current session is healthy; a replacement session must first
        // be stopped and receive fresh user consent.
        if (mediaProjection != null || frameOrchestrator != null) return START_NOT_STICKY
        return runCatching {
            startInForeground()
            startProjection(resultCode, resultData)
        }
            .fold(
                onSuccess = { START_NOT_STICKY },
                onFailure = {
                    stopCapture(CaptureStopReason.SERVICE_DESTROYED)
                    START_NOT_STICKY
                },
            )
    }

    /**
     * Satisfies the Context.startForegroundService() contract for a start that will never become
     * a capture session (denied consent dialog, malformed intent). Skipping startForeground()
     * crashes the whole process with ForegroundServiceDidNotStartInTimeException -- even when the
     * service stops itself immediately (verified on a Galaxy S23 / Android 16). Promotion here
     * uses the shortService type because the mediaProjection type is rejected by the OS without
     * the user's screen-capture consent, and shortService needs no permission or declaration.
     */
    private fun rejectStart() {
        if (!isForeground) {
            runCatching { startInForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE) }
                .onFailure { Log.e(TAG, "Foreground promotion for rejected start failed.", it) }
        }
        stopCapture(CaptureStopReason.USER)
    }

    override fun onDestroy() {
        stopCapture(CaptureStopReason.SERVICE_DESTROYED, stopService = false)
        if (screenOffReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiverRegistered = false
        }
        conversionExecutor.shutdownNow()
        captureThread.quitSafely()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { overlayWindow?.close() }
        }
        overlayWindow = null
        runCatching { ocrEngine?.close() }
        ocrEngine = null
        runCatching { speaker?.close() }
        speaker = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopCapture(CaptureStopReason.USER)
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val activeConfig = captureConfig ?: return
        val metrics = resources.displayMetrics
        if (metrics.widthPixels != activeConfig.widthPixels ||
            metrics.heightPixels != activeConfig.heightPixels ||
            metrics.densityDpi != activeConfig.densityDpi
        ) {
            resizeCaptureSurface(
                widthPixels = metrics.widthPixels,
                heightPixels = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = checkNotNull(manager.getMediaProjection(resultCode, resultData)) {
            "MediaProjection was not available."
        }
        val session = sessionGuard.begin()
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                // A callback already queued by an earlier projection must never tear down a newly
                // authorized session.
                if (mediaProjection === projection && sessionGuard.isActive(session)) {
                    stopCapture(CaptureStopReason.PROJECTION_REVOKED)
                }
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                // Post once more so an initial resize callback cannot run before
                // createVirtualDisplay() has returned and been stored.
                mainHandler.post {
                    if (sessionGuard.isActive(session) && width > 0 && height > 0) {
                        resizeCaptureSurface(width, height, resources.displayMetrics.densityDpi)
                    }
                }
            }
        }
        mediaProjection = projection
        projectionCallback = callback
        projection.registerCallback(callback, mainHandler)

        val metrics = resources.displayMetrics
        val config = captureConfigFor(
            widthPixels = metrics.widthPixels,
            heightPixels = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
        )
        captureConfig = config
        val reader = newImageReader(config)
        imageReader = reader
        frameOrchestrator = newFrameOrchestrator(reader, config, session)
        virtualDisplay = checkNotNull(
            projection.createVirtualDisplay(
                "DriverInteligenteCapture",
                config.widthPixels,
                config.heightPixels,
                config.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                Handler(captureThread.looper),
            ),
        ) { "MediaProjection could not create a virtual display." }
        publishLatencyDiagnostics(latencyDiagnostics.startNewSession())
        publishReaderState(isActive = true)
    }

    private fun processFrame(
        session: Long,
        frame: br.com.nexo.driver.capture.service.CapturedBitmapFrame,
    ) {
        if (!sessionGuard.isActive(session)) {
            frame.bitmap.recycle()
            return
        }
        val blocks = try {
            ocrEngine?.recognize(frame.bitmap).orEmpty()
        } catch (_: RuntimeException) {
            emptyList()
        } finally {
            frame.bitmap.recycle()
        }
        val ocrCompletedAtMillis = System.nanoTime() / NANOS_PER_MILLISECOND
        if (blocks.isEmpty()) return
        if (!sessionGuard.isActive(session)) return
        val output = pipeline.process(
            OcrTextSnapshot(
                blocks = blocks,
                capturedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        output.unrecognizedLayoutSource?.let { source ->
            // A known card marker (e.g. "UberX", "pgto. no app") was on screen but no parser
            // could extract a complete offer from it -- likely the target app's layout/copy
            // drifted from the hardcoded strings/regex this parser relies on.
            Log.w(TAG, "Recognized $source offer card but failed to parse its fields (layout drift?).")
        }
        val parsedOffer = output.offer ?: return
        if (output.isDuplicate) return
        val analysis = analysisProcessor.analyze(parsedOffer, AnalysisSource.OCR) ?: return
        val overlay = analysis.overlay
        val appearance = analysis.appearance
        val capturedAtMillis = frame.capturedAtNanos / NANOS_PER_MILLISECOND
        mainHandler.post {
            if (!sessionGuard.isActive(session)) return@post
            runCatching {
                overlayWindow?.show(
                    model = overlay,
                    themeMode = appearance.themeMode,
                    fontScale = appearance.fontScale,
                    visualStyle = appearance.visualStyle,
                )
            }
                .onSuccess {
                    val offerGeneration = activeOfferUpdateGate.open(SystemClock.elapsedRealtime())
                    runCatching { responseLatency.recordOverlayShown(capturedAtMillis) }
                        .onSuccess { snapshot -> publishLatencyDiagnostics(latencyDiagnostics.record(snapshot)) }
                    runCatching { postOcrLatency.recordOverlayShown(ocrCompletedAtMillis) }
                        .onSuccess { snapshot ->
                            if (!snapshot.meetsTarget) {
                                Log.w(
                                    TAG,
                                    "Post-OCR budget exceeded: latest=${snapshot.latestMillis}ms " +
                                        "target=${snapshot.targetMillis}ms " +
                                        "(parse+evaluate+enrich+render)",
                                )
                            }
                        }
                    analysisProcessor.analyzeDestinationUpdateAsync(parsedOffer, AnalysisSource.OCR) { update ->
                        mainHandler.post {
                            if (
                                sessionGuard.isActive(session) &&
                                activeOfferUpdateGate.accepts(offerGeneration, SystemClock.elapsedRealtime())
                            ) {
                                runCatching {
                                    overlayWindow?.update(
                                        model = update.overlay,
                                        themeMode = update.appearance.themeMode,
                                        fontScale = update.appearance.fontScale,
                                        visualStyle = update.appearance.visualStyle,
                                    )
                                }.onFailure { failure ->
                                    Log.w(TAG, "Late destination enrichment could not update overlay.", failure)
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun startInForeground(
        serviceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
    ) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Driver Inteligente ativo")
            .setContentText("Lendo ofertas localmente nesta sessão.")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, serviceType)
        isForeground = true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Leitor de ofertas", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stopCapture(reason: CaptureStopReason, stopService: Boolean = true) {
        if (isStopping) return
        isStopping = true
        try {
            // Invalidate first so in-flight OCR cannot publish after any resource below is closed.
            sessionGuard.invalidate()

            val orchestrator = frameOrchestrator.also { frameOrchestrator = null }
            val display = virtualDisplay.also { virtualDisplay = null }
            val reader = imageReader.also { imageReader = null }
            val projection = mediaProjection.also { mediaProjection = null }
            val callback = projectionCallback.also { projectionCallback = null }
            captureConfig = null

            runCatching { orchestrator?.stop(reason) }
            runCatching { display?.setSurface(null) }
            runCatching { display?.release() }
            runCatching { reader?.close() }
            if (projection != null && callback != null) {
                runCatching { projection.unregisterCallback(callback) }
            }
            runCatching { projection?.stop() }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                runCatching { overlayWindow?.hide() }
            } else {
                mainHandler.post { runCatching { overlayWindow?.hide() } }
            }
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
        } finally {
            publishLatencyDiagnostics(latencyDiagnostics.finishSession())
            // This runs for user stop, a revoked projection, startup failure and onDestroy. It
            // deliberately precedes stopSelf so a visible Activity receives the terminal state.
            publishReaderState(isActive = false, stopReason = reason)
            isStopping = false
            if (stopService) stopSelf()
        }
    }

    private fun publishReaderState(isActive: Boolean, stopReason: CaptureStopReason? = null) {
        val state = if (isActive) {
            CaptureRuntimeState(isActive = true, ownerProcessId = Process.myPid())
        } else {
            CaptureRuntimeState()
        }
        CaptureRuntimeStateStore.create(this).save(state)
        sendBroadcast(
            Intent(ACTION_READER_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_READER_ACTIVE, isActive)
                .apply {
                    stopReason?.let { putExtra(EXTRA_STOP_REASON, it.name) }
                },
        )
    }

    /**
     * Emits aggregate-only diagnostics for internal runtime validation. The broadcast is scoped
     * to this package and does not include an image, OCR text, or any parsed offer field.
     */
    private fun publishLatencyDiagnostics(diagnostics: CaptureLatencyDiagnostics) {
        sendBroadcast(
            Intent(ACTION_LATENCY_DIAGNOSTICS_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_LATENCY_SESSION_ACTIVE, diagnostics.isSessionActive)
                .putExtra(EXTRA_LATENCY_SAMPLE_COUNT, diagnostics.snapshot.sampleCount)
                .putExtra(EXTRA_LATENCY_P95_MILLIS, diagnostics.snapshot.p95Millis ?: -1L)
                .putExtra(EXTRA_LATENCY_MAX_MILLIS, diagnostics.snapshot.maxMillis ?: -1L)
                .putExtra(EXTRA_LATENCY_VIOLATION_COUNT, diagnostics.snapshot.violationCount),
        )
    }

    private fun newImageReader(config: MediaProjectionCaptureConfig): ImageReader =
        ImageReader.newInstance(
            config.widthPixels,
            config.heightPixels,
            config.pixelFormat,
            config.imageReaderMaxImages,
        )

    private fun newFrameOrchestrator(
        reader: ImageReader,
        config: MediaProjectionCaptureConfig,
        session: Long,
    ): MediaProjectionFrameOrchestrator = MediaProjectionFrameOrchestrator(
        imageReader = reader,
        config = config,
        conversionExecutor = conversionExecutor,
        consumer = CapturedBitmapConsumer { processFrame(session, it) },
    ).also { it.start(Handler(captureThread.looper)) }

    /** Resizes the existing VirtualDisplay; creating a second one is forbidden on Android 14+. */
    private fun resizeCaptureSurface(widthPixels: Int, heightPixels: Int, densityDpi: Int) {
        val display = virtualDisplay ?: return
        if (widthPixels <= 0 || heightPixels <= 0 || densityDpi <= 0) return
        val resizedConfig = captureConfigFor(widthPixels, heightPixels, densityDpi)
        val currentConfig = captureConfig?.let { current ->
            if (current == resizedConfig) return
            // The active generation is intentionally not exposed. Captured resize callbacks only
            // reach this method for their own active session, so advance no generation here.
            current
        } ?: return

        val generation = sessionGuard.currentOrNull() ?: return
        val replacementReader = newImageReader(resizedConfig)
        val replacementOrchestrator = try {
            newFrameOrchestrator(replacementReader, resizedConfig, generation)
        } catch (failure: RuntimeException) {
            replacementReader.close()
            return
        }

        val oldReader = imageReader
        val oldOrchestrator = frameOrchestrator
        try {
            display.resize(widthPixels, heightPixels, densityDpi)
            display.setSurface(replacementReader.surface)
            imageReader = replacementReader
            frameOrchestrator = replacementOrchestrator
            captureConfig = resizedConfig
            runCatching { oldOrchestrator?.stop(CaptureStopReason.CAPTURE_RESIZED) }
            runCatching { oldReader?.close() }
        } catch (_: RuntimeException) {
            replacementOrchestrator.stop(CaptureStopReason.SERVICE_DESTROYED)
            replacementReader.close()
            runCatching {
                display.resize(
                    currentConfig.widthPixels,
                    currentConfig.heightPixels,
                    currentConfig.densityDpi,
                )
                oldReader?.surface?.let(display::setSurface)
            }
        }
    }

    private fun captureConfigFor(
        widthPixels: Int,
        heightPixels: Int,
        densityDpi: Int,
    ): MediaProjectionCaptureConfig {
        val (boundedWidth, boundedHeight) = boundedCaptureSize(widthPixels, heightPixels)
        return MediaProjectionCaptureConfig(
            widthPixels = boundedWidth,
            heightPixels = boundedHeight,
            densityDpi = densityDpi,
        )
    }


    companion object {
        private const val ACTION_START = "br.com.nexo.driver.capture.START"
        const val ACTION_READER_STATE_CHANGED = "br.com.nexo.driver.capture.READER_STATE_CHANGED"
        const val ACTION_LATENCY_DIAGNOSTICS_CHANGED =
            "br.com.nexo.driver.capture.LATENCY_DIAGNOSTICS_CHANGED"
        const val EXTRA_READER_ACTIVE = "reader_active"
        const val EXTRA_STOP_REASON = "stop_reason"
        const val EXTRA_LATENCY_SESSION_ACTIVE = "latency_session_active"
        const val EXTRA_LATENCY_SAMPLE_COUNT = "latency_sample_count"
        const val EXTRA_LATENCY_P95_MILLIS = "latency_p95_millis"
        const val EXTRA_LATENCY_MAX_MILLIS = "latency_max_millis"
        const val EXTRA_LATENCY_VIOLATION_COUNT = "latency_violation_count"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "offer_capture"
        private const val NOTIFICATION_ID = 10_841
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val TAG = "OfferCaptureService"
        /** What remains of the one-second response target once the OCR stage's own timeout is spent. */
        private const val POST_OCR_BUDGET_MILLIS =
            OfferResponseLatencyTracker.DEFAULT_TARGET_MILLIS - DEFAULT_RECOGNITION_TIMEOUT_MILLIS

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, OfferCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OfferCaptureService::class.java))
        }

        /**
         * Returns active only when the current process still owns the capture session. A process
         * restart therefore cannot resurrect a reader switch after Android revoked projection.
         */
        fun isActive(context: Context): Boolean =
            CaptureRuntimeStateStore.create(context).load().isActiveFor(Process.myPid())

        /**
         * Returns local aggregate timing evidence. This API intentionally exposes no captured
         * image, OCR result or offer data, and is not rendered in the driver's primary UI.
         */
        fun latencyDiagnostics(context: Context): CaptureLatencyDiagnostics =
            CaptureLatencyDiagnosticsStore.create(context).load()
    }
}
