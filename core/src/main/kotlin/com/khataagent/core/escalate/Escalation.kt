package com.khataagent.core.escalate

import com.khataagent.core.model.DailyState
import com.khataagent.core.model.InventoryItem
import kotlinx.coroutines.flow.StateFlow

/**
 * P2 cloud layer. Connectivity-gated; NEVER blocks the local loop. Offline ⇒ queue + "will sync".
 * :escalate ships a Gemini-Flash-backed implementation behind this interface.
 */
interface EscalationClient {
    suspend fun requestReport(snapshot: LedgerSnapshot, kind: ReportKind): EscalationResult
}

enum class ReportKind { WEEKLY_SUMMARY, ANOMALY_REVIEW, REORDER_SUGGESTIONS }

data class CreditSummary(val customerName: String, val amount: Double)

/** Serialized day/week state shipped to the cloud agent. */
data class LedgerSnapshot(
    val generatedAt: Long,
    val days: List<DailyState>,
    val topOutstandingCredits: List<CreditSummary>,
    val lowStock: List<InventoryItem>,
    /** Full compact JSON of the window, ready to drop into the Gemini prompt. */
    val serializedJson: String,
)

sealed interface EscalationResult {
    data class Success(val report: EscalationReport) : EscalationResult
    /** Offline or no connectivity — request enqueued, badge "will sync". */
    data class Queued(val reason: String) : EscalationResult
    data class Failed(val error: String) : EscalationResult
}

data class EscalationReport(
    val kind: ReportKind,
    val title: String,
    val markdown: String,
    val generatedAt: Long,
)

/** Injected into UI + escalate so the whole app agrees on online/offline. */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
}
