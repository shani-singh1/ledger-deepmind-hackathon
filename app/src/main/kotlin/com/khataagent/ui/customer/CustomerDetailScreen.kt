package com.khataagent.ui.customer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.Transaction
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.components.AmountText
import com.khataagent.ui.components.TxnCard
import com.khataagent.ui.components.capitalizeWords
import com.khataagent.ui.components.formatRupees
import com.khataagent.ui.edit.EditTransactionSheet
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras
import com.khataagent.ui.theme.MoneyType

/**
 * One customer's khata page: name + big outstanding-balance figure up top, full transaction
 * history below (newest first), reusing the same [TxnCard] as the Today feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    repository: LedgerRepository,
    customerId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CustomerDetailViewModel = viewModel(
        factory = SimpleViewModelFactory { CustomerDetailViewModel(repository, customerId) },
    )
    val customer by viewModel.customer.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val context = LocalContext.current

    var editingTxn by remember { mutableStateOf<Transaction?>(null) }

    CustomerDetailContent(
        customer = customer,
        balance = balance,
        transactions = transactions,
        onBack = onBack,
        onSendReminder = { c, b -> sendBalanceReminder(context, c, b) },
        onTxnClick = { txn -> editingTxn = txn },
        modifier = modifier,
    )

    editingTxn?.let { txn ->
        EditTransactionSheet(
            txn = txn,
            repository = repository,
            onDismiss = { editingTxn = null },
        )
    }
}

@Composable
private fun CustomerDetailContent(
    customer: Customer?,
    balance: Double,
    transactions: List<Transaction>,
    onBack: () -> Unit,
    onSendReminder: (Customer, Double) -> Unit,
    onTxnClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to customers",
                )
            }
            Text(
                text = customer?.name?.capitalizeWords() ?: "Customer",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        BalanceHeader(customer = customer, balance = balance)

        if (customer != null && balance > 0.0) {
            SendReminderButton(
                onClick = { onSendReminder(customer, balance) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No transactions yet for this customer.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(transactions, key = { it.id }) { txn ->
                    TxnCard(txn = txn, onClick = { onTxnClick(txn) })
                }
            }
        }
    }
}

/**
 * Killer feature for kirana shops: one tap opens WhatsApp (falling back to SMS if WhatsApp
 * isn't installed) with a polite, pre-filled Hindi reminder — no typing required.
 */
@Composable
private fun SendReminderButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extras = KhataThemeExtras.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = extras.payment),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.height(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Send Reminder",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Builds a friendly Hindi/English reminder ("Namaste ... baaki hai. Dhanyavaad.") and opens it
 * in WhatsApp via wa.me. Falls back to a plain SMS composer if WhatsApp isn't installed —
 * either way the shopkeeper never has to type the message by hand.
 */
private fun sendBalanceReminder(context: Context, customer: Customer, balance: Double) {
    val name = customer.name.capitalizeWords()
    val amount = formatRupees(balance)
    val message = "Namaste $name ji, aapka hamare yahan $amount baaki hai. Dhanyavaad."
    val phone = customer.phoneHint?.filter { it.isDigit() }
    val encodedMessage = Uri.encode(message)

    val waUri = if (!phone.isNullOrBlank()) {
        Uri.parse("https://wa.me/91$phone?text=$encodedMessage")
    } else {
        Uri.parse("https://wa.me/?text=$encodedMessage")
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, waUri))
    } catch (e: ActivityNotFoundException) {
        try {
            val smsUri = Uri.parse("smsto:${phone.orEmpty()}")
            val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", message)
            }
            context.startActivity(smsIntent)
        } catch (e2: ActivityNotFoundException) {
            // Neither WhatsApp nor an SMS app is available — nothing more we can do here.
        }
    }
}

@Composable
private fun BalanceHeader(customer: Customer?, balance: Double) {
    val extras = KhataThemeExtras.colors
    val owesMoney = balance > 0.0
    val balanceColor = if (owesMoney) extras.credit else extras.payment

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "OUTSTANDING BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            AmountText(
                amount = balance,
                color = balanceColor,
                style = MoneyType.displayAmount,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (owesMoney) "${customer?.name?.capitalizeWords() ?: "This customer"} owes you" else "Fully settled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!customer?.phoneHint.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = customer?.phoneHint.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Customer Detail")
@Composable
private fun CustomerDetailScreenPreview() {
    val repo = FakeLedgerRepository()
    KhataTheme {
        CustomerDetailScreen(repository = repo, customerId = 1L, onBack = {})
    }
}
