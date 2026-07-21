package br.com.nexo.driver.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Appends one CSV row per served offer to a file the driver can pull after a full day of driving,
 * when logcat's ring buffer would long since have rolled over. It records **numbers and enums only**
 * -- decision, coverage, latency and the raw metric inputs -- and deliberately never the passenger
 * name, address, or any OCR/tree text, matching the read-only/no-exfiltration design of the app.
 *
 * File: `Android/data/<pkg>/files/logs/offer-log.csv` on the device's app-private external storage,
 * pullable with `adb pull` without root. Writes happen on a private single thread so the capture
 * path is never blocked on disk I/O.
 */
class OfferReadFileLogger(context: Context, private val appVersion: String) {
    private val logFile: File? = runCatching {
        context.getExternalFilesDir(LOG_DIR)?.let { dir ->
            if (!dir.exists()) dir.mkdirs()
            File(dir, FILE_NAME)
        }
    }.getOrNull()
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "offer-read-logger").apply { isDaemon = true }
    }
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    init {
        val file = logFile
        if (file != null) {
            io.execute {
                runCatching {
                    if (!file.exists() || file.length() == 0L) file.writeText(HEADER + "\n")
                }.onFailure { Log.w(TAG, "could not initialise offer log", it) }
            }
        }
    }

    /** Marks a fresh app/service start so a day's file can be split into runs. */
    fun sessionStart() = write(
        listOf(now(), epoch(), "session_start") + List(COLUMN_COUNT - 4) { "" } + appVersion,
    )

    fun logServed(
        path: String,
        provider: String,
        decision: String,
        reason: String,
        coveragePercent: Int,
        captureToOverlayMs: Long,
        alternatives: Int,
        payoutCents: Long?,
        pickupMeters: Long?,
        pickupSeconds: Long?,
        tripMeters: Long?,
        tripSeconds: Long?,
        ratingScaled: Long?,
        stopCount: Long?,
    ) = write(
        listOf(
            now(), epoch(), "served", path, provider, decision, reason,
            coveragePercent.toString(), captureToOverlayMs.toString(), alternatives.toString(),
            payoutCents.orBlank(), pickupMeters.orBlank(), pickupSeconds.orBlank(),
            tripMeters.orBlank(), tripSeconds.orBlank(), ratingScaled.orBlank(), stopCount.orBlank(),
            appVersion,
        ),
    )

    private fun write(fields: List<String>) {
        val file = logFile ?: return
        // Fields are numbers, enum names and an ISO timestamp -- none can contain a comma or newline,
        // so a plain join is a valid CSV row without escaping. Guarded anyway.
        val line = fields.joinToString(",") { it.replace(',', ' ').replace('\n', ' ') }
        io.execute {
            runCatching { file.appendText(line + "\n") }
                .onFailure { Log.w(TAG, "could not append offer log row", it) }
        }
    }

    private fun now(): String = timestampFormat.format(Date())
    private fun epoch(): String = System.currentTimeMillis().toString()
    private fun Long?.orBlank(): String = this?.toString() ?: ""

    private companion object {
        const val TAG = "OfferReadLogger"
        const val LOG_DIR = "logs"
        const val FILE_NAME = "offer-log.csv"
        const val HEADER =
            "timestamp,epochMs,event,path,provider,decision,reason,coveragePercent," +
                "captureToOverlayMs,alternatives,payoutCents,pickupMeters,pickupSeconds," +
                "tripMeters,tripSeconds,ratingScaled,stopCount,appVersion"
        val COLUMN_COUNT = HEADER.split(",").size
    }
}
