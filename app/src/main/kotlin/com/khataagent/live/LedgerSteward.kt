package com.khataagent.live

import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnType
import org.json.JSONObject
import java.time.LocalDate

/**
 * AGENT B — the on-device **Ledger Steward** (Problem Statement 2: autonomous orchestration).
 *
 * The cloud **Conversation Planner** (Gemini Live, Agent A) talks to the shopkeeper and proposes
 * ledger actions as tool calls. This agent is the **guardian of the shop's data truth**: it never
 * writes blindly. It applies the same business rules as the offline loop and either COMMITS to
 * SQLite or returns a structured **conflict** — over the daily limit, overpayment, unknown customer
 * — that the Planner must resolve with the shopkeeper (then re-issue the call with `confirmed:true`).
 *
 * So the two agents genuinely split labour: A plans & converses, B enforces & executes; they
 * exchange tool-call/tool-response messages and resolve conflicts without human hand-holding for
 * the safe majority of actions, while keeping a clear boundary on the risky ones.
 */
class LedgerSteward(
    private val repository: LedgerRepository,
    private val dailyMax: Double = 5_000.0,
) {
    /** The tool declarations advertised to the Planner (Gemini Live). */
    companion object {
        const val DAILY_MAX_DEFAULT = 5_000.0
    }

    suspend fun execute(name: String, args: JSONObject): JSONObject = runCatching {
        when (name) {
            "add_credit" -> addCredit(args)
            "record_payment" -> recordPayment(args)
            "record_sale" -> recordSale(args)
            "delete_last_transaction" -> deleteLast(args)
            "query_balance" -> queryBalance(args)
            "query_today" -> queryToday()
            else -> resp("error", "Unknown action '$name'.")
        }
    }.getOrElse { resp("error", it.message ?: "That action failed.") }

    private suspend fun addCredit(args: JSONObject): JSONObject {
        val name = args.getString("customer")
        val amount = args.getDouble("amount")
        val item = args.optString("item").ifBlank { null }
        val confirmed = args.optBoolean("confirmed", false)
        if (amount > dailyMax && !confirmed) {
            return conflict(
                "over_limit",
                "₹${fmt(amount)} is above the usual ₹${fmt(dailyMax)} single-entry limit. Ask the shopkeeper " +
                    "to confirm, then call add_credit again with confirmed=true.",
            )
        }
        val customer = findOrCreate(name)
        insert(customer, TxnType.CREDIT, amount, item)
        return resp(
            "done",
            "Added ₹${fmt(amount)} credit for ${customer.name}. They now owe ₹${fmt(repository.outstandingBalance(customer.id))}.",
        )
    }

    private suspend fun recordPayment(args: JSONObject): JSONObject {
        val name = args.getString("customer")
        val amount = args.getDouble("amount")
        val confirmed = args.optBoolean("confirmed", false)
        val customer = repository.findCustomerByName(name)
            ?: return conflict("unknown_customer", "No customer named '$name' exists. Confirm the name or add them first.")
        val balance = repository.outstandingBalance(customer.id)
        if (amount > balance && !confirmed) {
            return conflict(
                "overpayment",
                "₹${fmt(amount)} is more than ${customer.name}'s outstanding ₹${fmt(balance)}. Confirm with the " +
                    "shopkeeper, then call record_payment again with confirmed=true.",
            )
        }
        insert(customer, TxnType.PAYMENT, amount, null)
        return resp("done", "Recorded ₹${fmt(amount)} payment from ${customer.name}. Balance now ₹${fmt(repository.outstandingBalance(customer.id))}.")
    }

    private suspend fun recordSale(args: JSONObject): JSONObject {
        val item = args.getString("item")
        val qty = args.optDouble("qty", 1.0)
        val amount = args.getDouble("amount")
        val txn = Transaction(
            customerId = null, customerName = null, type = TxnType.SALE, amount = amount, item = item,
            source = TxnSource.VOICE, createdAt = System.currentTimeMillis(),
        )
        repository.insertTransaction(txn)
        runCatching { repository.adjustStock(item, -qty) }
        return resp("done", "Recorded a ₹${fmt(amount)} sale of ${fmt(qty)} $item.")
    }

    private suspend fun deleteLast(args: JSONObject): JSONObject {
        val name = args.optString("customer").ifBlank { null }
        val recent = repository.recentTransactions(30)
        val target = if (name != null) {
            recent.firstOrNull { it.customerName?.equals(name, ignoreCase = true) == true }
        } else {
            recent.firstOrNull()
        } ?: return conflict("not_found", "Couldn't find a recent transaction${name?.let { " for $it" } ?: ""} to delete.")
        repository.deleteTransaction(target.id)
        val who = target.customerName ?: "walk-in"
        return resp("done", "Deleted the last entry: ${target.type.name.lowercase()} of ₹${fmt(target.amount)} ($who).")
    }

    private suspend fun queryBalance(args: JSONObject): JSONObject {
        val name = args.getString("customer")
        val customer = repository.findCustomerByName(name)
            ?: return resp("done", "No customer named '$name' in the khata.")
        return resp("done", "${customer.name} owes ₹${fmt(repository.outstandingBalance(customer.id))}.")
    }

    private suspend fun queryToday(): JSONObject {
        val ds = repository.computeDailyState(LocalDate.now().toString())
        return resp("done", "Today: ₹${fmt(ds.totalCredit)} credit given, ₹${fmt(ds.totalPayments)} payments received, ${ds.txnCount} entries.")
    }

    // ---- helpers ----
    private suspend fun findOrCreate(name: String): Customer =
        repository.findCustomerByName(name) ?: Customer(id = repository.addCustomer(name, null), name = name)

    private suspend fun insert(customer: Customer, type: TxnType, amount: Double, item: String?) {
        repository.insertTransaction(
            Transaction(
                customerId = customer.id, customerName = customer.name, type = type, amount = amount,
                item = item, source = TxnSource.VOICE, createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun resp(status: String, message: String) = JSONObject().put("status", status).put("message", message)
    private fun conflict(reason: String, message: String) =
        JSONObject().put("status", "needs_confirmation").put("conflict", reason).put("message", message)

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
}
