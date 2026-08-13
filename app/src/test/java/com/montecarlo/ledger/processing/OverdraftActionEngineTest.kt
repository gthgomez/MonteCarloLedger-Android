package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverdraftActionEngineTest {

    @Test
    fun analyze_noRisk_returnsEmptyList() {
        val mcResult = MonteCarloResult(
            worst_10_balance_cents = 500_00L,
            median_balance_cents = 1000_00L,
            best_90_balance_cents = 2000_00L,
            probability_negative_pct = 0.0,
            runs = 500,
            negative_runs = 0,
            most_common_first_negative_date = null,
            negative_window_start = null,
            negative_window_end = null
        )

        val recommendations = OverdraftActionEngine.analyze(
            mcResult = mcResult,
            windows = emptyList(),
            events = emptyList()
        )

        assertTrue(recommendations.isEmpty())
    }

    @Test
    fun analyze_prePaydayBillCausesOverdraft_generatesRescheduleRecommendation() {
        val today = LocalDate.of(2026, 8, 1)
        val payday = LocalDate.of(2026, 8, 15)

        val mcResult = MonteCarloResult(
            worst_10_balance_cents = -50_00L,
            median_balance_cents = 100_00L,
            best_90_balance_cents = 500_00L,
            probability_negative_pct = 35.0,
            runs = 500,
            negative_runs = 175,
            most_common_first_negative_date = "2026-08-10",
            negative_window_start = "2026-08-10",
            negative_window_end = "2026-08-14"
        )

        val events = listOf(
            ForecastEvent(date = LocalDate.of(2026, 8, 10), description = "Car Insurance", amount_cents = 180_00L, type = "bill"),
            ForecastEvent(date = payday, description = "Paycheck", amount_cents = 1500_00L, type = "income")
        )

        val billOccurrences = listOf(
            BillOccurrenceEntity(id = 42, payment_id = 1, due_date = "2026-08-10", amount_cents = 180_00, is_paid = 0)
        )

        val recommendations = OverdraftActionEngine.analyze(
            mcResult = mcResult,
            windows = emptyList(),
            events = events,
            billOccurrences = billOccurrences,
        )

        val reschedule = recommendations.filterIsInstance<OverdraftRecommendation.RescheduleBill>().firstOrNull()
        assertTrue(reschedule != null)
        assertEquals(42, reschedule?.occurrenceId)
        assertEquals("Car Insurance", reschedule?.billName)
        assertEquals(LocalDate.of(2026, 8, 16), reschedule?.suggestedDueDate)
    }

    @Test
    fun analyze_savingsAssetExists_generatesAssetTransferRecommendation() {
        val today = LocalDate.of(2026, 8, 1)

        val mcResult = MonteCarloResult(
            worst_10_balance_cents = -100_00L,
            median_balance_cents = 50_00L,
            best_90_balance_cents = 200_00L,
            probability_negative_pct = 50.0,
            runs = 500,
            negative_runs = 250,
            most_common_first_negative_date = null,
            negative_window_start = null,
            negative_window_end = null
        )

        val assets = listOf(
            AssetEntity(id = 7, name = "Emergency Savings", type = "savings", balanceCents = 500_00, lastUpdated = "2026-08-01")
        )

        val recommendations = OverdraftActionEngine.analyze(
            mcResult = mcResult,
            windows = emptyList(),
            events = emptyList(),
            assets = assets,
        )

        val transfer = recommendations.filterIsInstance<OverdraftRecommendation.TransferFromAsset>().firstOrNull()
        assertTrue(transfer != null)
        assertEquals(7L, transfer?.assetId)
        assertEquals("Emergency Savings", transfer?.assetName)
        assertEquals(100_00L, transfer?.suggestedTransferCents)
    }

    @Test
    fun analyze_ignoresBillsThatAreAlreadyAfterPayday() {
        val payday = LocalDate.of(2026, 8, 15)
        val mcResult = MonteCarloResult(
            worst_10_balance_cents = -50_00L,
            median_balance_cents = 100_00L,
            best_90_balance_cents = 500_00L,
            probability_negative_pct = 35.0,
            runs = 500,
            negative_runs = 175,
            most_common_first_negative_date = "2026-08-10",
            negative_window_start = "2026-08-10",
            negative_window_end = "2026-08-14"
        )
        val events = listOf(
            ForecastEvent(date = LocalDate.of(2026, 8, 10), description = "Snack", amount_cents = 50_00L, type = "bill"),
            ForecastEvent(date = LocalDate.of(2026, 8, 20), description = "Rent", amount_cents = 2000_00L, type = "bill"),
            ForecastEvent(date = payday, description = "Paycheck", amount_cents = 1500_00L, type = "income"),
        )

        val recommendations = OverdraftActionEngine.analyze(
            mcResult = mcResult,
            windows = emptyList(),
            events = events,
        )

        val reschedule = recommendations.filterIsInstance<OverdraftRecommendation.RescheduleBill>().single()
        assertEquals("Snack", reschedule.billName)
    }

    @Test
    fun analyze_dailyCapSubtractsScheduledBillsFromTheWindow() {
        val window = CashFlowWindow(
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 11),
            startingBalanceCents = 1_000_00L,
            incomeCents = 0L,
            billCents = 1_200_00L,
            lowestBalanceCents = -200_00L,
            endingBalanceCents = -200_00L,
            safeToSpendCents = -200_00L,
            dailySafeSpendCents = 0L,
            shortfallCents = 200_00L,
            days = 10,
        )
        val mcResult = MonteCarloResult(
            worst_10_balance_cents = -200_00L,
            median_balance_cents = 0L,
            best_90_balance_cents = 100_00L,
            probability_negative_pct = 40.0,
            runs = 500,
            negative_runs = 200,
            most_common_first_negative_date = "2026-08-05",
            negative_window_start = "2026-08-05",
            negative_window_end = "2026-08-10"
        )

        val recommendations = OverdraftActionEngine.analyze(
            mcResult = mcResult,
            windows = listOf(window),
            events = emptyList(),
        )

        val cap = recommendations.filterIsInstance<OverdraftRecommendation.CapDailySpend>().single()
        assertEquals(0L, cap.suggestedDailyCapCents)
    }
}
