package com.montecarlo.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)
