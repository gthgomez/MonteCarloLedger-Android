package com.example.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getByType(type: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Int): TransactionEntity?

    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE type = 'income' AND date = :date AND description = :description"
    )
    suspend fun countIncomeByDescriptionAndDate(description: String, date: String): Int

    @Query("SELECT SUM(amount_cents) FROM transactions")
    fun getTotalBalanceCents(): Flow<Int?>

    @Query("SELECT * FROM transactions WHERE type = 'adjustment' ORDER BY date DESC")
    fun getAdjustmentHistory(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
