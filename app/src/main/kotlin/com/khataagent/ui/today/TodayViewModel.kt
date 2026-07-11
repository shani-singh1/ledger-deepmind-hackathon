package com.khataagent.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.TurnState
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.Transaction
import com.khataagent.fake.SeedData
import com.khataagent.fake.dayKey
import com.khataagent.turn.TurnController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TodayViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val turnController = TurnController(
        scope = viewModelScope,
        repository = repository,
        customerNames = SeedData.customerNames,
        itemNames = SeedData.items,
    )

    val turnState: StateFlow<TurnState> = turnController.state

    val todayTransactions: StateFlow<List<Transaction>> = repository.observeTodayTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val todayKey = dayKey(System.currentTimeMillis())

    val dailyState: StateFlow<DailyState> = repository.observeDailyState(todayKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyState(date = todayKey))

    fun onMicPress() = turnController.beginListening()
    fun onMicRelease() = turnController.submitTurn()
    fun onCancelListening() = turnController.cancelListening()
    fun onAcceptDeferred(card: ConfirmCard) = turnController.acceptDeferred(card)
    fun onRejectDeferred(card: ConfirmCard) = turnController.rejectDeferred(card)
    fun onAcknowledge() = turnController.acknowledgeAndReset()
}
