package com.khataagent.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * On-device speech-to-text via Android's [SpeechRecognizer] (works offline — the device ships
 * on-device recognition). The transcript is fed into the exact same text pipeline the typed path
 * uses, so the mic is a first-class input WITHOUT the model's native-audio tower (which forces the
 * GPU onto a broken/slow multimodal path). Honours the app's chosen language for recognition.
 *
 * Must be constructed and driven on the main thread.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        languageTag: String? = null,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        destroy()
        if (!available) {
            onError("Voice input isn't available on this device — type instead.")
            return
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) onError("Didn't catch that — try again or type it.") else onResult(text)
            }

            override fun onError(error: Int) =
                onError("Couldn't hear a clear command — type it instead.")

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
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        rec.startListening(intent)
    }

    /** Stop capturing and finalize — the transcript arrives via the onResult callback. */
    fun stop() {
        recognizer?.let { runCatching { it.stopListening() } }
    }

    fun destroy() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }
}
