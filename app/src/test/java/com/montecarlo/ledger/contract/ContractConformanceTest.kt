package com.montecarlo.ledger.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JUnit conformance runner for the vendored golden corpus.
 *
 * For each fixture it:
 * 1. validates the scenario against the vendored `scenario.schema.json` (via [MiniJsonSchema]),
 * 2. runs the contract engine,
 * 3. deep-diffs the emitted canonical result against `expected`,
 * 4. for `expected.error` fixtures asserts the matching error code,
 * 5. reports `pending-generation` fixtures without asserting.
 */
class ContractConformanceTest {

    @Test
    fun allGoldenFixturesConform() {
        val contractRoot = FixturePaths.contractRoot()
        val schema = Json.parseToJsonElement(
            File(contractRoot, "schemas/scenario.schema.json").readText()
        ).jsonObject
        val validator = MiniJsonSchema(schema)

        val fixturesDir = File(contractRoot, "fixtures")
        val fixtures = fixturesDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.path }
            .toList()

        assertTrue("no golden fixtures found under ${fixturesDir.absolutePath}", fixtures.isNotEmpty())

        val failures = mutableListOf<String>()
        var passed = 0
        var pending = 0

        for (fixture in fixtures) {
            val label = fixture.relativeTo(fixturesDir).path
            val doc = Json.parseToJsonElement(fixture.readText()).jsonObject
            val scenarioElement = doc["scenario"]
                ?: run { failures += "$label: fixture has no 'scenario'"; continue }
            val expected = doc["expected"]
            val status = (doc["expected_status"] as? JsonPrimitive)?.content ?: "frozen"

            if (status == "pending-generation" || expected == null || expected is JsonNull) {
                pending++
                continue
            }

            val schemaErrors = validator.validate(scenarioElement)
            val expectedError = expected.objectString("error")

            if (schemaErrors.isNotEmpty()) {
                if (expectedError == "SCHEMA_INVALID") {
                    passed++
                } else {
                    failures += "$label: unexpected schema errors: ${schemaErrors.joinToString("; ")}"
                }
                continue
            }

            var produced: ContractResult? = null
            var code: String? = null
            try {
                produced = ContractRunner.run(scenarioElement)
            } catch (e: ContractException) {
                code = e.code.name
            }

            if (expectedError != null) {
                if (code == expectedError) {
                    passed++
                } else {
                    failures += "$label: expected error $expectedError, got ${code ?: "success"}"
                }
                continue
            }

            if (code != null || produced == null) {
                failures += "$label: engine threw $code"
                continue
            }

            val actual = ContractResultEmitter.toJsonElement(produced)
            val diffs = deepDiff(expected, actual)
            if (diffs.isEmpty()) {
                passed++
            } else {
                failures += "$label: ${diffs.joinToString("; ")}"
            }
        }

        println(
            "Contract conformance: $passed passed, $pending pending-generation, " +
                "${failures.size} failed (of ${fixtures.size} fixtures)"
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun errorFixturesProduceTheExpectedCodes() {
        val contractRoot = FixturePaths.contractRoot()
        val schema = Json.parseToJsonElement(
            File(contractRoot, "schemas/scenario.schema.json").readText()
        ).jsonObject
        val validator = MiniJsonSchema(schema)
        val invalidDir = File(contractRoot, "fixtures/invalid")
        val fixtures = invalidDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            ?: emptyList()

        assertTrue("no invalid fixtures found", fixtures.isNotEmpty())

        for (fixture in fixtures) {
            val doc = Json.parseToJsonElement(fixture.readText()).jsonObject
            val scenario = doc["scenario"] ?: continue
            val expectedCode = doc["expected"]?.objectString("error") ?: continue

            val schemaErrors = validator.validate(scenario)
            val actualCode: String = if (schemaErrors.isNotEmpty()) {
                "SCHEMA_INVALID"
            } else {
                try {
                    ContractRunner.run(scenario)
                    "NO_ERROR"
                } catch (e: ContractException) {
                    e.code.name
                }
            }
            assertTrue(
                "${fixture.name}: expected $expectedCode, got $actualCode",
                expectedCode == actualCode,
            )
        }
    }

    private fun JsonElement.objectString(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun deepDiff(expected: JsonElement, actual: JsonElement, path: String = ""): List<String> {
        val diffs = mutableListOf<String>()
        when {
            expected is JsonObject && actual is JsonObject -> {
                for (key in (expected.keys + actual.keys).sorted()) {
                    val child = if (path.isEmpty()) key else "$path.$key"
                    val e = expected[key]
                    val a = actual[key]
                    when {
                        e == null -> diffs += "$child: unexpected in actual ($a)"
                        a == null -> diffs += "$child: missing in actual (expected $e)"
                        else -> diffs += deepDiff(e, a, child)
                    }
                }
            }
            expected is JsonArray && actual is JsonArray -> {
                if (expected.size != actual.size) {
                    diffs += "${path.ifEmpty { "<root>" }}: length ${actual.size} != expected ${expected.size}"
                }
                for (i in 0 until minOf(expected.size, actual.size)) {
                    diffs += deepDiff(expected[i], actual[i], "$path[$i]")
                }
            }
            expected != actual -> diffs += "${path.ifEmpty { "<root>" }}: $actual != expected $expected"
        }
        return diffs
    }
}

/** Locates the vendored contract tree from the Gradle test working directory. */
object FixturePaths {
    fun contractRoot(): File {
        val candidates = listOf(
            File("src/test/resources/contract"),
            File("app/src/test/resources/contract"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error(
                "vendored contract not found; cwd=${File(".").absolutePath}; tried $candidates"
            )
    }
}
