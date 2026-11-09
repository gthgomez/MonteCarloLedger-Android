package com.montecarlo.ledger.sync

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Content fingerprint aligned with Python `monte_carlo_ledger/sync.py::_snapshot_fingerprint`:
 * SHA-256 of canonical JSON with `updated_at` removed and object keys sorted.
 */
object VaultSnapshotFingerprint {
    fun ofSnapshotJson(json: String): String {
        val root = JSONObject(json)
        root.remove("updated_at")
        val canonical = canonicalValue(root)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = value.keys().asSequence().toList().sorted()
            buildString {
                append('{')
                keys.forEachIndexed { index, key ->
                    if (index > 0) append(',')
                    append(JSONObject.quote(key))
                    append(':')
                    append(canonicalValue(value.get(key)))
                }
                append('}')
            }
        }
        is JSONArray -> buildString {
            append('[')
            for (index in 0 until value.length()) {
                if (index > 0) append(',')
                append(canonicalValue(value.get(index)))
            }
            append(']')
        }
        is Boolean -> if (value) "true" else "false"
        is Number -> canonicalNumber(value)
        is String -> JSONObject.quote(value)
        else -> JSONObject.quote(value.toString())
    }

    private fun canonicalNumber(value: Number): String = when (value) {
        is Int, is Long, is Short, is Byte -> value.toLong().toString()
        else -> {
            val asDouble = value.toDouble()
            if (asDouble == asDouble.toLong().toDouble()) asDouble.toLong().toString() else value.toString()
        }
    }
}
