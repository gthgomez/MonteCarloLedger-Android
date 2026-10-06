package com.montecarlo.ledger.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Guards the vendored contract snapshot against silent drift.
 *
 * `contract-pin.json` records the SHA-256 of every file under `contracts/`, `schemas/`, and
 * `fixtures/`. If someone edits or upgrades the snapshot without regenerating the pin, this test
 * fails. Extra on-disk files under those trees are also a failure, so the pin is exhaustive.
 */
class ContractPinTest {

    private val pinnedRoots = listOf("contracts", "schemas", "fixtures")

    @Test
    fun vendoredFilesMatchThePin() {
        val root = FixturePaths.contractRoot()
        val pinFile = File(root, "contract-pin.json")
        assertTrue("contract-pin.json missing at ${pinFile.absolutePath}", pinFile.isFile)

        val pin = Json.parseToJsonElement(pinFile.readText()).jsonObject
        // Pin history: 5dfb40c (initial) -> eea85f0 (MC-03 amended MCD-0007, froze stochastic)
        // -> a233614 (MC-06 codified MCD-0021/0022 + anchor fixture)
        // -> d2e621c (MCD-0023 empty-schedule simulation + no-events fixture).
        // See contract/README.md for the re-pin procedure.
        assertEquals(
            "d2e621c4bd609fd5c85b6cdd5275686e8412ba17",
            (pin["source_commit"] as? JsonPrimitive)?.content,
        )
        assertEquals("1.0", (pin["contract_version"] as? JsonPrimitive)?.content)

        val expectedDigests = pin["files"]!!.jsonObject.mapValues { (_, v) ->
            (v as JsonPrimitive).content
        }.toMutableMap()

        val failures = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (pinnedRoot in pinnedRoots) {
            File(root, pinnedRoot).walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.path }
                .forEach { file ->
                    val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                    seen += relative
                    val actual = sha256(file)
                    val expected = expectedDigests.remove(relative)
                    when {
                        expected == null -> failures += "$relative: present on disk but not in the pin"
                        expected != actual -> failures += "$relative: sha256 drift ($actual != pin $expected)"
                    }
                }
        }

        // Anything still in the pin was never found on disk.
        failures += expectedDigests.keys.map { "$it: pinned but missing on disk" }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("pin is empty", seen.isNotEmpty())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(file.readBytes())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
