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

/** Liability kind: fixed-schedule installment loan vs credit-card style revolving balance. */
object DebtKind {
    const val INSTALLMENT = "installment"
    const val REVOLVING = "revolving"

    fun normalize(value: String?): String =
        if (value.equals(REVOLVING, ignoreCase = true)) REVOLVING else INSTALLMENT
}

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedPaymentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedAccountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["linkedPaymentId"]),
        Index(value = ["linkedAccountId"]),
    ],
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
    /** installment | revolving. Revolving debts use percent-of-balance minimums. */
    val kind: String = DebtKind.INSTALLMENT,
    /** Statement cycle close day (1–31), when tracked. */
    val statementDayOfMonth: Int? = null,
    /**
     * Revolving only: minimum-payment percentage of the statement balance in
     * basis points (300 = 3%). Ignored for installment debts.
     */
    val minPaymentPercentBps: Int = 0,
    /** Revolving only: flat floor applied to the computed percentage minimum. */
    val minPaymentFloorCents: Long = 0L,
    /** Credit account whose charges feed this liability (accounts table id). */
    val linkedAccountId: Long? = null,
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
