package com.daftech.airkita.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.daftech.airkita.data.BillWithCustomer
import com.daftech.airkita.data.entity.Bill
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM bills WHERE period = :period ORDER BY id DESC")
    fun withCustomer(period: String): Flow<List<BillWithCustomer>>

    @Transaction
    @Query("SELECT * FROM bills WHERE period = :period ORDER BY id DESC")
    suspend fun withCustomerOnce(period: String): List<BillWithCustomer>

    @Transaction
    @Query("SELECT * FROM bills WHERE paid = 0 ORDER BY period DESC, id DESC LIMIT :limit")
    suspend fun unpaid(limit: Int): List<BillWithCustomer>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun byId(id: Long): Bill?

    @Query("SELECT COALESCE(SUM(total),0) FROM bills WHERE period = :period")
    suspend fun sumTotal(period: String): Long

    @Query("SELECT COALESCE(SUM(total),0) FROM bills WHERE period = :period AND paid = 1")
    suspend fun sumPaid(period: String): Long

    @Query("SELECT COUNT(*) FROM bills WHERE period = :period AND paid = 0")
    suspend fun countUnpaid(period: String): Int

    @Insert
    suspend fun insert(b: Bill): Long

    @Insert
    suspend fun insertAll(list: List<Bill>)

    @Update
    suspend fun update(b: Bill)

    @Query("UPDATE bills SET paid = 1, paidAt = :at WHERE id = :id")
    suspend fun markPaid(id: Long, at: Long)

    @Query("SELECT * FROM bills")
    suspend fun allOnce(): List<Bill>

    @Query("DELETE FROM bills")
    suspend fun clear()
}
