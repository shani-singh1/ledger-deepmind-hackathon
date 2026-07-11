package com.khataagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.khataagent.data.dao.CustomerDao
import com.khataagent.data.dao.DeferralDao
import com.khataagent.data.dao.InventoryDao
import com.khataagent.data.dao.TransactionDao
import com.khataagent.data.entity.CustomerEntity
import com.khataagent.data.entity.DeferralEntity
import com.khataagent.data.entity.InventoryEntity
import com.khataagent.data.entity.TransactionEntity

@Database(
    entities = [
        CustomerEntity::class,
        TransactionEntity::class,
        InventoryEntity::class,
        DeferralEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class KhataDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun deferralDao(): DeferralDao

    companion object {
        private const val DB_NAME = "khata.db"

        @Volatile
        private var instance: KhataDatabase? = null

        /** Hackathon build: schema will churn, never block the demo on a migration. */
        fun build(context: Context): KhataDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KhataDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
