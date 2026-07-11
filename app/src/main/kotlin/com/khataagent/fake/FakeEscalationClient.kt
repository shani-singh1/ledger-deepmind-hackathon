package com.khataagent.fake

import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.core.escalate.EscalationClient
import com.khataagent.core.escalate.EscalationReport
import com.khataagent.core.escalate.EscalationResult
import com.khataagent.core.escalate.LedgerSnapshot
import com.khataagent.core.escalate.ReportKind
import kotlinx.coroutines.delay

/**
 * Fake Gemini-Flash escalation client. Connectivity-gated exactly like the real one will be:
 * offline never blocks, it just queues. Online simulates network latency then returns a
 * canned markdown report so Insights has real content to render.
 */
class FakeEscalationClient(
    private val connectivity: ConnectivityMonitor,
) : EscalationClient {

    override suspend fun requestReport(snapshot: LedgerSnapshot, kind: ReportKind): EscalationResult {
        if (!connectivity.isOnline.value) {
            return EscalationResult.Queued("No connection — request queued, will sync when back online.")
        }
        delay(1400) // simulated round trip
        val report = when (kind) {
            ReportKind.WEEKLY_SUMMARY -> EscalationReport(
                kind = kind,
                title = "Weekly Summary",
                markdown = buildWeeklySummary(snapshot),
                generatedAt = System.currentTimeMillis(),
            )
            ReportKind.ANOMALY_REVIEW -> EscalationReport(
                kind = kind,
                title = "Anomaly Review",
                markdown = buildAnomalyReview(snapshot),
                generatedAt = System.currentTimeMillis(),
            )
            ReportKind.REORDER_SUGGESTIONS -> EscalationReport(
                kind = kind,
                title = "Reorder Suggestions",
                markdown = buildReorderSuggestions(snapshot),
                generatedAt = System.currentTimeMillis(),
            )
        }
        return EscalationResult.Success(report)
    }

    private fun buildWeeklySummary(snapshot: LedgerSnapshot): String {
        val totalCredit = snapshot.days.sumOf { it.totalCredit }
        val totalPayments = snapshot.days.sumOf { it.totalPayments }
        val totalTxns = snapshot.days.sumOf { it.txnCount }
        val netPct = if (totalCredit > 0) 100.0 * (totalCredit - totalPayments) / totalCredit else 0.0
        val topCredits = snapshot.topOutstandingCredits.joinToString("\n") {
            "- ${it.customerName}: " + "₹%,.0f".format(it.amount)
        }
        return """
            ## This week at a glance
            - **$totalTxns** transactions across **${snapshot.days.size}** days
            - Credit extended: **${"₹%,.0f".format(totalCredit)}**
            - Payments collected: **${"₹%,.0f".format(totalPayments)}**
            - Net position: **${"₹%,.0f".format(totalCredit - totalPayments)}**

            ### Top outstanding customers
            $topCredits

            ### Notable
            Collections trailed credit extension by about ${"%.0f".format(netPct)}%. Consider a gentle
            reminder round for the customers above ₹1,000 outstanding before the weekend rush.
        """.trimIndent()
    }

    private fun buildAnomalyReview(snapshot: LedgerSnapshot): String {
        val lowItem = snapshot.lowStock.firstOrNull()?.item ?: "no item"
        return """
            ## Anomaly review
            - No duplicate-suspect transactions escaped to commit this week — the local
              validator caught the one attempt (see Agent Log).
            - One credit entry landed right at the ₹5,000 daily-max boundary; worth a
              second look if it recurs with the same customer.
            - Stock drawdown on **$lowItem** is faster than the historical average for
              this time of month.

            Nothing here needs urgent action — flagged for visibility only.
        """.trimIndent()
    }

    private fun buildReorderSuggestions(snapshot: LedgerSnapshot): String {
        if (snapshot.lowStock.isEmpty()) {
            return "## Reorder suggestions\nEverything is comfortably above its low-watermark this week. Nothing to reorder yet."
        }
        return buildString {
            appendLine("## Reorder suggestions")
            appendLine("Below low-watermark, ranked by urgency:")
            appendLine()
            snapshot.lowStock.forEach {
                appendLine("- **${it.item}** — ${it.qty} ${it.unit} left (watermark ${it.lowWatermark} ${it.unit})")
            }
        }
    }
}
