package com.khataagent.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.live.GeminiLiveClient
import com.khataagent.live.LedgerSteward
import com.khataagent.live.LiveContextBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [LiveChatScreen]. Builds the ledger-aware system prompt once via [LiveContextBuilder],
 * opens the [GeminiLiveClient] socket, and exposes its connection [status] plus whether the mic
 * is currently streaming ([talking]) for a simple tap-to-toggle control.
 */
class LiveChatViewModel(
    private val repository: LedgerRepository,
    private val apiKey: String,
    private val shopName: String = "the shop",
) : ViewModel() {

    // Agent B — the on-device Ledger Steward — is wired in as the Live client's tool executor.
    private val steward = LedgerSteward(repository)
    private val client = GeminiLiveClient(
        apiKey = apiKey,
        scope = viewModelScope,
        toolExecutor = { name, args -> steward.execute(name, args) },
    )
    val status: StateFlow<GeminiLiveClient.LiveStatus> = client.status

    private val _talking = MutableStateFlow(false)
    val talking: StateFlow<Boolean> = _talking.asStateFlow()

    init {
        viewModelScope.launch {
            val instruction = runCatching { LiveContextBuilder.build(repository, shopName) }
                .getOrDefault(
                    "You are the voice assistant inside KhataAgent, a ledger app for a small Indian " +
                        "kirana shop. Ledger data couldn't be loaded right now, so say so if asked " +
                        "about balances or stock, and keep replies short and warm.",
                )
            client.connect(instruction + TOOL_GUIDE)
        }
    }

    /** Tap-to-toggle: start streaming mic audio, or stop it (connection stays open). */
    fun toggleTalking() {
        if (_talking.value) {
            client.stopTalking()
            _talking.value = false
        } else {
            client.startTalking()
            _talking.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }

    private companion object {
        /** How Agent A (this Planner) collaborates with Agent B (the on-device Ledger Steward). */
        const val TOOL_GUIDE = "\n\nYou can also MANAGE the shop's ledger using tools: add_credit, " +
            "record_payment, record_sale, delete_last_transaction, query_balance, query_today. An " +
            "on-device Ledger Steward validates and executes every change and replies with the result. " +
            "If it returns status \"needs_confirmation\" (e.g. an amount over the daily limit, or an " +
            "overpayment), briefly tell the shopkeeper the reason in one sentence and, ONLY if they " +
            "agree, call the same tool again with confirmed:true. For safe requests, just do it and " +
            "confirm what changed. You may chain several tool calls to finish one request. Never invent " +
            "balances — read them with the query tools. Keep spoken replies short, warm and in the " +
            "shopkeeper's language."
    }
}
