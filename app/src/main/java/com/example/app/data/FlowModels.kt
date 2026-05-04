package com.example.app.data

data class CategorySpend(
    val category: String,
    @androidx.room.ColumnInfo(name = "total") val totalCents: Int,
)

data class FlowSummary(
    @androidx.room.ColumnInfo(name = "inflow") val inflowCents: Int,
    @androidx.room.ColumnInfo(name = "outflow") val outflowCents: Int,
)
