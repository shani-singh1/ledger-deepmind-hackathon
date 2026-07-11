package com.khataagent.core.tool

/**
 * The parsed intent Gemma emits as JSON, e.g.
 *   {"tool":"add_credit","customer":"ramesh","amount":250}
 *
 * The :agent module parses raw model text into one of these; :validate consumes them;
 * the orchestrator executes them. This sealed hierarchy is the contract between all three.
 */
sealed interface ToolCall {
    data class AddCredit(
        val customer: String,
        val amount: Double,
        val item: String? = null,
        val note: String? = null,
    ) : ToolCall

    data class RecordPayment(
        val customer: String,
        val amount: Double,
    ) : ToolCall

    data class RecordSale(
        val item: String,
        val qty: Double,
        val amount: Double,
    ) : ToolCall

    data class UpdateStock(
        val item: String,
        val qtyDelta: Double,
    ) : ToolCall

    data class QueryBalance(val customer: String) : ToolCall

    data object QueryToday : ToolCall

    data object CloseDay : ToolCall

    /** The model's sanctioned "I'm not sure" escape hatch — render as a confirm card. */
    data class AskClarification(val question: String) : ToolCall
}

/** Canonical wire names — prompt builder, parser and any schema doc must all agree on these. */
object ToolNames {
    const val ADD_CREDIT = "add_credit"
    const val RECORD_PAYMENT = "record_payment"
    const val RECORD_SALE = "record_sale"
    const val UPDATE_STOCK = "update_stock"
    const val QUERY_BALANCE = "query_balance"
    const val QUERY_TODAY = "query_today"
    const val CLOSE_DAY = "close_day"
    const val ASK_CLARIFICATION = "ask_clarification"
}

/** Result of parsing raw model output into a ToolCall. */
sealed interface ToolParseResult {
    data class Success(val call: ToolCall) : ToolParseResult
    data class Failure(val rawOutput: String, val error: String) : ToolParseResult
}
