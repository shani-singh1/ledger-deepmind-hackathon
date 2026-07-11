package com.khataagent.agent

import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.DeferKind
import com.khataagent.core.agent.InferenceBackend
import com.khataagent.core.agent.InferenceEngine
import com.khataagent.core.agent.StateBlockBuilder
import com.khataagent.core.agent.TurnState
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.Customer
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.InventoryItem
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.tool.ToolCall
import com.khataagent.core.validate.DeferReason
import com.khataagent.core.validate.ValidationContext
import com.khataagent.core.validate.ValidationResult
import com.khataagent.core.validate.Validator
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {

    // ---- fakes ----

    private class FakeInferenceEngine(private val responses: MutableList<String>) : InferenceEngine {
        override val backend: InferenceBackend = InferenceBackend.STUB
        var callCount = 0
            private set

        override suspend fun warmUp() = Unit

        override suspend fun generate(prompt: String, maxTokens: Int): String {
            // A real inference call always genuinely suspends. A suspension point here (rather
            // than a plain synchronous call) gives the test's StateFlow collector a chance to
            // process each intermediate TurnState instead of racing a fully synchronous call
            // chain (StateFlow is conflated - a collector that's never scheduled between writes
            // can legitimately miss them). Runs under UnconfinedTestDispatcher's virtual time, so
            // this costs no real wall-clock time in the test.
            delay(1)
            callCount++
            check(responses.isNotEmpty()) { "FakeInferenceEngine ran out of scripted responses" }
            return responses.removeAt(0)
        }

        override suspend fun generateWithAudio(
            prompt: String,
            audioPcm: ShortArray,
            sampleRate: Int,
            maxTokens: Int,
        ): String = generate(prompt, maxTokens)

        override fun close() = Unit
    }

    private class FakeValidator(
        private val handler: (ToolCall, ValidationContext) -> ValidationResult = { call, _ -> ValidationResult.Valid(call) },
    ) : Validator {
        val seenCalls = mutableListOf<ToolCall>()
        override fun validate(call: ToolCall, ctx: ValidationContext): ValidationResult {
            seenCalls.add(call)
            return handler(call, ctx)
        }
    }

    private class FakeStateBlockBuilder : StateBlockBuilder {
        var buildCount = 0
            private set

        override suspend fun build(): String {
            delay(1) // see comment on FakeInferenceEngine.generate()
            buildCount++
            return "STATE-$buildCount"
        }
    }

    private class FakeLedgerRepository : LedgerRepository {
        val customers = mutableListOf<Customer>()
        val transactions = mutableListOf<Transaction>()
        val inventory = mutableListOf<InventoryItem>()
        val deferrals = mutableListOf<DeferralEntry>()
        private var nextCustomerId = 1L
        private var nextTxnId = 1L
        private var nextDeferralId = 1L

        override suspend fun findCustomerByName(name: String): Customer? =
            customers.firstOrNull { it.name.equals(name, ignoreCase = true) }

        override suspend fun searchByPhonetic(phoneticKey: String): List<Customer> = emptyList()

        override suspend fun allCustomers(): List<Customer> {
            delay(1) // see comment on FakeInferenceEngine.generate()
            return customers.toList()
        }

        override suspend fun upsertCustomer(customer: Customer): Long {
            val id = if (customer.id != 0L) customer.id else nextCustomerId++
            customers.removeAll { it.id == id }
            customers.add(customer.copy(id = id))
            return id
        }

        override suspend fun insertTransaction(txn: Transaction): Long {
            delay(1) // see comment on FakeInferenceEngine.generate()
            val id = nextTxnId++
            transactions.add(txn.copy(id = id))
            return id
        }

        override suspend fun updateTransactionStatus(id: Long, status: TxnStatus) {
            val idx = transactions.indexOfFirst { it.id == id }
            if (idx >= 0) transactions[idx] = transactions[idx].copy(status = status)
        }

        override suspend fun recentTransactions(limit: Int): List<Transaction> = transactions.takeLast(limit)

        override suspend fun transactionsSince(sinceMillis: Long): List<Transaction> =
            transactions.filter { it.createdAt >= sinceMillis }

        override suspend fun outstandingBalance(customerId: Long): Double = 0.0

        override fun observeTodayTransactions(): Flow<List<Transaction>> = MutableStateFlow(transactions.toList())

        override suspend fun getInventory(): List<InventoryItem> = inventory.toList()

        override suspend fun adjustStock(item: String, qtyDelta: Double) {
            val idx = inventory.indexOfFirst { it.item == item }
            if (idx >= 0) {
                inventory[idx] = inventory[idx].copy(qty = inventory[idx].qty + qtyDelta)
            } else {
                inventory.add(InventoryItem(id = inventory.size + 1L, item = item, qty = qtyDelta))
            }
        }

        override fun observeInventory(): Flow<List<InventoryItem>> = MutableStateFlow(inventory.toList())

        override suspend fun logDeferral(entry: DeferralEntry): Long {
            delay(1) // see comment on FakeInferenceEngine.generate()
            val id = nextDeferralId++
            deferrals.add(entry.copy(id = id))
            return id
        }

        override suspend fun resolveDeferral(id: Long, resolution: String) {
            val idx = deferrals.indexOfFirst { it.id == id }
            if (idx >= 0) deferrals[idx] = deferrals[idx].copy(resolution = resolution)
        }

        override fun observeDeferrals(): Flow<List<DeferralEntry>> = MutableStateFlow(deferrals.toList())

        override suspend fun computeDailyState(date: String): DailyState =
            DailyState(date = date, txnCount = transactions.size)

        override fun observeDailyState(date: String): Flow<DailyState> = MutableStateFlow(DailyState(date = date))
    }

    /**
     * Collects every [TurnState] emitted during a test. Launched UNDISPATCHED on the same
     * [UnconfinedTestDispatcher] used by the orchestrator and by [runWithCollector], on
     * [TestScope.backgroundScope] (auto-cancelled when the test ends) - this is the standard
     * kotlinx-coroutines-test pattern for deterministically observing every StateFlow emission
     * from a coroutine that runs entirely on virtual time, instead of racing real threads.
     */
    private fun TestScope.collectStates(
        orchestrator: AgentOrchestrator,
        dispatcher: TestDispatcher,
    ): MutableList<TurnState> {
        val states = mutableListOf<TurnState>()
        backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            orchestrator.state.collect { states.add(it) }
        }
        return states
    }

    /** Runs [block] with a fresh [UnconfinedTestDispatcher] shared by the test body, the orchestrator and the collector. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun runWithCollector(block: suspend TestScope.(TestDispatcher) -> Unit) {
        val dispatcher = UnconfinedTestDispatcher()
        runTest(dispatcher) { block(dispatcher) }
    }

    @Test
    fun `happy path commits a valid tool call`() = runWithCollector { dispatcher ->
        val engine = FakeInferenceEngine(mutableListOf("""{"tool":"add_credit","customer":"ramesh","amount":250}"""))
        val validator = FakeValidator()
        val repo = FakeLedgerRepository()
        val orchestrator = AgentOrchestrator(engine, validator, repo, FakeStateBlockBuilder(), dispatcher = dispatcher)
        val states = collectStates(orchestrator, dispatcher)

        orchestrator.submitText("ramesh ko 250 udhaar likho")

        assertEquals(TurnState.Idle, states.first())
        assertTrue(states.any { it is TurnState.Inferring })
        assertTrue(states.any { it is TurnState.Validating })
        val committed = states.last()
        assertTrue(committed is TurnState.Committed)
        committed as TurnState.Committed
        assertTrue(committed.spokenConfirmation.contains("ramesh"))
        assertEquals(1, repo.transactions.size)
        assertEquals(250.0, repo.transactions.first().amount, 0.0001)
        assertEquals(1, engine.callCount)
    }

    @Test
    fun `malformed then valid JSON retries once then commits`() = runWithCollector { dispatcher ->
        val engine = FakeInferenceEngine(
            mutableListOf(
                "not json at all",
                """{"tool":"record_payment","customer":"sita","amount":500}""",
            ),
        )
        val validator = FakeValidator()
        val repo = FakeLedgerRepository()
        val orchestrator = AgentOrchestrator(engine, validator, repo, FakeStateBlockBuilder(), dispatcher = dispatcher)
        val states = collectStates(orchestrator, dispatcher)

        orchestrator.submitText("sita ne 500 diya")

        assertTrue(states.any { it is TurnState.Retrying && it.attempt == 1 })
        val committed = states.last()
        assertTrue(committed is TurnState.Committed)
        assertEquals(2, engine.callCount)
        assertEquals(1, repo.transactions.size)
    }

    @Test
    fun `two malformed responses defer with schema reason`() = runWithCollector { dispatcher ->
        val engine = FakeInferenceEngine(mutableListOf("still not json", "also not json"))
        val validator = FakeValidator()
        val repo = FakeLedgerRepository()
        val orchestrator = AgentOrchestrator(engine, validator, repo, FakeStateBlockBuilder(), dispatcher = dispatcher)
        val states = collectStates(orchestrator, dispatcher)

        orchestrator.submitText("gibberish")

        assertTrue(states.any { it is TurnState.Retrying && it.attempt == 1 })
        val deferred = states.last()
        assertTrue(deferred is TurnState.Deferred)
        deferred as TurnState.Deferred
        assertEquals(DeferKind.SCHEMA, deferred.card.kind)
        assertEquals(2, engine.callCount)
        assertEquals(1, repo.deferrals.size)
        assertTrue(repo.transactions.isEmpty())
    }

    @Test
    fun `validator defer produces a Deferred state and logs it, then accept commits`() = runWithCollector { dispatcher ->
        val engine = FakeInferenceEngine(mutableListOf("""{"tool":"add_credit","customer":"ramesh","amount":50000}"""))
        lateinit var deferCard: ConfirmCard
        val validator = FakeValidator { call, _ ->
            val c = ConfirmCard(
                turnId = "t1",
                understoodAs = call,
                humanReason = "That's a lot more than usual - confirm?",
                rawModelOutput = """{"tool":"add_credit","customer":"ramesh","amount":50000}""",
                kind = DeferKind.OVER_LIMIT,
            )
            deferCard = c
            ValidationResult.Defer(reason = DeferReason.OVER_DAILY_MAX, humanMessage = c.humanReason, card = c)
        }
        val repo = FakeLedgerRepository()
        val orchestrator = AgentOrchestrator(engine, validator, repo, FakeStateBlockBuilder(), dispatcher = dispatcher)
        val states = collectStates(orchestrator, dispatcher)

        orchestrator.submitText("ramesh ko 50000 udhaar likho")

        val deferred = states.last()
        assertTrue(deferred is TurnState.Deferred)
        deferred as TurnState.Deferred
        assertEquals(DeferKind.OVER_LIMIT, deferred.card.kind)
        assertEquals(1, repo.deferrals.size)
        assertTrue(repo.transactions.isEmpty())

        // human accepts -> commits the originally understood call
        orchestrator.resolveDeferral(accept = true, card = deferCard)
        val finalState = states.last()
        assertTrue(finalState is TurnState.Committed)
        assertEquals(1, repo.transactions.size)
        assertEquals("committed", repo.deferrals.first().resolution)
    }

    @Test
    fun `model asks for clarification and orchestrator surfaces it as Deferred`() = runWithCollector { dispatcher ->
        val engine = FakeInferenceEngine(mutableListOf("""{"tool":"ask_clarification","question":"Which Ramesh?"}"""))
        val validator = FakeValidator { call, _ ->
            check(call is ToolCall.AskClarification)
            ValidationResult.Defer(
                reason = DeferReason.MODEL_CLARIFICATION,
                humanMessage = call.question,
                card = ConfirmCard(
                    turnId = "t2",
                    understoodAs = call,
                    humanReason = call.question,
                    rawModelOutput = call.question,
                    kind = DeferKind.CLARIFICATION,
                ),
            )
        }
        val repo = FakeLedgerRepository()
        val orchestrator = AgentOrchestrator(engine, validator, repo, FakeStateBlockBuilder(), dispatcher = dispatcher)
        val states = collectStates(orchestrator, dispatcher)

        orchestrator.submitText("kal wale customer ko kitna dena hai")

        val deferred = states.last()
        assertTrue(deferred is TurnState.Deferred)
        deferred as TurnState.Deferred
        assertEquals(DeferKind.CLARIFICATION, deferred.card.kind)
        assertEquals("Which Ramesh?", deferred.card.humanReason)
        assertEquals(1, repo.deferrals.size)

        // resolving a clarification card just closes it - nothing to commit either way
        orchestrator.resolveDeferral(accept = true, card = deferred.card)
        assertEquals(TurnState.Idle, states.last())
        assertEquals("acknowledged", repo.deferrals.first().resolution)
    }
}
