package com.khataagent.core.validate

import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.model.Customer
import com.khataagent.core.model.Transaction
import com.khataagent.core.tool.ToolCall

/**
 * The "check step in code, not the model." Pure Kotlin, deterministic, unit-testable.
 * Implementation lives in :validate (zero Android deps).
 */
interface Validator {
    fun validate(call: ToolCall, ctx: ValidationContext): ValidationResult
}

/** Everything the validator needs, snapshotted by the orchestrator before each check. */
data class ValidationContext(
    val knownCustomers: List<Customer>,
    /** customerId -> current outstanding credit balance. */
    val balancesByCustomerId: Map<Long, Double>,
    /** Recent txns (last few minutes) for duplicate-suspect detection. */
    val recentTransactions: List<Transaction>,
    val dailyMaxSingleTxn: Double = 5_000.0,
    val duplicateWindowMillis: Long = 120_000L,
    val nowMillis: Long = System.currentTimeMillis(),
)

sealed interface ValidationResult {
    /** Passed every rule — safe to commit. */
    data class Valid(val call: ToolCall) : ValidationResult

    /** A rule fired — do NOT execute; raise a confirm card and log it. */
    data class Defer(
        val reason: DeferReason,
        val humanMessage: String,
        val card: ConfirmCard,
    ) : ValidationResult
}

enum class DeferReason {
    OVER_DAILY_MAX,
    CUSTOMER_NOT_FOUND,
    CUSTOMER_AMBIGUOUS,
    OVERPAYMENT,
    DUPLICATE_SUSPECT,
    SCHEMA_VIOLATION,
    MODEL_CLARIFICATION,
}
