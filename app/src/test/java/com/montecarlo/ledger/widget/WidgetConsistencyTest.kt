package com.montecarlo.ledger.widget

import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.dashboard.DashboardDeriver
import com.montecarlo.ledger.dashboard.ReportingPackage
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.processing.BalanceSeedResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Widget/dashboard consistency: the home-screen widget must derive its headline numbers from the
 * same cash-routing and simulation-calibration basis as the dashboard, so one surface can never
 * quietly disagree with the other for the same ledger.
 */
class WidgetConsistencyTest {

    private val today: LocalDate = LocalDate.of(2026, 1, 1)

    private fun account(id: Long, type: String) = AccountEntity(
        id = id,
        name = "Account $id",
        type = type,
        balanceCents = 0L,
        lastUpdated = "2026-01-01",
    )

    private fun txn(
        amount: Long,
        date: String = "2026-01-01",
        accountId: Long? = null,
        type: String = "expense",
        description: String = "Coffee",
    ) = TransactionEntity(
        description = description,
        amount_cents = amount,
        date = date,
        type = type,
        category = type,
        account_id = accountId,
    )

    @Test
    fun creditAccountChargesAreExcludedFromTheWidgetCashSeed() {
        val accounts = listOf(account(id = 1, type = "checking"), account(id = 2, type = "credit"))
        val txns = listOf(
            txn(amount = -50_000L, accountId = 1), // checking card charge: moves cash
            txn(amount = -20_000L, accountId = 2), // credit charge: lives on the liability
            txn(amount = -5_000L),                 // no account: legacy cash
        )

        assertEquals(-55_000L, widgetCashLedgerCents(txns, accounts))
    }

    @Test
    fun creditChargeMustNotDifferFromDashboardSeed() {
        // Parity check with the seed path the dashboard uses (BalanceSeedResolver over the cash
        // ledger): for the same data both surfaces must resolve the identical forecast seed. The
        // credit charge (-20k) is excluded, so the unreconciled seed anchors to the cash ledger
        // (100k), not to the bank figure and not to the all-transactions sum (80k).
        val accounts = listOf(account(id = 2, type = "credit"))
        val txns = listOf(txn(amount = -20_000L, accountId = 2), txn(amount = 100_000L, accountId = null, type = "income"))
        val cash = widgetCashLedgerCents(txns, accounts)
        val seed = BalanceSeedResolver.resolve(cash, bankBalanceCents = 80_000L, isReconciled = false)
        assertEquals(100_000L, seed)
    }

    @Test
    fun widgetCalibratesFromHistoryLikeTheDashboard() {
        // Three distinct months of history: enough for the calibrator to report a calibrated
        // basis (MIN_CALIBRATED_MONTHS = 3), which the widget must inherit — not silently use
        // the hardcoded defaults the uncalibrated widget used to use.
        val history = listOf(
            txn(220_000L, "2025-11-05", type = "income", description = "Paycheck"),
            txn(225_000L, "2025-12-05", type = "income", description = "Paycheck"),
            txn(230_000L, "2026-01-05", type = "income", description = "Paycheck"),
        )

        val calibratedForecast = deriveWidgetForecast(
            today = today,
            contractForecastEnabled = true,
            forecastSeedCents = 200_000L,
            incomes = listOf(
                IncomeEntity(
                    id = 1,
                    name = "Paycheck",
                    amount_cents = 220_000L,
                    frequency = "Monthly",
                    day_of_month = 5,
                    next_date = "2026-02-05",
                )
            ),
            payments = listOf(
                PaymentEntity(
                    id = 1,
                    name = "Rent",
                    amount_cents = 60_000L,
                    frequency = "Monthly",
                    day_of_month = 3,
                    next_date = "2026-01-03",
                )
            ),
            occurrences = emptyList(),
            txns = history,
            rules = emptyList(),
        )

        assertTrue(
            "with >=3 months of history the widget must report a calibrated basis",
            calibratedForecast.calibrated,
        )
        assertEquals(CanonicalForecastStatus.Live, calibratedForecast.status)
        assertTrue(calibratedForecast.available)

        // A brand-new ledger has no basis and must not pretend otherwise.
        val freshForecast = deriveWidgetForecast(
            today = today,
            contractForecastEnabled = true,
            forecastSeedCents = 200_000L,
            incomes = emptyList(),
            payments = emptyList(),
            occurrences = emptyList(),
            txns = emptyList(),
            rules = emptyList(),
        )
        assertFalse("an empty history must not be labelled calibrated", freshForecast.calibrated)
    }

    @Test
    fun widgetAndDashboardReachTheSameConclusionForTheSameLedger() = runBlocking {
        // End-to-end parity, not "both use the same helper": run BOTH real pipelines on one
        // synthetic ledger and compare the user-visible safe-to-spend and overdraft probability.
        // This fails if either surface changes its run count, seed, horizon or calibration basis.
        val history = listOf(
            txn(220_000L, "2025-11-05", type = "income", description = "Paycheck"),
            txn(225_000L, "2025-12-05", type = "income", description = "Paycheck"),
            txn(230_000L, "2026-01-05", type = "income", description = "Paycheck"),
        )
        val incomes = listOf(
            IncomeEntity(
                id = 1,
                name = "Paycheck",
                amount_cents = 220_000L,
                frequency = "Monthly",
                day_of_month = 5,
                next_date = "2026-02-05",
            )
        )
        val payments = listOf(
            PaymentEntity(
                id = 1,
                name = "Rent",
                amount_cents = 60_000L,
                frequency = "Monthly",
                day_of_month = 3,
                next_date = "2026-01-03",
            )
        )
        val pack = ReportingPackage(
            incomes = incomes,
            payments = payments,
            txns = history,
            accounts = emptyList(),
            balanceState = LedgerRepository.BalanceState(
                bankBalanceCents = 0L,
                isReconciled = false,
            ),
            billOccurrences = emptyList(),
            assets = emptyList(),
            goals = emptyList(),
            categoryBudgets = emptyList(),
            debts = emptyList(),
            rules = emptyList(),
            dashboardConfig = DashboardConfig(),
        )
        val dashboard = DashboardDeriver().derive(pack, today)
        val seed = BalanceSeedResolver.resolve(
            widgetCashLedgerCents(history, emptyList()),
            bankBalanceCents = 0L,
            isReconciled = false,
        )
        val widget = deriveWidgetForecast(
            today = today,
            contractForecastEnabled = true,
            forecastSeedCents = seed,
            incomes = incomes,
            payments = payments,
            occurrences = emptyList(),
            txns = history,
            rules = emptyList(),
        )

        assertTrue(widget.available)
        assertEquals(
            "the widget and dashboard must agree on safe-to-spend for the same ledger",
            dashboard.uiState.safeToSpendCents,
            widget.safeToSpendCents,
        )
        assertEquals(
            "the widget and dashboard must agree on the overdraft probability",
            dashboard.uiState.probabilityNegativePct,
            widget.probabilityNegativePct,
            1e-9,
        )
    }
}
