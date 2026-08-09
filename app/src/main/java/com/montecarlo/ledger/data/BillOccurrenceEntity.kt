package com.montecarlo.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bill_occurrences",
    foreignKeys = [ForeignKey(
        entity = PaymentEntity::class,
        parentColumns = ["id"],
        childColumns = ["payment_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("payment_id")]
)
data class BillOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val payment_id: Int,
    val due_date: String,
    val amount_cents: Long,
    val is_paid: Int = 0,
    val transaction_id: Int? = null,
    val created_at: String? = null,
    val original_due_date: String? = null,
    val is_user_modified: Int = 0
)
