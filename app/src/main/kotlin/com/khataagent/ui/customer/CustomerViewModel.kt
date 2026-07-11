package com.khataagent.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row on the customer list: the customer plus their current outstanding balance. */
data class CustomerBalance(val customer: Customer, val balance: Double)

/**
 * Backs [CustomerListScreen]. The repository has no reactive "all customers" observer, so this
 * loads customers + balances once (and on [refresh]) and sorts biggest udhaar first.
 */
class CustomerListViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _customers = MutableStateFlow<List<CustomerBalance>>(emptyList())
    val customers: StateFlow<List<CustomerBalance>> = _customers.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val withBalances = repository.allCustomers()
                .map { customer -> CustomerBalance(customer, repository.outstandingBalance(customer.id)) }
                .sortedByDescending { it.balance }
            _customers.value = withBalances
            _loading.value = false
        }
    }
}

/** Backs [CustomerDetailScreen]: one customer's header balance + full transaction history. */
class CustomerDetailViewModel(
    private val repository: LedgerRepository,
    private val customerId: Long,
) : ViewModel() {

    private val _customer = MutableStateFlow<Customer?>(null)
    val customer: StateFlow<Customer?> = _customer.asStateFlow()

    private val _balance = MutableStateFlow(0.0)
    val balance: StateFlow<Double> = _balance.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = repository.observeTransactionsForCustomer(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _customer.value = repository.allCustomers().firstOrNull { it.id == customerId }
        }
        // Recompute the outstanding balance whenever this customer's transaction history changes.
        viewModelScope.launch {
            transactions.collect {
                _balance.value = repository.outstandingBalance(customerId)
            }
        }
    }
}
