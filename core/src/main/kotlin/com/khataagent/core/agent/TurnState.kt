package com.khataagent.core.agent

import com.khataagent.core.model.Transaction
import com.khataagent.core.tool.ToolCall

/**
 * The turn state machine from BUILD.md. Emitted by the orchestrator as a StateFlow so the
 * UI can animate every transition. Every failure has a defined next state — no dead ends.
 *
 * IDLE → LISTENING → INFERRING → VALIDATING
 *   VALIDATING → COMMITTED | RETRYING(→VALIDATING) | DEFERRED
 *   DEFERRED   → COMMITTED | REJECTED
 *   any        → ERRORED → IDLE
 */
sealed interface TurnState {
    data object Idle : TurnState
    data object Listening : TurnState
    data object Inferring : TurnState
    data class Validating(val call: ToolCall) : TurnState
    data class Committed(val spokenConfirmation: String, val transaction: Transaction?) : TurnState
    data class Retrying(val attempt: Int, val error: String) : TurnState
    data class Deferred(val card: ConfirmCard) : TurnState
    data class Rejected(val reason: String) : TurnState
    data class Errored(val message: String) : TurnState
}

/** Reason bucket that drives both the confirm-card copy and the deferral-log tag. */
enum class DeferKind {
    NEW_CUSTOMER,
    AMBIGUOUS_CUSTOMER,
    OVER_LIMIT,
    OVERPAYMENT,
    DUPLICATE,
    SCHEMA,
    CLARIFICATION,
}

/**
 * What the Confirm-card modal renders. This screen IS the thesis (see BUILD.md Frontend).
 * [humanReason] must be plain shopkeeper language, e.g. "₹12,000 is above your usual — confirm?"
 */
data class ConfirmCard(
    val turnId: String,
    val understoodAs: ToolCall,
    val humanReason: String,
    val rawModelOutput: String,
    val kind: DeferKind,
)
