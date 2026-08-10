package com.montecarlo.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedPaymentId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index(value = ["linkedPaymentId"])],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val balanceCents: Long,
    /** APR in hundredths of a percent. 1850 represents 18.50%. */
    val aprBasisPoints: Int,
    val minimumPaymentCents: Long,
    val dueDayOfMonth: Int,
    val linkedPaymentId: Int? = null,
    val isActive: Boolean = true,
)

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY name ASC")
    fun getAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE isActive = 1 ORDER BY name ASC")
    fun getActive(): Flow<List<DebtEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: DebtEntity): Long

    @Update
    suspend fun update(debt: DebtEntity)

    @Delete
    suspend fun delete(debt: DebtEntity)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}
