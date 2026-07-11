package com.khataagent.turn

import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.DeferKind
import com.khataagent.core.agent.TurnState
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import com.khataagent.core.tool.ToolCall
import com.khataagent.ui.components.capitalizeWords
import com.khataagent.ui.confirm.toWireJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Stand-in for the real :agent orchestrator. Emits [TurnState] over a [StateFlow] exactly like
 * the real LiteRT-LM-backed one will, so the UI (mic button, confirm sheet, ledger feed) never
 * has to change when the integrator swaps this for the genuine inference pipeline in Phase 2.
 *
 * Simulates IDLE → LISTENING → INFERRING → VALIDATING → {COMMITTED | DEFERRED}, with DEFERRED
 * resolving to COMMITTED or REJECTED on the shopkeeper's decision in the confirm sheet.
 */
class TurnController(
    private val scope: CoroutineScope,
    private val repository: LedgerRepository,
    private val customerNames: List<String>,
    private val itemNames: List<String>,
) {
    private val _state = MutableStateFlow<TurnState>(TurnState.Idle)
    val state: StateFlow<TurnState> = _state.asStateFlow()

    private var turnJob: Job? = null
    private var turnCounter = 0
    private var lastDeferralId: Long? = null

    /** Mic pressed down — freeze-frame the waveform, no work yet (perceived-latency trick). */
    fun beginListening() {
        turnJob?.cancel()
        _state.value = TurnState.Listening
    }

    fun cancelListening() {
        turnJob?.cancel()
        _state.value = TurnState.Idle
    }

    /** Mic released — run the simulated inference → validate pipeline. */
    fun submitTurn() {
        turnJob = scope.launch {
            _state.value = TurnState.Inferring
            delay(750)
            turnCounter += 1
            val turnId = "turn-$turnCounter"
            val scenario = pickScenario()
            _state.value = TurnState.Validating(scenario.call)
            delay(500)

            val deferKind = scenario.deferKind
            if (deferKind == null) {
                val txn = toTransaction(scenario.call)
                if (txn != null) repository.insertTransaction(txn)
                _state.value = TurnState.Committed(spokenConfirmation(scenario.call), txn)
            } else {
                val card = ConfirmCard(
                    turnId = turnId,
                    understoodAs = scenario.call,
                    humanReason = scenario.humanReason ?: "Not sure about this one — please confirm.",
                    rawModelOutput = scenario.call.toWireJson(),
                    kind = deferKind,
                )
                lastDeferralId = repository.logDeferral(
                    DeferralEntry(
                        turnId = turnId,
                        rawModelOutput = card.rawModelOutput,
                        reason = "${deferKind.name} — ${card.humanReason}",
                    ),
                )
                _state.value = TurnState.Deferred(card)
            }
        }
    }

    fun acceptDeferred(card: ConfirmCard) {
        scope.launch {
            val txn = toTransaction(card.understoodAs)
            if (txn != null) repository.insertTransaction(txn)
            lastDeferralId?.let { repository.resolveDeferral(it, "committed") }
            _state.value = TurnState.Committed(spokenConfirmation(card.understoodAs), txn)
        }
    }

    fun rejectDeferred(card: ConfirmCard) {
        scope.launch {
            lastDeferralId?.let { repository.resolveDeferral(it, "rejected") }
            _state.value = TurnState.Rejected("Rejected by shopkeeper")
        }
    }

    /** Return to Idle after the ledger/UI has finished animating the committed/rejected state. */
    fun acknowledgeAndReset() {
        turnJob?.cancel()
        _state.value = TurnState.Idle
    }

    private fun toTransaction(call: ToolCall): Transaction? = when (call) {
        is ToolCall.AddCredit -> Transaction(
            customerName = call.customer.capitalizeWords(),
            type = TxnType.CREDIT,
            amount = call.amount,
            item = call.item,
            note = call.note,
            status = TxnStatus.CONFIRMED,
            source = TxnSource.VOICE,
            createdAt = System.currentTimeMillis(),
        )
        is ToolCall.RecordPayment -> Transaction(
            customerName = call.customer.capitalizeWords(),
            type = TxnType.PAYMENT,
            amount = call.amount,
            status = TxnStatus.CONFIRMED,
            source = TxnSource.VOICE,
            createdAt = System.currentTimeMillis(),
        )
        is ToolCall.RecordSale -> Transaction(
            customerName = null,
            type = TxnType.SALE,
            amount = call.amount,
            item = call.item,
            status = TxnStatus.CONFIRMED,
            source = TxnSource.VOICE,
            createdAt = System.currentTimeMillis(),
        )
        else -> null
    }

    private fun spokenConfirmation(call: ToolCall): String = when (call) {
        is ToolCall.AddCredit -> "Added ₹${call.amount.toInt()} credit for ${call.customer.capitalizeWords()}"
        is ToolCall.RecordPayment -> "Recorded ₹${call.amount.toInt()} payment from ${call.customer.capitalizeWords()}"
        is ToolCall.RecordSale -> "Recorded sale of ${call.qty.toInt()} ${call.item}"
        else -> "Done"
    }

    private data class Scenario(val call: ToolCall, val deferKind: DeferKind?, val humanReason: String?)

    private fun pickScenario(): Scenario {
        val name = customerNames.random()
        val item = itemNames.random()
        val roll = Random.nextInt(0, 100)
        return when {
            roll < 40 -> Scenario(
                ToolCall.AddCredit(customer = name.lowercase(), amount = roundish(50.0, 1500.0), item = item),
                null, null,
            )
            roll < 65 -> Scenario(
                ToolCall.RecordPayment(customer = name.lowercase(), amount = roundish(100.0, 2000.0)),
                null, null,
            )
            roll < 80 -> Scenario(
                ToolCall.RecordSale(item = item, qty = Random.nextInt(1, 5).toDouble(), amount = roundish(30.0, 600.0)),
                null, null,
            )
            roll < 90 -> {
                val amount = roundish(5200.0, 14000.0)
                Scenario(
                    ToolCall.AddCredit(customer = name.lowercase(), amount = amount, item = item),
                    DeferKind.OVER_LIMIT,
                    "₹${amount.toInt()} is above your usual ₹5,000 limit — confirm?",
                )
            }
            roll < 96 -> Scenario(
                ToolCall.AskClarification(question = "Which $name did you mean — there are two who sound alike."),
                DeferKind.AMBIGUOUS_CUSTOMER,
                "Two customers sound like \"$name\" — which one did you mean?",
            )
            else -> Scenario(
                ToolCall.AddCredit(customer = "Naya Grahak", amount = roundish(80.0, 400.0), item = item),
                DeferKind.NEW_CUSTOMER,
                "\"Naya Grahak\" isn't in your khata yet — add as a new customer?",
            )
        }
    }

    private fun roundish(min: Double, max: Double): Double =
        ((Random.nextDouble(min, max) / 10).toLong() * 10).toDouble()
}
