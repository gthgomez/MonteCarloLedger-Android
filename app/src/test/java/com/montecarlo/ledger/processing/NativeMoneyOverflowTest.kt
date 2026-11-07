package com.montecarlo.ledger.processing

import com.montecarlo.ledger.util.MoneyArithmeticOverflow
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

/** Native fallback paths must fail loudly instead of wrapping a Long into a plausible number. */
class NativeMoneyOverflowTest {

    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun forecastAdditionOverflowThrows() {
        assertThrows(MoneyArithmeticOverflow::class.java) {
            ForecastEngine.calculateSafeToSpend(
                Long.MAX_VALUE,
                listOf(ForecastEvent(today, "income", 1L, "income")),
            )
        }
    }

    @Test
    fun forecastSubtractionUnderflowThrows() {
        assertThrows(MoneyArithmeticOverflow::class.java) {
            ForecastEngine.calculateSafeToSpend(
                Long.MIN_VALUE,
                listOf(ForecastEvent(today, "bill", 1L, "bill")),
            )
        }
    }

    @Test
    fun monteCarloRunOverflowThrowsInsteadOfReturningAResult() {
        assertThrows(MoneyArithmeticOverflow::class.java) {
            MonteCarloEngine(MonteCarloParams(runs = 1, seed = 1))
                .runSimulation(
                    Long.MAX_VALUE,
                    listOf(ForecastEvent(today, "income", 1L, "income")),
                    today,
                )
        }
    }

    @Test
    fun cashFlowWindowOverflowThrows() {
        assertThrows(MoneyArithmeticOverflow::class.java) {
            ForecastEngine.buildCashFlowWindows(
                Long.MAX_VALUE,
                listOf(ForecastEvent(today, "income", 1L, "income")),
                today,
                30,
            )
        }
    }
}
