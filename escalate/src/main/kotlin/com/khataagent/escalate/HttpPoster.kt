package com.khataagent.escalate

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Tiny seam around raw HTTP so tests can inject a fake instead of hitting the network.
 * Blocking by design -- [GeminiEscalationClient] calls it from a background dispatcher.
 */
fun interface HttpPoster {
    /** POSTs [body] (JSON) to [url] and returns the raw response body. Throws on failure. */
    fun post(url: String, body: String): String
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Default okhttp-backed [HttpPoster] used in production. */
class OkHttpPoster(
    timeoutMillis: Long = 15_000L,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build(),
) : HttpPoster {

    override fun post(url: String, body: String): String {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code}: $responseBody")
            }
            return responseBody
        }
    }
}
