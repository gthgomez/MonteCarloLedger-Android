package com.montecarlo.ledger.contract

import kotlinx.serialization.json.JsonElement

/**
 * Convenience facade: parse a canonical scenario, run the contract engine, and produce a canonical
 * result. The conformance runner and the emitter tests both go through this entry point.
 */
object ContractRunner {

    fun run(scenarioJson: String): ContractResult = run(ContractScenarioParser.parse(scenarioJson))

    fun run(scenarioElement: JsonElement): ContractResult = run(ContractScenarioParser.parse(scenarioElement))

    fun run(scenario: ContractScenario): ContractResult {
        val baseEvents = ContractEngine.inWindowBaseEvents(scenario)
        val forecast = ContractEngine.forecast(
            startingBalanceCents = scenario.startingBalanceCents,
            orderedEvents = baseEvents,
            asOf = scenario.asOf,
        )
        val risk = if (scenario.simulation != null) {
            ContractSimulation.simulate(
                scenario = scenario,
                baseEvents = baseEvents,
                projectedLowPointCents = forecast.minimumBalanceCents,
            )
        } else {
            null
        }
        return ContractResult(
            scenarioId = scenario.scenarioId,
            forecast = ContractForecastResult(
                minimumBalanceCents = forecast.minimumBalanceCents,
                minimumBalanceDate = forecast.minimumBalanceDate,
                endingBalanceCents = forecast.endingBalanceCents,
                firstNegativeDate = forecast.firstNegativeDate,
            ),
            risk = risk,
            // Echo the scenario's declared version so a 1.0 scenario stays byte-identical.
            contractVersion = scenario.contractVersion,
        )
    }
}
