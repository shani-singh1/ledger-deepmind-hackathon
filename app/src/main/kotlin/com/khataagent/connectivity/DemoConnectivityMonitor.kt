package com.khataagent.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.khataagent.core.escalate.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real, live internet state via [ConnectivityManager] — so the app AUTOMATICALLY switches: online
 * ⇒ cloud Gemini + Google ASR/TTS, offline (airplane mode) ⇒ on-device Gemma + local ASR/TTS. A
 * manual override ([toggle]) is kept so a presenter can force offline/online on stage without
 * fiddling with airplane mode. Default (override = null) simply follows the real network.
 */
class DemoConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** null = follow the real network; true/false = forced by the presenter. */
    @Volatile private var override: Boolean? = null

    private val _isOnline = MutableStateFlow(realOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = recompute()
        override fun onLost(network: Network) = recompute()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = recompute()
    }

    init {
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
    }

    private fun realOnline(): Boolean {
        val network = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun recompute() {
        _isOnline.value = override ?: realOnline()
    }

    /** Stage override: real → forced-offline → forced-online → real. */
    fun toggle() {
        override = when (override) {
            null -> false
            false -> true
            else -> null
        }
        recompute()
    }
}
