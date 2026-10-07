package com.montecarlo.ledger.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A deliberately small JSON Schema (2020-12 subset) validator.
 *
 * Motivated by the contract's directive that the conformance runner must validate the scenario
 * against `scenario.schema.json` without pulling a heavy dependency into a proprietary app. It
 * supports exactly the keywords that schema uses: `$ref`/`$defs`, `type`, `required`,
 * `additionalProperties: false`, `properties`, `items`, `enum`, `const`, and the numeric/length
 * bounds `minimum`/`maximum`/`exclusiveMinimum`/`exclusiveMaximum`/`minLength`/`maxLength`.
 *
 * Unknown keywords (e.g. `if`/`then`/`format`) are ignored, not rejected — the contract therefore
 * must not rely on them for cross-engine parity (see MC-09).
 */
class MiniJsonSchema(private val root: JsonObject) {

    fun validate(instance: JsonElement): List<String> = validateAt(root, instance, "$")

    private fun validateAt(schema: JsonObject, instance: JsonElement, path: String): List<String> {
        val resolved = (schema["\$ref"] as? JsonPrimitive)?.content?.let { resolveRef(it) } ?: schema
        val errors = mutableListOf<String>()

        val typeSpec = resolved["type"]
        if (typeSpec != null) {
            val types = when (typeSpec) {
                is JsonPrimitive -> listOf(typeSpec.content)
                is JsonArray -> typeSpec.mapNotNull { (it as? JsonPrimitive)?.content }
                else -> emptyList()
            }
            if (types.isNotEmpty() && types.none { matchesType(it, instance) }) {
                errors += "$path: expected type $types, got ${describe(instance)}"
                // A type mismatch makes deeper checks meaningless.
                return errors
            }
        }

        resolved["const"]?.let { const ->
            if (instance != const) errors += "$path: must equal $const, got $instance"
        }

        resolved["enum"]?.let { enumSpec ->
            val options = (enumSpec as? JsonArray)?.toList().orEmpty()
            if (options.isNotEmpty() && instance !in options) {
                errors += "$path: $instance is not one of ${options.joinToString { it.toString() }}"
            }
        }

        when (instance) {
            is JsonObject -> validateObject(resolved, instance, path, errors)
            is JsonArray -> {
                val items = resolved["items"] as? JsonObject
                if (items != null) {
                    instance.forEachIndexed { index, child ->
                        errors += validateAt(items, child, "$path[$index]")
                    }
                }
            }
            is JsonPrimitive -> {
                numericBound(resolved, instance, "minimum", path, errors) { v, b -> v < b }
                numericBound(resolved, instance, "maximum", path, errors) { v, b -> v > b }
                numericBound(resolved, instance, "exclusiveMinimum", path, errors) { v, b -> v <= b }
                numericBound(resolved, instance, "exclusiveMaximum", path, errors) { v, b -> v >= b }
                lengthBound(resolved, instance, "minLength", path, errors) { n, b -> n < b }
                lengthBound(resolved, instance, "maxLength", path, errors) { n, b -> n > b }
            }
        }
        return errors
    }

    private fun numericBound(
        schema: JsonObject,
        instance: JsonPrimitive,
        keyword: String,
        path: String,
        errors: MutableList<String>,
        fails: (value: Long, bound: Long) -> Boolean,
    ) {
        val bound = (schema[keyword] as? JsonPrimitive)?.longOrNull ?: return
        if (instance.isString) return
        val value = instance.longOrNull ?: return
        if (fails(value, bound)) errors += "$path: $value violates $keyword $bound"
    }

    private fun lengthBound(
        schema: JsonObject,
        instance: JsonPrimitive,
        keyword: String,
        path: String,
        errors: MutableList<String>,
        fails: (length: Long, bound: Long) -> Boolean,
    ) {
        val bound = (schema[keyword] as? JsonPrimitive)?.longOrNull ?: return
        if (!instance.isString) return
        val length = instance.content.length.toLong()
        if (fails(length, bound)) errors += "$path: length $length violates $keyword $bound"
    }

    private fun validateObject(
        schema: JsonObject,
        instance: JsonObject,
        path: String,
        errors: MutableList<String>,
    ) {
        (schema["required"] as? JsonArray)?.forEach { req ->
            val key = (req as? JsonPrimitive)?.content ?: return@forEach
            if (!instance.containsKey(key)) errors += "$path: missing required property '$key'"
        }

        val properties = schema["properties"] as? JsonObject
        val additional = schema["additionalProperties"]
        if (properties != null) {
            properties.forEach { (key, childSchema) ->
                val child = instance[key] ?: return@forEach
                errors += validateAt(childSchema as JsonObject, child, "$path.$key")
            }
            if (additional is JsonPrimitive && additional.content == "false") {
                (instance.keys - properties.keys).forEach { extra ->
                    errors += "$path: additional property '$extra' is not allowed"
                }
            }
        }
    }

    private fun resolveRef(ref: String): JsonObject {
        require(ref.startsWith("#/")) { "unsupported \$ref: $ref" }
        var current: JsonElement = root
        for (segment in ref.removePrefix("#/").split("/")) {
            val obj = current as? JsonObject ?: error("cannot resolve \$ref $ref")
            current = obj[segment] ?: error("cannot resolve \$ref $ref (missing $segment)")
        }
        return current as? JsonObject ?: error("\$ref $ref is not an object")
    }

    private fun matchesType(type: String, value: JsonElement): Boolean = when (type) {
        "object" -> value is JsonObject
        "array" -> value is JsonArray
        "string" -> (value as? JsonPrimitive)?.isString == true
        "integer" -> {
            val p = value as? JsonPrimitive
            p != null && !p.isString && p.longOrNull != null
        }
        "null" -> value is JsonNull
        "boolean" -> (value as? JsonPrimitive)?.let { it.content == "true" || it.content == "false" } == true
        else -> true
    }

    private fun describe(value: JsonElement): String = when (value) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonNull -> "null"
        is JsonPrimitive -> if (value.isString) "string" else "number"
    }
}
