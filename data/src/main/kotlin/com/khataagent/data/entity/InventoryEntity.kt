package com.khataagent.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room-backed mirror of [com.khataagent.core.model.InventoryItem]. */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val item: String,
    val qty: Double,
    val unit: String = "pcs",
    val lowWatermark: Double = 0.0,
)
