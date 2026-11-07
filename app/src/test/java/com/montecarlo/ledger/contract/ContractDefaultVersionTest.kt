package com.montecarlo.ledger.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * MCD-0027: a version-less scenario must evaluate as the latest released contract version on
 * every engine. The Python reference and the TypeScript port both default to 2.0; the Kotlin
 * engine previously *required* the field, making hand-written version-less scenarios engine-
 * dependent. These tests pin the aligned behaviour.
 */
class ContractDefaultVersionTest {

    private fun versionlessScenario() = """
        {
          "scenario_id": "versionless-probe",
          "as_of": "2026-11-09",
          "starting_balance_cents": 0,
          "horizon_days": 1,
          "events": []
        }
    """.trimIndent()

    @Test
    fun versionlessScenarioEvaluatesAsLatestReleasedVersion() {
        val scenario = ContractScenarioParser.parse(versionlessScenario())
        val result = ContractRunner.run(scenario)
        assertEquals("2.0", result.contractVersion)
    }

    @Test
    fun unsupportedVersionIsStillRejected() {
        val fromFuture = versionlessScenario().replace(
            "\"scenario_id\"",
            "\"contract_version\": \"3.0\", \"scenario_id\"",
        )
        assertThrows(ContractException::class.java) {
            ContractScenarioParser.parse(fromFuture)
        }
    }

    @Test
    fun explicitlyDeclaredVersionIsStillHonoured() {
        val v10 = versionlessScenario().replace(
            "\"scenario_id\"",
            "\"contract_version\": \"1.0\", \"scenario_id\"",
        )
        val result = ContractRunner.run(ContractScenarioParser.parse(v10))
        assertEquals("1.0", result.contractVersion)
    }
}
