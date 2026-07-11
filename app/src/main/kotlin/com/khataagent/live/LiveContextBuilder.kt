package com.khataagent.live

import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the `systemInstruction` text handed to Gemini Live so the voice assistant can answer
 * real questions about *this shop's* books ("who owes me the most?", "how was this week?",
 * "what's running low?") instead of generic chit-chat. Pulled fresh from [LedgerRepository] each
 * time a live-chat session starts.
 */
object LiveContextBuilder {

    private val isoDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val humanDayFormat = SimpleDateFormat("EEEE d MMM", Locale.US)
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    suspend fun build(repository: LedgerRepository, shopName: String = "the shop"): String {
        val now = System.currentTimeMillis()
        val todayKey = isoDayFormat.format(Date(now))
        val today = repository.computeDailyState(todayKey)

        val weekTxns = repository.transactionsSince(now - 7 * DAY_MILLIS)
            .filter { it.status == TxnStatus.CONFIRMED }
        val weekCredit = weekTxns.filter { it.type == TxnType.CREDIT }.sumOf { it.amount }
        val weekPayments = weekTxns.filter { it.type == TxnType.PAYMENT }.sumOf { it.amount }
        val weekSales = weekTxns.filter { it.type == TxnType.SALE }.sumOf { it.amount }

        val debtors = repository.allCustomers()
            .map { customer -> Triple(customer.name, repository.outstandingBalance(customer.id), customer.id) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(8)

        val inventory = repository.getInventory()
        val lowStock = inventory.filter { it.isLow }

        val debtorsLine = if (debtors.isEmpty()) {
            "Nobody currently owes money — every customer is settled up."
        } else {
            debtors.joinToString("; ") { (name, balance, _) -> "$name owes Rs.${"%.0f".format(balance)}" }
        }

        val lowStockLine = if (lowStock.isEmpty()) {
            "Nothing is below its reorder level right now."
        } else {
            lowStock.joinToString("; ") { "${it.item}: ${"%.1f".format(it.qty)} ${it.unit} left (reorder below ${"%.1f".format(it.lowWatermark)})" }
        }

        return """
            You are the voice assistant built into KhataAgent, a ledger app for $shopName, an Indian
            kirana (small neighbourhood) shop. The shopkeeper is talking to you out loud, hands-free,
            usually while serving customers, so keep every answer short, warm, and conversational -
            a sentence or two, not a report. Reply in whatever language or mix (Hindi, Hinglish,
            English, etc.) the shopkeeper speaks in. Always state rupee amounts clearly, e.g. "five
            hundred rupees". You can ONLY see the numbers listed below - never invent a customer,
            balance, or item that isn't in this data, and say so plainly if you don't have an answer.

            Today is ${humanDayFormat.format(Date(now))} ($todayKey).

            TODAY'S BOOKS: credit given Rs.${"%.0f".format(today.totalCredit)}, payments received
            Rs.${"%.0f".format(today.totalPayments)}, across ${today.txnCount} transaction(s). Net
            movement Rs.${"%.0f".format(today.totalCredit - today.totalPayments)}.

            LAST 7 DAYS: credit Rs.${"%.0f".format(weekCredit)}, payments Rs.${"%.0f".format(weekPayments)},
            cash sales Rs.${"%.0f".format(weekSales)}, across ${weekTxns.size} transaction(s).

            WHO OWES MONEY (highest first): $debtorsLine

            STOCK STATUS: $lowStockLine

            Use only these figures to answer questions like "who owes me the most", "how was
            business this week", "is anything low on stock", or "how much credit did I give today".
            For anything else (advice, unrelated topics, actions you can't verify from this data),
            say briefly that you don't have that information rather than guessing.
        """.trimIndent()
    }
}
