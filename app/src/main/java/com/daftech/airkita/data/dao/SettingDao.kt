package com.daftech.airkita.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daftech.airkita.data.entity.Setting
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun get(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getOnce(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Setting)

    @Query("SELECT * FROM settings")
    suspend fun allOnce(): List<Setting>

    @Query("DELETE FROM settings")
    suspend fun clear()
}
