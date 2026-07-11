package com.khataagent.turn

import com.khataagent.agent.AgentOrchestrator
import com.khataagent.audio.AudioRecorder
import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.TurnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The REAL turn driver — swaps in for the simulated [TurnController] at integration. Exposes the
 * same surface the UI already calls (begin/cancel/submit/accept/reject/acknowledge + a state flow)
 * so no Composable changes, and adds [submitText] for the typed "demo insurance" path.
 *
 * It mirrors the orchestrator's own [AgentOrchestrator.state] into a local flow so the mic press
 * can show a [TurnState.Listening] overlay (a UI concern the stateless orchestrator doesn't emit)
 * and so [acknowledgeAndReset] can drop a lingering Committed/Rejected toast back to Idle.
 */
class AgentTurnController(
    private val scope: CoroutineScope,
    private val orchestrator: AgentOrchestrator,
    private val audioRecorder: AudioRecorder,
    private val voiceAvailable: () -> Boolean,
) {
    private val _state = MutableStateFlow<TurnState>(TurnState.Idle)
    val state: StateFlow<TurnState> = _state.asStateFlow()

    init {
        scope.launch { orchestrator.state.collect { _state.value = it } }
    }

    /** Mic down — freeze-frame + start capturing (perceived-latency trick). */
    fun beginListening() {
        if (!voiceAvailable()) return
        _state.value = TurnState.Listening
        runCatching { audioRecorder.start() }
    }

    fun cancelListening() {
        runCatching { audioRecorder.stop() }
        _state.value = TurnState.Idle
    }

    /** Mic up — run the real audio turn, or nudge the user to type if this model has no audio tower. */
    fun submitTurn() {
        if (!voiceAvailable()) {
            _state.value = TurnState.Errored("Voice isn't available on this model — type the entry below.")
            scope.launch { delay(1800); if (_state.value is TurnState.Errored) _state.value = TurnState.Idle }
            return
        }
        val pcm = runCatching { audioRecorder.stop() }.getOrNull() ?: ShortArray(0)
        scope.launch { orchestrator.submitAudio(pcm) }
    }

    /** The typed path — always available, fully real (text -> Gemma -> validate -> SQLite). */
    fun submitText(text: String) {
        if (text.isBlank()) return
        scope.launch { orchestrator.submitText(text.trim()) }
    }

    fun acceptDeferred(card: ConfirmCard) {
        scope.launch { orchestrator.resolveDeferral(accept = true, card = card) }
    }

    fun rejectDeferred(card: ConfirmCard) {
        scope.launch { orchestrator.resolveDeferral(accept = false, card = card) }
    }

    /** After the ledger has animated the committed/rejected line, return to Idle. */
    fun acknowledgeAndReset() {
        _state.value = TurnState.Idle
    }
}
