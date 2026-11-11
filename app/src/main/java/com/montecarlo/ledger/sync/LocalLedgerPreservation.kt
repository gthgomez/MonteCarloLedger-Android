package com.montecarlo.ledger.sync

import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.isAppLockSettingKey
import org.json.JSONArray
import org.json.JSONObject

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
        val root = JSONObject()
        root.put(
            "goals",
            JSONArray().apply {
                repo.getAllGoalsList().sortedBy { it.id }.forEach { goal ->
                    put(
                        JSONObject()
                            .put("id", goal.id)
                            .put("name", goal.name)
                            .put("targetAmountCents", goal.targetAmountCents)
                            .put("currentAmountCents", goal.currentAmountCents)
                            .put("deadline", goal.deadline)
                            .put("createdAt", goal.createdAt),
                    )
                }
            },
        )
        root.put(
            "assets",
            JSONArray().apply {
                repo.getAllAssetsList().sortedBy { it.id }.forEach { asset ->
                    put(
                        JSONObject()
                            .put("id", asset.id)
                            .put("name", asset.name)
                            .put("type", asset.type)
                            .put("balanceCents", asset.balanceCents)
                            .put("lastUpdated", asset.lastUpdated),
                    )
                }
            },
        )
        root.put(
            "rules",
            JSONArray().apply {
                repo.getAllTransactionRulesList().sortedBy { it.id }.forEach { rule ->
                    put(
                        JSONObject()
                            .put("id", rule.id)
                            .put("match_text", rule.match_text)
                            .put("category", rule.category)
                            .put("is_active", rule.is_active)
                            .put("priority", rule.priority)
                            .put("created_at", rule.created_at),
                    )
                }
            },
        )
        root.put(
            "categoryBudgets",
            JSONArray().apply {
                repo.getAllCategoryBudgetsList().sortedBy { it.id }.forEach { budget ->
                    put(
                        JSONObject()
                            .put("id", budget.id)
                            .put("category", budget.category)
                            .put("limitCents", budget.limitCents)
                            .put("enabled", budget.enabled)
                            .put("createdAt", budget.createdAt),
                    )
                }
            },
        )
        root.put(
            "accounts",
            JSONArray().apply {
                repo.getAllAccountsList().sortedBy { it.id }.forEach { account ->
                    put(
                        JSONObject()
                            .put("id", account.id)
                            .put("name", account.name)
                            .put("type", account.type)
                            .put("balanceCents", account.balanceCents)
                            .put("isReconciled", account.isReconciled)
                            .put("isDefault", account.isDefault)
                            .put("lastUpdated", account.lastUpdated),
                    )
                }
            },
        )
        root.put(
            "settings",
            JSONArray().apply {
                repo.getAllSettingsList()
                    .filter { settingsRowIncludedInPreservation(it) }
                    .sortedBy { it.key }
                    .forEach { setting ->
                        put(JSONObject().put("key", setting.key).put("value", setting.value))
                    }
            },
        )
        return VaultSnapshotFingerprint.ofSnapshotJson(root.toString())
    }

    private fun settingsRowIncludedInPreservation(row: SettingsEntity): Boolean {
        if (isAppLockSettingKey(row.key)) return false
        if (row.key in VAULT_RESTORED_SETTING_KEYS) return false
        return true
    }
}
