package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F2: the balance card's red "Difference" line must follow the cash basis. Subtracting the
 * all-transaction ledger from the bank balance locally re-introduces the very false alarm the
 * cash ledger exists to remove.
 */
class BalanceDifferenceLabelTest {

    @Test
    fun hiddenWhenTheCashBasisAgreesWithTheBankEvenIfTheAppTotalDoesNot() {
        // A reconciled dashboard whose app total carries a credit-card charge: cash agrees with the
        // bank, so no difference belongs on screen.
        val uiState = AppUiState(
            bankBalanceCents = 0L,
            ledgerBalanceCents = -5_000L,
            cashLedgerCents = 0L,
            isBalanceReconciled = true,
            bankLedgerMismatch = false,
            driftCents = 0L,
        )

        assertNull(balanceDifferenceLabel(uiState))
    }

    @Test
    fun reportsTheCashBasisDriftRatherThanTheAllTransactionLedger() {
        val uiState = AppUiState(
            bankBalanceCents = 10_000L,
            ledgerBalanceCents = 25_000L,
            cashLedgerCents = 15_000L,
            isBalanceReconciled = true,
            bankLedgerMismatch = true,
            driftCents = 5_000L,
        )

        assertEquals("Difference: \$50.00 app cash total above bank", balanceDifferenceLabel(uiState))
    }

    @Test
    fun reportsBankAboveTheAppCashTotalWhenTheDriftIsNegative() {
        val uiState = AppUiState(
            bankBalanceCents = 10_000L,
            ledgerBalanceCents = 12_000L,
            cashLedgerCents = 7_500L,
            isBalanceReconciled = true,
            bankLedgerMismatch = true,
            driftCents = -2_500L,
        )

        assertEquals("Difference: \$25.00 bank above app cash total", balanceDifferenceLabel(uiState))
    }
}
