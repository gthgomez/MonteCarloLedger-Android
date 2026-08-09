package com.montecarlo.ledger.data

data class CategorySpend(
    val category: String,
    @androidx.room.ColumnInfo(name = "total") val totalCents: Long,
)

data class FlowSummary(
    @androidx.room.ColumnInfo(name = "inflow") val inflowCents: Long,
    @androidx.room.ColumnInfo(name = "outflow") val outflowCents: Long,
)
