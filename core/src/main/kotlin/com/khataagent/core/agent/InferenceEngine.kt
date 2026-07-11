package com.khataagent.core.agent

/**
 * Abstraction over LiteRT-LM / Gemma 4 E2B. The :agent module ships the real MediaPipe-backed
 * implementation; :app and tests can inject a [InferenceBackend.STUB] fake so the whole app runs
 * without the 2.6 GB model (demo insurance + fast UI iteration).
 */
interface InferenceEngine {

    /** Which backend actually initialized. Drives the "on-device / latency-mode" UI hint. */
    val backend: InferenceBackend

    /** Initialize the LiteRT-LM session at splash — NEVER on first mic press (cold session is slow). */
    suspend fun warmUp()

    /** Text prompt → raw model text (expected to contain a single JSON tool call). */
    suspend fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String

    /** Native-audio path: 16 kHz mono PCM straight into Gemma's audio tower, no separate ASR. */
    suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int = 16_000,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
    ): String

    fun close()

    companion object {
        /** Tool calls are ≤ ~80 tokens; cap decode so the model can't ramble. */
        const val DEFAULT_MAX_TOKENS = 96
    }
}

enum class InferenceBackend { GPU, CPU, STUB }
