package com.khataagent.data

import com.khataagent.core.model.InventoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedTest {

    private val data = DemoSeed.buildDemoData(nowMillis = 1_720_000_000_000L)

    @Test
    fun `has 20 customers`() {
        assertEquals(20, data.customers.size)
        assertEquals(20, data.customers.map { it.id }.distinct().size)
        assertEquals(DemoSeed.CUSTOMER_NAMES.toSet(), data.customers.map { it.name }.toSet())
    }

    @Test
    fun `every customer has a non-blank phonetic key`() {
        assertTrue(data.customers.all { !it.namePhonetic.isNullOrBlank() })
    }

    @Test
    fun `has 60 transactions`() {
        assertEquals(60, data.transactions.size)
        assertEquals(60, data.transactions.map { it.id }.distinct().size)
    }

    @Test
    fun `has 12 inventory items with at least 2 below low watermark`() {
        assertEquals(12, data.inventory.size)
        val lowCount = data.inventory.count {
            InventoryItem(it.id, it.item, it.qty, it.unit, it.lowWatermark).isLow
        }
        assertTrue("expected >= 2 low-stock items, got $lowCount", lowCount >= 2)
    }

    @Test
    fun `contains a duplicate-suspect pair - same customer, same amount, within 2 minutes`() {
        val twoMinutesMillis = 120_000L
        val duplicatePairExists = data.transactions.any { a ->
            data.transactions.any { b ->
                a.id != b.id &&
                    a.customerId == b.customerId &&
                    a.amount == b.amount &&
                    Math.abs(a.createdAt - b.createdAt) <= twoMinutesMillis
            }
        }
        assertTrue("expected at least one duplicate-suspect pair in seed data", duplicatePairExists)
    }

    @Test
    fun `duplicate-suspect pair uses the documented amount and Ramesh Kumar`() {
        val ramesh = data.customers.first { it.name == "Ramesh Kumar" }
        val rameshCreditTxns = data.transactions.filter {
            it.customerId == ramesh.id && it.amount == DemoSeed.DUPLICATE_AMOUNT
        }
        assertTrue("expected >= 2 txns for the duplicate-suspect pair", rameshCreditTxns.size >= 2)
    }

    @Test
    fun `transactions reference only known customer ids`() {
        val ids = data.customers.map { it.id }.toSet()
        assertTrue(data.transactions.all { it.customerId in ids })
    }

    @Test
    fun `mixes credit, payment and sale transaction types`() {
        val types = data.transactions.map { it.type }.toSet()
        assertEquals(setOf("CREDIT", "PAYMENT", "SALE"), types)
    }

    @Test
    fun `is deterministic across calls`() {
        val again = DemoSeed.buildDemoData(nowMillis = 1_720_000_000_000L)
        assertEquals(data, again)
    }
}
