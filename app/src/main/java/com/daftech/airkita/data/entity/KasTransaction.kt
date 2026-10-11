package com.daftech.airkita.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kas")
data class KasTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long, // epoch millis
    val kind: String, // "IN" atau "OUT"
    val category: String,
    val note: String,
    val amount: Long,
    val auto: Boolean = false
)
