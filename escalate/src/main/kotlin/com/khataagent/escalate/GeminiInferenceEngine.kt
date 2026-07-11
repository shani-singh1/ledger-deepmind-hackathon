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

    /**
     * ONLINE voice path: send the raw recorded audio straight to Gemini's multimodal model (far
     * better at Hindi/Kannada/mixed speech than the on-device recognizer). Gemini transcribes AND
     * turns it into the tool call in one shot.
     */
    override suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int,
        maxTokens: Int,
    ): String = withContext(Dispatchers.IO) {
        withTimeout(timeoutMillis) {
            val wav = pcmToWav(audioPcm, sampleRate)
            val b64 = java.util.Base64.getEncoder().encodeToString(wav)
            val requestJson = json.encodeToString(
                GeminiRequest.serializer(),
                GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt),
                                GeminiPart(inlineData = GeminiInlineData(mimeType = "audio/wav", data = b64)),
                            ),
                        ),
                    ),
                ),
            )
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val raw = httpPoster.post(url, requestJson)
            json.decodeFromString(GeminiResponse.serializer(), raw)
                .candidates.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
                ?: ""
        }
    }

    override fun close() = Unit

    /** 16-bit PCM (mono) -> a minimal WAV container Gemini accepts as audio/wav. */
    private fun pcmToWav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = pcm.size * 2
        val out = java.io.ByteArrayOutputStream(44 + dataSize)
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun i16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        str("RIFF"); i32(36 + dataSize); str("WAVE")
        str("fmt "); i32(16); i16(1); i16(1)               // PCM, mono
        i32(sampleRate); i32(sampleRate * 2); i16(2); i16(16)
        str("data"); i32(dataSize)
        for (s in pcm) { val v = s.toInt(); out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        return out.toByteArray()
    }
}
