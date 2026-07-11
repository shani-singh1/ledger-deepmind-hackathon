package com.khataagent.data.entity

import com.khataagent.core.model.Customer
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.InventoryItem
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun `customer round trip`() {
        val original = Customer(
            id = 7,
            name = "Ramesh Kumar",
            phoneHint = "9876543210",
            namePhonetic = "rmskmr",
            createdAt = 1_720_000_000_000L,
        )
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `customer entity round trip preserves nulls`() {
        val entity = CustomerEntity(id = 1, name = "Sita Devi", phoneHint = null, namePhonetic = null, createdAt = 0L)
        val roundTripped = entity.toDomain().toEntity()
        assertEquals(entity, roundTripped)
    }

    @Test
    fun `transaction round trip preserves enums`() {
        val original = Transaction(
            id = 42,
            customerId = 3,
            customerName = "Lakshmi Iyer",
            type = TxnType.CREDIT,
            amount = 250.0,
            item = "rice",
            note = "on account",
            status = TxnStatus.PENDING,
            source = TxnSource.VOICE,
            createdAt = 1_720_000_500_000L,
        )
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `transaction with null customer round trips (walk-in sale)`() {
        val original = Transaction(
            id = 1,
            customerId = null,
            customerName = null,
            type = TxnType.SALE,
            amount = 40.0,
            item = "biscuits",
            note = null,
            status = TxnStatus.CONFIRMED,
            source = TxnSource.TEXT,
            createdAt = 0L,
        )
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `inventory item round trip`() {
        val original = InventoryItem(id = 5, item = "oil", qty = 4.0, unit = "ltr", lowWatermark = 5.0)
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
        assertEquals(true, roundTripped.isLow)
    }

    @Test
    fun `deferral entry round trip`() {
        val original = DeferralEntry(
            id = 9,
            turnId = "turn-abc",
            rawModelOutput = "{\"tool\":\"add_credit\"}",
            reason = "DUPLICATE_SUSPECT",
            resolution = null,
            createdAt = 123L,
        )
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
    }
}
