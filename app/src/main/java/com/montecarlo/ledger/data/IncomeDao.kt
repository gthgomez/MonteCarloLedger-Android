package com.montecarlo.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(income: IncomeEntity)

    @Update
    suspend fun updateIncome(income: IncomeEntity)

    @Delete
    suspend fun deleteIncome(income: IncomeEntity)

    @Query("SELECT * FROM income WHERE id = :id")
    suspend fun getIncomeById(id: Int): IncomeEntity?

    @Query("SELECT * FROM income")
    fun getAllIncomes(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM income")
    suspend fun getAllIncomesList(): List<IncomeEntity>

    @Query("DELETE FROM income")
    suspend fun deleteAllIncomes()
}