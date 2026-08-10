package com.montecarlo.ledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetAmountCents: Long,
    val currentAmountCents: Long,
    val deadline: String? = null,
    val createdAt: String = java.time.LocalDate.now().toString()
)

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY targetAmountCents DESC")
    fun getAllGoals(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Delete
    suspend fun deleteGoal(goal: GoalEntity)

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()
}
