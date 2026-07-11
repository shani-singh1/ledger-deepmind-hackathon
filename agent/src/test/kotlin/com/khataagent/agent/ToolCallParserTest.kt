package com.khataagent.agent

import com.khataagent.core.tool.ToolCall
import com.khataagent.core.tool.ToolParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolCallParserTest {

    private fun parseSuccess(raw: String): ToolCall {
        val result = ToolCallParser.parse(raw)
        return (result as? ToolParseResult.Success)?.call
            ?: fail("Expected Success but got $result").let { error("unreachable") }
    }

    private fun parseFailure(raw: String): ToolParseResult.Failure {
        val result = ToolCallParser.parse(raw)
        return result as? ToolParseResult.Failure
            ?: fail("Expected Failure but got $result").let { error("unreachable") }
    }

    @Test
    fun `valid plain JSON add_credit parses`() {
        val call = parseSuccess("""{"tool":"add_credit","customer":"ramesh","amount":250,"item":"rice"}""")
        assertEquals(ToolCall.AddCredit(customer = "ramesh", amount = 250.0, item = "rice"), call)
    }

    @Test
    fun `add_credit without optional fields parses with nulls`() {
        val call = parseSuccess("""{"tool":"add_credit","customer":"ramesh","amount":250}""")
        assertEquals(ToolCall.AddCredit(customer = "ramesh", amount = 250.0, item = null, note = null), call)
    }

    @Test
    fun `record_payment parses`() {
        val call = parseSuccess("""{"tool":"record_payment","customer":"sita","amount":500}""")
        assertEquals(ToolCall.RecordPayment(customer = "sita", amount = 500.0), call)
    }

    @Test
    fun `record_sale parses`() {
        val call = parseSuccess("""{"tool":"record_sale","item":"sugar","qty":2,"amount":90}""")
        assertEquals(ToolCall.RecordSale(item = "sugar", qty = 2.0, amount = 90.0), call)
    }

    @Test
    fun `update_stock parses snake_case qty_delta`() {
        val call = parseSuccess("""{"tool":"update_stock","item":"rice","qty_delta":-5}""")
        assertEquals(ToolCall.UpdateStock(item = "rice", qtyDelta = -5.0), call)
    }

    @Test
    fun `query_balance parses`() {
        val call = parseSuccess("""{"tool":"query_balance","customer":"ramesh"}""")
        assertEquals(ToolCall.QueryBalance(customer = "ramesh"), call)
    }

    @Test
    fun `query_today parses with no params`() {
        val call = parseSuccess("""{"tool":"query_today"}""")
        assertEquals(ToolCall.QueryToday, call)
    }

    @Test
    fun `close_day parses with no params`() {
        val call = parseSuccess("""{"tool":"close_day"}""")
        assertEquals(ToolCall.CloseDay, call)
    }

    @Test
    fun `ask_clarification parses`() {
        val call = parseSuccess("""{"tool":"ask_clarification","question":"Which Ramesh?"}""")
        assertEquals(ToolCall.AskClarification(question = "Which Ramesh?"), call)
    }

    @Test
    fun `fenced json code block is stripped`() {
        val raw = "```json\n{\"tool\":\"add_credit\",\"customer\":\"ramesh\",\"amount\":250}\n```"
        val call = parseSuccess(raw)
        assertEquals(ToolCall.AddCredit(customer = "ramesh", amount = 250.0), call)
    }

    @Test
    fun `bare fence without json tag is stripped`() {
        val raw = "```\n{\"tool\":\"record_payment\",\"customer\":\"sita\",\"amount\":500}\n```"
        val call = parseSuccess(raw)
        assertEquals(ToolCall.RecordPayment(customer = "sita", amount = 500.0), call)
    }

    @Test
    fun `leading and trailing prose around JSON is tolerated`() {
        val raw = "Sure! Here you go: {\"tool\":\"query_today\"} Hope that helps."
        val call = parseSuccess(raw)
        assertEquals(ToolCall.QueryToday, call)
    }

    @Test
    fun `amount given as a quoted string is coerced to Double`() {
        val call = parseSuccess("""{"tool":"add_credit","customer":"ramesh","amount":"250"}""")
        assertEquals(250.0, (call as ToolCall.AddCredit).amount, 0.0001)
    }

    @Test
    fun `qty given as a quoted string is coerced to Double`() {
        val call = parseSuccess("""{"tool":"record_sale","item":"sugar","qty":"2.5","amount":90}""")
        assertEquals(2.5, (call as ToolCall.RecordSale).qty, 0.0001)
    }

    @Test
    fun `malformed json with unbalanced braces fails`() {
        val failure = parseFailure("""{"tool":"add_credit","customer":"ramesh","amount":250""")
        assertTrue(failure.error.isNotBlank())
        assertEquals("""{"tool":"add_credit","customer":"ramesh","amount":250""", failure.rawOutput)
    }

    @Test
    fun `completely non-json text fails`() {
        val failure = parseFailure("I'm not sure what you mean.")
        assertTrue(failure.error.isNotBlank())
    }

    @Test
    fun `missing tool field fails`() {
        val failure = parseFailure("""{"customer":"ramesh","amount":250}""")
        assertTrue(failure.error.contains("tool", ignoreCase = true))
    }

    @Test
    fun `unknown tool name fails`() {
        val failure = parseFailure("""{"tool":"delete_everything","customer":"ramesh"}""")
        assertTrue(failure.error.contains("delete_everything"))
    }

    @Test
    fun `missing required field fails with informative error`() {
        val failure = parseFailure("""{"tool":"add_credit","customer":"ramesh"}""")
        assertTrue(failure.error.contains("amount"))
    }

    @Test
    fun `blank required string field fails`() {
        val failure = parseFailure("""{"tool":"add_credit","customer":"","amount":250}""")
        assertTrue(failure.error.contains("customer"))
    }

    @Test
    fun `non-numeric amount fails`() {
        val failure = parseFailure("""{"tool":"add_credit","customer":"ramesh","amount":"not-a-number"}""")
        assertTrue(failure.error.contains("amount"))
    }

    @Test
    fun `ask_clarification without question falls back to default text`() {
        val call = parseSuccess("""{"tool":"ask_clarification"}""")
        assertEquals(ToolCall.AskClarification(question = "Could you clarify?"), call)
    }
}
