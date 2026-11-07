package com.montecarlo.ledger.contract

import kotlinx.serialization.json.JsonElement

/**
 * Convenience facade: parse a canonical scenario, run the contract engine, and produce a canonical
 * result. The conformance runner and the emitter tests both go through this entry point.
 */
object ContractRunner {

    fun run(scenarioJson: String): ContractResult = run(ContractScenarioParser.parse(scenarioJson))

    fun run(scenarioElement: JsonElement): ContractResult = run(ContractScenarioParser.parse(scenarioElement))

    fun run(scenario: ContractScenario): ContractResult = execute(scenario, withDaily = false).first

    /**
     * Canonical result plus daily fan-chart percentiles computed from the same simulation paths.
     * The chart therefore visualizes the canonical model rather than a second engine.
     */
    fun runWithDaily(
        scenario: ContractScenario,
    ): Pair<ContractResult, List<ContractSimulation.ContractDailyPoint>> =
        execute(scenario, withDaily = true)

    private fun execute(
        scenario: ContractScenario,
        withDaily: Boolean,
    ): Pair<ContractResult, List<ContractSimulation.ContractDailyPoint>> {
        val baseEvents = ContractEngine.inWindowBaseEvents(scenario)
        val forecast = ContractEngine.forecast(
            startingBalanceCents = scenario.startingBalanceCents,
            orderedEvents = baseEvents,
            asOf = scenario.asOf,
        )
        val risk: ContractRiskResult?
        val daily: List<ContractSimulation.ContractDailyPoint>
        if (scenario.simulation != null) {
            if (withDaily) {
                val (riskBlock, points) = ContractSimulation.simulateWithDaily(
                    scenario = scenario,
                    baseEvents = baseEvents,
                    projectedLowPointCents = forecast.minimumBalanceCents,
                )
                risk = riskBlock
                daily = points
            } else {
                risk = ContractSimulation.simulate(
                    scenario = scenario,
                    baseEvents = baseEvents,
                    projectedLowPointCents = forecast.minimumBalanceCents,
                )
                daily = emptyList()
            }
        } else {
            risk = null
            daily = emptyList()
        }
        // Contract 2.0 (MCD-0026): the debt block is present iff the scenario declares liabilities.
        // It is deterministic and independent of the cash-ledger forecast and the simulation.
        val debt = scenario.liabilities?.let {
            ContractDebt.amortize(
                asOf = scenario.asOf,
                liabilities = it,
                strategy = scenario.debtStrategy,
                extraMonthlyPaymentCents = scenario.extraMonthlyPaymentCents,
            )
        }
        val result = ContractResult(
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
            debt = debt,
        )
        return result to daily
    }
}
