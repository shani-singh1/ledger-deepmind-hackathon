package com.khataagent.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.khataagent.agent.AgentOrchestrator
import com.khataagent.audio.AudioRecorder
import com.khataagent.core.AgentStatus
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.core.escalate.EscalationClient
import com.khataagent.status.AppStatusController
import com.khataagent.ui.components.StatusPill
import com.khataagent.ui.log.AgentLogScreen
import com.khataagent.ui.insights.InsightsScreen
import com.khataagent.ui.today.TodayScreen

private sealed class KhataDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Today : KhataDestination("today", "Today", Icons.AutoMirrored.Filled.ReceiptLong)
    data object Log : KhataDestination("log", "Agent Log", Icons.Filled.History)
    data object Insights : KhataDestination("insights", "Insights", Icons.Filled.QueryStats)
}

private val bottomDestinations = listOf(KhataDestination.Today, KhataDestination.Log, KhataDestination.Insights)

/**
 * Single-activity NavHost. Confirm is deliberately NOT a destination — it's a modal bottom
 * sheet layered over Today (see [com.khataagent.ui.today.TodayContent]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataNav(
    repository: LedgerRepository,
    orchestrator: AgentOrchestrator,
    audioRecorder: AudioRecorder,
    voiceAvailable: () -> Boolean,
    escalationClient: EscalationClient,
    connectivityMonitor: ConnectivityMonitor,
    statusController: AppStatusController,
    agentStatus: AgentStatus,
    isOnline: Boolean,
    onToggleConnectivity: () -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KhataAgent", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    StatusPill(status = agentStatus, modifier = Modifier.padding(end = 4.dp))
                    IconButton(onClick = onToggleConnectivity) {
                        Icon(
                            imageVector = if (isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            contentDescription = if (isOnline) "Simulate going offline" else "Simulate going online",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar {
                bottomDestinations.forEach { dest ->
                    val selected = currentRoute?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KhataDestination.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(KhataDestination.Today.route) {
                TodayScreen(
                    repository = repository,
                    orchestrator = orchestrator,
                    audioRecorder = audioRecorder,
                    voiceAvailable = voiceAvailable,
                )
            }
            composable(KhataDestination.Log.route) {
                AgentLogScreen(repository = repository)
            }
            composable(KhataDestination.Insights.route) {
                InsightsScreen(
                    repository = repository,
                    escalationClient = escalationClient,
                    connectivityMonitor = connectivityMonitor,
                    statusController = statusController,
                )
            }
        }
    }
}
