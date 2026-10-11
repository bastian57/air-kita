package com.daftech.airkita.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.daftech.airkita.data.entity.KasTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KasDao {
    @Query("SELECT * FROM kas WHERE strftime('%Y-%m', date/1000, 'unixepoch') = :period ORDER BY date DESC")
    fun forPeriod(period: String): Flow<List<KasTransaction>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM kas WHERE kind = 'IN' AND strftime('%Y-%m', date/1000, 'unixepoch') = :period")
    suspend fun sumIn(period: String): Long

    @Query("SELECT COALESCE(SUM(amount),0) FROM kas WHERE kind = 'OUT' AND strftime('%Y-%m', date/1000, 'unixepoch') = :period")
    suspend fun sumOut(period: String): Long

    @Query("SELECT COALESCE(SUM(CASE WHEN kind='IN' THEN amount ELSE -amount END),0) FROM kas")
    suspend fun balanceDelta(): Long

    @Insert
    suspend fun insert(tx: KasTransaction): Long

    @Insert
    suspend fun insertAll(list: List<KasTransaction>)

    @Query("SELECT * FROM kas")
    suspend fun allOnce(): List<KasTransaction>

    @Query("DELETE FROM kas")
    suspend fun clear()
}
