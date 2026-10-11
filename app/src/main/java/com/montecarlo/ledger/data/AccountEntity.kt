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

/**
 * Single source of truth for cash-vs-card routing: only the account's TYPE decides it.
 *
 * A credit-type account's charges raise its linked card liability instead of moving cash; every
 * other account (and a `null` account id) moves the bank. Both the repository's transaction side
 * effects and the dashboard's cash ledger must use this predicate so they can never disagree.
 */
fun AccountEntity.isCreditAccount(): Boolean = type.equals("credit", ignoreCase = true)
