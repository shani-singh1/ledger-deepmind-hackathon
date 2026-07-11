package com.khataagent.ui.edit

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khataagent.R
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnType
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.ui.components.capitalizeWords
import com.khataagent.ui.components.txnTypeColor
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras
import kotlinx.coroutines.launch

@Composable
private fun txnTypeChipLabel(type: TxnType): String = when (type) {
    TxnType.CREDIT -> stringResource(R.string.txn_type_credit)
    TxnType.PAYMENT -> stringResource(R.string.txn_type_payment)
    TxnType.SALE -> stringResource(R.string.txn_type_sale)
}

/**
 * Modal bottom sheet to edit (amount / item / note / type) or delete one ledger row.
 * Opened by tapping a [com.khataagent.ui.components.TxnCard]. Delete requires an explicit
 * second tap on a confirm step — a shopkeeper's khata entry is not something to lose by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(
    txn: Transaction,
    repository: LedgerRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = onDismiss,
    onDeleted: () -> Unit = onDismiss,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        EditTransactionSheetContent(
            txn = txn,
            repository = repository,
            onSaved = onSaved,
            onDeleted = onDeleted,
        )
    }
}

@Composable
private fun EditTransactionSheetContent(
    txn: Transaction,
    repository: LedgerRepository,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val extras = KhataThemeExtras.colors
    val scope = rememberCoroutineScope()

    var type by remember(txn.id) { mutableStateOf(txn.type) }
    var amountText by remember(txn.id) {
        mutableStateOf(
            if (txn.amount == txn.amount.toLong().toDouble()) txn.amount.toLong().toString() else txn.amount.toString(),
        )
    }
    var itemText by remember(txn.id) { mutableStateOf(txn.item.orEmpty()) }
    var noteText by remember(txn.id) { mutableStateOf(txn.note.orEmpty()) }
    var error by remember(txn.id) { mutableStateOf<String?>(null) }
    var saving by remember(txn.id) { mutableStateOf(false) }
    var confirmingDelete by remember(txn.id) { mutableStateOf(false) }
    val invalidAmountError = stringResource(R.string.edit_txn_error_invalid_amount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.edit_txn_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = txn.customerName?.capitalizeWords() ?: stringResource(R.string.txn_walk_in),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- type selector ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(TxnType.CREDIT, TxnType.PAYMENT, TxnType.SALE).forEach { candidate ->
                val selected = candidate == type
                val color = txnTypeColor(candidate)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            if (selected) color.copy(alpha = 0.14f) else extras.paperSurfaceRaised,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { type = candidate },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = txnTypeChipLabel(candidate),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.edit_txn_amount_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
                amountText = input.filter { it.isDigit() || it == '.' }
                error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            leadingIcon = { Text("₹", style = MaterialTheme.typography.titleMedium) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.edit_txn_item_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = itemText,
            onValueChange = { itemText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(stringResource(R.string.edit_txn_item_placeholder)) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.edit_txn_note_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(stringResource(R.string.edit_txn_note_placeholder)) },
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = error!!, style = MaterialTheme.typography.bodyMedium, color = extras.credit)
        }

        Spacer(modifier = Modifier.height(22.dp))

        if (!confirmingDelete) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = extras.credit),
                    border = BorderStroke(1.dp, extras.credit.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.edit_txn_delete))
                }
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            error = invalidAmountError
                            return@Button
                        }
                        saving = true
                        error = null
                        scope.launch {
                            repository.updateTransaction(
                                txn.copy(
                                    type = type,
                                    amount = amount,
                                    item = itemText.trim().ifBlank { null },
                                    note = noteText.trim().ifBlank { null },
                                ),
                            )
                            saving = false
                            onSaved()
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = extras.payment),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.edit_txn_save_changes))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(extras.creditContainer, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.edit_txn_delete_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = extras.credit,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.edit_txn_delete_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { confirmingDelete = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                repository.deleteTransaction(txn.id)
                                saving = false
                                onDeleted()
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = extras.credit),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.edit_txn_confirm_delete))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Edit Transaction")
@Composable
private fun EditTransactionSheetContentPreview() {
    KhataTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            EditTransactionSheetContent(
                txn = Transaction(
                    id = 1,
                    customerId = 1,
                    customerName = "Ramesh Kumar",
                    type = TxnType.CREDIT,
                    amount = 250.0,
                    item = "rice",
                    note = null,
                    createdAt = System.currentTimeMillis(),
                ),
                repository = FakeLedgerRepository(),
                onSaved = {},
                onDeleted = {},
            )
        }
    }
}
