package com.daftech.airkita.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.daftech.airkita.data.entity.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun all(): Flow<List<Customer>>

    @Query("SELECT * FROM customers")
    suspend fun allOnce(): List<Customer>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :q || '%' OR meterNo LIKE '%' || :q || '%' ORDER BY name ASC")
    fun search(q: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun byId(id: Long): Customer?

    @Query("SELECT COUNT(*) FROM customers WHERE active = 1")
    fun countActive(): Flow<Int>

    @Insert
    suspend fun insert(c: Customer): Long

    @Insert
    suspend fun insertAll(list: List<Customer>)

    @Update
    suspend fun update(c: Customer)

    @Query("UPDATE customers SET lastMeter = :meter WHERE id = :id")
    suspend fun updateLastMeter(id: Long, meter: Int)

    @Query("DELETE FROM customers")
    suspend fun clear()
}
