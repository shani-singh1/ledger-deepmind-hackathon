package com.khataagent.core.model

/**
 * Pure-Kotlin domain models. Shared by every module. NO Android / Room deps here —
 * the :data module maps these to/from Room @Entity classes.
 */

enum class TxnType { CREDIT, PAYMENT, SALE }

enum class TxnStatus { CONFIRMED, PENDING, REJECTED }

/** Source of a transaction — drives the demo's "voice vs text insurance" story. */
enum class TxnSource { VOICE, TEXT, SEED }

data class Customer(
    val id: Long = 0,
    val name: String,
    val phoneHint: String? = null,
    /** Soundex/phonetic key for fuzzy matching spoken names. Filled by :data. */
    val namePhonetic: String? = null,
    val createdAt: Long = 0L,
)

data class Transaction(
    val id: Long = 0,
    /** null for a walk-in sale with no ledger customer. */
    val customerId: Long? = null,
    /** Denormalized for display so the UI never needs a join. */
    val customerName: String? = null,
    val type: TxnType,
    val amount: Double,
    val item: String? = null,
    val note: String? = null,
    val status: TxnStatus = TxnStatus.CONFIRMED,
    val source: TxnSource = TxnSource.TEXT,
    val createdAt: Long = 0L,
)

data class InventoryItem(
    val id: Long = 0,
    val item: String,
    val qty: Double,
    val unit: String = "pcs",
    val lowWatermark: Double = 0.0,
) {
    val isLow: Boolean get() = qty <= lowWatermark
}

data class DeferralEntry(
    val id: Long = 0,
    val turnId: String,
    val rawModelOutput: String,
    val reason: String,
    /** null = still open; else "committed" / "rejected" / free text. */
    val resolution: String? = null,
    val createdAt: Long = 0L,
)

/** Materialized per turn — the header numbers on the Today screen. */
data class DailyState(
    val date: String, // yyyy-MM-dd
    val openingCash: Double = 0.0,
    val totalCredit: Double = 0.0,
    val totalPayments: Double = 0.0,
    val txnCount: Int = 0,
)
