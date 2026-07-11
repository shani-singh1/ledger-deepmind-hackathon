package com.khataagent.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room-backed mirror of [com.khataagent.core.model.DeferralEntry] — the "clear boundaries" log. */
@Entity(tableName = "deferral_log")
data class DeferralEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: String,
    val rawModelOutput: String,
    val reason: String,
    /** null = still open; else "committed" / "rejected" / free text. */
    val resolution: String? = null,
    val createdAt: Long = 0L,
)
