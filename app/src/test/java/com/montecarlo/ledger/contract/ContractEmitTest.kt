package com.montecarlo.ledger.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Emits the canonical result of every frozen fixture to `build/contract-results/<scenario_id>.json`
 * so the language-neutral comparer (`MonteCarlo-Ledger/tools/conformance/compare.py`) can compare
 * Python and Kotlin output against the fixture and against each other. This test computes nothing
 * financial; it only serializes [ContractRunner] output.
 */
class ContractEmitTest {

    @Test
    fun emitAllFrozenFixtures() {
        val contractRoot = FixturePaths.contractRoot()
        val schema = Json.parseToJsonElement(
            File(contractRoot, "schemas/scenario.schema.json").readText()
        ).jsonObject
        val validator = MiniJsonSchema(schema)

        val outDir = File("build/contract-results").apply { mkdirs() }
        // Clear stale results so a removed fixture cannot linger.
        outDir.listFiles()?.forEach { it.delete() }

        val fixturesDir = File(contractRoot, "fixtures")
        val fixtures = fixturesDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.path }
            .toList()

        var emitted = 0
        for (fixture in fixtures) {
            val doc = Json.parseToJsonElement(fixture.readText()).jsonObject
            val scenario = doc["scenario"] ?: continue
            val status = (doc["expected_status"] as? JsonPrimitive)?.content ?: "frozen"
            if (status == "pending-generation") continue

            val id = (scenario.jsonObject["scenario_id"] as? JsonPrimitive)?.content
                ?: fixture.nameWithoutExtension

            val text: String = if (validator.validate(scenario).isNotEmpty()) {
                "{\"error\":\"SCHEMA_INVALID\"}"
            } else {
                try {
                    ContractResultEmitter.toJsonString(ContractRunner.run(scenario))
                } catch (e: ContractException) {
                    "{\"error\":\"${e.code.name}\"}"
                }
            }
            File(outDir, "$id.json").writeText(text)
            emitted++
        }

        assertTrue("expected to emit fixtures, got $emitted", emitted >= 20)
        println("Emitted $emitted canonical results to ${outDir.absolutePath}")
    }
}
