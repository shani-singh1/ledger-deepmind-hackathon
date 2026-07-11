package com.khataagent.escalate

import com.khataagent.core.agent.InferenceBackend
import com.khataagent.core.agent.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

/**
 * Cloud inference via Gemini — used automatically WHEN ONLINE (see RoutingInferenceEngine). It runs
 * the exact same tool-call prompt the local Gemma engine does, so the agent loop downstream is
 * identical; only the brain changes. Reuses the same okhttp [HttpPoster] + Gemini DTOs as the
 * escalation client. Offline, the router falls back to on-device Gemma.
 */
class GeminiInferenceEngine(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    private val httpPoster: HttpPoster = OkHttpPoster(),
    private val timeoutMillis: Long = 20_000L,
) : InferenceEngine {

    override val backend: InferenceBackend = InferenceBackend.CPU // n/a for cloud; not STUB
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun warmUp() = Unit

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            withTimeout(timeoutMillis) {
                val requestJson = json.encodeToString(
                    GeminiRequest.serializer(),
                    GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt))))),
                )
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val raw = httpPoster.post(url, requestJson)
                json.decodeFromString(GeminiResponse.serializer(), raw)
                    .candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: ""
            }
        }

    // Audio is handled upstream by Android SpeechRecognizer -> text; cloud just sees the transcript.
    override suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int,
        maxTokens: Int,
    ): String = generate(prompt, maxTokens)

    override fun close() = Unit
}
