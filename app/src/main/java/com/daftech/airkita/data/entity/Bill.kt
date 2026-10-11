package com.daftech.airkita.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    indices = [Index(value = ["customerId", "period"], unique = true)]
)
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val period: String, // yyyy-MM
    val meterStart: Int,
    val meterEnd: Int,
    val usageM3: Int,
    val block1m3: Int,
    val block2m3: Int,
    val block3m3: Int,
    val abonemen: Long,
    val fine: Long,
    val total: Long,
    val paid: Boolean = false,
    val paidAt: Long? = null
)
