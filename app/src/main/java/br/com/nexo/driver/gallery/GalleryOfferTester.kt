package br.com.nexo.driver.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import br.com.nexo.driver.accessibility.AnalysisSource
import br.com.nexo.driver.analysis.OfferAnalysisProcessor
import br.com.nexo.driver.ocr.OfferOcrPipeline
import br.com.nexo.driver.ocr.OcrTextSnapshot
import br.com.nexo.driver.ocr.mlkit.MlKitBitmapOcrEngine
import br.com.nexo.driver.overlay.OverlayStatus
import br.com.nexo.driver.overlay.WindowManagerOfferOverlay
import java.io.Closeable
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Runs a user-selected screenshot through the exact local OCR/parser/evaluator/overlay path. */
class GalleryOfferTester(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "GalleryOfferTest").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ocrEngine = MlKitBitmapOcrEngine()
    private val processor = OfferAnalysisProcessor(appContext)
    private val overlayWindow = WindowManagerOfferOverlay(appContext)
    @Volatile private var closed = false

    fun test(uri: Uri, onResult: (GalleryTestResult) -> Unit) {
        if (closed) return
        worker.execute {
            val startedAtNanos = System.nanoTime()
            val result = runCatching {
                val bitmap = decodeBoundedBitmap(uri)
                val blocks = try {
                    ocrEngine.recognize(bitmap)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                if (blocks.isEmpty()) return@runCatching GalleryTestResult.NoText
                val output = OfferOcrPipeline().process(
                    OcrTextSnapshot(
                        blocks = blocks,
                        capturedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                val offer = output.offer ?: return@runCatching GalleryTestResult.NotRecognized(
                    providerHint = output.unrecognizedLayoutSource?.name,
                )
                val analysis = processor.analyze(
                    offer = offer,
                    source = AnalysisSource.OCR,
                    allowSideEffects = false,
                ) ?: return@runCatching GalleryTestResult.NotRecognized(offer.source.name)
                GalleryTestResult.Success(
                    provider = offer.source.name,
                    decision = analysis.overlay.status,
                    payout = analysis.overlay.payout,
                    latencyMs = elapsedMs(startedAtNanos),
                    showOverlay = {
                        if (!Settings.canDrawOverlays(appContext)) {
                            false
                        } else {
                            overlayWindow.show(
                                model = analysis.overlay,
                                themeMode = analysis.appearance.themeMode,
                                fontScale = analysis.appearance.fontScale,
                                visualStyle = analysis.appearance.visualStyle,
                            )
                            true
                        }
                    },
                )
            }.getOrElse { failure ->
                GalleryTestResult.Failure(failure.message ?: failure.javaClass.simpleName)
            }
            mainHandler.post {
                if (closed) return@post
                val delivered = if (result is GalleryTestResult.Success) {
                    val overlayShown = runCatching { result.showOverlay() }.getOrDefault(false)
                    result.withoutAction(overlayShown)
                } else {
                    result
                }
                onResult(delivered)
            }
        }
    }

    private fun decodeBoundedBitmap(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longestEdge = maxOf(width, height)
            if (longestEdge > MAX_IMAGE_EDGE_PIXELS) {
                val scale = MAX_IMAGE_EDGE_PIXELS.toDouble() / longestEdge
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    override fun close() {
        closed = true
        worker.shutdownNow()
        runCatching { ocrEngine.close() }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { overlayWindow.close() }
        } else {
            mainHandler.post { runCatching { overlayWindow.close() } }
        }
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L

    private companion object {
        const val MAX_IMAGE_EDGE_PIXELS = 2_048
    }
}

sealed interface GalleryTestResult {
    data object NoText : GalleryTestResult
    data class NotRecognized(val providerHint: String?) : GalleryTestResult
    data class Failure(val reason: String) : GalleryTestResult
    data class Success(
        val provider: String,
        val decision: OverlayStatus,
        val payout: String,
        val latencyMs: Long,
        val overlayShown: Boolean = false,
        internal val showOverlay: () -> Boolean = { false },
    ) : GalleryTestResult {
        internal fun withoutAction(wasShown: Boolean) = copy(
            overlayShown = wasShown,
            showOverlay = { false },
        )
    }
}

fun GalleryTestResult.message(): String = when (this) {
    GalleryTestResult.NoText -> "Nenhum texto foi encontrado na imagem."
    is GalleryTestResult.NotRecognized -> if (providerHint == null) {
        "A imagem foi lida, mas nenhum card Uber/99 foi reconhecido."
    } else {
        "Card $providerHint encontrado, mas faltaram campos para calcular a oferta."
    }
    is GalleryTestResult.Failure -> "Falha no teste: $reason"
    is GalleryTestResult.Success -> {
        val decisionLabel = when (decision) {
            OverlayStatus.ACCEPT -> "aceitar"
            OverlayStatus.ANALYZE -> "analisar"
            OverlayStatus.REJECT -> "recusar"
            OverlayStatus.UNKNOWN -> "indefinida"
        }
        "$provider · $decisionLabel · $payout · ${latencyMs} ms" +
            if (overlayShown) "" else " · autorize a sobreposição para ver o card"
    }
}
