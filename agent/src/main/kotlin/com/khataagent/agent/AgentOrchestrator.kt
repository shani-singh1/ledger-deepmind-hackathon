package com.khataagent.agent

import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.DeferKind
import com.khataagent.core.agent.InferenceEngine
import com.khataagent.core.agent.StateBlockBuilder
import com.khataagent.core.agent.TurnState
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnType
import com.khataagent.core.tool.ToolCall
import com.khataagent.core.tool.ToolParseResult
import com.khataagent.core.validate.ValidationContext
import com.khataagent.core.validate.ValidationResult
import com.khataagent.core.validate.Validator
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The turn state machine from BUILD.md. Model is stateless; SQLite (via [LedgerRepository]) is
 * the brain - every turn rebuilds a fresh state block and prompt. The check step lives in code
 * ([Validator]), not the model. Every failure has a defined next state - this class never lets
 * an exception escape a public `submit*`/`resolveDeferral` call; everything funnels to
 * [TurnState.Errored] followed by [TurnState.Idle].
 *
 * IDLE -> INFERRING -> (parse fail -> RETRYING(1) -> INFERRING -> parse fail again -> DEFERRED)
 *                    -> (parse ok)  -> VALIDATING -> COMMITTED | DEFERRED
 * DEFERRED -> COMMITTED | REJECTED (via [resolveDeferral])
 * any turn -> ERRORED -> IDLE on timeout or unexpected exception.
 */
class AgentOrchestrator(
    private val engine: InferenceEngine,
    private val validator: Validator,
    private val repository: LedgerRepository,
    private val stateBlockBuilder: StateBlockBuilder,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val turnTimeoutMillis: Long = DEFAULT_TURN_TIMEOUT_MILLIS,
) {

    private val _state = MutableStateFlow<TurnState>(TurnState.Idle)
    val state: StateFlow<TurnState> = _state.asStateFlow()

    /** The one open confirm card awaiting a human decision, if any. Single-turn app - one at a time. */
    private var pendingDeferral: PendingDeferral? = null

    suspend fun submitText(text: String) = executeTurn(TurnInput.Text(text))

    suspend fun submitAudio(pcm: ShortArray) = executeTurn(TurnInput.Audio(pcm))

    /** Human resolves a [TurnState.Deferred] confirm card: commit the understood call, or drop it. */
    suspend fun resolveDeferral(accept: Boolean, card: ConfirmCard) {
        withContext(dispatcher) {
            try {
                val pending = pendingDeferral
                if (pending == null || pending.card.turnId != card.turnId) {
                    _state.value = TurnState.Errored("No matching pending deferral for turn ${card.turnId}")
                    _state.value = TurnState.Idle
                    return@withContext
                }
                pendingDeferral = null

                if (card.understoodAs is ToolCall.AskClarification) {
                    // Nothing to commit/reject in the ledger for a clarification card - just log
                    // the human's response and go back to idle.
                    repository.resolveDeferral(pending.deferralLogId, if (accept) "acknowledged" else "dismissed")
                    _state.value = TurnState.Idle
                    return@withContext
                }

                if (accept) {
                    val txn = executeToolCall(card.understoodAs, TxnSource.TEXT)
                    repository.resolveDeferral(pending.deferralLogId, "committed")
                    _state.value = TurnState.Committed(buildConfirmation(card.understoodAs), txn)
                } else {
                    repository.resolveDeferral(pending.deferralLogId, "rejected")
                    _state.value = TurnState.Rejected(card.humanReason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = TurnState.Errored(e.message ?: "Failed to resolve deferral")
                _state.value = TurnState.Idle
            }
        }
    }

    private sealed interface TurnInput {
        data class Text(val text: String) : TurnInput
        data class Audio(val pcm: ShortArray) : TurnInput
    }

    private fun sourceFor(input: TurnInput) = when (input) {
        is TurnInput.Text -> TxnSource.TEXT
        is TurnInput.Audio -> TxnSource.VOICE
    }

    private suspend fun executeTurn(input: TurnInput) {
        withContext(dispatcher) {
            try {
                withTimeout(turnTimeoutMillis) {
                    runTurn(input)
                }
            } catch (e: TimeoutCancellationException) {
                _state.value = TurnState.Errored("Turn timed out after ${turnTimeoutMillis}ms")
                _state.value = TurnState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = TurnState.Errored(e.message ?: "Unexpected error")
                _state.value = TurnState.Idle
            }
        }
    }

    private suspend fun runTurn(input: TurnInput) {
        val utteranceText = (input as? TurnInput.Text)?.text

        // Deterministic chit-chat guard: a typed line with no number AND no ledger keyword is a
        // greeting / question, not a khata entry. Small on-device models otherwise hallucinate a
        // fake transaction from it — short-circuit to gentle guidance instead of calling the model.
        if (utteranceText != null && !looksLikeLedgerCommand(utteranceText)) {
            _state.value = TurnState.Inferring
            deferAndLog(chitChatCard(utteranceText))
            return
        }

        _state.value = TurnState.Inferring
        var retryError: String? = null
        var raw: String
        var parsed: ToolParseResult
        var attempt = 0

        while (true) {
            val stateBlock = stateBlockBuilder.build()
            val prompt = PromptBuilder.build(stateBlock = stateBlock, utterance = utteranceText, retryError = retryError)

            raw = when (input) {
                is TurnInput.Text -> engine.generate(prompt)
                is TurnInput.Audio -> engine.generateWithAudio(prompt, input.pcm)
            }

            parsed = ToolCallParser.parse(raw)
            if (parsed is ToolParseResult.Success) break

            val failure = parsed as ToolParseResult.Failure
            attempt++
            if (attempt > MAX_RETRY_ATTEMPTS) {
                deferSchemaFailure(failure.rawOutput, failure.error)
                return
            }
            retryError = failure.error
            _state.value = TurnState.Retrying(attempt, failure.error)
            // loop: rebuild prompt (fresh state block) with the error injected, try again
        }

        val call = (parsed as ToolParseResult.Success).call
        _state.value = TurnState.Validating(call)

        val ctx = buildValidationContext()
        when (val result = validator.validate(call, ctx)) {
            is ValidationResult.Valid -> commit(result.call, sourceFor(input))
            is ValidationResult.Defer -> deferAndLog(result.card)
        }
    }

    private suspend fun commit(call: ToolCall, source: TxnSource) {
        if (call is ToolCall.AskClarification) {
            // Defensive only: the validator's MODEL_CLARIFICATION rule should always defer an
            // AskClarification before it reaches here. Never crash on it either way.
            deferAndLog(clarificationCard(call))
            return
        }
        val txn = executeToolCall(call, source)
        val confirmation = buildConfirmation(call)
        _state.value = TurnState.Committed(confirmation, txn)
    }

    private suspend fun deferSchemaFailure(rawOutput: String, error: String) {
        val card = ConfirmCard(
            turnId = UUID.randomUUID().toString(),
            understoodAs = ToolCall.AskClarification(
                "I couldn't understand that - could you rephrase, or type the customer and amount?",
            ),
            humanReason = "The assistant's reply didn't match any known command (parse error: $error).",
            rawModelOutput = rawOutput,
            kind = DeferKind.SCHEMA,
        )
        deferAndLog(card)
    }

    /** A ledger command almost always has a number (amount/qty) or a known ledger keyword. */
    private fun looksLikeLedgerCommand(text: String): Boolean {
        val t = text.lowercase()
        if (t.any { it.isDigit() }) return true
        return LEDGER_KEYWORDS.any { t.contains(it) }
    }

    private fun chitChatCard(text: String): ConfirmCard = ConfirmCard(
        turnId = UUID.randomUUID().toString(),
        understoodAs = ToolCall.AskClarification(
            "I only keep your shop's khata. Please say an entry — like \"Ramesh ko 250 udhaar\" or \"Sita ne 500 diya\".",
        ),
        humanReason = "That doesn't look like a khata entry.",
        rawModelOutput = text,
        kind = DeferKind.CLARIFICATION,
    )

    private fun clarificationCard(call: ToolCall.AskClarification): ConfirmCard = ConfirmCard(
        turnId = UUID.randomUUID().toString(),
        understoodAs = call,
        humanReason = call.question,
        rawModelOutput = call.question,
        kind = DeferKind.CLARIFICATION,
    )

    private suspend fun deferAndLog(card: ConfirmCard) {
        val logId = repository.logDeferral(
            DeferralEntry(
                turnId = card.turnId,
                rawModelOutput = card.rawModelOutput,
                reason = card.kind.name,
                resolution = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        pendingDeferral = PendingDeferral(card, logId)
        _state.value = TurnState.Deferred(card)
    }

    private suspend fun buildValidationContext(): ValidationContext {
        val customers = repository.allCustomers()
        val balances = customers.associate { it.id to repository.outstandingBalance(it.id) }
        // Matches ValidationContext's own default duplicateWindowMillis (see :core Validation.kt);
        // kept in sync manually since :agent has no dependency on :validate's implementation.
        val recent = repository.transactionsSince(System.currentTimeMillis() - DUPLICATE_WINDOW_MILLIS)
        return ValidationContext(
            knownCustomers = customers,
            balancesByCustomerId = balances,
            recentTransactions = recent,
        )
    }

    private suspend fun executeToolCall(call: ToolCall, source: TxnSource): Transaction? = when (call) {
        is ToolCall.AddCredit -> {
            val customer = findOrCreateCustomer(call.customer)
            val txn = Transaction(
                customerId = customer.id,
                customerName = customer.name,
                type = TxnType.CREDIT,
                amount = call.amount,
                item = call.item,
                note = call.note,
                source = source,
                createdAt = System.currentTimeMillis(),
            )
            txn.copy(id = repository.insertTransaction(txn))
        }

        is ToolCall.RecordPayment -> {
            val customer = repository.findCustomerByName(call.customer) ?: findOrCreateCustomer(call.customer)
            val txn = Transaction(
                customerId = customer.id,
                customerName = customer.name,
                type = TxnType.PAYMENT,
                amount = call.amount,
                source = source,
                createdAt = System.currentTimeMillis(),
            )
            txn.copy(id = repository.insertTransaction(txn))
        }

        is ToolCall.RecordSale -> {
            val txn = Transaction(
                customerId = null,
                customerName = null,
                type = TxnType.SALE,
                amount = call.amount,
                item = call.item,
                source = source,
                createdAt = System.currentTimeMillis(),
            )
            val saved = txn.copy(id = repository.insertTransaction(txn))
            repository.adjustStock(call.item, -call.qty)
            saved
        }

        is ToolCall.UpdateStock -> {
            repository.adjustStock(call.item, call.qtyDelta)
            null
        }

        is ToolCall.QueryBalance -> null
        ToolCall.QueryToday -> null
        ToolCall.CloseDay -> null
        is ToolCall.AskClarification -> null
    }

    private suspend fun findOrCreateCustomer(name: String): Customer =
        repository.findCustomerByName(name) ?: run {
            val customer = Customer(name = name, createdAt = System.currentTimeMillis())
            customer.copy(id = repository.upsertCustomer(customer))
        }

    private suspend fun buildConfirmation(call: ToolCall): String = when (call) {
        is ToolCall.AddCredit -> "Added ₹${fmt(call.amount)} credit for ${call.customer}."
        is ToolCall.RecordPayment -> "Recorded ₹${fmt(call.amount)} payment from ${call.customer}."
        is ToolCall.RecordSale -> "Sold ${fmt(call.qty)} ${call.item} for ₹${fmt(call.amount)}."
        is ToolCall.UpdateStock -> {
            val sign = if (call.qtyDelta >= 0) "+" else ""
            "Updated ${call.item} stock by $sign${fmt(call.qtyDelta)}."
        }
        is ToolCall.QueryBalance -> {
            val customer = repository.findCustomerByName(call.customer)
            val balance = customer?.let { repository.outstandingBalance(it.id) } ?: 0.0
            "${call.customer} owes ₹${fmt(balance)}."
        }
        ToolCall.QueryToday -> {
            val ds = repository.computeDailyState(today())
            "Today: ${ds.txnCount} transactions, ₹${fmt(ds.totalCredit)} credit, ₹${fmt(ds.totalPayments)} payments."
        }
        ToolCall.CloseDay -> {
            val ds = repository.computeDailyState(today())
            "Day closed. Credit ₹${fmt(ds.totalCredit)}, payments ₹${fmt(ds.totalPayments)}, ${ds.txnCount} txns."
        }
        is ToolCall.AskClarification -> call.question
    }

    private fun today(): String = LocalDate.now().toString()

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private data class PendingDeferral(val card: ConfirmCard, val deferralLogId: Long)

    companion object {
        const val MAX_RETRY_ATTEMPTS = 1
        const val DEFAULT_TURN_TIMEOUT_MILLIS = 20_000L

        /** Words that signal a real ledger command (used by the chit-chat guard). */
        private val LEDGER_KEYWORDS = listOf(
            "udhaar", "udhar", "credit", "likho", "likh", "diya", "diye", "payment", "jama", "paid",
            "chuka", "becha", "bech", "sale", "sold", "bik", "stock", "restock", "mangwaya",
            "balance", "baaki", "dena", "lena", "kitna", "total", "hisaab", "aaj ka", "today",
            "close", "band karo", "khata", "rupee", "rupaye", "paisa",
        )

        /** Mirrors ValidationContext's default duplicateWindowMillis in :core (120s). */
        private const val DUPLICATE_WINDOW_MILLIS = 120_000L
    }
}
