package com.khataagent.data

import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.InventoryItem
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import com.khataagent.core.util.Phonetic
import com.khataagent.data.dao.CustomerDao
import com.khataagent.data.dao.DeferralDao
import com.khataagent.data.dao.InventoryDao
import com.khataagent.data.dao.TransactionDao
import com.khataagent.data.entity.CustomerEntity
import com.khataagent.data.entity.InventoryEntity
import com.khataagent.data.entity.toDomain
import com.khataagent.data.entity.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Room-backed [LedgerRepository]. Model is stateless; this class (backed by SQLite) is the brain.
 */
class RoomLedgerRepository(
    private val customerDao: CustomerDao,
    private val transactionDao: TransactionDao,
    private val inventoryDao: InventoryDao,
    private val deferralDao: DeferralDao,
) : LedgerRepository {

    // ---- customers ----

    override suspend fun findCustomerByName(name: String): Customer? =
        customerDao.findByName(name)?.toDomain()

    override suspend fun searchByPhonetic(phoneticKey: String): List<Customer> =
        customerDao.searchByPhonetic(phoneticKey).map { it.toDomain() }

    override suspend fun allCustomers(): List<Customer> =
        customerDao.allCustomers().map { it.toDomain() }

    /** Manual "add customer" entry point — always inserts a fresh row (no merge-by-name). */
    override suspend fun addCustomer(name: String, phoneHint: String?): Long =
        customerDao.insert(
            CustomerEntity(
                name = name,
                phoneHint = phoneHint,
                namePhonetic = Phonetic.key(name),
                createdAt = System.currentTimeMillis(),
            )
        )

    /**
     * Merge-by-name upsert. Always (re)computes namePhonetic via [Phonetic.key] so fuzzy lookup
     * stays correct even if the caller didn't set it. Composed from find+insert/update rather than
     * Room's @Upsert, whose update path returns -1 instead of the existing row id.
     */
    override suspend fun upsertCustomer(customer: Customer): Long {
        val phonetic = Phonetic.key(customer.name)
        val existing = customerDao.findByName(customer.name)
        return if (existing != null) {
            customerDao.update(
                existing.copy(
                    phoneHint = customer.phoneHint ?: existing.phoneHint,
                    namePhonetic = phonetic,
                )
            )
            existing.id
        } else {
            customerDao.insert(
                customer.toEntity().copy(
                    id = 0,
                    namePhonetic = phonetic,
                    createdAt = customer.createdAt.takeIf { it != 0L } ?: System.currentTimeMillis(),
                )
            )
        }
    }

    // ---- transactions ----

    override suspend fun insertTransaction(txn: Transaction): Long =
        transactionDao.insert(txn.toEntity())

    override suspend fun updateTransactionStatus(id: Long, status: TxnStatus) =
        transactionDao.updateStatus(id, status.name)

    override suspend fun updateTransaction(txn: Transaction) =
        transactionDao.update(txn.toEntity())

    override suspend fun deleteTransaction(id: Long) =
        transactionDao.delete(id)

    override suspend fun getTransaction(id: Long): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun recentTransactions(limit: Int): List<Transaction> =
        transactionDao.recent(limit).map { it.toDomain() }

    override suspend fun transactionsSince(sinceMillis: Long): List<Transaction> =
        transactionDao.since(sinceMillis).map { it.toDomain() }

    override suspend fun outstandingBalance(customerId: Long): Double =
        transactionDao.outstandingBalance(customerId)

    override fun observeTodayTransactions(): Flow<List<Transaction>> =
        transactionDao.observeSince(startOfDayMillis(LocalDate.now(ZoneId.systemDefault())))
            .map { list -> list.map { it.toDomain() } }

    override fun observeTransactionsForCustomer(customerId: Long): Flow<List<Transaction>> =
        transactionDao.observeForCustomer(customerId).map { list -> list.map { it.toDomain() } }

    // ---- inventory ----

    override suspend fun getInventory(): List<InventoryItem> =
        inventoryDao.getAll().map { it.toDomain() }

    override suspend fun adjustStock(item: String, qtyDelta: Double) {
        val existing = inventoryDao.findByItem(item)
        if (existing == null) {
            // Unknown item mentioned by the model — create the row rather than silently drop it.
            inventoryDao.insert(InventoryEntity(item = item, qty = qtyDelta.coerceAtLeast(0.0)))
        } else {
            inventoryDao.adjustStock(item, qtyDelta)
        }
    }

    override suspend fun addInventoryItem(item: InventoryItem): Long =
        inventoryDao.insert(item.toEntity())

    override fun observeInventory(): Flow<List<InventoryItem>> =
        inventoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    // ---- deferral log ----

    override suspend fun logDeferral(entry: DeferralEntry): Long =
        deferralDao.insert(entry.toEntity())

    override suspend fun resolveDeferral(id: Long, resolution: String) =
        deferralDao.resolve(id, resolution)

    override fun observeDeferrals(): Flow<List<DeferralEntry>> =
        deferralDao.observeAll().map { list -> list.map { it.toDomain() } }

    // ---- daily state ----

    override suspend fun computeDailyState(date: String): DailyState {
        val day = LocalDate.parse(date)
        val start = startOfDayMillis(day)
        val end = start + ONE_DAY_MILLIS
        val txns = transactionDao.since(start)
            .asSequence()
            .filter { it.createdAt < end && it.status == TxnStatus.CONFIRMED.name }
            .map { it.toDomain() }
            .toList()
        return dailyStateFrom(date, txns)
    }

    override fun observeDailyState(date: String): Flow<DailyState> {
        val day = LocalDate.parse(date)
        val start = startOfDayMillis(day)
        val end = start + ONE_DAY_MILLIS
        return transactionDao.observeSince(start).map { list ->
            val txns = list
                .asSequence()
                .filter { it.createdAt < end && it.status == TxnStatus.CONFIRMED.name }
                .map { it.toDomain() }
                .toList()
            dailyStateFrom(date, txns)
        }
    }

    companion object {
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

        fun startOfDayMillis(date: LocalDate): Long =
            date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        fun dailyStateFrom(date: String, txns: List<Transaction>): DailyState {
            val credit = txns.filter { it.type == TxnType.CREDIT }.sumOf { it.amount }
            val payments = txns.filter { it.type == TxnType.PAYMENT }.sumOf { it.amount }
            return DailyState(
                date = date,
                totalCredit = credit,
                totalPayments = payments,
                txnCount = txns.size,
            )
        }
    }
}
