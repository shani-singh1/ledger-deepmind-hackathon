package com.khataagent.escalate

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.khataagent.core.escalate.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real device implementation of [ConnectivityMonitor], backed by [ConnectivityManager].
 * Drives the top-bar `● on-device / ○ offline / ↑ syncing` status pill and gates
 * [GeminiEscalationClient] -- escalation is never allowed to block the local loop.
 *
 * Requires the `ACCESS_NETWORK_STATE` permission (declared in this module's manifest).
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = currentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value = capabilities.hasInternet()
        }

        override fun onUnavailable() {
            _isOnline.value = currentlyOnline()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    /** Call from the hosting component's onDestroy/onCleared to avoid leaking the callback. */
    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private fun currentlyOnline(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasInternet()
    }

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
