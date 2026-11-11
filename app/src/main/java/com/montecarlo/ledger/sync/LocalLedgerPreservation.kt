package com.montecarlo.ledger.sync

import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.isAppLockSettingKey

/**
 * Android-only ledger data not carried in the cross-platform vault envelope (2.0/2.1).
 * Preservation checks are intentionally separate from [VaultSnapshotFingerprint].
 */
object LocalLedgerPreservation {
    private val VAULT_RESTORED_SETTING_KEYS = setOf("current_balance")

    suspend fun hasPreservationRisk(repo: LedgerRepository): Boolean {
        if (repo.getAllGoalsList().isNotEmpty()) return true
        if (repo.getAllAssetsList().isNotEmpty()) return true
        if (repo.getAllTransactionRulesList().isNotEmpty()) return true
        if (repo.getAllCategoryBudgetsList().isNotEmpty()) return true
        if (repo.hasCustomAccounts()) return true
        if (repo.hasNonVaultLocalSettings()) return true
        return false
    }

    suspend fun fingerprint(repo: LedgerRepository): String {
        val lines = buildList {
            repo.getAllGoalsList().sortedBy { it.id }.forEach {
                add("goal|${it.id}|${it.name}|${it.targetAmountCents}|${it.currentAmountCents}")
            }
            repo.getAllAssetsList().sortedBy { it.id }.forEach {
                add("asset|${it.id}|${it.name}|${it.type}|${it.balanceCents}")
            }
            repo.getAllTransactionRulesList().sortedBy { it.id }.forEach {
                add("rule|${it.id}|${it.match_text}|${it.category}|${it.priority}")
            }
            repo.getAllCategoryBudgetsList().sortedBy { it.id }.forEach {
                add("budget|${it.id}|${it.category}|${it.limitCents}")
            }
            repo.getAllAccountsList().sortedBy { it.id }.forEach {
                add("account|${it.id}|${it.name}|${it.type}|${it.balanceCents}|${it.isDefault}")
            }
            repo.getAllSettingsList()
                .filter { settingsRowIncludedInPreservation(it) }
                .sortedBy { it.key }
                .forEach { add("setting|${it.key}|${it.value}") }
        }
        val payload = buildJsonObject(lines)
        return VaultSnapshotFingerprint.ofSnapshotJson(payload)
    }

    private fun settingsRowIncludedInPreservation(row: SettingsEntity): Boolean {
        if (isAppLockSettingKey(row.key)) return false
        if (row.key in VAULT_RESTORED_SETTING_KEYS) return false
        return true
    }

    private fun buildJsonObject(lines: List<String>): String {
        val array = lines.joinToString(prefix = "{\"preservation\":[", postfix = "]}", separator = ",") { part ->
            "\"${part.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        return array
    }
}
