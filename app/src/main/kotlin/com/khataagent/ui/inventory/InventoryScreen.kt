package com.khataagent.ui.inventory

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.khataagent.core.model.InventoryItem
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.components.capitalizeWords
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras

/**
 * Glanceable stock view: every inventory item with its quantity, low-stock items pushed to the
 * top and flagged red with a "Low" chip, and a +/- stepper per row wired to
 * [LedgerRepository.adjustStock] for quick manual corrections (a delivery arrives, a count is off).
 */
@Composable
fun InventoryScreen(repository: LedgerRepository, modifier: Modifier = Modifier) {
    val viewModel: InventoryViewModel = viewModel(factory = SimpleViewModelFactory { InventoryViewModel(repository) })
    val inventory by viewModel.inventory.collectAsState()
    val loading by viewModel.loading.collectAsState()
    InventoryContent(
        inventory = inventory,
        loading = loading,
        onIncrement = { item -> viewModel.adjust(item, stepFor(item)) },
        onDecrement = { item -> viewModel.adjust(item, -stepFor(item)) },
        modifier = modifier,
    )
}

private fun stepFor(item: InventoryItem): Double = when (item.unit.lowercase()) {
    "kg", "l", "litre", "liter" -> 0.5
    else -> 1.0
}

/** Maps a few common raw unit codes to a localized display label; unrecognized units pass through as-is. */
@Composable
private fun localizedUnit(unit: String): String = when (unit.lowercase()) {
    "kg" -> stringResource(R.string.unit_kg)
    "g", "gram", "grams" -> stringResource(R.string.unit_g)
    "l", "litre", "liter" -> stringResource(R.string.unit_l)
    "ml" -> stringResource(R.string.unit_ml)
    "pcs", "pc", "piece", "pieces" -> stringResource(R.string.unit_pcs)
    "dozen" -> stringResource(R.string.unit_dozen)
    else -> unit
}

@Composable
private fun InventoryContent(
    inventory: List<InventoryItem>,
    loading: Boolean,
    onIncrement: (InventoryItem) -> Unit,
    onDecrement: (InventoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (inventory.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = stringResource(R.string.inventory_empty_state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.inventory_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(inventory, key = { it.id }) { stockItem ->
            InventoryRow(
                item = stockItem,
                onIncrement = { onIncrement(stockItem) },
                onDecrement = { onDecrement(stockItem) },
            )
        }
    }
}

@Composable
private fun InventoryRow(item: InventoryItem, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    val extras = KhataThemeExtras.colors
    val low = item.isLow

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (low) extras.creditContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.item.capitalizeWords(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (low) extras.credit else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    if (low) {
                        Spacer(modifier = Modifier.width(8.dp))
                        LowChip()
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatQty(item.qty)} ${localizedUnit(item.unit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (low) extras.credit else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.inventory_cd_decrease, item.item),
                    onClick = onDecrement,
                )
                Spacer(modifier = Modifier.width(4.dp))
                StepperButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.inventory_cd_increase, item.item),
                    onClick = onIncrement,
                )
            }
        }
    }
}

@Composable
private fun LowChip() {
    val extras = KhataThemeExtras.colors
    Box(
        modifier = Modifier
            .background(extras.credit.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.inventory_low_chip),
            style = MaterialTheme.typography.labelSmall,
            color = extras.credit,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.1f".format(qty)

@Preview(showBackground = true, name = "Inventory")
@Composable
private fun InventoryScreenPreview() {
    KhataTheme {
        InventoryScreen(repository = FakeLedgerRepository())
    }
}
