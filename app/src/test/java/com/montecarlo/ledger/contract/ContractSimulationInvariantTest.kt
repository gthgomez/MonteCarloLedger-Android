package com.montecarlo.ledger.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Invariants of the simulation/risk path on the (still `pending-generation`) stochastic fixtures.
 * These do not assert golden numbers; they prove the contract engine is deterministic and that the
 * risk block obeys the definitions in `contracts/risk.md`.
 */
class ContractSimulationInvariantTest {

    private fun fixture(name: String): JsonObject {
        val file = File(FixturePaths.contractRoot(), "fixtures/stochastic/$name.json")
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun runFixture(name: String): ContractResult {
        val scenario = fixture(name)["scenario"]!!
        return ContractRunner.run(scenario)
    }

    @Test
    fun seededSimulationIsReproducible() {
        val first = ContractResultEmitter.toJsonString(runFixture("seeded-reproducibility"))
        val second = ContractResultEmitter.toJsonString(runFixture("seeded-reproducibility"))
        assertEquals(first, second)
    }

    @Test
    fun riskBlockIsPresentAndPpmIsInRange() {
        val risk = runFixture("negative-probability").risk
        assertNotNull(risk)
        assertTrue(risk!!.negativeBalanceProbabilityPpm in 0..1_000_000)
    }

    @Test
    fun safeToSpendIsTheLowerTroughQuantileMinusReserve() {
        // safe-to-spend fixture: quantile 1/10, reserve 10000.
        val risk = runFixture("safe-to-spend").risk!!
        assertEquals(risk.minimumBalanceP10Cents - 10_000L, risk.safeToSpendCents)
    }

    @Test
    fun projectedLowPointIsTheDeterministicMinimum() {
        val scenarioJson = fixture("safe-to-spend")["scenario"]!!
        val scenario = ContractScenarioParser.parse(scenarioJson)
        val forecast = ContractEngine.forecast(
            startingBalanceCents = scenario.startingBalanceCents,
            orderedEvents = ContractEngine.inWindowBaseEvents(scenario),
            asOf = scenario.asOf,
        )
        val risk = ContractRunner.run(scenario).risk!!
        assertEquals(forecast.minimumBalanceCents, risk.projectedLowPointCents)
    }

    @Test
    fun percentileOrderIsMonotonic() {
        val risk = runFixture("income-variation").risk!!
        assertTrue(risk.minimumBalanceP10Cents <= risk.minimumBalanceP50Cents)
        assertTrue(risk.minimumBalanceP50Cents <= risk.minimumBalanceP90Cents)
        assertTrue(risk.endingBalanceP10Cents <= risk.endingBalanceP50Cents)
        assertTrue(risk.endingBalanceP50Cents <= risk.endingBalanceP90Cents)
    }
}
