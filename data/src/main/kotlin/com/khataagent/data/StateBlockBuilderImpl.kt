package com.khataagent.data

import com.khataagent.core.agent.StateBlockBuilder
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnType
import java.time.LocalDate
import java.util.Locale

/**
 * Builds the compact, stateless-model-facing state block. Hard-capped at [MAX_CHARS] characters
 * (~300 tokens per build.md's prefill budget) so it never blows the prompt budget even with a
 * large customer roster.
 *
 * Format:
 * ```
 * TODAY: credit ₹X, payments ₹Y, N txns
 * OPEN CREDITS: Name ₹bal, Name ₹bal, ...
 * LAST 3: credit Name ₹amt (item) | payment Name ₹amt | sale walk-in ₹amt (item)
 * KNOWN CUSTOMERS: Name, Name, ...
 * ```
 */
class StateBlockBuilderImpl(
    private val repository: LedgerRepository,
) : StateBlockBuilder {

    override suspend fun build(): String {
        val today = LocalDate.now().toString()
        val daily = repository.computeDailyState(today)
        val recent = repository.recentTransactions(RECENT_COUNT)
        val customers = repository.allCustomers()

        val openCredits = customers
            .mapNotNull { c ->
                val balance = repository.outstandingBalance(c.id)
                if (balance > 0.0) c.name to balance else null
            }
            .sortedByDescending { it.second }
            .take(TOP_OPEN_CREDITS)

        val sb = StringBuilder()

        sb.append("TODAY: credit ").append(rupee(daily.totalCredit))
            .append(", payments ").append(rupee(daily.totalPayments))
            .append(", ").append(daily.txnCount).append(" txns\n")

        sb.append("OPEN CREDITS: ")
        sb.append(
            if (openCredits.isEmpty()) "none"
            else openCredits.joinToString(", ") { (name, bal) -> "$name ${rupee(bal)}" }
        )
        sb.append("\n")

        sb.append("LAST 3: ")
        sb.append(
            if (recent.isEmpty()) "none"
            else recent.joinToString(" | ") { txnSummary(it) }
        )
        sb.append("\n")

        sb.append("KNOWN CUSTOMERS: ")
        sb.append(customers.joinToString(", ") { it.name })

        val text = sb.toString()
        return if (text.length > MAX_CHARS) text.substring(0, MAX_CHARS - 1) + "…" else text
    }

    private fun txnSummary(t: Transaction): String {
        val who = t.customerName ?: "walk-in"
        val label = when (t.type) {
            TxnType.CREDIT -> "credit"
            TxnType.PAYMENT -> "payment"
            TxnType.SALE -> "sale"
        }
        val itemPart = t.item?.let { " ($it)" } ?: ""
        return "$label $who ${rupee(t.amount)}$itemPart"
    }

    private fun rupee(amount: Double): String = "₹" + String.format(Locale.US, "%.0f", amount)

    companion object {
        /** Hard cap per build.md's prefill budget (state block capped, keeps prompt small). */
        const val MAX_CHARS = 1200
        const val RECENT_COUNT = 3
        const val TOP_OPEN_CREDITS = 5
    }
}
