package com.khataagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khataagent.R
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnType
import com.khataagent.ui.theme.KhataThemeExtras
import com.khataagent.ui.theme.MoneyType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Type-color + sign + label for a transaction, per the sacred credit=red / payment=green mapping. */
@Composable
fun txnTypeColor(type: TxnType): Color {
    val extras = KhataThemeExtras.colors
    return when (type) {
        TxnType.CREDIT -> extras.credit
        TxnType.PAYMENT -> extras.payment
        TxnType.SALE -> extras.sale
    }
}

@Composable
private fun txnTypeLabel(type: TxnType): String = when (type) {
    TxnType.CREDIT -> stringResource(R.string.txn_type_credit)
    TxnType.PAYMENT -> stringResource(R.string.txn_type_payment)
    TxnType.SALE -> stringResource(R.string.txn_type_sale)
}

private fun txnSign(type: TxnType): String = when (type) {
    TxnType.CREDIT -> "+"
    TxnType.PAYMENT -> "−"
    TxnType.SALE -> ""
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

/**
 * One ledger line: customer, amount (type-colored, tabular), item, time.
 * Reads like an actual account-book row, not a chat bubble.
 */
@Composable
fun TxnCard(txn: Transaction, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val color = txnTypeColor(txn.type)
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = clickableModifier.fillMaxWidth(),
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
            // type accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = txn.customerName ?: stringResource(R.string.txn_walk_in),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (txn.source == TxnSource.VOICE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🎙",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = txnTypeLabel(txn.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!txn.item.isNullOrBlank()) {
                        Text(
                            text = "· ${txn.item}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "· ${timeFormat.format(Date(txn.createdAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AmountText(
                amount = txn.amount,
                color = color,
                style = MoneyType.bodyAmount,
                prefix = txnSign(txn.type),
            )
        }
    }
}
