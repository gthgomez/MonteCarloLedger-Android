package com.montecarlo.ledger.sync

import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject

/** Snapshot-local canonical identifiers are kept separate from Room keys. */
data class CanonicalIdMapping(
    val localToCanonical: Map<String, Map<Int, CanonicalValue>> = emptyMap(),
) {
    data class CanonicalValue(val value: String, val isString: Boolean) {
        fun element(): JsonPrimitive =
            if (isString) JsonPrimitive(value) else JsonPrimitive(value.toLong())
    }

    private val canonicalToLocal = localToCanonical.mapValues { (_, rows) ->
        rows.entries.associate { (local, canonical) -> canonical.value to local }
    }

    fun canonicalElement(kind: String, localId: Int): JsonPrimitive =
        canonicalElement(kind, localId.toLong())

    fun canonicalElement(kind: String, localId: Long): JsonPrimitive {
        val mapped = if (localId in 1L..Int.MAX_VALUE.toLong()) {
            localToCanonical[kind]?.get(localId.toInt())
        } else null
        if (mapped != null) return mapped.element()
        var label = localId.toString()
        while (canonicalToLocal[kind]?.containsKey(label) == true) {
            label = "local-$kind-$label"
        }
        return if (kind == "debts" || label != localId.toString()) JsonPrimitive(label) else JsonPrimitive(localId)
    }

    fun local(kind: String, canonicalId: String): Int? = canonicalToLocal[kind]?.get(canonicalId)

    fun toJson(): String = JSONObject().apply {
        localToCanonical.toSortedMap().forEach { (kind, rows) ->
            put(kind, JSONObject().apply {
                rows.toSortedMap().forEach { (local, canonical) ->
                    put(local.toString(), if (canonical.isString) canonical.value else canonical.value.toLong())
                }
            })
        }
    }.toString()

    companion object {
        fun fromJson(value: String?): CanonicalIdMapping {
            if (value.isNullOrBlank()) return CanonicalIdMapping()
            val root = JSONObject(value)
            val result = linkedMapOf<String, Map<Int, CanonicalValue>>()
            root.keys().forEach { kind ->
                val rows = root.getJSONObject(kind)
                val mapped = linkedMapOf<Int, CanonicalValue>()
                val seen = mutableSetOf<String>()
                rows.keys().forEach { key ->
                    val local = key.toIntOrNull()
                    require(local != null && local > 0) { "Invalid local id in vault mapping" }
                    val canonical = readId(rows.get(key))
                    require(seen.add(canonical.value)) { "Duplicate canonical id in vault mapping" }
                    require(mapped.put(local, canonical) == null) { "Duplicate local id in vault mapping" }
                }
                result[kind] = mapped
            }
            return CanonicalIdMapping(result)
        }

        fun readId(value: Any?): CanonicalValue = when (value) {
            is Int, is Long -> CanonicalValue(value.toString(), false)
            is String -> {
                require(value.isNotEmpty()) { "A vault id must not be empty" }
                CanonicalValue(value, true)
            }
            else -> throw IllegalArgumentException("A vault id must be a string or integer")
        }

        /** Prefer native keys when possible and allocate the remaining keys reproducibly. */
        fun assign(rows: List<CanonicalValue>): Map<Int, CanonicalValue> {
            val assigned = mutableMapOf<Int, CanonicalValue>()
            val deferred = mutableListOf<CanonicalValue>()
            val seen = mutableSetOf<String>()
            for (row in rows.sortedBy { it.value }) {
                require(seen.add(row.value)) { "Duplicate vault identifier ${row.value}" }
                val native = row.value.toIntOrNull()
                    ?.takeIf { it > 0 && it < Int.MAX_VALUE && it.toString() == row.value }
                if (native != null && native !in assigned) assigned[native] = row
                else deferred.add(row)
            }
            var next = 1
            for (row in deferred) {
                while (next in assigned) next++
                assigned[next++] = row
            }
            return assigned
        }
    }
}
