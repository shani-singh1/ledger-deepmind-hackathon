package com.khataagent.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.core.escalate.CreditSummary
import com.khataagent.core.escalate.EscalationClient
import com.khataagent.core.escalate.EscalationReport
import com.khataagent.core.escalate.EscalationResult
import com.khataagent.core.escalate.LedgerSnapshot
import com.khataagent.core.escalate.ReportKind
import com.khataagent.fake.dayKey
import com.khataagent.status.AppStatusController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InsightsViewModel(
    private val repository: LedgerRepository,
    private val escalationClient: EscalationClient,
    val connectivityMonitor: ConnectivityMonitor,
    private val statusController: AppStatusController,
) : ViewModel() {

    private val _reports = MutableStateFlow(seedReports())
    val reports: StateFlow<List<EscalationReport>> = _reports.asStateFlow()

    private val _pendingKind = MutableStateFlow<ReportKind?>(null)
    val pendingKind: StateFlow<ReportKind?> = _pendingKind.asStateFlow()

    private val _queuedMessage = MutableStateFlow<String?>(null)
    val queuedMessage: StateFlow<String?> = _queuedMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline

    fun requestReport(kind: ReportKind) {
        viewModelScope.launch {
            _pendingKind.value = kind
            _queuedMessage.value = null
            statusController.setSyncing(true)
            val snapshot = buildSnapshot()
            when (val result = escalationClient.requestReport(snapshot, kind)) {
                is EscalationResult.Success -> {
                    _reports.value = listOf(result.report) + _reports.value
                }
                is EscalationResult.Queued -> {
                    _queuedMessage.value = result.reason
                }
                is EscalationResult.Failed -> {
                    _queuedMessage.value = "Couldn't reach the cloud agent: ${result.error}"
                }
            }
            statusController.setSyncing(false)
            _pendingKind.value = null
        }
    }

    private suspend fun buildSnapshot(): LedgerSnapshot {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val days = (0..6).map { offset -> repository.computeDailyState(dayKey(now - offset * dayMs)) }
        val customers = repository.allCustomers()
        val topCredits = customers.mapNotNull { c ->
            val balance = repository.outstandingBalance(c.id)
            if (balance > 0) CreditSummary(c.name, balance) else null
        }.sortedByDescending { it.amount }.take(5)
        val lowStock = repository.getInventory().filter { it.isLow }
        return LedgerSnapshot(
            generatedAt = now,
            days = days,
            topOutstandingCredits = topCredits,
            lowStock = lowStock,
            serializedJson = "{\"days\":${days.size},\"lowStock\":${lowStock.size}}",
        )
    }

    companion object {
        /** A couple of already-generated reports so Insights isn't empty on first launch. */
        private fun seedReports(): List<EscalationReport> = listOf(
            EscalationReport(
                kind = ReportKind.WEEKLY_SUMMARY,
                title = "Weekly Summary",
                markdown = """
                    ## Last week at a glance
                    - **86** transactions across **7** days
                    - Credit extended: **₹42,300**
                    - Payments collected: **₹31,150**
                    - Net position: **₹11,150**

                    ### Top outstanding customers
                    - Ramesh Kumar: ₹2,400
                    - Suresh Reddy: ₹1,850
                    - Priya Sharma: ₹1,200

                    ### Notable
                    Collections trailed credit extension by about 26%. A gentle reminder
                    round before the weekend helped bring three accounts current.
                """.trimIndent(),
                generatedAt = System.currentTimeMillis() - 20 * 60 * 60 * 1000,
            ),
        )
    }
}
