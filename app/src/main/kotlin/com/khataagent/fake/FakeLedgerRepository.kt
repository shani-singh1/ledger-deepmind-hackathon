package com.khataagent.fake

import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.InventoryItem
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import com.khataagent.core.util.Phonetic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

fun dayKey(millis: Long): String = dayFormat.format(Date(millis))

/**
 * In-memory [LedgerRepository] fake, pre-seeded so every screen is populated on first run.
 * Backed by [MutableStateFlow]s so the whole UI is reactive exactly like the real Room impl
 * will be — the integrator swaps this out for :data without touching any Composable.
 */
class FakeLedgerRepository : LedgerRepository {

    private val mutex = Mutex()
    private val customerIds = AtomicLong(1)
    private val txnIds = AtomicLong(1)
    private val deferralIds = AtomicLong(1)

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _inventory = MutableStateFlow<List<InventoryItem>>(emptyList())
    private val _deferrals = MutableStateFlow<List<DeferralEntry>>(emptyList())

    init {
        seed()
    }

    // ---- customers ----

    override suspend fun findCustomerByName(name: String): Customer? =
        _customers.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun searchByPhonetic(phoneticKey: String): List<Customer> =
        _customers.value.filter { it.namePhonetic == phoneticKey }

    override suspend fun allCustomers(): List<Customer> = _customers.value

    override suspend fun upsertCustomer(customer: Customer): Long = mutex.withLock {
        if (customer.id != 0L) {
            _customers.value = _customers.value.map { if (it.id == customer.id) customer else it }
            customer.id
        } else {
            val id = customerIds.getAndIncrement()
            val withId = customer.copy(id = id, namePhonetic = Phonetic.key(customer.name))
            _customers.value = _customers.value + withId
            id
        }
    }

    // ---- transactions ----

    override suspend fun insertTransaction(txn: Transaction): Long = mutex.withLock {
        val id = txnIds.getAndIncrement()
        val withId = txn.copy(id = id)
        _transactions.value = listOf(withId) + _transactions.value
        id
    }

    override suspend fun updateTransactionStatus(id: Long, status: TxnStatus) {
        _transactions.value = _transactions.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    override suspend fun recentTransactions(limit: Int): List<Transaction> =
        _transactions.value.sortedByDescending { it.createdAt }.take(limit)

    override suspend fun transactionsSince(sinceMillis: Long): List<Transaction> =
        _transactions.value.filter { it.createdAt >= sinceMillis }

    override suspend fun outstandingBalance(customerId: Long): Double {
        val net = _transactions.value
            .filter { it.customerId == customerId && it.status == TxnStatus.CONFIRMED }
            .sumOf {
                when (it.type) {
                    TxnType.CREDIT -> it.amount
                    TxnType.PAYMENT -> -it.amount
                    TxnType.SALE -> 0.0
                }
            }
        return net.coerceAtLeast(0.0)
    }

    override fun observeTodayTransactions(): Flow<List<Transaction>> {
        val today = dayKey(System.currentTimeMillis())
        return _transactions.map { list ->
            list.filter { dayKey(it.createdAt) == today }.sortedByDescending { it.createdAt }
        }
    }

    // ---- inventory ----

    override suspend fun getInventory(): List<InventoryItem> = _inventory.value

    override suspend fun adjustStock(item: String, qtyDelta: Double) {
        _inventory.value = _inventory.value.map {
            if (it.item.equals(item, ignoreCase = true)) it.copy(qty = (it.qty + qtyDelta).coerceAtLeast(0.0)) else it
        }
    }

    override fun observeInventory(): Flow<List<InventoryItem>> = _inventory

    // ---- deferral log ----

    override suspend fun logDeferral(entry: DeferralEntry): Long = mutex.withLock {
        val id = deferralIds.getAndIncrement()
        val withId = entry.copy(id = id)
        _deferrals.value = listOf(withId) + _deferrals.value
        id
    }

    override suspend fun resolveDeferral(id: Long, resolution: String) {
        _deferrals.value = _deferrals.value.map {
            if (it.id == id) it.copy(resolution = resolution) else it
        }
    }

    override fun observeDeferrals(): Flow<List<DeferralEntry>> = _deferrals

    // ---- daily state ----

    override suspend fun computeDailyState(date: String): DailyState {
        val todays = _transactions.value.filter { dayKey(it.createdAt) == date && it.status == TxnStatus.CONFIRMED }
        return DailyState(
            date = date,
            openingCash = 0.0,
            totalCredit = todays.filter { it.type == TxnType.CREDIT }.sumOf { it.amount },
            totalPayments = todays.filter { it.type == TxnType.PAYMENT }.sumOf { it.amount },
            txnCount = todays.size,
        )
    }

    override fun observeDailyState(date: String): Flow<DailyState> =
        _transactions.map { list ->
            val todays = list.filter { dayKey(it.createdAt) == date && it.status == TxnStatus.CONFIRMED }
            DailyState(
                date = date,
                openingCash = 0.0,
                totalCredit = todays.filter { it.type == TxnType.CREDIT }.sumOf { it.amount },
                totalPayments = todays.filter { it.type == TxnType.PAYMENT }.sumOf { it.amount },
                txnCount = todays.size,
            )
        }

    // Exposed for the Insights fake escalation snapshot builder.
    val transactionsFlow: StateFlow<List<Transaction>> get() = _transactions
    val customersFlow: StateFlow<List<Customer>> get() = _customers
    val inventoryFlow: StateFlow<List<InventoryItem>> get() = _inventory

    // ---- seed ----

    private fun seed() {
        val rng = Random(42)
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        val customers = SeedData.customerNames.map { name ->
            Customer(
                id = customerIds.getAndIncrement(),
                name = name,
                phoneHint = "98${rng.nextInt(10000000, 99999999)}".take(10),
                namePhonetic = Phonetic.key(name),
                createdAt = now - dayMs * rng.nextInt(5, 60),
            )
        }
        _customers.value = customers

        val inventory = SeedData.inventorySpec.mapIndexed { idx, (item, unit, low) ->
            val baseQty = low * (1.2 + rng.nextDouble() * 2.0)
            // a couple deliberately below watermark, per CONTRACTS.md seed spec
            val qty = if (idx == 2 || idx == 7) low * 0.5 else baseQty
            InventoryItem(
                id = idx + 1L,
                item = item,
                qty = (qty * 10).toLong() / 10.0,
                unit = unit,
                lowWatermark = low,
            )
        }
        _inventory.value = inventory

        val items = SeedData.items
        val txns = mutableListOf<Transaction>()

        // Spread ~40 transactions across the last 3 days, newest last-in-list-order handled by sort later.
        repeat(40) { i ->
            val dayOffset = when {
                i < 14 -> 0 // today
                i < 27 -> 1 // yesterday
                else -> 2 // day before
            }
            val hourOfDay = rng.nextInt(9, 20)
            val minute = rng.nextInt(0, 60)
            val createdAt = startOfDay(now - dayOffset * dayMs) + hourOfDay * 3_600_000L + minute * 60_000L

            val roll = rng.nextInt(0, 100)
            val customer = customers[rng.nextInt(customers.size)]
            val item = items[rng.nextInt(items.size)]

            val txn = when {
                roll < 45 -> Transaction(
                    id = txnIds.getAndIncrement(),
                    customerId = customer.id,
                    customerName = customer.name,
                    type = TxnType.CREDIT,
                    amount = roundRupee(rng.nextDouble(40.0, 1800.0)),
                    item = item,
                    status = TxnStatus.CONFIRMED,
                    source = if (rng.nextBoolean()) TxnSource.VOICE else TxnSource.SEED,
                    createdAt = createdAt,
                )
                roll < 75 -> Transaction(
                    id = txnIds.getAndIncrement(),
                    customerId = customer.id,
                    customerName = customer.name,
                    type = TxnType.SALE,
                    amount = roundRupee(rng.nextDouble(20.0, 900.0)),
                    item = item,
                    status = TxnStatus.CONFIRMED,
                    source = TxnSource.SEED,
                    createdAt = createdAt,
                )
                else -> Transaction(
                    id = txnIds.getAndIncrement(),
                    customerId = customer.id,
                    customerName = customer.name,
                    type = TxnType.PAYMENT,
                    amount = roundRupee(rng.nextDouble(100.0, 2500.0)),
                    item = null,
                    status = TxnStatus.CONFIRMED,
                    source = TxnSource.SEED,
                    createdAt = createdAt,
                )
            }
            txns += txn
        }
        _transactions.value = txns.sortedByDescending { it.createdAt }

        // A handful of seeded deferral-log entries so Agent Log isn't empty on first launch,
        // covering the spread of DeferKind scenarios the validator can raise.
        val seededDeferrals = listOf(
            DeferralEntry(
                id = deferralIds.getAndIncrement(),
                turnId = "seed-1",
                rawModelOutput = """{"tool":"add_credit","customer":"ramesh","amount":12000,"item":"cement"}""",
                reason = "OVER_DAILY_MAX — ₹12,000 is above the ₹5,000 single-transaction limit",
                resolution = "rejected",
                createdAt = now - dayMs - 3_600_000L,
            ),
            DeferralEntry(
                id = deferralIds.getAndIncrement(),
                turnId = "seed-2",
                rawModelOutput = """{"tool":"add_credit","customer":"lakshmi","amount":300,"item":"sugar"}""",
                reason = "CUSTOMER_AMBIGUOUS — 2 matches: Lakshmi Amma, Lakshmi (new)",
                resolution = "committed",
                createdAt = now - dayMs * 2 - 7_200_000L,
            ),
            DeferralEntry(
                id = deferralIds.getAndIncrement(),
                turnId = "seed-3",
                rawModelOutput = """{"tool":"record_payment","customer":"suresh","amount":5000}""",
                reason = "OVERPAYMENT — Suresh Reddy's outstanding balance is less than ₹5,000",
                resolution = "committed",
                createdAt = now - dayMs * 2 - 3_600_000L,
            ),
            DeferralEntry(
                id = deferralIds.getAndIncrement(),
                turnId = "seed-4",
                rawModelOutput = """{"tool":"ask_clarification","question":"Which Ramesh — the one on MG Road?"}""",
                reason = "MODEL_CLARIFICATION — model asked instead of guessing",
                resolution = null,
                createdAt = now - 1_800_000L,
            ),
            DeferralEntry(
                id = deferralIds.getAndIncrement(),
                turnId = "seed-5",
                rawModelOutput = """{"tool":"add_credit","customer":"kavita","amount":450,"item":"rice"}""",
                reason = "DUPLICATE_SUSPECT — same customer + amount within 2 minutes",
                resolution = null,
                createdAt = now - 600_000L,
            ),
        )
        _deferrals.value = seededDeferrals
    }

    private fun startOfDay(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun roundRupee(v: Double): Double = (v / 5.0).toLong() * 5.0
}
