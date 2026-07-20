package br.com.nexo.driver.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import br.com.nexo.driver.BuildConfig
import br.com.nexo.driver.overlay.OfferOverlayUiModel
import br.com.nexo.driver.overlay.OverlayStatus
import java.util.Locale
import java.util.UUID

class OfferDecisionSpeaker(context: Context) : AutoCloseable {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { }
        .build()
    private var ready = false
    private var pendingModel: OfferOverlayUiModel? = null
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        debugLog("init status=$status ready=$ready")
        if (ready) {
            pendingModel?.also {
                pendingModel = null
                speakReady(it)
            }
        }
    }
    private val spoken = OfferSpeechDeduplicator()

    init {
        debugLog("setAudioAttributes=${tts.setAudioAttributes(audioAttributes)}")
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = debugLog("utterance started")

            override fun onDone(utteranceId: String?) {
                debugLog("utterance completed")
                abandonAudioFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                debugLog("utterance failed")
                abandonAudioFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                debugLog("utterance failed code=$errorCode")
                abandonAudioFocus()
            }
        })
    }

    fun speak(model: OfferOverlayUiModel) {
        if (!ready) {
            // The first accessibility event can arrive before the TTS engine finishes loading.
            // Keep only the newest offer; stale speech is worse than a short initialization delay.
            pendingModel = model
            return
        }
        speakReady(model)
    }

    private fun speakReady(model: OfferOverlayUiModel) {
        val portugueseBrazil = Locale.forLanguageTag("pt-BR")
        debugLog("pt-BR availability=${tts.isLanguageAvailable(portugueseBrazil)}")
        debugLog("pt-BR setLanguage=${tts.setLanguage(portugueseBrazil)}")
        val phrase = OfferSpeechFormatter.phraseFor(model) ?: return
        if (spoken.isDuplicate(model, phrase)) return
        debugLog("audioFocus=${audioManager.requestAudioFocus(audioFocusRequest)}")
        val speakResult = tts.speak(
            phrase,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "driver-inteligente-${UUID.randomUUID()}",
        )
        debugLog("speak result=$speakResult")
    }

    fun stop() {
        if (ready) tts.stop()
        abandonAudioFocus()
    }

    override fun close() {
        pendingModel = null
        tts.stop()
        tts.shutdown()
        abandonAudioFocus()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private fun abandonAudioFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
    }

    private companion object {
        const val TAG = "OfferDecisionSpeaker"
    }
}

object OfferSpeechFormatter {
    fun phraseFor(model: OfferOverlayUiModel): String? {
        // A blocklist match is a hard reject and takes precedence over any other spoken cue.
        if (model.isBlockedSupermarket) return "recusar corrida, supermercado"

        val action = when (model.status) {
            OverlayStatus.ACCEPT -> "aceitar corrida"
            OverlayStatus.ANALYZE -> "analisar corrida"
            OverlayStatus.REJECT -> "recusar corrida"
            OverlayStatus.UNKNOWN -> return null
        }
        val value = model.payout.takeIf { model.isPayoutAvailable } ?: "valor não identificado"
        val base = "$action, $value"
        return if (model.isTowardHome) "$base, sentido casa" else base
    }
}

class OfferSpeechDeduplicator(
    private val windowMs: Long = 15_000L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var lastKey: String? = null
    private var lastSpokenAtMs: Long = Long.MIN_VALUE

    fun isDuplicate(model: OfferOverlayUiModel, phrase: String): Boolean {
        val key = "${model.status}|${model.payout}|$phrase"
        val now = nowMs()
        val duplicate = key == lastKey && now - lastSpokenAtMs <= windowMs
        lastKey = key
        lastSpokenAtMs = now
        return duplicate
    }
}
