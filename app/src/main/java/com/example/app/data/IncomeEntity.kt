package com.example.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "income")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val amount_cents: Int,
    val frequency: String,
    val day_of_month: Int?,
    val next_date: String,
    val expectedAmountCents: Int? = null,
    val payType: String = "FLAT"
)