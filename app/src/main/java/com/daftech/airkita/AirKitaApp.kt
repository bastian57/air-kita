package com.daftech.airkita

import android.app.Application
import androidx.room.Room
import com.daftech.airkita.data.AppDatabase
import com.daftech.airkita.data.Repository

class AirKitaApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "airkita.db").build()
    }
    val repository: Repository by lazy { Repository(database) }
}
