package com.khataagent.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.live.GeminiLiveClient
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

    private val client = GeminiLiveClient(apiKey = apiKey)
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
            client.connect(instruction)
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
}
