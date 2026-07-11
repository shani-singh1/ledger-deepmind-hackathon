package com.khataagent.data

import com.khataagent.core.model.Customer
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class StateBlockBuilderImplTest {

    private fun customer(id: Long, name: String) = Customer(id = id, name = name, createdAt = 0L)

    private fun txn(
        id: Long,
        customerName: String?,
        type: TxnType,
        amount: Double,
        item: String? = null,
        createdAt: Long = 0L,
    ) = Transaction(
        id = id,
        customerId = id,
        customerName = customerName,
        type = type,
        amount = amount,
        item = item,
        status = TxnStatus.CONFIRMED,
        source = TxnSource.SEED,
        createdAt = createdAt,
    )

    @Test
    fun `contains todays totals`() = runTest {
        val repo = FakeLedgerRepository(
            dailyState = DailyState(date = "2026-07-11", totalCredit = 1500.0, totalPayments = 800.0, txnCount = 12),
        )
        val block = StateBlockBuilderImpl(repo).build()

        assertTrue(block.contains("TODAY:"))
        assertTrue(block.contains("credit ₹1500"))
        assertTrue(block.contains("payments ₹800"))
        assertTrue(block.contains("12 txns"))
    }

    @Test
    fun `contains last 3 transactions and known customers`() = runTest {
        val repo = FakeLedgerRepository(
            customers = listOf(customer(1, "Ramesh Kumar"), customer(2, "Sita Devi")),
            recent = listOf(
                txn(1, "Ramesh Kumar", TxnType.CREDIT, 250.0, "rice"),
                txn(2, "Sita Devi", TxnType.PAYMENT, 500.0),
                txn(3, null, TxnType.SALE, 40.0, "biscuits"),
            ),
        )
        val block = StateBlockBuilderImpl(repo).build()

        assertTrue(block.contains("LAST 3:"))
        assertTrue(block.contains("credit Ramesh Kumar"))
        assertTrue(block.contains("payment Sita Devi"))
        assertTrue(block.contains("sale walk-in"))
        assertTrue(block.contains("KNOWN CUSTOMERS:"))
        assertTrue(block.contains("Ramesh Kumar"))
        assertTrue(block.contains("Sita Devi"))
    }

    @Test
    fun `contains open credits sorted by balance descending`() = runTest {
        val repo = FakeLedgerRepository(
            customers = listOf(customer(1, "Ramesh Kumar"), customer(2, "Sita Devi"), customer(3, "Anil Gupta")),
            balances = mapOf(1L to 300.0, 2L to 1200.0, 3L to 0.0),
        )
        val block = StateBlockBuilderImpl(repo).build()

        assertTrue(block.contains("OPEN CREDITS:"))
        val sitaIndex = block.indexOf("Sita Devi")
        val rameshIndex = block.indexOf("Ramesh Kumar", startIndex = block.indexOf("OPEN CREDITS"))
        assertTrue("Sita Devi (higher balance) should appear before Ramesh Kumar", sitaIndex < rameshIndex)
        // Anil Gupta has zero balance -> should not appear in OPEN CREDITS section
        val openCreditsLine = block.lines().first { it.startsWith("OPEN CREDITS:") }
        assertTrue(!openCreditsLine.contains("Anil Gupta"))
    }

    @Test
    fun `stays under the hard char cap even with many customers`() = runTest {
        val manyCustomers = (1..500).map { customer(it.toLong(), "Customer Number $it With A Fairly Long Name") }
        val repo = FakeLedgerRepository(customers = manyCustomers)
        val block = StateBlockBuilderImpl(repo).build()

        assertTrue("state block was ${block.length} chars", block.length <= StateBlockBuilderImpl.MAX_CHARS)
    }

    @Test
    fun `handles empty ledger gracefully`() = runTest {
        val repo = FakeLedgerRepository()
        val block = StateBlockBuilderImpl(repo).build()

        assertTrue(block.contains("OPEN CREDITS: none"))
        assertTrue(block.contains("LAST 3: none"))
        assertTrue(block.length <= StateBlockBuilderImpl.MAX_CHARS)
    }
}
