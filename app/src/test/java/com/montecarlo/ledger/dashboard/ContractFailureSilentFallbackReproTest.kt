package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractErrorCode
import com.montecarlo.ledger.contract.ContractException
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.processing.MonteCarloCalibrator
import com.montecarlo.ledger.processing.RecurringDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * WS2 failing-first reproduction of the SILENT canonical-calculation fallback.
 *
 * When `contractForecastEnabled` is true the canonical contract engine must be the *only* source of
 * the dashboard headline numbers. A genuine contract failure (a real `MONEY_OVERFLOW`, not a mock)
 * used to be swallowed by `adoptContract`'s `runCatching { }.getOrNull()` in [DashboardDeriver], so
 * the headline numbers silently switched to the legacy `ForecastEngine` / `MonteCarloEngine`.
 *
 * These tests are deliberately written against ONLY pre-existing product API, so the file compiles
 * on the unmodified base and *fails* there — the failure output (`Actual: -9223372036854755809`,
 * the wrapped native value) is what proves the silent substitution. After the fix they pass.
 */
class ContractFailureSilentFallbackReproTest {

    private val today = ContractFailureFixtures.today

    /**
     * Proves the failure is genuine: the contract path really throws for the failing pack, so no
     * mock is involved and the reproduction cannot be dismissed as a contrived seam.
     */
    @Test
    fun failingPackGenuinelyThrowsContractOverflow() {
        val pack = ContractFailureFixtures.failingPack()
        val calibration = MonteCarloCalibrator.calibrate(
            transactions = pack.txns,
            today = today,
            recurringPatterns = RecurringDetector.detect(pack.txns).map { it.pattern }.toSet(),
        )
        val scenario = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = ContractFailureFixtures.seedOf(pack),
                asOf = today,
                incomes = pack.incomes,
                payments = pack.payments,
                billOccurrences = pack.billOccurrences,
                simulation = ContractScenarioBridge.simulationParams(calibration),
            )
        )
        val thrown = assertThrows(ContractException::class.java) { ContractRunner.run(scenario) }
        assertEquals(ContractErrorCode.MONEY_OVERFLOW, thrown.code)
    }

    /**
     * (b) On a genuine contract failure the headline safe-to-spend must NOT silently become the
     * native-engine value; it must keep the last known valid canonical value.
     */
    @Test
    fun contractFailureDoesNotSubstituteNativeSafeToSpend() = runBlocking {
        val deriver = DashboardDeriver()
        val healthy = deriver.derive(ContractFailureFixtures.healthyPack(), today, contractForecastEnabled = true).uiState
        val failing = deriver.derive(ContractFailureFixtures.failingPack(), today, contractForecastEnabled = true).uiState

        val nativeFailing = ContractFailureFixtures.nativeSafeToSpend(ContractFailureFixtures.failingPack())
        assertNotEquals(
            "headline safe-to-spend must not be the native-engine value after a contract failure",
            nativeFailing,
            failing.safeToSpendCents,
        )
        assertEquals(
            "headline safe-to-spend must retain the last known valid canonical value",
            healthy.safeToSpendCents,
            failing.safeToSpendCents,
        )
    }

    /** (c) The last known valid canonical Monte Carlo risk is retained after a prior success. */
    @Test
    fun contractFailureRetainsLastKnownCanonicalMonteCarlo() = runBlocking {
        val deriver = DashboardDeriver()
        val healthy = deriver.derive(ContractFailureFixtures.healthyPack(), today, contractForecastEnabled = true).uiState
        val failing = deriver.derive(ContractFailureFixtures.failingPack(), today, contractForecastEnabled = true).uiState

        val nativeFailing = ContractFailureFixtures.nativeMonteCarlo(ContractFailureFixtures.failingPack())
        assertNotEquals(
            "headline Monte Carlo risk must not be the native-engine value after a contract failure",
            nativeFailing.median_balance_cents,
            failing.monteCarlo50thCents,
        )
        assertEquals(
            "retained canonical Monte Carlo median",
            healthy.monteCarlo50thCents,
            failing.monteCarlo50thCents,
        )
        assertEquals(
            "retained canonical negative-balance probability",
            healthy.probabilityNegativePct,
            failing.probabilityNegativePct,
            1e-9,
        )
    }
}
