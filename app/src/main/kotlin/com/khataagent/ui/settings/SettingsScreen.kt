package com.khataagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khataagent.R
import com.khataagent.settings.SettingsStore
import com.khataagent.ui.theme.KhataTheme

/**
 * Shop-level settings backed by [SettingsStore] (plain SharedPreferences — no repository, no DI).
 * "Change language" is deliberately just a callback: the integrator wires [onChangeLanguage] to
 * re-show onboarding, this screen doesn't know how language selection actually happens.
 */
@Composable
fun SettingsScreen(
    onChangeLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore(context) }

    var shopName by rememberSaveable { mutableStateOf(store.getShopName()) }
    var dailyLimitText by rememberSaveable { mutableStateOf(store.getDailyLimit().toLong().toString()) }
    var savedTick by remember { mutableStateOf(0) }

    LaunchedEffect(savedTick) {
        if (savedTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(1600)
        savedTick = 0
    }

    SettingsContent(
        shopName = shopName,
        onShopNameChange = { shopName = it },
        dailyLimitText = dailyLimitText,
        onDailyLimitChange = { text -> if (text.length <= 8 && text.all { it.isDigit() }) dailyLimitText = text },
        showSaved = savedTick != 0,
        onSave = {
            store.setShopName(shopName.trim())
            shopName = store.getShopName()
            store.setDailyLimit(dailyLimitText.toDoubleOrNull() ?: SettingsStore.DEFAULT_DAILY_LIMIT)
            dailyLimitText = store.getDailyLimit().toLong().toString()
            savedTick++
        },
        onChangeLanguage = onChangeLanguage,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    shopName: String,
    onShopNameChange: (String) -> Unit,
    dailyLimitText: String,
    onDailyLimitChange: (String) -> Unit,
    showSaved: Boolean,
    onSave: () -> Unit,
    onChangeLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(R.string.settings_shop_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = onShopNameChange,
                        label = { Text(stringResource(R.string.settings_shop_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column {
                        OutlinedTextField(
                            value = dailyLimitText,
                            onValueChange = onDailyLimitChange,
                            label = { Text(stringResource(R.string.settings_daily_limit_label)) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.settings_daily_limit_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (showSaved) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text(stringResource(R.string.settings_save), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_language_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onChangeLanguage,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(imageVector = Icons.Filled.Language, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_change_language), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Settings")
@Composable
private fun SettingsScreenPreview() {
    KhataTheme {
        SettingsScreen(onChangeLanguage = {})
    }
}
