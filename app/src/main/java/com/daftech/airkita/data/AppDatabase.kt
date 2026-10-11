package com.daftech.airkita.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.daftech.airkita.data.dao.BillDao
import com.daftech.airkita.data.dao.CustomerDao
import com.daftech.airkita.data.dao.KasDao
import com.daftech.airkita.data.dao.SettingDao
import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.data.entity.Customer
import com.daftech.airkita.data.entity.KasTransaction
import com.daftech.airkita.data.entity.Setting

@Database(
    entities = [Customer::class, Bill::class, KasTransaction::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun billDao(): BillDao
    abstract fun kasDao(): KasDao
    abstract fun settingDao(): SettingDao
}
