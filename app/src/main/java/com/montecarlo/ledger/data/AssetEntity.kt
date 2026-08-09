package com.montecarlo.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "Cash", "Stock", "Crypto", "Property", "Other"
    val balanceCents: Long,
    val lastUpdated: String, // ISO date
)
