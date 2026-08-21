package com.montecarlo.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Bank clearing state for a transaction: authorized but not yet posted vs settled. */
object ClearingStatus {
    const val PENDING = "pending"
    const val POSTED = "posted"

    fun normalize(value: String?): String =
        if (value.equals(PENDING, ignoreCase = true)) PENDING else POSTED
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount_cents: Long,
    val date: String,
    val type: String, // income, expense, adjustment
    val category: String = "uncategorized",
    val source: String = "manual",
    val review_status: String = "approved",
    val reviewed_at: String? = null,
    /** pending | posted. Pending charges are committed money but not yet settled by the bank. */
    val clearing_status: String = ClearingStatus.POSTED,
    /** Optional money account this transaction belongs to (accounts table id). */
    val account_id: Long? = null,
)
