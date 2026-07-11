package com.khataagent.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One row in the "who owes me the most" list on the reports screen. */
data class TopDebtor(val customerId: Long, val name: String, val balance: Double)

data class ReportsUiState(
    val today: DailyState = DailyState(date = ""),
    val weekCredit: Double = 0.0,
    val weekPayments: Double = 0.0,
    val weekTxnCount: Int = 0,
    val topDebtors: List<TopDebtor> = emptyList(),
    val loading: Boolean = true,
) {
    val todayNet: Double get() = today.totalCredit - today.totalPayments
    val weekNet: Double get() = weekCredit - weekPayments
}

private val isoDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Backs [ReportsScreen]. The repository has no dedicated "weekly totals" query, so this composes
 * [LedgerRepository.computeDailyState] (today) with [LedgerRepository.transactionsSince] (the
 * trailing 7 days) and [LedgerRepository.outstandingBalance] per customer for the debtors list.
 */
class ReportsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)

            val now = System.currentTimeMillis()
            val todayKey = isoDayFormat.format(Date(now))
            val today = repository.computeDailyState(todayKey)

            val weekStart = now - 7 * DAY_MILLIS
            val weekTxns = repository.transactionsSince(weekStart)
                .filter { it.status == TxnStatus.CONFIRMED }
            val weekCredit = weekTxns.filter { it.type == TxnType.CREDIT }.sumOf { it.amount }
            val weekPayments = weekTxns.filter { it.type == TxnType.PAYMENT }.sumOf { it.amount }

            val debtors = repository.allCustomers()
                .map { customer -> TopDebtor(customer.id, customer.name, repository.outstandingBalance(customer.id)) }
                .filter { it.balance > 0.0 }
                .sortedByDescending { it.balance }
                .take(5)

            _uiState.value = ReportsUiState(
                today = today,
                weekCredit = weekCredit,
                weekPayments = weekPayments,
                weekTxnCount = weekTxns.size,
                topDebtors = debtors,
                loading = false,
            )
        }
    }
}
