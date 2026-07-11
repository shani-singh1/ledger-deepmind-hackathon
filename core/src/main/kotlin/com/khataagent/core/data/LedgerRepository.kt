package com.khataagent.core.data

import com.khataagent.core.model.Customer
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.InventoryItem
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnStatus
import kotlinx.coroutines.flow.Flow

/**
 * The single source of truth. :data ships the Room-backed implementation; :app may inject an
 * in-memory fake for previews. Suspend fns run off the main thread; Flows drive the reactive UI.
 */
interface LedgerRepository {

    // ---- customers ----
    suspend fun findCustomerByName(name: String): Customer?
    suspend fun searchByPhonetic(phoneticKey: String): List<Customer>
    suspend fun allCustomers(): List<Customer>
    suspend fun upsertCustomer(customer: Customer): Long

    // ---- transactions ----
    suspend fun insertTransaction(txn: Transaction): Long
    suspend fun updateTransactionStatus(id: Long, status: TxnStatus)
    suspend fun recentTransactions(limit: Int): List<Transaction>
    suspend fun transactionsSince(sinceMillis: Long): List<Transaction>
    suspend fun outstandingBalance(customerId: Long): Double
    fun observeTodayTransactions(): Flow<List<Transaction>>
    /** Full transaction history for one customer, newest first. Drives the customer drill-down screen. */
    fun observeTransactionsForCustomer(customerId: Long): Flow<List<Transaction>>

    // ---- inventory ----
    suspend fun getInventory(): List<InventoryItem>
    suspend fun adjustStock(item: String, qtyDelta: Double)
    fun observeInventory(): Flow<List<InventoryItem>>

    // ---- deferral log ----
    suspend fun logDeferral(entry: DeferralEntry): Long
    suspend fun resolveDeferral(id: Long, resolution: String)
    fun observeDeferrals(): Flow<List<DeferralEntry>>

    // ---- daily state ----
    suspend fun computeDailyState(date: String): DailyState
    fun observeDailyState(date: String): Flow<DailyState>
}
