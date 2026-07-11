package com.khataagent.engine

import com.khataagent.core.agent.InferenceBackend
import com.khataagent.core.agent.InferenceEngine

/**
 * Wraps the real LiteRT-LM engine with the model-free stub as a safety net (BUILD.md error matrix:
 * "Model file missing/corrupt -> text-only stub mode"). [warmUp] tries [primary]; if it throws
 * (model not on device, GPU+CPU both fail to init), it silently falls back to [fallback] so the
 * whole agent loop still runs on stage. Everything downstream is identical either way.
 */
class ResilientInferenceEngine(
    private val primary: InferenceEngine,
    private val fallback: InferenceEngine,
) : InferenceEngine {

    private var active: InferenceEngine = primary

    override val backend: InferenceBackend get() = active.backend

    override suspend fun warmUp() {
        active = try {
            primary.warmUp()
            primary
        } catch (e: Throwable) {
            fallback.warmUp()
            fallback
        }
    }

    /** True only when the real engine won and it actually loaded an audio tower. */
    val usingRealEngine: Boolean get() = active === primary

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        active.generate(prompt, maxTokens)

    override suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int,
        maxTokens: Int,
    ): String = active.generateWithAudio(prompt, audioPcm, sampleRate, maxTokens)

    override fun close() {
        runCatching { primary.close() }
        runCatching { fallback.close() }
    }
}
