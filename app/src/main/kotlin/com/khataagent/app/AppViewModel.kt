package com.khataagent.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.fake.FakeConnectivityMonitor
import com.khataagent.fake.FakeEscalationClient
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.status.AppStatusController

/**
 * Holds the app-lifetime fakes (survives configuration changes) — the seam the integrator
 * will replace in Phase 2: swap [FakeLedgerRepository] for the real :data Room impl,
 * [FakeEscalationClient] for the real :escalate Gemini client, and wire a real Android
 * ConnectivityManager-backed monitor in place of [FakeConnectivityMonitor].
 */
class AppViewModel : ViewModel() {
    val repository = FakeLedgerRepository()
    val connectivity = FakeConnectivityMonitor(initiallyOnline = true)
    val escalationClient = FakeEscalationClient(connectivity)
    val statusController = AppStatusController(connectivity, viewModelScope)
}
