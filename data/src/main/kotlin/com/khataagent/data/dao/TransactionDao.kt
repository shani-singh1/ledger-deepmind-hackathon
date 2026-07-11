package com.khataagent.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khataagent.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * NOTE: no unit tests here — Room DAOs need an actual SQLite driver (instrumented/on-device
 * test), which this pure-JVM test task can't run. Exercise via :app on-device instead.
 */
@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(txn: TransactionEntity): Long

    /** Bulk insert used by DemoSeed. */
    @Insert
    suspend fun insertAll(txns: List<TransactionEntity>): List<Long>

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /** Full-row update — backs the edit-transaction sheet (amount/item/note/type edits). */
    @Update
    suspend fun update(txn: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE createdAt >= :sinceMillis ORDER BY createdAt DESC")
    suspend fun since(sinceMillis: Long): List<TransactionEntity>

    /** Backs both observeTodayTransactions() and observeDailyState(date) in the repository. */
    @Query("SELECT * FROM transactions WHERE createdAt >= :sinceMillis ORDER BY createdAt DESC")
    fun observeSince(sinceMillis: Long): Flow<List<TransactionEntity>>

    /** Full history for one customer, newest first — backs the customer drill-down screen. */
    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<TransactionEntity>>

    /** SUM(credit) - SUM(payment) over CONFIRMED transactions for one customer. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END), 0.0) -
               COALESCE(SUM(CASE WHEN type = 'PAYMENT' THEN amount ELSE 0 END), 0.0)
        FROM transactions
        WHERE customerId = :customerId AND status = 'CONFIRMED'
        """
    )
    suspend fun outstandingBalance(customerId: Long): Double
}
