package com.khataagent.escalate

import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.core.escalate.EscalationResult
import com.khataagent.core.escalate.LedgerSnapshot
import com.khataagent.core.escalate.ReportKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeConnectivityMonitor(online: Boolean) : ConnectivityMonitor {
    private val flow = MutableStateFlow(online)
    override val isOnline: StateFlow<Boolean> = flow
}

private class FakeHttpPoster(
    private val response: (url: String, body: String) -> String = { _, _ -> "" },
) : HttpPoster {
    var lastUrl: String? = null
    var lastBody: String? = null

    override fun post(url: String, body: String): String {
        lastUrl = url
        lastBody = body
        return response(url, body)
    }
}

private class ThrowingHttpPoster(private val exception: Exception) : HttpPoster {
    override fun post(url: String, body: String): String = throw exception
}

class GeminiEscalationClientTest {

    private val snapshot = LedgerSnapshot(
        generatedAt = 1L,
        days = emptyList(),
        topOutstandingCredits = emptyList(),
        lowStock = emptyList(),
        serializedJson = """{"generatedAt":1,"days":[],"topOutstandingCredits":[],"lowStock":[]}""",
    )

    @Test
    fun `offline immediately returns Queued without touching the network`() = runTest {
        val poster = FakeHttpPoster()
        val client = GeminiEscalationClient(
            connectivityMonitor = FakeConnectivityMonitor(online = false),
            httpPoster = poster,
        )

        val result = client.requestReport(snapshot, ReportKind.WEEKLY_SUMMARY)

        assertTrue(result is EscalationResult.Queued)
        assertEquals("offline — will sync", (result as EscalationResult.Queued).reason)
        assertEquals(null, poster.lastUrl) // never called
    }

    @Test
    fun `successful Gemini response is parsed into an EscalationReport`() = runTest {
        val sampleResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "## Weekly Summary\n- Total credit: ₹1900\n- Total payments: ₹900" }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()
        val poster = FakeHttpPoster(response = { _, _ -> sampleResponse })
        val client = GeminiEscalationClient(
            connectivityMonitor = FakeConnectivityMonitor(online = true),
            apiKey = "test-key",
            httpPoster = poster,
        )

        val result = client.requestReport(snapshot, ReportKind.WEEKLY_SUMMARY)

        assertTrue(result is EscalationResult.Success)
        val report = (result as EscalationResult.Success).report
        assertEquals(ReportKind.WEEKLY_SUMMARY, report.kind)
        assertEquals("Weekly Summary", report.title)
        assertTrue(report.markdown.contains("Total credit"))

        // Sanity-check the request was actually addressed at the right model/endpoint and carried the key.
        assertTrue(poster.lastUrl!!.contains("gemini-2.5-flash:generateContent"))
        assertTrue(poster.lastUrl!!.contains("key=test-key"))

        // The prompt text is nested inside a JSON string, so decode the request body rather than
        // substring-matching the raw (unescaped) snapshot JSON against it.
        val requestBody = kotlinx.serialization.json.Json.parseToJsonElement(poster.lastBody!!).jsonObject
        val promptText = requestBody["contents"]!!.jsonArray[0].jsonObject["parts"]!!
            .jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(promptText.contains(snapshot.serializedJson))
    }

    @Test
    fun `different report kinds produce different titles`() = runTest {
        val sampleResponse = """{"candidates":[{"content":{"parts":[{"text":"- item A: reorder 10kg"}]}}]}"""
        val poster = FakeHttpPoster(response = { _, _ -> sampleResponse })
        val client = GeminiEscalationClient(
            connectivityMonitor = FakeConnectivityMonitor(online = true),
            httpPoster = poster,
        )

        val result = client.requestReport(snapshot, ReportKind.REORDER_SUGGESTIONS)

        assertTrue(result is EscalationResult.Success)
        assertEquals("Reorder Suggestions", (result as EscalationResult.Success).report.title)
    }

    @Test
    fun `response with no candidates fails gracefully`() = runTest {
        val poster = FakeHttpPoster(response = { _, _ -> """{"candidates":[]}""" })
        val client = GeminiEscalationClient(
            connectivityMonitor = FakeConnectivityMonitor(online = true),
            httpPoster = poster,
        )

        val result = client.requestReport(snapshot, ReportKind.ANOMALY_REVIEW)

        assertTrue(result is EscalationResult.Failed)
    }

    @Test
    fun `malformed json response fails gracefully`() = runTest {
        val poster = FakeHttpPoster(response = { _, _ -> "not json at all" })
        val client = GeminiEscalationClient(
            connectivityMonitor = FakeConnectivityMonitor(online = true),
            httpPoster = poster,
        )

        val result = client.requestReport(snapshot, ReportKind.ANOMALY_REVIEW)

        assertTrue(result is EscalationResult.Failed)
    }

    @Test
    fun `network error from the poster surfaces as Failed`() = runTest {
        val client = GeminiEscalationClient(
            connectivityMonitor = FakeConnectivityMonitor(online = true),
            httpPoster = ThrowingHttpPoster(java.io.IOException("connection reset")),
        )

        val result = client.requestReport(snapshot, ReportKind.WEEKLY_SUMMARY)

        assertTrue(result is EscalationResult.Failed)
        assertEquals("connection reset", (result as EscalationResult.Failed).error)
    }
}
