package com.khataagent.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.R
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.core.escalate.EscalationClient
import com.khataagent.core.escalate.EscalationReport
import com.khataagent.core.escalate.ReportKind
import com.khataagent.fake.FakeConnectivityMonitor
import com.khataagent.fake.FakeEscalationClient
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.status.AppStatusController
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private val reportTimeFormat = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

/**
 * Escalation reports from the cloud agent. Offline shows an elegant "queued — will sync"
 * state, never an error — the escalation layer is optional by design (BUILD.md).
 */
@Composable
fun InsightsScreen(
    repository: LedgerRepository,
    escalationClient: EscalationClient,
    connectivityMonitor: ConnectivityMonitor,
    statusController: AppStatusController,
    modifier: Modifier = Modifier,
) {
    val viewModel: InsightsViewModel = viewModel(
        factory = SimpleViewModelFactory {
            InsightsViewModel(repository, escalationClient, connectivityMonitor, statusController)
        },
    )
    val reports by viewModel.reports.collectAsState()
    val pendingKind by viewModel.pendingKind.collectAsState()
    val queuedMessage by viewModel.queuedMessage.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    InsightsContent(
        reports = reports,
        pendingKind = pendingKind,
        queuedMessage = queuedMessage,
        isOnline = isOnline,
        onRequest = viewModel::requestReport,
        modifier = modifier,
    )
}

@Composable
private fun InsightsContent(
    reports: List<EscalationReport>,
    pendingKind: ReportKind?,
    queuedMessage: String?,
    isOnline: Boolean,
    onRequest: (ReportKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.insights_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.insights_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RequestChip(stringResource(R.string.insights_chip_weekly), Icons.Filled.QueryStats, pendingKind == ReportKind.WEEKLY_SUMMARY) {
                    onRequest(ReportKind.WEEKLY_SUMMARY)
                }
                RequestChip(stringResource(R.string.insights_chip_anomalies), Icons.Filled.Warning, pendingKind == ReportKind.ANOMALY_REVIEW) {
                    onRequest(ReportKind.ANOMALY_REVIEW)
                }
                RequestChip(stringResource(R.string.insights_chip_reorder), Icons.Filled.Widgets, pendingKind == ReportKind.REORDER_SUGGESTIONS) {
                    onRequest(ReportKind.REORDER_SUGGESTIONS)
                }
            }
        }

        if (!isOnline || queuedMessage != null) {
            item { QueuedBanner(message = queuedMessage ?: stringResource(R.string.insights_offline_queue_default)) }
        }

        if (reports.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.insights_no_reports),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item { HorizontalDivider(color = KhataThemeExtras.colors.ruleLineFaint) }
            items(reports, key = { it.generatedAt.toString() + it.kind.name }) { report ->
                ReportCard(report)
            }
        }
    }
}

@Composable
private fun RequestChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, loading: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !loading, shape = RoundedCornerShape(50)) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QueuedBanner(message: String) {
    val extras = KhataThemeExtras.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(stringResource(R.string.insights_queued_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ReportCard(report: EscalationReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    reportTimeFormat.format(Date(report.generatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownText(report.markdown)
        }
    }
}

@Preview(showBackground = true, name = "Insights — online")
@Composable
private fun InsightsScreenOnlinePreview() {
    val scope = CoroutineScope(Dispatchers.Main)
    val connectivity = FakeConnectivityMonitor(initiallyOnline = true)
    KhataTheme {
        InsightsScreen(
            repository = FakeLedgerRepository(),
            escalationClient = FakeEscalationClient(connectivity),
            connectivityMonitor = connectivity,
            statusController = AppStatusController(connectivity, scope),
        )
    }
}

@Preview(showBackground = true, name = "Insights — offline queued")
@Composable
private fun InsightsScreenOfflinePreview() {
    val scope = CoroutineScope(Dispatchers.Main)
    val connectivity = FakeConnectivityMonitor(initiallyOnline = false)
    KhataTheme {
        InsightsScreen(
            repository = FakeLedgerRepository(),
            escalationClient = FakeEscalationClient(connectivity),
            connectivityMonitor = connectivity,
            statusController = AppStatusController(connectivity, scope),
        )
    }
}
