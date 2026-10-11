package com.daftech.airkita.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val meterNo: String,
    val address: String = "",
    val lastMeter: Int = 0,
    val active: Boolean = true
)
