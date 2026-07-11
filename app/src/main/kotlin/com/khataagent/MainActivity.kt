package com.khataagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.app.AppViewModel
import com.khataagent.ui.nav.KhataNav
import com.khataagent.ui.theme.KhataTheme

/**
 * Single activity. Installs [KhataTheme] + [KhataNav], wired to the app-lifetime fakes held
 * by [AppViewModel]. The integrator swaps those fakes for the real :data/:agent/:escalate
 * implementations here in Phase 2 — no Composable below this needs to change.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val agentStatus by appViewModel.statusController.status.collectAsState()
            val isOnline by appViewModel.connectivity.isOnline.collectAsState()

            KhataTheme {
                KhataNav(
                    repository = appViewModel.repository,
                    escalationClient = appViewModel.escalationClient,
                    connectivityMonitor = appViewModel.connectivity,
                    statusController = appViewModel.statusController,
                    agentStatus = agentStatus,
                    isOnline = isOnline,
                    onToggleConnectivity = { appViewModel.connectivity.toggle() },
                )
            }
        }
    }
}
