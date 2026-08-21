package com.montecarlo.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user money account (checking, savings, credit, cash, other).
 *
 * v1 groundwork only: the primary balance/reconciliation pipeline still runs on the
 * `bank_balance_cents` setting. Migration 14 seeds one default account mirroring
 * that balance so per-account forecasting can adopt this table incrementally.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** checking | savings | credit | cash | other */
    val type: String,
    val balanceCents: Long,
    val isReconciled: Boolean = false,
    val isDefault: Boolean = false,
    val lastUpdated: String,
)
