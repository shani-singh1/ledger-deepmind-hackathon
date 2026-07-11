package com.khataagent.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.khataagent.data.entity.DeferralEntity
import kotlinx.coroutines.flow.Flow

/**
 * NOTE: no unit tests here — Room DAOs need an actual SQLite driver (instrumented/on-device
 * test), which this pure-JVM test task can't run. Exercise via :app on-device instead.
 */
@Dao
interface DeferralDao {

    @Insert
    suspend fun insert(entry: DeferralEntity): Long

    @Query("UPDATE deferral_log SET resolution = :resolution WHERE id = :id")
    suspend fun resolve(id: Long, resolution: String)

    @Query("SELECT * FROM deferral_log ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DeferralEntity>>
}
