package com.khataagent.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Validation failures from [AddCustomerViewModel.save] — the screen maps these to localized text. */
enum class AddCustomerError {
    NAME_REQUIRED,
    PHONE_LENGTH,
    BALANCE_INVALID,
}

/**
 * Backs [AddCustomerScreen]. Plain form state + friendly (shopkeeper-language) validation.
 * On save: creates the customer via [LedgerRepository.addCustomer], then — if an opening
 * balance was entered — records it as a CREDIT transaction so the new khata page isn't a lie.
 */
class AddCustomerViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _openingBalance = MutableStateFlow("")
    val openingBalance: StateFlow<String> = _openingBalance.asStateFlow()

    private val _error = MutableStateFlow<AddCustomerError?>(null)
    val error: StateFlow<AddCustomerError?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Set once save succeeds — the new customer's id. Screen observes this to navigate away. */
    private val _savedCustomerId = MutableStateFlow<Long?>(null)
    val savedCustomerId: StateFlow<Long?> = _savedCustomerId.asStateFlow()

    fun onNameChange(value: String) {
        _name.value = value
        if (_error.value != null) _error.value = null
    }

    fun onPhoneChange(value: String) {
        _phone.value = value.filter { it.isDigit() }.take(10)
        if (_error.value != null) _error.value = null
    }

    fun onOpeningBalanceChange(value: String) {
        // Allow digits and a single decimal point — this is a rupee amount, not free text.
        _openingBalance.value = value.filter { it.isDigit() || it == '.' }
        if (_error.value != null) _error.value = null
    }

    fun save() {
        val trimmedName = _name.value.trim()
        if (trimmedName.isBlank()) {
            _error.value = AddCustomerError.NAME_REQUIRED
            return
        }
        val phoneValue = _phone.value.trim()
        if (phoneValue.isNotBlank() && phoneValue.length != 10) {
            _error.value = AddCustomerError.PHONE_LENGTH
            return
        }
        val balanceText = _openingBalance.value.trim()
        val openingAmount = if (balanceText.isBlank()) 0.0 else balanceText.toDoubleOrNull()
        if (balanceText.isNotBlank() && (openingAmount == null || openingAmount < 0.0)) {
            _error.value = AddCustomerError.BALANCE_INVALID
            return
        }

        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            val customerId = repository.addCustomer(
                name = trimmedName,
                phoneHint = phoneValue.ifBlank { null },
            )
            if (openingAmount != null && openingAmount > 0.0) {
                repository.insertTransaction(
                    Transaction(
                        customerId = customerId,
                        customerName = trimmedName,
                        type = TxnType.CREDIT,
                        amount = openingAmount,
                        note = "Opening balance",
                        status = TxnStatus.CONFIRMED,
                        source = TxnSource.TEXT,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            _saving.value = false
            _savedCustomerId.value = customerId
        }
    }
}
