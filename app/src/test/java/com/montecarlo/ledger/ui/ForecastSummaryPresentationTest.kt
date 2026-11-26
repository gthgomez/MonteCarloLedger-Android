package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.dashboard.DashboardDeriver
import com.montecarlo.ledger.dashboard.ReportingPackage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The forecast summary card must show the authoritative forecast summary, not a value rebuilt from
 * post-event rows. Rows exclude the opening balance and are empty when the window has no dated
 * events, which previously rendered a fabricated `$0`; a withheld calculation must render
 * "Unavailable" instead of the fail-closed neutral zero.
 */
class ForecastSummaryPresentationTest {

    private val today = LocalDate.of(2026, 1, 1)

    private fun pack(
        balanceCents: Long,
        incomes: List<IncomeEntity> = emptyList(),
    ) = ReportingPackage(
        incomes = incomes,
        payments = emptyList(),
        txns = emptyList(),
        accounts = emptyList(),
        balanceState = LedgerRepository.BalanceState(
            bankBalanceCents = balanceCents,
            isReconciled = true,
        ),
        billOccurrences = emptyList(),
        assets = emptyList(),
        goals = emptyList(),
        categoryBudgets = emptyList(),
        debts = emptyList(),
        rules = emptyList(),
        dashboardConfig = DashboardConfig(),
    )

    private fun oneTimeIncome(amountCents: Long = 100_000L) = IncomeEntity(
        id = 1,
        name = "One-time income",
        amount_cents = amountCents,
        frequency = "one-time",
        day_of_month = null,
        next_date = "2026-01-05",
    )

    private fun modelOf(state: AppUiState) = forecastSummaryModel(
        rows = state.forecastRows,
        lowestBalanceCents = state.forecastLowestBalanceCents,
        endingBalanceCents = state.forecastEndingBalanceCents,
        firstNegativeDateLabel = state.firstNegativeDateLabel,
        staleAsOf = forecastStaleAsOf(state.canonicalForecastStatus, state.canonicalForecastAsOf),
    )

    @Test
    fun openingBalanceIsShownWhenThereAreNoDatedEvents() = runBlocking {
        val state = DashboardDeriver()
            .derive(pack(balanceCents = 50_000L), today, contractForecastEnabled = true)
            .uiState

        assertEquals(CanonicalForecastStatus.Live, state.canonicalForecastStatus)
        assertTrue("the window legitimately has no dated events", state.forecastRows.isEmpty())
        assertEquals(50_000L, state.forecastLowestBalanceCents)
        assertEquals(50_000L, state.forecastEndingBalanceCents)

        val model = modelOf(state)
        assertTrue(model.isAvailable)
        assertFalse(model.hasDatedEvents)
        assertEquals(0, model.eventCount)
        assertEquals(50_000L, model.lowestBalanceCents)
        assertEquals(50_000L, model.endingBalanceCents)
        assertNull(model.staleLabel)
    }

    @Test
    fun openingBalanceRemainsTheLowPointWhenOnlyIncomeFollows() = runBlocking {
        val state = DashboardDeriver()
            .derive(
                pack(balanceCents = 50_000L, incomes = listOf(oneTimeIncome())),
                today,
                contractForecastEnabled = true,
            )
            .uiState

        assertEquals(CanonicalForecastStatus.Live, state.canonicalForecastStatus)
        assertEquals(listOf(LocalDate.of(2026, 1, 5)), state.forecastRows.map { it.date })
        assertEquals(50_000L, state.forecastLowestBalanceCents)
        assertEquals(150_000L, state.forecastEndingBalanceCents)

        val model = modelOf(state)
        assertTrue(model.isAvailable)
        assertTrue(model.hasDatedEvents)
        assertEquals(1, model.eventCount)
        assertEquals(50_000L, model.lowestBalanceCents)
        assertEquals(150_000L, model.endingBalanceCents)
        assertNull(model.staleLabel)
    }

    @Test
    fun withheldForecastIsUnavailableInsteadOfZero() = runBlocking {
        // A reconciled Long.MAX opening plus a dated income overflows the contract's checked money
        // arithmetic, so the canonical calculation is genuinely withheld with no retained result.
        val state = DashboardDeriver()
            .derive(
                pack(balanceCents = Long.MAX_VALUE, incomes = listOf(oneTimeIncome())),
                today,
                contractForecastEnabled = true,
            )
            .uiState

        assertEquals(CanonicalForecastStatus.Failed, state.canonicalForecastStatus)
        assertTrue(state.forecastRows.isEmpty())
        assertNull(state.forecastLowestBalanceCents)
        assertNull(state.forecastEndingBalanceCents)

        val model = modelOf(state)
        assertFalse(model.isAvailable)
        assertNull(model.lowestBalanceCents)
        assertNull(model.endingBalanceCents)
        assertEquals(0, model.eventCount)
        assertNull(model.staleLabel)
    }

    @Test
    fun staleForecastStaysAvailableAndCarriesItsTimestamp() = runBlocking {
        val deriver = DashboardDeriver()
        val healthy = deriver.derive(pack(balanceCents = 50_000L), today, contractForecastEnabled = true).uiState
        val stale = deriver
            .derive(
                pack(balanceCents = Long.MAX_VALUE, incomes = listOf(oneTimeIncome())),
                today,
                contractForecastEnabled = true,
            )
            .uiState

        assertEquals(CanonicalForecastStatus.Live, healthy.canonicalForecastStatus)
        assertEquals(CanonicalForecastStatus.Stale, stale.canonicalForecastStatus)
        assertEquals(healthy.forecastLowestBalanceCents, stale.forecastLowestBalanceCents)
        assertEquals(healthy.forecastEndingBalanceCents, stale.forecastEndingBalanceCents)

        val asOf = stale.canonicalForecastAsOf
        assertNotNull(asOf)
        val model = modelOf(stale)
        assertTrue(model.isAvailable)
        assertEquals("Previous forecast — calculated $asOf", model.staleLabel)
        assertNull(modelOf(healthy).staleLabel)
        assertNull(forecastStaleAsOf(CanonicalForecastStatus.Live, asOf))
    }
}
