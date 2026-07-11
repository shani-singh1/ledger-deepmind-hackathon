package com.khataagent.escalate

import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.core.escalate.EscalationClient
import com.khataagent.core.escalate.EscalationReport
import com.khataagent.core.escalate.EscalationResult
import com.khataagent.core.escalate.LedgerSnapshot
import com.khataagent.core.escalate.ReportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P2 cloud escalation layer. Connectivity-gated; NEVER blocks the local loop.
 *
 * - Offline -> [EscalationResult.Queued] immediately, no network attempt.
 * - Network/HTTP/parse error -> [EscalationResult.Failed].
 * - The actual HTTP call is behind [HttpPoster] so this class is unit-testable with a fake.
 *
 * The API key is supplied by the integrator via constructor -- NEVER hardcoded here.
 */
class GeminiEscalationClient(
    private val connectivityMonitor: ConnectivityMonitor,
    private val apiKey: String = "",
    private val model: String = "gemini-2.5-flash",
    private val httpPoster: HttpPoster = OkHttpPoster(),
    private val timeoutMillis: Long = 20_000L,
) : EscalationClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestReport(snapshot: LedgerSnapshot, kind: ReportKind): EscalationResult {
        if (!connectivityMonitor.isOnline.value) {
            return EscalationResult.Queued("offline — will sync")
        }

        return try {
            withContext(Dispatchers.IO) {
                withTimeout(timeoutMillis) {
                    val prompt = buildPrompt(snapshot, kind)
                    val requestJson = json.encodeToString(
                        GeminiRequest.serializer(),
                        GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt))))),
                    )
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" +
                        "?key=$apiKey"
                    val rawResponse = httpPoster.post(url, requestJson)
                    parseResponse(rawResponse, kind)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            EscalationResult.Failed("timed out waiting for Gemini")
        } catch (e: Exception) {
            EscalationResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun buildPrompt(snapshot: LedgerSnapshot, kind: ReportKind): String {
        val instruction = when (kind) {
            ReportKind.WEEKLY_SUMMARY ->
                "You are the back-office assistant for a small Indian kirana (general) store. " +
                    "Given the ledger snapshot JSON below, write a concise weekly summary in markdown: " +
                    "total credit given, total payments received, net cash position, and any notable trend."
            ReportKind.ANOMALY_REVIEW ->
                "You are reviewing a kirana store's ledger for anomalies. Given the snapshot JSON below, " +
                    "flag unusual transactions, customers with fast-growing outstanding credit, or " +
                    "suspicious patterns. Respond in concise markdown with a bulleted list."
            ReportKind.REORDER_SUGGESTIONS ->
                "You are a stock-reorder assistant for a kirana store. Given the low-stock and sales data " +
                    "in the snapshot JSON below, suggest which items to reorder and roughly how much. " +
                    "Respond in concise markdown with a bulleted list."
        }
        return "$instruction\n\nLedger snapshot JSON:\n${snapshot.serializedJson}"
    }

    private fun parseResponse(rawResponse: String, kind: ReportKind): EscalationResult {
        val response = json.decodeFromString(GeminiResponse.serializer(), rawResponse)
        val text = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text

        return if (text.isNullOrBlank()) {
            EscalationResult.Failed("Gemini response contained no report text")
        } else {
            EscalationResult.Success(
                EscalationReport(
                    kind = kind,
                    title = titleFor(kind),
                    markdown = text,
                    generatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun titleFor(kind: ReportKind): String = when (kind) {
        ReportKind.WEEKLY_SUMMARY -> "Weekly Summary"
        ReportKind.ANOMALY_REVIEW -> "Anomaly Review"
        ReportKind.REORDER_SUGGESTIONS -> "Reorder Suggestions"
    }
}

@Serializable
internal data class GeminiRequest(val contents: List<GeminiContent>)

@Serializable
internal data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)

@Serializable
internal data class GeminiPart(val text: String)

@Serializable
internal data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
internal data class GeminiCandidate(val content: GeminiContent? = null)
