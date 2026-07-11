package com.khataagent.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.khataagent.data.entity.CustomerEntity

/**
 * NOTE: no unit tests here — Room DAOs need an actual SQLite driver (instrumented/on-device
 * test), which this pure-JVM test task can't run. Exercise via :app on-device instead.
 *
 * "Upsert" (merge-by-name) is composed in RoomLedgerRepository from [findByName] + [insert]/
 * [update] rather than Room's @Upsert, which returns -1 (not the row id) on the update path —
 * a foot-gun for callers that need the id back.
 */
@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE namePhonetic = :phoneticKey")
    suspend fun searchByPhonetic(phoneticKey: String): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun allCustomers(): List<CustomerEntity>

    @Insert
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    /** Bulk insert used by DemoSeed. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>): List<Long>
}
