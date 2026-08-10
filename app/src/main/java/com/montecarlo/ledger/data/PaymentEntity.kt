package com.montecarlo.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val amount_cents: Long,
    val frequency: String, // Weekly, Bi-weekly, Semi-monthly, Monthly, Bi-monthly, Quarterly, Yearly, One-time
    val day_of_month: Int?,
    val next_date: String,
    val is_active: Int = 1,
    val isAutoWithdraw: Boolean = false
)
