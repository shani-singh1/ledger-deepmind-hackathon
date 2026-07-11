package com.khataagent.validate

import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.DeferKind
import com.khataagent.core.model.Customer
import com.khataagent.core.tool.ToolCall
import com.khataagent.core.util.Phonetic
import com.khataagent.core.validate.DeferReason
import com.khataagent.core.validate.ValidationContext
import com.khataagent.core.validate.ValidationResult
import com.khataagent.core.validate.Validator
import java.util.Locale
import java.util.UUID

/**
 * The "check step in code, not the model." Pure Kotlin, deterministic, zero Android deps.
 *
 * Rule precedence (first match wins — a call only ever defers for one reason):
 *  1. [ToolCall.AskClarification] passthrough           -> MODEL_CLARIFICATION
 *  2. amount > ctx.dailyMaxSingleTxn                      -> OVER_DAILY_MAX
 *  3. customer name resolution (not found / ambiguous)    -> CUSTOMER_NOT_FOUND / CUSTOMER_AMBIGUOUS
 *  4. payment > outstanding balance                       -> OVERPAYMENT
 *  5. duplicate suspect (same customer+amount, in window) -> DUPLICATE_SUSPECT
 *  else -> Valid
 */
class KhataValidator(
    /** Phonetic similarity threshold above which a known customer counts as a match. */
    private val phoneticThreshold: Double = 0.8,
) : Validator {

    override fun validate(call: ToolCall, ctx: ValidationContext): ValidationResult {
        // 1. The model's sanctioned "I'm not sure" escape hatch always defers, unconditionally.
        if (call is ToolCall.AskClarification) {
            return defer(
                call = call,
                reason = DeferReason.MODEL_CLARIFICATION,
                kind = DeferKind.CLARIFICATION,
                humanMessage = call.question,
            )
        }

        // 2. Daily max single-transaction ceiling.
        amountOf(call)?.let { amount ->
            if (amount > ctx.dailyMaxSingleTxn) {
                return defer(
                    call = call,
                    reason = DeferReason.OVER_DAILY_MAX,
                    kind = DeferKind.OVER_LIMIT,
                    humanMessage = "₹${formatAmount(amount)} is above your usual " +
                        "₹${formatAmount(ctx.dailyMaxSingleTxn)} limit — confirm?",
                )
            }
        }

        // 3. Customer name resolution (only for calls that carry a customer name).
        var resolvedCustomer: Customer? = null
        customerNameOf(call)?.let { name ->
            when (val resolution = resolveCustomer(name, ctx.knownCustomers)) {
                is CustomerResolution.NotFound -> return defer(
                    call = call,
                    reason = DeferReason.CUSTOMER_NOT_FOUND,
                    kind = DeferKind.NEW_CUSTOMER,
                    humanMessage = "No customer named '$name' yet — add as new?",
                )

                is CustomerResolution.Ambiguous -> return defer(
                    call = call,
                    reason = DeferReason.CUSTOMER_AMBIGUOUS,
                    kind = DeferKind.AMBIGUOUS_CUSTOMER,
                    humanMessage = "Did you mean " +
                        resolution.matches.joinToString(" or ") { it.name } + "?",
                )

                is CustomerResolution.Found -> resolvedCustomer = resolution.customer
            }
        }

        // 4. Overpayment: payment strictly greater than the resolved customer's balance.
        if (call is ToolCall.RecordPayment) {
            val customerId = resolvedCustomer?.id
            val balance = customerId?.let { ctx.balancesByCustomerId[it] } ?: 0.0
            if (call.amount > balance) {
                return defer(
                    call = call,
                    reason = DeferReason.OVERPAYMENT,
                    kind = DeferKind.OVERPAYMENT,
                    humanMessage = "₹${formatAmount(call.amount)} payment is more than " +
                        "${call.customer}'s outstanding balance of ₹${formatAmount(balance)} — confirm?",
                )
            }
        }

        // 5. Duplicate suspect: same customer + same amount within the duplicate window.
        val duplicateAmount = amountOf(call)
        val customerIdForDup = resolvedCustomer?.id
        if (duplicateAmount != null && customerIdForDup != null) {
            val txnType = txnTypeOf(call)
            val isDuplicate = ctx.recentTransactions.any { txn ->
                txn.customerId == customerIdForDup &&
                    txn.amount == duplicateAmount &&
                    (txnType == null || txn.type == txnType) &&
                    (ctx.nowMillis - txn.createdAt) >= 0 &&
                    (ctx.nowMillis - txn.createdAt) < ctx.duplicateWindowMillis
            }
            if (isDuplicate) {
                val customerLabel = resolvedCustomer?.name ?: customerNameOf(call) ?: "this customer"
                return defer(
                    call = call,
                    reason = DeferReason.DUPLICATE_SUSPECT,
                    kind = DeferKind.DUPLICATE,
                    humanMessage = "Another ₹${formatAmount(duplicateAmount)} entry for $customerLabel " +
                        "was just recorded — confirm this isn't a duplicate?",
                )
            }
        }

        return ValidationResult.Valid(call)
    }

    // ---- helpers -----------------------------------------------------------------------

    private fun defer(
        call: ToolCall,
        reason: DeferReason,
        kind: DeferKind,
        humanMessage: String,
    ): ValidationResult.Defer {
        val card = ConfirmCard(
            turnId = UUID.randomUUID().toString(),
            understoodAs = call,
            humanReason = humanMessage,
            rawModelOutput = call.toString(),
            kind = kind,
        )
        return ValidationResult.Defer(reason = reason, humanMessage = humanMessage, card = card)
    }

    private fun amountOf(call: ToolCall): Double? = when (call) {
        is ToolCall.AddCredit -> call.amount
        is ToolCall.RecordPayment -> call.amount
        is ToolCall.RecordSale -> call.amount
        else -> null
    }

    private fun customerNameOf(call: ToolCall): String? = when (call) {
        is ToolCall.AddCredit -> call.customer
        is ToolCall.RecordPayment -> call.customer
        is ToolCall.QueryBalance -> call.customer
        else -> null
    }

    private fun txnTypeOf(call: ToolCall): com.khataagent.core.model.TxnType? = when (call) {
        is ToolCall.AddCredit -> com.khataagent.core.model.TxnType.CREDIT
        is ToolCall.RecordPayment -> com.khataagent.core.model.TxnType.PAYMENT
        else -> null
    }

    private fun resolveCustomer(name: String, known: List<Customer>): CustomerResolution {
        // Exact (case-insensitive) name match wins outright — no fuzziness needed.
        known.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let {
            return CustomerResolution.Found(it)
        }
        val matches = known.filter { Phonetic.similarity(it.name, name) >= phoneticThreshold }
        return when {
            matches.isEmpty() -> CustomerResolution.NotFound
            matches.size == 1 -> CustomerResolution.Found(matches[0])
            else -> CustomerResolution.Ambiguous(matches)
        }
    }

    private fun formatAmount(amount: Double): String {
        val rounded = Math.round(amount * 100) / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
    }

    private sealed interface CustomerResolution {
        data class Found(val customer: Customer) : CustomerResolution
        data object NotFound : CustomerResolution
        data class Ambiguous(val matches: List<Customer>) : CustomerResolution
    }
}
