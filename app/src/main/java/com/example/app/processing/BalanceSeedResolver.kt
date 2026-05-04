package com.example.app.processing

object BalanceSeedResolver {

    fun resolve(ledgerBalanceCents: Int, bankBalanceCents: Int, isReconciled: Boolean): Int {
        return if (isReconciled) bankBalanceCents else ledgerBalanceCents
    }
}
