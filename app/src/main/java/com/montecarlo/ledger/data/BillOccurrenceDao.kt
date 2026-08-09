package com.montecarlo.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BillOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(occurrence: BillOccurrenceEntity): Long

    @Update
    suspend fun update(occurrence: BillOccurrenceEntity)

    @Delete
    suspend fun delete(
        occurrence: BillOccurrenceEntity
    )

    @Query("DELETE FROM bill_occurrences WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM bill_occurrences WHERE id = :id")
    suspend fun getById(id: Int): BillOccurrenceEntity?

    @Query("SELECT * FROM bill_occurrences ORDER BY due_date ASC")
    fun getAll(): Flow<List<BillOccurrenceEntity>>

    @Query("SELECT * FROM bill_occurrences ORDER BY due_date ASC")
    suspend fun getAllOccurrencesList(): List<BillOccurrenceEntity>

    @Query("SELECT * FROM bill_occurrences WHERE payment_id = :paymentId ORDER BY due_date ASC")
    fun getForPayment(paymentId: Int): Flow<List<BillOccurrenceEntity>>

    @Query("SELECT * FROM bill_occurrences WHERE is_paid = 0 ORDER BY due_date ASC")
    fun getUnpaid(): Flow<List<BillOccurrenceEntity>>

    @Query("UPDATE bill_occurrences SET is_paid = 1, transaction_id = :transactionId WHERE id = :id")
    suspend fun markPaid(id: Int, transactionId: Int? = null)

    @Query("SELECT * FROM bill_occurrences WHERE due_date BETWEEN :start AND :end ORDER BY due_date ASC")
    suspend fun getInRange(start: LocalDate, end: LocalDate): List<BillOccurrenceEntity>

    @Query("DELETE FROM bill_occurrences WHERE due_date < :date")
    suspend fun deleteBefore(date: LocalDate)

    @Query(
        "DELETE FROM bill_occurrences " +
            "WHERE payment_id = :paymentId AND is_paid = 0 AND is_user_modified = 0 AND due_date BETWEEN :start AND :end"
    )
    suspend fun deleteUnpaidForPaymentBetween(paymentId: Int, start: LocalDate, end: LocalDate)

    @Query("SELECT COALESCE(SUM(amount_cents), 0) FROM bill_occurrences WHERE is_paid = 1 AND due_date BETWEEN :start AND :end")
    suspend fun getPaidAmountBetween(start: LocalDate, end: LocalDate): Long

    @Query("SELECT COUNT(*) FROM bill_occurrences WHERE payment_id = :paymentId AND due_date = :dueDate")
    suspend fun countForPaymentOnDate(paymentId: Int, dueDate: String): Int

    @Query(
        "SELECT COUNT(*) FROM bill_occurrences " +
            "WHERE payment_id = :paymentId AND (due_date = :scheduleDate OR original_due_date = :scheduleDate)"
    )
    suspend fun countForPaymentOnScheduleDate(paymentId: Int, scheduleDate: String): Int

    @Query("DELETE FROM bill_occurrences")
    suspend fun deleteAllBillOccurrences()
}
