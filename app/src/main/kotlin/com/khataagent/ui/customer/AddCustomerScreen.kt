package com.khataagent.ui.customer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.core.data.LedgerRepository
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras

/**
 * Manual "add customer" form — for the shopkeeper who wants a fresh khata page ready before
 * the customer's first credit, rather than waiting for the agent to create one on the fly.
 * Name is required; phone and an opening balance are optional.
 */
@Composable
fun AddCustomerScreen(
    repository: LedgerRepository,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AddCustomerViewModel =
        viewModel(factory = SimpleViewModelFactory { AddCustomerViewModel(repository) })

    val name by viewModel.name.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val openingBalance by viewModel.openingBalance.collectAsState()
    val error by viewModel.error.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val savedCustomerId by viewModel.savedCustomerId.collectAsState()

    LaunchedEffect(savedCustomerId) {
        savedCustomerId?.let { onSaved(it) }
    }

    AddCustomerContent(
        name = name,
        phone = phone,
        openingBalance = openingBalance,
        error = error,
        saving = saving,
        onNameChange = viewModel::onNameChange,
        onPhoneChange = viewModel::onPhoneChange,
        onOpeningBalanceChange = viewModel::onOpeningBalanceChange,
        onSave = viewModel::save,
        onCancel = onCancel,
        modifier = modifier,
    )
}

@Composable
private fun AddCustomerContent(
    name: String,
    phone: String,
    openingBalance: String,
    error: String?,
    saving: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = KhataThemeExtras.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cancel",
                )
            }
            Text(
                text = "Add Customer",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "CUSTOMER NAME",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            placeholder = { Text("e.g. Ramesh Kumar") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PHONE NUMBER (OPTIONAL)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            placeholder = { Text("10-digit mobile number") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "OPENING BALANCE (OPTIONAL)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = openingBalance,
            onValueChange = onOpeningBalanceChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            placeholder = { Text("₹0 — amount already owed, if any") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Text("₹", style = MaterialTheme.typography.titleMedium) },
            colors = OutlinedTextFieldDefaults.colors(),
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = extras.credit,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = extras.payment),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "Save Customer",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, name = "Add Customer")
@Composable
private fun AddCustomerScreenPreview() {
    KhataTheme {
        AddCustomerScreen(repository = FakeLedgerRepository(), onSaved = {}, onCancel = {})
    }
}
