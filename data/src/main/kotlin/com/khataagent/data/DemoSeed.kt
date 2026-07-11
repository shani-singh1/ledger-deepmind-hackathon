package com.khataagent.data

import com.khataagent.core.util.Phonetic
import com.khataagent.data.entity.CustomerEntity
import com.khataagent.data.entity.InventoryEntity
import com.khataagent.data.entity.TransactionEntity
import java.util.Random

/**
 * Realistic demo data — the app must never demo empty.
 *
 * [buildDemoData] is a pure function (no Room/Android/coroutines) so it's unit-testable on the
 * JVM; [seedIfEmpty] does the actual Room writes and is exercised on-device only.
 */
object DemoSeed {

    data class DemoData(
        val customers: List<CustomerEntity>,
        val transactions: List<TransactionEntity>,
        val inventory: List<InventoryEntity>,
    )

    /** 20 realistic kirana customers (Indian names). */
    val CUSTOMER_NAMES: List<String> = listOf(
        "Ramesh Kumar", "Sita Devi", "Lakshmi Iyer", "Suresh Reddy", "Priya Sharma",
        "Anil Gupta", "Kavita Nair", "Rajesh Singh", "Meena Joshi", "Vijay Patel",
        "Sunita Rao", "Arjun Menon", "Deepa Pillai", "Manoj Yadav", "Pooja Verma",
        "Ravi Shankar", "Geeta Bhat", "Ashok Choudhary", "Neha Kapoor", "Karthik Iyengar",
    )

    private val ITEMS = listOf("rice", "atta", "oil", "sugar", "dal", "milk", "biscuits", "tea")

    /** item, unit, startQty, lowWatermark — 12 rows, 2 deliberately at/under watermark (oil, milk). */
    private data class InvSeed(val item: String, val unit: String, val qty: Double, val low: Double)

    private val INVENTORY_SEED = listOf(
        InvSeed("rice", "kg", 40.0, 10.0),
        InvSeed("atta", "kg", 35.0, 10.0),
        InvSeed("oil", "ltr", 4.0, 5.0),      // low
        InvSeed("sugar", "kg", 25.0, 8.0),
        InvSeed("dal", "kg", 18.0, 6.0),
        InvSeed("milk", "ltr", 2.0, 8.0),     // low
        InvSeed("biscuits", "pkt", 60.0, 15.0),
        InvSeed("tea", "kg", 10.0, 3.0),
        InvSeed("salt", "kg", 20.0, 5.0),
        InvSeed("coffee", "kg", 8.0, 3.0),
        InvSeed("soap", "pcs", 30.0, 10.0),
        InvSeed("matchbox", "pcs", 50.0, 20.0),
    )

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val REGULAR_TXN_COUNT = 58
    private const val SEED_RNG = 42L

    /** Amount used for the deliberate duplicate-suspect pair (same customer, same amount, 90s apart). */
    const val DUPLICATE_AMOUNT = 500.0

    /**
     * Pure, deterministic builder — no I/O, no Android deps. Safe to unit test on the JVM.
     * [nowMillis] is a parameter (not System.currentTimeMillis() at call sites) purely so tests
     * can pin "now" if ever needed; production code omits it.
     */
    fun buildDemoData(nowMillis: Long = System.currentTimeMillis()): DemoData {
        val customers = CUSTOMER_NAMES.mapIndexed { idx, name ->
            CustomerEntity(
                id = (idx + 1).toLong(),
                name = name,
                phoneHint = "9" + (700000000L + idx * 137).toString().padStart(9, '0').takeLast(9),
                namePhonetic = Phonetic.key(name),
                createdAt = nowMillis - DAY_MILLIS * (30 - idx),
            )
        }

        val rng = Random(SEED_RNG)
        val transactions = mutableListOf<TransactionEntity>()
        var txnId = 1L

        // 58 regular transactions spread across the most recent 4 days (today, -1, -2, -3).
        repeat(REGULAR_TXN_COUNT) { i ->
            val daysAgo = i % 4
            val customer = customers[rng.nextInt(customers.size)]
            val type = when (rng.nextInt(3)) {
                0 -> "CREDIT"
                1 -> "PAYMENT"
                else -> "SALE"
            }
            val item = ITEMS[rng.nextInt(ITEMS.size)]
            val amount = (50 + rng.nextInt(1950)).toDouble()
            val createdAt = nowMillis - DAY_MILLIS * daysAgo - (i / 4) * 90_000L
            transactions += TransactionEntity(
                id = txnId++,
                customerId = customer.id,
                customerName = customer.name,
                type = type,
                amount = amount,
                item = if (type == "SALE") item else item.takeIf { rng.nextBoolean() },
                note = null,
                status = "CONFIRMED",
                source = "SEED",
                createdAt = createdAt,
            )
        }

        // Deliberate duplicate-suspect pair: same customer + same amount, 90s apart (within the
        // 2-minute validator window) so the demo can show a DUPLICATE_SUSPECT deferral.
        val dupCustomer = customers[0] // Ramesh Kumar
        val dupBase = nowMillis - 3_600_000L // an hour ago, today
        transactions += TransactionEntity(
            id = txnId++,
            customerId = dupCustomer.id,
            customerName = dupCustomer.name,
            type = "CREDIT",
            amount = DUPLICATE_AMOUNT,
            item = "rice",
            note = null,
            status = "CONFIRMED",
            source = "SEED",
            createdAt = dupBase,
        )
        transactions += TransactionEntity(
            id = txnId++,
            customerId = dupCustomer.id,
            customerName = dupCustomer.name,
            type = "CREDIT",
            amount = DUPLICATE_AMOUNT,
            item = "rice",
            note = "re-entered — possible duplicate",
            status = "CONFIRMED",
            source = "SEED",
            createdAt = dupBase + 90_000L,
        )

        val inventory = INVENTORY_SEED.map { s ->
            InventoryEntity(item = s.item, qty = s.qty, unit = s.unit, lowWatermark = s.low)
        }

        return DemoData(customers = customers, transactions = transactions, inventory = inventory)
    }

    /** Seeds the DB if (and only if) it's currently empty. Room-dependent — exercised on-device. */
    suspend fun seedIfEmpty(db: KhataDatabase) {
        if (db.customerDao().allCustomers().isNotEmpty()) return
        val data = buildDemoData()
        db.customerDao().insertAll(data.customers)
        db.transactionDao().insertAll(data.transactions)
        db.inventoryDao().insertAll(data.inventory)
    }
}
