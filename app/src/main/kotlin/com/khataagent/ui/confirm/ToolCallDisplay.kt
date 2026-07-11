package com.khataagent.ui.confirm

import com.khataagent.core.tool.ToolCall
import com.khataagent.ui.components.formatRupees

/** Plain-shopkeeper-language summary of what the agent understood — the confirm card's headline. */
fun ToolCall.summary(): String = when (this) {
    is ToolCall.AddCredit -> buildString {
        append("Add credit of ${formatRupees(amount)} to $customer")
        if (!item.isNullOrBlank()) append(" for $item")
    }
    is ToolCall.RecordPayment -> "Record payment of ${formatRupees(amount)} from $customer"
    is ToolCall.RecordSale -> "Record sale: $qty × $item for ${formatRupees(amount)}"
    is ToolCall.UpdateStock -> {
        val sign = if (qtyDelta >= 0) "+" else ""
        "Update stock: $item ($sign${qtyDelta.toCleanString()})"
    }
    is ToolCall.QueryBalance -> "Check balance for $customer"
    is ToolCall.QueryToday -> "Read out today's totals"
    is ToolCall.CloseDay -> "Close the day's khata"
    is ToolCall.AskClarification -> question
}

/** The wire-format JSON tool name, for the "raw model output" style rendering. */
fun ToolCall.wireName(): String = when (this) {
    is ToolCall.AddCredit -> "add_credit"
    is ToolCall.RecordPayment -> "record_payment"
    is ToolCall.RecordSale -> "record_sale"
    is ToolCall.UpdateStock -> "update_stock"
    is ToolCall.QueryBalance -> "query_balance"
    is ToolCall.QueryToday -> "query_today"
    is ToolCall.CloseDay -> "close_day"
    is ToolCall.AskClarification -> "ask_clarification"
}

/** Renders the ToolCall back into the exact JSON wire format Gemma would have emitted. */
fun ToolCall.toWireJson(): String = when (this) {
    is ToolCall.AddCredit ->
        """{"tool":"add_credit","customer":"$customer","amount":${amount.toCleanString()},"item":${item.jsonOrNull()},"note":${note.jsonOrNull()}}"""
    is ToolCall.RecordPayment ->
        """{"tool":"record_payment","customer":"$customer","amount":${amount.toCleanString()}}"""
    is ToolCall.RecordSale ->
        """{"tool":"record_sale","item":"$item","qty":${qty.toCleanString()},"amount":${amount.toCleanString()}}"""
    is ToolCall.UpdateStock ->
        """{"tool":"update_stock","item":"$item","qty_delta":${qtyDelta.toCleanString()}}"""
    is ToolCall.QueryBalance ->
        """{"tool":"query_balance","customer":"$customer"}"""
    is ToolCall.QueryToday ->
        """{"tool":"query_today"}"""
    is ToolCall.CloseDay ->
        """{"tool":"close_day"}"""
    is ToolCall.AskClarification ->
        """{"tool":"ask_clarification","question":"$question"}"""
}

private fun Double.toCleanString(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

private fun String?.jsonOrNull(): String = if (this == null) "null" else "\"$this\""
