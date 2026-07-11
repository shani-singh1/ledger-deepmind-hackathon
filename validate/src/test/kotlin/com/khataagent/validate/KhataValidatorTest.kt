package com.khataagent.validate

import com.khataagent.core.agent.DeferKind
import com.khataagent.core.model.Customer
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnType
import com.khataagent.core.tool.ToolCall
import com.khataagent.core.validate.DeferReason
import com.khataagent.core.validate.ValidationContext
import com.khataagent.core.validate.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataValidatorTest {

    private val validator = KhataValidator()

    private val ramesh = Customer(id = 1, name = "Ramesh")
    private val sita = Customer(id = 2, name = "Sita")
    private val kiran = Customer(id = 3, name = "Kiran")
    private val keeran = Customer(id = 4, name = "Keeran")

    private val knownCustomers = listOf(ramesh, sita, kiran, keeran)

    private fun ctx(
        knownCustomers: List<Customer> = this.knownCustomers,
        balances: Map<Long, Double> = emptyMap(),
        recentTransactions: List<Transaction> = emptyList(),
        dailyMaxSingleTxn: Double = 5_000.0,
        duplicateWindowMillis: Long = 120_000L,
        nowMillis: Long = 1_000_000_000L,
    ) = ValidationContext(
        knownCustomers = knownCustomers,
        balancesByCustomerId = balances,
        recentTransactions = recentTransactions,
        dailyMaxSingleTxn = dailyMaxSingleTxn,
        duplicateWindowMillis = duplicateWindowMillis,
        nowMillis = nowMillis,
    )

    // ---- OVER_DAILY_MAX ------------------------------------------------------------------

    @Test
    fun `amount over daily max defers`() {
        val call = ToolCall.AddCredit(customer = "Ramesh", amount = 6_000.0)
        val result = validator.validate(call, ctx())
        assertDefers(result, DeferReason.OVER_DAILY_MAX, DeferKind.OVER_LIMIT)
    }

    @Test
    fun `amount over daily max fires before customer lookup`() {
        // Unknown customer AND over limit -- OVER_DAILY_MAX must win (checked first).
        val call = ToolCall.AddCredit(customer = "Nobody", amount = 6_000.0)
        val result = validator.validate(call, ctx())
        assertDefers(result, DeferReason.OVER_DAILY_MAX, DeferKind.OVER_LIMIT)
    }

    @Test
    fun `amount exactly at daily max is valid`() {
        val call = ToolCall.AddCredit(customer = "Ramesh", amount = 5_000.0)
        val result = validator.validate(call, ctx())
        assertValid(result)
    }

    @Test
    fun `record sale amount over daily max also defers`() {
        val call = ToolCall.RecordSale(item = "rice", qty = 10.0, amount = 5_500.0)
        val result = validator.validate(call, ctx())
        assertDefers(result, DeferReason.OVER_DAILY_MAX, DeferKind.OVER_LIMIT)
    }

    // ---- CUSTOMER_NOT_FOUND / CUSTOMER_AMBIGUOUS ------------------------------------------

    @Test
    fun `unknown customer with no phonetic match defers as not found`() {
        val call = ToolCall.AddCredit(customer = "Zubin", amount = 100.0)
        val result = validator.validate(call, ctx())
        assertDefers(result, DeferReason.CUSTOMER_NOT_FOUND, DeferKind.NEW_CUSTOMER)
    }

    @Test
    fun `two phonetic matches defer as ambiguous`() {
        // "Kiren" folds to the same phonetic key as both "Kiran" and "Keeran".
        val call = ToolCall.AddCredit(customer = "Kiren", amount = 100.0)
        val result = validator.validate(call, ctx())
        assertDefers(result, DeferReason.CUSTOMER_AMBIGUOUS, DeferKind.AMBIGUOUS_CUSTOMER)
        val defer = result as ValidationResult.Defer
        assertTrue(defer.humanMessage.contains("Kiran"))
        assertTrue(defer.humanMessage.contains("Keeran"))
    }

    @Test
    fun `single phonetic match resolves silently as valid`() {
        // "Rmesh" (vowel dropped) phonetically matches only "Ramesh" -- no defer.
        val call = ToolCall.AddCredit(customer = "Rmesh", amount = 100.0)
        val result = validator.validate(call, ctx())
        assertValid(result)
    }

    @Test
    fun `exact case-insensitive match resolves without touching phonetics`() {
        val call = ToolCall.AddCredit(customer = "RAMESH", amount = 100.0)
        val result = validator.validate(call, ctx())
        assertValid(result)
    }

    // ---- OVERPAYMENT -----------------------------------------------------------------------

    @Test
    fun `payment over balance defers as overpayment`() {
        val call = ToolCall.RecordPayment(customer = "Sita", amount = 600.0)
        val result = validator.validate(call, ctx(balances = mapOf(sita.id to 500.0)))
        assertDefers(result, DeferReason.OVERPAYMENT, DeferKind.OVERPAYMENT)
    }

    @Test
    fun `payment exactly equal to balance is valid`() {
        val call = ToolCall.RecordPayment(customer = "Sita", amount = 500.0)
        val result = validator.validate(call, ctx(balances = mapOf(sita.id to 500.0)))
        assertValid(result)
    }

    @Test
    fun `payment under balance is valid`() {
        val call = ToolCall.RecordPayment(customer = "Sita", amount = 100.0)
        val result = validator.validate(call, ctx(balances = mapOf(sita.id to 500.0)))
        assertValid(result)
    }

    // ---- DUPLICATE_SUSPECT -------------------------------------------------------------------

    @Test
    fun `same customer and amount within window defers as duplicate`() {
        val now = 1_000_000_000L
        val recent = Transaction(
            id = 1,
            customerId = ramesh.id,
            customerName = ramesh.name,
            type = TxnType.CREDIT,
            amount = 250.0,
            createdAt = now - 60_000L, // 1 minute ago, window is 2 minutes
        )
        val call = ToolCall.AddCredit(customer = "Ramesh", amount = 250.0)
        val result = validator.validate(call, ctx(recentTransactions = listOf(recent), nowMillis = now))
        assertDefers(result, DeferReason.DUPLICATE_SUSPECT, DeferKind.DUPLICATE)
    }

    @Test
    fun `near-duplicate just outside the window is valid`() {
        val now = 1_000_000_000L
        val recent = Transaction(
            id = 1,
            customerId = ramesh.id,
            customerName = ramesh.name,
            type = TxnType.CREDIT,
            amount = 250.0,
            createdAt = now - 121_000L, // just past the 2-minute window
        )
        val call = ToolCall.AddCredit(customer = "Ramesh", amount = 250.0)
        val result = validator.validate(call, ctx(recentTransactions = listOf(recent), nowMillis = now))
        assertValid(result)
    }

    @Test
    fun `duplicate check ignores different amounts`() {
        val now = 1_000_000_000L
        val recent = Transaction(
            id = 1,
            customerId = ramesh.id,
            customerName = ramesh.name,
            type = TxnType.CREDIT,
            amount = 999.0,
            createdAt = now - 1_000L,
        )
        val call = ToolCall.AddCredit(customer = "Ramesh", amount = 250.0)
        val result = validator.validate(call, ctx(recentTransactions = listOf(recent), nowMillis = now))
        assertValid(result)
    }

    @Test
    fun `duplicate check ignores different transaction type`() {
        // A credit and a payment of the same amount close together shouldn't collide.
        val now = 1_000_000_000L
        val recent = Transaction(
            id = 1,
            customerId = ramesh.id,
            customerName = ramesh.name,
            type = TxnType.PAYMENT,
            amount = 250.0,
            createdAt = now - 1_000L,
        )
        val call = ToolCall.AddCredit(customer = "Ramesh", amount = 250.0)
        val result = validator.validate(call, ctx(recentTransactions = listOf(recent), nowMillis = now))
        assertValid(result)
    }

    // ---- MODEL_CLARIFICATION ------------------------------------------------------------------

    @Test
    fun `ask clarification always defers with its own question as the message`() {
        val call = ToolCall.AskClarification(question = "Which Ramesh -- the one on MG Road?")
        val result = validator.validate(call, ctx())
        assertDefers(result, DeferReason.MODEL_CLARIFICATION, DeferKind.CLARIFICATION)
        val defer = result as ValidationResult.Defer
        assertEquals("Which Ramesh -- the one on MG Road?", defer.humanMessage)
    }

    // ---- calls without amount/customer fields are always valid ------------------------------

    @Test
    fun `query today is always valid`() {
        val result = validator.validate(ToolCall.QueryToday, ctx())
        assertValid(result)
    }

    @Test
    fun `close day is always valid`() {
        val result = validator.validate(ToolCall.CloseDay, ctx())
        assertValid(result)
    }

    @Test
    fun `update stock is always valid`() {
        val call = ToolCall.UpdateStock(item = "rice", qtyDelta = -5.0)
        val result = validator.validate(call, ctx())
        assertValid(result)
    }

    @Test
    fun `query balance for unknown customer still defers as not found`() {
        val call = ToolCall.QueryBalance(customer = "Zubin")
        val result = validator.validate(call, ctx())
        assertDefers(result, DeferReason.CUSTOMER_NOT_FOUND, DeferKind.NEW_CUSTOMER)
    }

    // ---- happy path -----------------------------------------------------------------------

    @Test
    fun `well-formed add credit for a known customer under all limits is valid`() {
        val call = ToolCall.AddCredit(customer = "Ramesh", amount = 250.0, item = "rice")
        val result = validator.validate(call, ctx())
        assertValid(result)
        assertEquals(call, (result as ValidationResult.Valid).call)
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun assertValid(result: ValidationResult) {
        assertTrue("expected Valid but was $result", result is ValidationResult.Valid)
    }

    private fun assertDefers(result: ValidationResult, reason: DeferReason, kind: DeferKind) {
        assertTrue("expected Defer but was $result", result is ValidationResult.Defer)
        val defer = result as ValidationResult.Defer
        assertEquals(reason, defer.reason)
        assertEquals(kind, defer.card.kind)
        assertTrue("humanMessage should not be blank", defer.humanMessage.isNotBlank())
        assertEquals(defer.humanMessage, defer.card.humanReason)
    }
}
