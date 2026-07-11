package com.khataagent.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room-backed mirror of [com.khataagent.core.model.Customer]. Enums stored as their name string. */
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneHint: String? = null,
    /** Phonetic.key(name) — computed and stored by RoomLedgerRepository on every upsert. */
    val namePhonetic: String? = null,
    val createdAt: Long = 0L,
)
