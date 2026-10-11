package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * WS4 failing-first provenance guard.
 *
 * The dashboard displays an overdraft/negative-balance probability that is produced by a Monte Carlo
 * simulation whose variation ranges may be *default* assumptions, and whose probability has never
 * been validated out-of-sample. Before WS4 nothing in [com.montecarlo.ledger.AppUiState] said so in
 * a machine-readable way: the coarse `monteCarloBasisLabel` distinguished calibrated vs default
 * *inputs*, but the probability itself carried no unverified/insufficient-data marker anywhere.
 *
 * These assertions deliberately use only symbols that exist on the pre-fix (WS2) base so the
 * failure they produce is a clean assertion failure, not a compilation error. They fail on the
 * pre-fix base and pass once provenance is surfaced.
 */
class RiskProbabilityProvenanceTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun expense(description: String, cents: Long, date: String, category: String) =
        TransactionEntity(
            description = description,
            amount_cents = -cents,
            date = date,
            type = "expense",
            category = category,
        )

    private fun income(cents: Long, date: String) = TransactionEntity(
        description = "Paycheck",
        amount_cents = cents,
        date = date,
        type = "income",
        category = "income",
    )

    private fun pack(txns: List<TransactionEntity>) = ReportingPackage(
        incomes = emptyList(),
        payments = emptyList(),
        txns = txns,
        accounts = emptyList(),
        balanceState = LedgerRepository.BalanceState(bankBalanceCents = 0L, isReconciled = false),
        billOccurrences = emptyList(),
        assets = emptyList(),
        goals = emptyList(),
        categoryBudgets = emptyList(),
        debts = emptyList(),
        rules = emptyList(),
        dashboardConfig = DashboardConfig(),
    )

    /** All trust-signal text flattened, so we can assert a human-visible marker is present. */
    private fun trustText(state: com.montecarlo.ledger.AppUiState): String =
        state.trustSignals.joinToString(" | ") { "${it.label}=${it.value}: ${it.detail}" }

    /** Two months of history: enough to have a basis, below the calibrated floor (3 months). */
    private fun insufficientHistory(): List<TransactionEntity> = listOf(
        income(200_000L, "2026-07-01"),
        expense("Rent", 100_000L, "2026-07-02", "bills"),
        income(200_000L, "2026-08-01"),
        expense("Rent", 100_000L, "2026-08-02", "bills"),
    )

    /** Five distinct months: above [com.montecarlo.ledger.processing.MonteCarloCalibration.MIN_CALIBRATED_MONTHS]. */
    private fun calibratedHistory(): List<TransactionEntity> = buildList {
        for (month in 1..5) {
            add(income(200_000L, "2026-0$month-01"))
            add(expense("Rent", 100_000L, "2026-0$month-02", "bills"))
            add(expense("Groceries", 40_000L + month * 1_000L, "2026-0$month-10", "food"))
        }
    }

    @Test
    fun insufficientHistory_marksRiskProbabilityUnverified() = runBlocking {
        val derivation = DashboardDeriver().derive(pack(insufficientHistory()), today)
        val state = derivation.uiState
        val text = trustText(state)

        // Pre-fix this is the ONLY provenance signal the user gets, and it never says the probability
        // is unverified — it merely names the inputs as "default assumptions".
        assertTrue(
            "expected an explicit unverified/insufficient-data provenance marker, got trust signals: $text",
            text.contains("unverified", ignoreCase = true),
        )
    }

    @Test
    fun calibratedInputs_stillMarkRiskProbabilityUnverified() = runBlocking {
        val derivation = DashboardDeriver().derive(pack(calibratedHistory()), today)
        val state = derivation.uiState

        // Inputs are calibrated from real history...
        assertEquals(
            "Based on 5 months of your history",
            state.monteCarloBasisLabel,
        )
        // ...but the displayed probability has never been validated out-of-sample, and the UI must say so.
        assertTrue(
            "calibrated inputs must not imply a calibrated probability, got trust signals: ${trustText(state)}",
            trustText(state).contains("unverified", ignoreCase = true),
        )
    }
}
