package com.khataagent.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-backed mirror of [com.khataagent.core.model.Transaction].
 * [type], [status], [source] store the enum's `.name` as TEXT (Room has no native enum column).
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null for a walk-in sale with no ledger customer. */
    val customerId: Long? = null,
    /** Denormalized for display so the UI never needs a join. */
    val customerName: String? = null,
    /** TxnType.name: CREDIT | PAYMENT | SALE */
    val type: String,
    val amount: Double,
    val item: String? = null,
    val note: String? = null,
    /** TxnStatus.name: CONFIRMED | PENDING | REJECTED */
    val status: String = "CONFIRMED",
    /** TxnSource.name: VOICE | TEXT | SEED */
    val source: String = "TEXT",
    val createdAt: Long = 0L,
)
