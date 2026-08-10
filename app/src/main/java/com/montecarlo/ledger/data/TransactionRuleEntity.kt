package com.montecarlo.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_rules")
data class TransactionRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val match_text: String,
    val category: String,
    val is_active: Int = 1,
    val priority: Int = 0,
    val created_at: String = "",
)
