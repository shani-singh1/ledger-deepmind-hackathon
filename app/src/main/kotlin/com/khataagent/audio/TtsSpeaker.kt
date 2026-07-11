package com.khataagent.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * P1 "Win the Gemma track" item 6: offline TTS confirmations (BUILD.md). Wraps Android's built-in
 * [TextToSpeech] engine -- entirely on-device, no network, works in airplane mode exactly like the
 * rest of the local loop. Uses whatever offline voice pack is already installed for the device's
 * default language; if that language isn't available it falls back to US English rather than
 * failing silently (BUILD.md error matrix: "TTS voice pack missing -> visual-only confirmations").
 *
 * Usage (wire into the turn-commit path, e.g. from AgentOrchestrator's COMMITTED state or the
 * screen observing it):
 *   val tts = rememberTtsSpeaker()
 *   ...
 *   LaunchedEffect(turnState) {
 *       if (turnState is TurnState.Committed) tts.speak(turnState.confirmationText)
 *   }
 *
 * or construct directly outside Compose: `TtsSpeaker(context).speak("Saved. Ramesh, 250 rupees credit.")`.
 */
class TtsSpeaker(context: Context) {

    private val appContext = context.applicationContext
    @Volatile private var ready = false
    private val pending = ArrayList<String>()

    private val engine: TextToSpeech = TextToSpeech(appContext) { status ->
        synchronized(this) {
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyDeviceLocale()
                // Flush anything requested before init completed.
                pending.forEach { speakInternal(it) }
                pending.clear()
            }
        }
    }

    /** Speak [text] immediately, interrupting any confirmation still playing (QUEUE_FLUSH). */
    fun speak(text: String) {
        if (text.isBlank()) return
        synchronized(this) {
            if (ready) speakInternal(text) else pending.add(text)
        }
    }

    /** Stop any in-flight utterance without shutting the engine down. */
    fun stop() {
        runCatching { engine.stop() }
    }

    /** Release the underlying engine. Call from onCleared()/onDestroy() to avoid leaking it. */
    fun shutdown() {
        runCatching { engine.stop() }
        runCatching { engine.shutdown() }
    }

    private fun speakInternal(text: String) {
        runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "khata-confirm-${System.nanoTime()}")
        }
    }

    private fun applyDeviceLocale() {
        val deviceLocale = Locale.getDefault()
        val result = runCatching { engine.setLanguage(deviceLocale) }.getOrNull()
        val unsupported = result == null ||
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        if (unsupported) {
            // Voice pack for the device language isn't installed offline -- fall back rather than
            // silently dropping every confirmation (BUILD.md: "TTS voice pack missing").
            runCatching { engine.setLanguage(Locale.US) }
        }
    }
}

/**
 * Remembers a [TtsSpeaker] scoped to the current composition and shuts it down when it leaves
 * composition, so callers don't have to manage the engine lifecycle by hand.
 */
@Composable
fun rememberTtsSpeaker(): TtsSpeaker {
    val context = LocalContext.current
    val speaker = remember { TtsSpeaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }
    return speaker
}
