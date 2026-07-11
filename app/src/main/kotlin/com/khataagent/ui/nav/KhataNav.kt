package com.khataagent.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.khataagent.R
import com.khataagent.agent.AgentOrchestrator
import com.khataagent.audio.AudioRecorder
import com.khataagent.core.AgentStatus
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.core.escalate.EscalationClient
import com.khataagent.onboarding.LocaleManager
import com.khataagent.status.AppStatusController
import com.khataagent.ui.components.StatusPill
import com.khataagent.ui.customer.AddCustomerScreen
import com.khataagent.ui.customer.CustomerDetailScreen
import com.khataagent.ui.customer.CustomerListScreen
import com.khataagent.ui.insights.InsightsScreen
import com.khataagent.ui.inventory.InventoryScreen
import com.khataagent.ui.log.AgentLogScreen
import com.khataagent.ui.reports.ReportsScreen
import com.khataagent.ui.settings.SettingsScreen
import com.khataagent.ui.today.TodayScreen

private data class Dest(val route: String, val labelRes: Int, val icon: ImageVector)

/**
 * Single-activity nav. Four clear bottom tabs (Today · Customers · Reports · Insights) for the
 * everyday flow; the rest (Inventory · Agent Log · Settings, plus the demo online/offline switch)
 * live behind the top-bar ⋮ menu so the main surface stays simple for a non-technical shopkeeper.
 * The Confirm sheet is NOT a destination — it's a modal over Today.
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
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val bottom = listOf(
        Dest("today", R.string.nav_today, Icons.AutoMirrored.Filled.ReceiptLong),
        Dest("customers", R.string.nav_customers, Icons.Filled.People),
        Dest("reports", R.string.nav_reports, Icons.AutoMirrored.Filled.Assignment),
        Dest("insights", R.string.nav_insights, Icons.Filled.QueryStats),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    StatusPill(status = agentStatus, modifier = Modifier.padding(end = 4.dp))
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_inventory)) },
                            leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                            onClick = { menuOpen = false; navController.navigate("inventory") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_log)) },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                            onClick = { menuOpen = false; navController.navigate("log") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_settings)) },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; navController.navigate("settings") },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isOnline) "Go offline (demo)" else "Go online (demo)") },
                            leadingIcon = {
                                Icon(if (isOnline) Icons.Filled.WifiOff else Icons.Filled.Wifi, contentDescription = null)
                            },
                            onClick = { menuOpen = false; onToggleConnectivity() },
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
                bottom.forEach { dest ->
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
                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("today") {
                TodayScreen(
                    repository = repository,
                    orchestrator = orchestrator,
                    audioRecorder = audioRecorder,
                    voiceAvailable = voiceAvailable,
                    isOnline = isOnline,
                )
            }
            composable("customers") {
                Box(Modifier.fillMaxSize()) {
                    CustomerListScreen(
                        repository = repository,
                        onCustomerClick = { navController.navigate("customer/$it") },
                    )
                    FloatingActionButton(
                        onClick = { navController.navigate("add_customer") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.add_customer))
                    }
                }
            }
            composable("add_customer") {
                AddCustomerScreen(
                    repository = repository,
                    onSaved = { id -> navController.navigate("customer/$id") { popUpTo("customers") } },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(
                route = "customer/{customerId}",
                arguments = listOf(navArgument("customerId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val customerId = backStackEntry.arguments?.getLong("customerId") ?: 0L
                CustomerDetailScreen(
                    repository = repository,
                    customerId = customerId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("reports") { ReportsScreen(repository = repository) }
            composable("insights") {
                InsightsScreen(
                    repository = repository,
                    escalationClient = escalationClient,
                    connectivityMonitor = connectivityMonitor,
                    statusController = statusController,
                )
            }
            composable("inventory") { InventoryScreen(repository = repository) }
            composable("log") { AgentLogScreen(repository = repository) }
            composable("settings") {
                SettingsScreen(
                    onChangeLanguage = {
                        LocaleManager(context).clear()
                        (context as? android.app.Activity)?.recreate()
                    },
                )
            }
        }
    }
}
