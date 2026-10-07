package com.montecarlo.ledger.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MC-09: the Kotlin test-side validator must enforce the keywords the scenario schema actually
 * uses, so it does not diverge from the Python reference (which uses `jsonschema`).
 */
class MiniJsonSchemaTest {

    private fun validator(schema: String) =
        MiniJsonSchema(Json.parseToJsonElement(schema).jsonObject)

    @Test
    fun enforcesMinimumAndMaximum() {
        val schema = """{"type":"object","properties":{"v":{"type":"integer","minimum":1,"maximum":5}}}"""
        val v = validator(schema)
        assertTrue(v.validate(Json.parseToJsonElement("""{"v":6}""")).isNotEmpty())
        assertTrue(v.validate(Json.parseToJsonElement("""{"v":0}""")).isNotEmpty())
        assertTrue(v.validate(Json.parseToJsonElement("""{"v":5}""")).isEmpty())
    }

    @Test
    fun enforcesMinLength() {
        val schema = """{"type":"object","properties":{"s":{"type":"string","minLength":1}}}"""
        val v = validator(schema)
        assertTrue(v.validate(Json.parseToJsonElement("""{"s":""}""")).isNotEmpty())
        assertTrue(v.validate(Json.parseToJsonElement("""{"s":"x"}""")).isEmpty())
    }

    @Test
    fun unknownKeywordsAreIgnoredNotRejected() {
        // Documents the deliberate limitation that motivates the MC-09 engine-level rule: `if`/`then`
        // (and `format`) are not implemented, so the contract must not rely on them for parity.
        val schema = """{"if":{"type":"object"},"then":{"required":["missing"]},"type":"object"}"""
        assertTrue(validator(schema).validate(Json.parseToJsonElement("""{}""")).isEmpty())
    }
}
