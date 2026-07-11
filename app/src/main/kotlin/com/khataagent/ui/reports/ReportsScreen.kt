package com.khataagent.ui.reports

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.core.data.LedgerRepository
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.settings.SettingsStore
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.components.AmountText
import com.khataagent.ui.components.capitalizeWords
import com.khataagent.ui.components.formatRupees
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras
import com.khataagent.ui.theme.MoneyType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val reportDateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/**
 * Daily/weekly reconciliation view (BUILD.md P3 "reports/reconciliation"): today's and this
 * week's credit vs payments, net movement, transaction counts, and a "top debtors" list so the
 * shopkeeper can see at a glance who owes the most. A Share button builds a plain-text summary
 * and hands it to any installed app (WhatsApp, notes, SMS) via [Intent.ACTION_SEND].
 */
@Composable
fun ReportsScreen(repository: LedgerRepository, modifier: Modifier = Modifier) {
    val viewModel: ReportsViewModel = viewModel(factory = SimpleViewModelFactory { ReportsViewModel(repository) })
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val shopName = remember(context) { SettingsStore(context).getShopName() }

    ReportsContent(
        uiState = uiState,
        shopName = shopName,
        onShare = { shareReport(context, shopName, uiState) },
        modifier = modifier,
    )
}

@Composable
private fun ReportsContent(
    uiState: ReportsUiState,
    shopName: String,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.loading && uiState.today.txnCount == 0 && uiState.topDebtors.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Reports",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "$shopName — ${reportDateFormat.format(Date())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SummaryCard(
                title = "Today",
                credit = uiState.today.totalCredit,
                payments = uiState.today.totalPayments,
                net = uiState.todayNet,
                txnCount = uiState.today.txnCount,
            )
        }
        item {
            SummaryCard(
                title = "This week",
                credit = uiState.weekCredit,
                payments = uiState.weekPayments,
                net = uiState.weekNet,
                txnCount = uiState.weekTxnCount,
            )
        }
        item {
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share report", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Text(
                text = "Top debtors",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (uiState.topDebtors.isEmpty()) {
            item {
                Text(
                    text = "Nobody owes you right now — every customer is settled up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(uiState.topDebtors, key = { it.customerId }) { debtor ->
                DebtorRow(debtor)
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, credit: Double, payments: Double, net: Double, txnCount: Int) {
    val extras = KhataThemeExtras.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Credit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(amount = credit, color = extras.credit, style = MoneyType.largeAmount)
                }
                Column {
                    Text("Payments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(amount = payments, color = extras.payment, style = MoneyType.largeAmount)
                }
                Column {
                    Text("Net", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(
                        amount = net,
                        color = if (net >= 0) extras.credit else extras.payment,
                        style = MoneyType.largeAmount,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$txnCount transaction${if (txnCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DebtorRow(debtor: TopDebtor) {
    val extras = KhataThemeExtras.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = debtor.name.capitalizeWords(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            AmountText(amount = debtor.balance, color = extras.credit, style = MoneyType.bodyAmount)
        }
    }
}

/** Builds a plain-text summary and fires an [Intent.ACTION_SEND] share sheet (WhatsApp/notes/SMS). */
private fun shareReport(context: Context, shopName: String, uiState: ReportsUiState) {
    val dateLabel = reportDateFormat.format(Date())
    val debtorsLine = if (uiState.topDebtors.isEmpty()) {
        "none"
    } else {
        uiState.topDebtors.joinToString(", ") { "${it.name.capitalizeWords()} ${formatRupees(it.balance)}" }
    }
    val text = buildString {
        append("KhataAgent — $shopName — $dateLabel\n")
        append("Credit ${formatRupees(uiState.today.totalCredit)} Payments ${formatRupees(uiState.today.totalPayments)}\n")
        append("This week: Credit ${formatRupees(uiState.weekCredit)} Payments ${formatRupees(uiState.weekPayments)} (${uiState.weekTxnCount} txns)\n")
        append("Top debtors: $debtorsLine")
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "KhataAgent report — $shopName")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share report"))
}

@Preview(showBackground = true, name = "Reports")
@Composable
private fun ReportsScreenPreview() {
    KhataTheme {
        ReportsScreen(repository = FakeLedgerRepository())
    }
}
