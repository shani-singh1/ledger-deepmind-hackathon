package com.khataagent.agent

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.khataagent.core.agent.InferenceBackend
import com.khataagent.core.agent.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * Real on-device engine: Gemma 4 E2B via **LiteRT-LM** (`com.google.ai.edge.litertlm`) — the same
 * native runtime the Google AI Edge Gallery uses to run this exact `.litertlm` on the GPU at
 * ~50 tok/s. We switched to it from `mediapipe:tasks-genai` because that library's OpenCL executor
 * fails to attach the GPU delegate to `.litertlm` files (RET_CHECK in llm_litert_opencl_executor)
 * and silently drops to CPU, which is minutes-per-turn slow.
 *
 * Model is stateless (BUILD.md): a fresh [com.google.ai.edge.litertlm.Conversation] is created and
 * closed per turn so no chat history bleeds between turns — the whole prompt is rebuilt each time.
 * Native audio isn't wired on this path yet; [generateWithAudio] throws so the orchestrator/UI fall
 * back to text.
 */
class LiteRtInferenceEngine(
    @Suppress("unused") private val context: Context,
    private val modelPath: String = DEFAULT_MODEL_PATH,
    private val preferredBackend: InferenceBackend = InferenceBackend.GPU,
) : InferenceEngine {

    override var backend: InferenceBackend = preferredBackend
        private set

    /** Native-audio path not implemented on the LiteRT-LM runtime yet — text is the demo path. */
    val audioAvailable: Boolean get() = false

    private var engine: Engine? = null

    override suspend fun warmUp() {
        val order = linkedSetOf(preferredBackend, InferenceBackend.CPU)
            .filter { it != InferenceBackend.STUB }
            .ifEmpty { listOf(InferenceBackend.CPU) }

        var lastError: Throwable? = null
        for (candidate in order) {
            try {
                val t0 = System.currentTimeMillis()
                val eng = Engine(EngineConfig(modelPath = modelPath, backend = toBackend(candidate)))
                runInterruptible(Dispatchers.Default) { eng.initialize() }
                engine = eng
                backend = candidate
                android.util.Log.i(TAG, "warmUp OK backend=$candidate loadMs=${System.currentTimeMillis() - t0}")
                return
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "warmUp failed backend=$candidate: ${e.message}")
                lastError = e
            }
        }
        throw IllegalStateException("Failed to initialize LiteRT-LM engine on any backend (tried $order)", lastError)
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String {
        val eng = engine ?: error("warmUp() must complete successfully before generate()")
        val t0 = System.currentTimeMillis()
        val out = withContext(Dispatchers.Default) {
            val conversation = eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = DEFAULT_TOP_K,
                        topP = DEFAULT_TOP_P,
                        temperature = DEFAULT_TEMPERATURE,
                    ),
                ),
            )
            try {
                val reply = runInterruptible { conversation.sendMessage(prompt) }
                reply.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            } finally {
                runCatching { conversation.close() }
            }
        }
        android.util.Log.i(
            TAG,
            "generate backend=$backend promptChars=${prompt.length} outChars=${out.length} " +
                "ms=${System.currentTimeMillis() - t0} out=${out.take(160).replace("\n", "\\n")}",
        )
        return out
    }

    override suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int,
        maxTokens: Int,
    ): String = throw UnsupportedOperationException(
        "Native audio isn't wired on the LiteRT-LM path yet — fall back to text input for this turn.",
    )

    override fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    private fun toBackend(backend: InferenceBackend): Backend = when (backend) {
        InferenceBackend.GPU -> Backend.GPU()
        InferenceBackend.CPU -> Backend.CPU()
        InferenceBackend.STUB -> Backend.CPU() // never actually requested; defensive only
    }

    companion object {
        const val DEFAULT_MODEL_PATH = "/sdcard/Download/gemma-4-E2B-it.litertlm"
        private const val TAG = "KhataEngine"

        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TOP_P = 0.95
        private const val DEFAULT_TEMPERATURE = 0.2
    }
}
