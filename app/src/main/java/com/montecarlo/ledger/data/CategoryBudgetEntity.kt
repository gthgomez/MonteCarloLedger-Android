package com.montecarlo.ledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val limitCents: Long,
    val enabled: Int = 1,
    val createdAt: String = java.time.LocalDate.now().toString()
)

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets ORDER BY category ASC")
    fun getAll(): Flow<List<CategoryBudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: CategoryBudgetEntity)

    @Update
    suspend fun update(budget: CategoryBudgetEntity)

    @Delete
    suspend fun delete(budget: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAll()
}
