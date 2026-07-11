package com.khataagent.fake

import com.khataagent.core.escalate.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Demo-toggleable connectivity fake. Real airplane-mode is flipped physically on stage; this
 * lets the UI be exercised (and previewed) in both states without touching the device radio.
 */
class FakeConnectivityMonitor(initiallyOnline: Boolean = true) : ConnectivityMonitor {
    private val _isOnline = MutableStateFlow(initiallyOnline)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }

    fun toggle() {
        _isOnline.value = !_isOnline.value
    }
}
