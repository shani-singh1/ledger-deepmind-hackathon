package com.khataagent.data

import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.InventoryItem
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake of [LedgerRepository] for pure-JVM tests of things that depend on the
 * interface (e.g. StateBlockBuilderImpl) without needing a real Room database.
 */
class FakeLedgerRepository(
    var customers: List<Customer> = emptyList(),
    var recent: List<Transaction> = emptyList(),
    var dailyState: DailyState = DailyState(date = "2026-07-11"),
    var balances: Map<Long, Double> = emptyMap(),
    var inventory: List<InventoryItem> = emptyList(),
) : LedgerRepository {

    override suspend fun findCustomerByName(name: String): Customer? =
        customers.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun searchByPhonetic(phoneticKey: String): List<Customer> =
        customers.filter { it.namePhonetic == phoneticKey }

    override suspend fun allCustomers(): List<Customer> = customers

    override suspend fun upsertCustomer(customer: Customer): Long = customer.id

    override suspend fun addCustomer(name: String, phoneHint: String?): Long {
        val id = (customers.maxOfOrNull { it.id } ?: 0L) + 1
        customers = customers + Customer(id = id, name = name, phoneHint = phoneHint)
        return id
    }

    override suspend fun insertTransaction(txn: Transaction): Long = txn.id

    override suspend fun updateTransactionStatus(id: Long, status: TxnStatus) { /* no-op */ }

    override suspend fun updateTransaction(txn: Transaction) {
        recent = recent.map { if (it.id == txn.id) txn else it }
    }

    override suspend fun deleteTransaction(id: Long) {
        recent = recent.filterNot { it.id == id }
    }

    override suspend fun getTransaction(id: Long): Transaction? = recent.firstOrNull { it.id == id }

    override suspend fun recentTransactions(limit: Int): List<Transaction> = recent.take(limit)

    override suspend fun transactionsSince(sinceMillis: Long): List<Transaction> =
        recent.filter { it.createdAt >= sinceMillis }

    override suspend fun outstandingBalance(customerId: Long): Double =
        balances[customerId] ?: 0.0

    override fun observeTodayTransactions(): Flow<List<Transaction>> = MutableStateFlow(recent)

    override fun observeTransactionsForCustomer(customerId: Long): Flow<List<Transaction>> =
        MutableStateFlow(recent.filter { it.customerId == customerId })

    override suspend fun getInventory(): List<InventoryItem> = inventory

    override suspend fun adjustStock(item: String, qtyDelta: Double) { /* no-op */ }

    override suspend fun addInventoryItem(item: InventoryItem): Long {
        val id = (inventory.maxOfOrNull { it.id } ?: 0L) + 1
        inventory = inventory + item.copy(id = id)
        return id
    }

    override fun observeInventory(): Flow<List<InventoryItem>> = MutableStateFlow(inventory)

    override suspend fun logDeferral(entry: DeferralEntry): Long = entry.id

    override suspend fun resolveDeferral(id: Long, resolution: String) { /* no-op */ }

    override fun observeDeferrals(): Flow<List<DeferralEntry>> = MutableStateFlow(emptyList())

    override suspend fun computeDailyState(date: String): DailyState = dailyState

    override fun observeDailyState(date: String): Flow<DailyState> = MutableStateFlow(dailyState)
}
