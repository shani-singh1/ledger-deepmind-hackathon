package com.khataagent.escalate

import com.khataagent.core.escalate.CreditSummary
import com.khataagent.core.escalate.LedgerSnapshot
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.InventoryItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Builds the [LedgerSnapshot.serializedJson] blob shipped to the cloud agent. The `:core`
 * domain models aren't `@Serializable` (they're frozen, zero-dependency pure Kotlin), so this
 * module mirrors the fields it needs into small local DTOs purely for the wire format.
 */
object SnapshotSerializer {

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    fun build(
        days: List<DailyState>,
        topOutstandingCredits: List<CreditSummary>,
        lowStock: List<InventoryItem>,
        generatedAt: Long = System.currentTimeMillis(),
    ): LedgerSnapshot {
        val dto = SnapshotDto(
            generatedAt = generatedAt,
            days = days.map { it.toDto() },
            topOutstandingCredits = topOutstandingCredits.map { it.toDto() },
            lowStock = lowStock.map { it.toDto() },
        )
        return LedgerSnapshot(
            generatedAt = generatedAt,
            days = days,
            topOutstandingCredits = topOutstandingCredits,
            lowStock = lowStock,
            serializedJson = json.encodeToString(SnapshotDto.serializer(), dto),
        )
    }

    private fun DailyState.toDto() = DailyStateDto(
        date = date,
        openingCash = openingCash,
        totalCredit = totalCredit,
        totalPayments = totalPayments,
        txnCount = txnCount,
    )

    private fun CreditSummary.toDto() = CreditSummaryDto(
        customerName = customerName,
        amount = amount,
    )

    private fun InventoryItem.toDto() = InventoryItemDto(
        item = item,
        qty = qty,
        unit = unit,
        lowWatermark = lowWatermark,
        isLow = isLow,
    )
}

@Serializable
internal data class SnapshotDto(
    val generatedAt: Long,
    val days: List<DailyStateDto>,
    val topOutstandingCredits: List<CreditSummaryDto>,
    val lowStock: List<InventoryItemDto>,
)

@Serializable
internal data class DailyStateDto(
    val date: String,
    val openingCash: Double,
    val totalCredit: Double,
    val totalPayments: Double,
    val txnCount: Int,
)

@Serializable
internal data class CreditSummaryDto(
    val customerName: String,
    val amount: Double,
)

@Serializable
internal data class InventoryItemDto(
    val item: String,
    val qty: Double,
    val unit: String,
    val lowWatermark: Double,
    val isLow: Boolean,
)
