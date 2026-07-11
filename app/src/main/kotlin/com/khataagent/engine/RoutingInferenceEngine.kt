package com.khataagent.engine

import com.khataagent.core.agent.InferenceBackend
import com.khataagent.core.agent.InferenceEngine
import com.khataagent.core.escalate.ConnectivityMonitor

/**
 * Connectivity-aware brain switch: ONLINE ⇒ [cloud] (Gemini), OFFLINE ⇒ [local] (on-device Gemma).
 * The choice is made per-turn from live connectivity, so it changes automatically as the shopkeeper
 * moves in/out of network (or flips airplane mode on stage). If the cloud call fails for any reason
 * it falls back to the local engine, so a turn never dies on a flaky network.
 */
class RoutingInferenceEngine(
    private val local: InferenceEngine,
    private val cloud: InferenceEngine?,
    private val connectivity: ConnectivityMonitor,
) : InferenceEngine {

    override val backend: InferenceBackend get() = local.backend

    /** True when this turn will use the cloud brain. */
    val usingCloud: Boolean get() = cloud != null && connectivity.isOnline.value

    override suspend fun warmUp() {
        local.warmUp() // cloud needs no warm-up
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        if (usingCloud) {
            runCatching { cloud!!.generate(prompt, maxTokens) }.getOrElse { local.generate(prompt, maxTokens) }
        } else {
            local.generate(prompt, maxTokens)
        }

    override suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int,
        maxTokens: Int,
    ): String = if (usingCloud) {
        runCatching { cloud!!.generateWithAudio(prompt, audioPcm, sampleRate, maxTokens) }
            .getOrElse { local.generateWithAudio(prompt, audioPcm, sampleRate, maxTokens) }
    } else {
        local.generateWithAudio(prompt, audioPcm, sampleRate, maxTokens)
    }

    override fun close() {
        runCatching { local.close() }
        runCatching { cloud?.close() }
    }
}
