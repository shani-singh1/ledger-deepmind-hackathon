package com.khataagent.escalate

import com.khataagent.core.escalate.CreditSummary
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.InventoryItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSerializerTest {

    @Test
    fun `serializes days, credits and low stock into the snapshot json`() {
        val days = listOf(
            DailyState(date = "2026-07-10", openingCash = 1000.0, totalCredit = 500.0, totalPayments = 300.0, txnCount = 12),
            DailyState(date = "2026-07-11", openingCash = 1200.0, totalCredit = 400.0, totalPayments = 600.0, txnCount = 9),
        )
        val credits = listOf(
            CreditSummary(customerName = "Ramesh", amount = 1200.0),
            CreditSummary(customerName = "Sita", amount = 800.0),
        )
        val lowStock = listOf(
            InventoryItem(id = 1, item = "rice", qty = 2.0, unit = "kg", lowWatermark = 5.0),
        )

        val snapshot = SnapshotSerializer.build(
            days = days,
            topOutstandingCredits = credits,
            lowStock = lowStock,
            generatedAt = 42L,
        )

        // Pass-through fields preserved verbatim.
        assertEquals(42L, snapshot.generatedAt)
        assertEquals(days, snapshot.days)
        assertEquals(credits, snapshot.topOutstandingCredits)
        assertEquals(lowStock, snapshot.lowStock)

        // The serialized JSON round-trips the same data.
        val parsed = Json.parseToJsonElement(snapshot.serializedJson).jsonObject
        assertEquals(42L, parsed["generatedAt"]!!.jsonPrimitive.long)

        val parsedDays = parsed["days"]!!.jsonArray
        assertEquals(2, parsedDays.size)
        assertEquals("2026-07-10", parsedDays[0].jsonObject["date"]!!.jsonPrimitive.content)
        assertEquals(500.0, parsedDays[0].jsonObject["totalCredit"]!!.jsonPrimitive.double, 0.0001)

        val parsedCredits = parsed["topOutstandingCredits"]!!.jsonArray
        assertEquals("Ramesh", parsedCredits[0].jsonObject["customerName"]!!.jsonPrimitive.content)
        assertEquals(1200.0, parsedCredits[0].jsonObject["amount"]!!.jsonPrimitive.double, 0.0001)

        val parsedLowStock = parsed["lowStock"]!!.jsonArray
        assertEquals("rice", parsedLowStock[0].jsonObject["item"]!!.jsonPrimitive.content)
        assertTrue(parsedLowStock[0].jsonObject["isLow"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `empty snapshot still serializes valid json`() {
        val snapshot = SnapshotSerializer.build(
            days = emptyList(),
            topOutstandingCredits = emptyList(),
            lowStock = emptyList(),
            generatedAt = 0L,
        )
        val parsed = Json.parseToJsonElement(snapshot.serializedJson).jsonObject
        assertTrue(parsed["days"]!!.jsonArray.isEmpty())
        assertTrue(parsed["topOutstandingCredits"]!!.jsonArray.isEmpty())
        assertTrue(parsed["lowStock"]!!.jsonArray.isEmpty())
    }
}
