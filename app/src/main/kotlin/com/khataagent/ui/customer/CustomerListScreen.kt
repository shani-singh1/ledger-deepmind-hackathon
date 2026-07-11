package com.khataagent.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.core.data.LedgerRepository
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.components.AmountText
import com.khataagent.ui.components.capitalizeWords
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras
import com.khataagent.ui.theme.MoneyType

/**
 * All customers, biggest outstanding balance first — the shopkeeper's "who owes me the most"
 * view. Tapping a row opens [CustomerDetailScreen] for that customer's full history.
 */
@Composable
fun CustomerListScreen(
    repository: LedgerRepository,
    onCustomerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CustomerListViewModel =
        viewModel(factory = SimpleViewModelFactory { CustomerListViewModel(repository) })
    val customers by viewModel.customers.collectAsState()
    val loading by viewModel.loading.collectAsState()
    CustomerListContent(
        customers = customers,
        loading = loading,
        onCustomerClick = onCustomerClick,
        modifier = modifier,
    )
}

@Composable
private fun CustomerListContent(
    customers: List<CustomerBalance>,
    loading: Boolean,
    onCustomerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading && customers.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        customers.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No customers yet — they'll show up here once you log a credit or sale.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "Customers",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(customers, key = { it.customer.id }) { entry ->
                    CustomerRow(entry = entry, onClick = { onCustomerClick(entry.customer.id) })
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(entry: CustomerBalance, onClick: () -> Unit) {
    val extras = KhataThemeExtras.colors
    val owesMoney = entry.balance > 0.0
    val balanceColor = if (owesMoney) extras.credit else extras.payment

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(extras.paperSurfaceRaised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.customer.name.capitalizeWords(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val phone = entry.customer.phoneHint
                if (!phone.isNullOrBlank()) {
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amount = entry.balance,
                    color = balanceColor,
                    style = MoneyType.bodyAmount,
                )
                Text(
                    text = if (owesMoney) "owes" else "settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Customer List")
@Composable
private fun CustomerListScreenPreview() {
    KhataTheme {
        CustomerListScreen(repository = FakeLedgerRepository(), onCustomerClick = {})
    }
}
