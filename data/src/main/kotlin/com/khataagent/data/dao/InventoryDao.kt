package com.khataagent.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.khataagent.data.entity.InventoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * NOTE: no unit tests here — Room DAOs need an actual SQLite driver (instrumented/on-device
 * test), which this pure-JVM test task can't run. Exercise via :app on-device instead.
 */
@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory ORDER BY item ASC")
    suspend fun getAll(): List<InventoryEntity>

    @Query("SELECT * FROM inventory ORDER BY item ASC")
    fun observeAll(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE item = :item COLLATE NOCASE LIMIT 1")
    suspend fun findByItem(item: String): InventoryEntity?

    @Query("UPDATE inventory SET qty = qty + :qtyDelta WHERE item = :item COLLATE NOCASE")
    suspend fun adjustStock(item: String, qtyDelta: Double)

    @Insert
    suspend fun insert(item: InventoryEntity): Long

    /** Bulk insert used by DemoSeed. */
    @Insert
    suspend fun insertAll(items: List<InventoryEntity>): List<Long>
}
