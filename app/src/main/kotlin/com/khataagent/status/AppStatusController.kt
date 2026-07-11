package com.khataagent.status

import com.khataagent.core.AgentStatus
import com.khataagent.core.escalate.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Derives the single top-bar [AgentStatus] pill from connectivity + whether an escalation
 * request is currently in flight. The local turn loop never affects this — it's always
 * ON_DEVICE-capable regardless of network, per BUILD.md's "local loop never depends on network".
 */
class AppStatusController(
    connectivity: ConnectivityMonitor,
    scope: CoroutineScope,
) {
    private val _syncing = MutableStateFlow(false)

    val status: StateFlow<AgentStatus> = combine(connectivity.isOnline, _syncing) { online, syncing ->
        when {
            syncing -> AgentStatus.SYNCING
            !online -> AgentStatus.OFFLINE
            else -> AgentStatus.ON_DEVICE
        }
    }.stateIn(scope, SharingStarted.Eagerly, AgentStatus.ON_DEVICE)

    fun setSyncing(value: Boolean) {
        _syncing.value = value
    }
}
