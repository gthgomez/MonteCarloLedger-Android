package com.montecarlo.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("SELECT * FROM payments ORDER BY name ASC")
    fun getAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY name ASC")
    suspend fun getAllPaymentsList(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Int): PaymentEntity?

    @Query("SELECT * FROM payments WHERE is_active = 1 ORDER BY name ASC")
    fun getActive(): Flow<List<PaymentEntity>>

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
}
