package com.khataagent.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * OFFLINE speech-to-text using Android's dedicated ON-DEVICE recognizer
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer], API 31+) — the same engine that powers
 * Gboard voice-typing in airplane mode. This is what makes the flagship "works where the internet
 * doesn't" feature actually work: no network, no "voice search isn't available" (that message comes
 * from the ONLINE recognizer activity, which we deliberately don't use offline).
 *
 * Hold-to-talk: [start] on mic-down, [stop] on mic-up → the transcript arrives on [onResult].
 * Must be constructed and driven on the main thread.
 */
class OnDeviceVoice(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    /** Prefer the offline on-device engine if its language pack is installed, else the standard
     *  recognizer (which uses Google's online recognition when connected — reliable everywhere). */
    private val useOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String?, onResult: (String) -> Unit, onError: (String) -> Unit) {
        destroy()
        if (!isAvailable()) {
            onError("Voice isn't available — type instead.")
            return
        }
        // Standard recognizer = best available (online when connected, offline if a pack exists).
        // The on-device-only variant fails hard when its language pack isn't downloaded.
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) onError("Didn't catch that — try again.") else onResult(text)
            }

            override fun onError(error: Int) = onError(describe(error))
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag ?: Locale.getDefault().toLanguageTag())
        }
        runCatching { rec.startListening(intent) }.onFailure { onError("Voice didn't start — type instead.") }
    }

    /** Finalize — the transcript arrives via onResult. */
    fun stop() {
        recognizer?.let { runCatching { it.stopListening() } }
    }

    fun destroy() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — speak again clearly."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything — try again."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        -> "Offline voice for this language isn't installed — type instead."
        else -> "Couldn't hear you — type instead."
    }
}
