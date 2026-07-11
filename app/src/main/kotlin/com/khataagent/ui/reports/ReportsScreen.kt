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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.R
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
fun ReportsScreen(
    repository: LedgerRepository,
    onLiveChat: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: ReportsViewModel = viewModel(factory = SimpleViewModelFactory { ReportsViewModel(repository) })
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val shopName = remember(context) { SettingsStore(context).getShopName() }
    val shareStrings = ShareStrings(
        none = stringResource(R.string.reports_share_none),
        subject = stringResource(R.string.reports_share_subject, shopName),
        header = stringResource(R.string.reports_share_header, shopName, reportDateFormat.format(Date())),
        todayLineTemplate = R.string.reports_share_today_line,
        weekLineTemplate = R.string.reports_share_week_line,
        debtorsLineTemplate = R.string.reports_share_debtors_line,
        chooserTitle = stringResource(R.string.reports_share_button),
    )
    val resources = context.resources

    ReportsContent(
        uiState = uiState,
        shopName = shopName,
        onShare = { shareReport(context, uiState, shareStrings, resources) },
        onLiveChat = onLiveChat,
        modifier = modifier,
    )
}

/** Pre-resolved (Composable-context) strings needed by the non-Composable [shareReport] builder. */
private data class ShareStrings(
    val none: String,
    val subject: String,
    val header: String,
    val todayLineTemplate: Int,
    val weekLineTemplate: Int,
    val debtorsLineTemplate: Int,
    val chooserTitle: String,
)

@Composable
private fun ReportsContent(
    uiState: ReportsUiState,
    shopName: String,
    onShare: () -> Unit,
    onLiveChat: () -> Unit,
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
                    text = stringResource(R.string.reports_title),
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
                title = stringResource(R.string.reports_period_today),
                credit = uiState.today.totalCredit,
                payments = uiState.today.totalPayments,
                net = uiState.todayNet,
                txnCount = uiState.today.txnCount,
            )
        }
        item {
            SummaryCard(
                title = stringResource(R.string.reports_period_week),
                credit = uiState.weekCredit,
                payments = uiState.weekPayments,
                net = uiState.weekNet,
                txnCount = uiState.weekTxnCount,
            )
        }
        item {
            Button(
                onClick = onLiveChat,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(imageVector = Icons.Filled.GraphicEq, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reports_live_chat), style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reports_share_button), style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Text(
                text = stringResource(R.string.reports_top_debtors),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (uiState.topDebtors.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.reports_no_debtors),
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
                    Text(stringResource(R.string.reports_credit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(amount = credit, color = extras.credit, style = MoneyType.largeAmount)
                }
                Column {
                    Text(stringResource(R.string.reports_payments), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(amount = payments, color = extras.payment, style = MoneyType.largeAmount)
                }
                Column {
                    Text(stringResource(R.string.reports_net), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(
                        amount = net,
                        color = if (net >= 0) extras.credit else extras.payment,
                        style = MoneyType.largeAmount,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (txnCount == 1) R.string.reports_txn_count_one else R.string.reports_txn_count_other,
                    txnCount,
                ),
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
private fun shareReport(
    context: Context,
    uiState: ReportsUiState,
    strings: ShareStrings,
    resources: android.content.res.Resources,
) {
    val debtorsLine = if (uiState.topDebtors.isEmpty()) {
        strings.none
    } else {
        uiState.topDebtors.joinToString(", ") { "${it.name.capitalizeWords()} ${formatRupees(it.balance)}" }
    }
    val text = buildString {
        append(strings.header + "\n")
        append(
            resources.getString(
                strings.todayLineTemplate,
                formatRupees(uiState.today.totalCredit),
                formatRupees(uiState.today.totalPayments),
            ) + "\n",
        )
        append(
            resources.getString(
                strings.weekLineTemplate,
                formatRupees(uiState.weekCredit),
                formatRupees(uiState.weekPayments),
                uiState.weekTxnCount,
            ) + "\n",
        )
        append(resources.getString(strings.debtorsLineTemplate, debtorsLine))
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, strings.subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, strings.chooserTitle))
}

@Preview(showBackground = true, name = "Reports")
@Composable
private fun ReportsScreenPreview() {
    KhataTheme {
        ReportsScreen(repository = FakeLedgerRepository())
    }
}
