package com.montecarlo.ledger.processing

object BalanceSeedResolver {

    fun resolve(ledgerBalanceCents: Long, bankBalanceCents: Long, isReconciled: Boolean): Long {
        return if (isReconciled) bankBalanceCents else ledgerBalanceCents
    }
}
