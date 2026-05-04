package com.example.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionRuleDao {
    @Query("SELECT * FROM transaction_rules ORDER BY priority DESC, LENGTH(match_text) DESC, created_at DESC")
    fun getAll(): Flow<List<TransactionRuleEntity>>

    @Query("SELECT * FROM transaction_rules WHERE is_active = 1 ORDER BY priority DESC, LENGTH(match_text) DESC, created_at DESC")
    suspend fun getActiveRules(): List<TransactionRuleEntity>

    @Query("SELECT * FROM transaction_rules WHERE match_text = :matchText LIMIT 1")
    suspend fun getByMatchText(matchText: String): TransactionRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: TransactionRuleEntity): Long

    @Delete
    suspend fun delete(rule: TransactionRuleEntity)

    @Query("DELETE FROM transaction_rules")
    suspend fun deleteAll()
}
