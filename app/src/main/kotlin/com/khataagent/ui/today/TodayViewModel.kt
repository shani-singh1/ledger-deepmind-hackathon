package com.khataagent.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.agent.AgentOrchestrator
import com.khataagent.audio.AudioRecorder
import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.TurnState
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.Transaction
import com.khataagent.fake.dayKey
import com.khataagent.turn.AgentTurnController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TodayViewModel(
    private val repository: LedgerRepository,
    orchestrator: AgentOrchestrator,
    audioRecorder: AudioRecorder,
    private val voiceAvailable: () -> Boolean,
) : ViewModel() {

    private val turnController = AgentTurnController(
        scope = viewModelScope,
        orchestrator = orchestrator,
        audioRecorder = audioRecorder,
        voiceAvailable = voiceAvailable,
    )

    val turnState: StateFlow<TurnState> = turnController.state

    val voiceEnabled: Boolean get() = voiceAvailable()

    val todayTransactions: StateFlow<List<Transaction>> = repository.observeTodayTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val todayKey = dayKey(System.currentTimeMillis())

    val dailyState: StateFlow<DailyState> = repository.observeDailyState(todayKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyState(date = todayKey))

    fun onMicPress() = turnController.beginListening()
    fun onMicRelease() = turnController.submitTurn()
    fun onCancelListening() = turnController.cancelListening()
    fun onSubmitText(text: String) = turnController.submitText(text)
    fun onAcceptDeferred(card: ConfirmCard) = turnController.acceptDeferred(card)
    fun onRejectDeferred(card: ConfirmCard) = turnController.rejectDeferred(card)
    fun onAcknowledge() = turnController.acknowledgeAndReset()
}
