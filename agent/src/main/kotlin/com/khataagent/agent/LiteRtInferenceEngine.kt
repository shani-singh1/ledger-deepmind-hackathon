package com.khataagent.agent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.AudioModelOptions
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.khataagent.core.agent.InferenceBackend
import com.khataagent.core.agent.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout

/**
 * Real on-device engine: Gemma 4 E2B via LiteRT-LM (MediaPipe `tasks-genai` 0.10.35).
 *
 * ### API notes / deviations from BUILD.md's assumed surface
 * (inspected directly from the cached 0.10.35 AAR — javap against the runtime jar)
 *
 * - [LlmInference.generateResponse] is a synchronous, **stateless** call - no session object
 *   needed for plain text. That's a perfect fit: KhataAgent rebuilds the full prompt every turn
 *   and never carries chat history, so there is nothing a session would buy us for text turns.
 * - Native audio *is* exposed, but only via [LlmInferenceSession.addAudio] (raw 16-bit PCM
 *   bytes), not on [LlmInference] directly. A session must be created per audio turn with
 *   [GraphOptions.enableAudioModality] set, AND the underlying model must have been constructed
 *   with [AudioModelOptions] on [LlmInference.LlmInferenceOptions] in the first place - that
 *   flag cannot be toggled after the model is loaded. So [warmUp] tries an audio-enabled model
 *   first and falls back to a text-only model if that construction throws (e.g. an older/plain
 *   `.litertlm` file with no audio tower baked in); [generateWithAudio] then throws
 *   [UnsupportedOperationException] if that fallback happened, per the error-recovery matrix
 *   ("mic/audio fail -> fall back to text input").
 * - There is no per-call decode-length knob anywhere in this API surface.
 *   [LlmInference.LlmInferenceOptions.maxTokens] is a fixed **total context window**
 *   (prompt + response) set once at model construction, not a per-request cap - and recreating
 *   the model per call to honor a caller-supplied `maxTokens` would mean reloading a 2.6 GB
 *   model on every turn, which is a non-starter for latency. Deviation: the `maxTokens`
 *   parameter on [generate]/[generateWithAudio] is accepted for interface compatibility but is
 *   advisory only in this implementation - the real cap is [CONTEXT_WINDOW_TOKENS], sized to
 *   comfortably fit the ~1200-token prompt budget plus a short tool-call response. What
 *   actually keeps decode short is the system prompt's "exactly one JSON tool call" instruction
 *   plus the model's own end-of-turn token.
 */
class LiteRtInferenceEngine(
    private val context: Context,
    private val modelPath: String = DEFAULT_MODEL_PATH,
    private val preferredBackend: InferenceBackend = InferenceBackend.GPU,
) : InferenceEngine {

    override var backend: InferenceBackend = preferredBackend
        private set

    private var llmInference: LlmInference? = null

    /** True once a model with a working audio tower has actually been loaded. */
    private var audioSupported: Boolean = false

    /** Public read for the integrator: is the native-audio path usable on the loaded model file? */
    val audioAvailable: Boolean get() = audioSupported

    override suspend fun warmUp() {
        val order = linkedSetOf(preferredBackend, InferenceBackend.CPU)
            .filter { it != InferenceBackend.STUB }
            .ifEmpty { listOf(InferenceBackend.CPU) }

        var lastError: Throwable? = null
        for (candidate in order) {
            val mpBackend = toMediaPipeBackend(candidate)

            try {
                llmInference = runInterruptible(Dispatchers.Default) { createEngine(mpBackend, withAudio = true) }
                audioSupported = true
                backend = candidate
                return
            } catch (e: Throwable) {
                lastError = e
            }

            try {
                llmInference = runInterruptible(Dispatchers.Default) { createEngine(mpBackend, withAudio = false) }
                audioSupported = false
                backend = candidate
                return
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw IllegalStateException(
            "Failed to initialize LiteRT-LM engine on any backend (tried $order)",
            lastError,
        )
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String {
        val engine = llmInference ?: error("warmUp() must complete successfully before generate()")
        return withTimeout(TIMEOUT_MILLIS) {
            runInterruptible(Dispatchers.Default) { engine.generateResponse(prompt) }
        }
    }

    override suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int,
        maxTokens: Int,
    ): String {
        require(sampleRate == EXPECTED_SAMPLE_RATE) {
            "LiteRT-LM audio tower expects ${EXPECTED_SAMPLE_RATE}Hz mono PCM, got ${sampleRate}Hz"
        }
        val engine = llmInference ?: error("warmUp() must complete successfully before generateWithAudio()")
        if (!audioSupported) {
            throw UnsupportedOperationException(
                "The loaded .litertlm model has no working audio tower (absent, or failed to " +
                    "initialize with AudioModelOptions at warmUp()) - fall back to text input for this turn.",
            )
        }

        val clamped = if (audioPcm.size > MAX_AUDIO_SAMPLES) audioPcm.copyOf(MAX_AUDIO_SAMPLES) else audioPcm
        val bytes = clamped.toLittleEndianBytes()

        return withTimeout(TIMEOUT_MILLIS) {
            runInterruptible(Dispatchers.Default) {
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(DEFAULT_TOP_K)
                    .setTemperature(DEFAULT_TEMPERATURE)
                    .setGraphOptions(GraphOptions.builder().setEnableAudioModality(true).build())
                    .build()
                val session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
                try {
                    session.addQueryChunk(prompt)
                    session.addAudio(bytes)
                    session.generateResponse()
                } finally {
                    session.close()
                }
            }
        }
    }

    override fun close() {
        llmInference?.close()
        llmInference = null
    }

    private fun createEngine(mpBackend: LlmInference.Backend, withAudio: Boolean): LlmInference {
        val builder = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(CONTEXT_WINDOW_TOKENS)
            .setPreferredBackend(mpBackend)
        val finalBuilder = if (withAudio) {
            builder.setAudioModelOptions(
                AudioModelOptions.builder().setMaxAudioSequenceLength(MAX_AUDIO_SEQUENCE_LENGTH).build(),
            )
        } else {
            builder
        }
        return LlmInference.createFromOptions(context, finalBuilder.build())
    }

    private fun toMediaPipeBackend(backend: InferenceBackend): LlmInference.Backend = when (backend) {
        InferenceBackend.GPU -> LlmInference.Backend.GPU
        InferenceBackend.CPU -> LlmInference.Backend.CPU
        InferenceBackend.STUB -> LlmInference.Backend.CPU // never actually requested; defensive only
    }

    private fun ShortArray.toLittleEndianBytes(): ByteArray {
        val bytes = ByteArray(size * 2)
        for (i in indices) {
            val s = this[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    companion object {
        const val DEFAULT_MODEL_PATH = "/sdcard/Download/gemma-4-E2B-it.litertlm"
        const val EXPECTED_SAMPLE_RATE = 16_000

        private const val TIMEOUT_MILLIS = 20_000L

        /** Fixed total context window (prompt + response) - see class-level API notes above. */
        private const val CONTEXT_WINDOW_TOKENS = 1_536

        /** Best-effort default absent product docs for this exact model/version; tune on-device. */
        private const val MAX_AUDIO_SEQUENCE_LENGTH = 256

        private const val MAX_AUDIO_SECONDS = 15
        private const val MAX_AUDIO_SAMPLES = EXPECTED_SAMPLE_RATE * MAX_AUDIO_SECONDS

        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TEMPERATURE = 0.2f
    }
}
