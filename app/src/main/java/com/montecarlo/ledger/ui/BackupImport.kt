package com.montecarlo.ledger.ui

import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.data.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject

internal fun parseLedgerBackupJson(jsonText: String): LedgerBackupSnapshot {
    val root = JSONObject(jsonText)
    val schemaVersion = root.optInt("schemaVersion", 0)
    require(schemaVersion == 1 || schemaVersion == 2) {
        "Unsupported backup schema version: $schemaVersion"
    }

    val summary = root.optJSONObject("summary") ?: JSONObject()
    val onboarding = root.optJSONObject("onboarding") ?: JSONObject()

    return LedgerBackupSnapshot(
        schemaVersion = schemaVersion,
        exportedAtIso = root.optString("exportedAt").takeIf { it.isNotBlank() },
        bankBalanceCents = summary.optLong("bankBalanceCents", 0L),
        isBalanceReconciled = summary.optBoolean("isBalanceReconciled", false),
        onboardingProgress = OnboardingProgress(
            firstIncomeCompleted = onboarding.optBoolean("firstIncomeCompleted", false),
            firstBillCompleted = onboarding.optBoolean("firstBillCompleted", false),
            firstExpenseCompleted = onboarding.optBoolean("firstExpenseCompleted", false),
            firstGoalCompleted = onboarding.optBoolean("firstGoalCompleted", false),
            reconciliationCompleted = onboarding.optBoolean("reconciliationCompleted", false),
        ),
        settings = root.optJSONArray("settings").toSettingsEntities(),
        rules = root.optJSONArray("rules").toRuleEntities(),
        incomes = root.optJSONArray("incomes").toIncomeEntities(),
        payments = root.optJSONArray("payments").toPaymentEntities(),
        transactions = root.optJSONArray("transactions").toTransactionEntities(),
        billOccurrences = root.optJSONArray("billOccurrences").toBillOccurrenceEntities(),
        assets = root.optJSONArray("assets").toAssetEntities(),
        goals = root.optJSONArray("goals").toGoalEntities(),
        categoryBudgets = root.optJSONArray("categoryBudgets").toCategoryBudgetEntities(),
    )
}

private fun JSONArray?.toIncomeEntities(): List<IncomeEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toIncomeEntity()
        }
    } ?: emptyList()

private fun JSONArray?.toPaymentEntities(): List<PaymentEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toPaymentEntity()
        }
    } ?: emptyList()

private fun JSONArray?.toTransactionEntities(): List<TransactionEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toTransactionEntity()
        }
    } ?: emptyList()

private fun JSONArray?.toBillOccurrenceEntities(): List<BillOccurrenceEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toBillOccurrenceEntity()
        }
    } ?: emptyList()

private fun JSONArray?.toSettingsEntities(): List<SettingsEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toSettingsEntity()
        }
    } ?: emptyList()

private fun JSONArray?.toRuleEntities(): List<TransactionRuleEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toRuleEntity()
        }
    } ?: emptyList()

private fun JSONArray?.toAssetEntities(): List<AssetEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toAssetEntity()
        }
    } ?: emptyList()

private fun JSONArray?.toGoalEntities(): List<GoalEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toGoalEntity()
        }
    } ?: emptyList()

private fun JSONObject.toIncomeEntity(): IncomeEntity =
    IncomeEntity(
        id = optInt("id", 0),
        name = getString("name"),
        amount_cents = optLong("amount_cents", 0L),
        frequency = getString("frequency"),
        day_of_month = if (isNull("day_of_month")) null else optInt("day_of_month"),
        next_date = getString("next_date"),
        expectedAmountCents = if (isNull("expectedAmountCents")) null else optLong("expectedAmountCents"),
        payType = optString("payType").ifBlank { "FLAT" },
    )

private fun JSONObject.toPaymentEntity(): PaymentEntity =
    PaymentEntity(
        id = optInt("id", 0),
        name = getString("name"),
        amount_cents = optLong("amount_cents", 0L),
        frequency = getString("frequency"),
        day_of_month = if (isNull("day_of_month")) null else optInt("day_of_month"),
        next_date = getString("next_date"),
        is_active = if (optBoolean("is_active", true)) 1 else 0,
        isAutoWithdraw = optBoolean("isAutoWithdraw", false),
    )

private fun JSONObject.toTransactionEntity(): TransactionEntity =
    TransactionEntity(
        id = optInt("id", 0),
        description = getString("description"),
        amount_cents = optLong("amount_cents", 0L),
        date = getString("date"),
        type = getString("type"),
        category = optString("category").ifBlank { "uncategorized" },
        source = optString("source").ifBlank { "manual" },
        review_status = optString("review_status").ifBlank { "approved" },
        reviewed_at = if (isNull("reviewed_at")) null else optString("reviewed_at").ifBlank { null },
    )

private fun JSONObject.toBillOccurrenceEntity(): BillOccurrenceEntity =
    BillOccurrenceEntity(
        id = optInt("id", 0),
        payment_id = optInt("payment_id", 0),
        due_date = getString("due_date"),
        amount_cents = optLong("amount_cents", 0L),
        is_paid = if (optBoolean("is_paid", false)) 1 else 0,
        transaction_id = if (isNull("transaction_id")) null else optInt("transaction_id"),
        created_at = if (isNull("created_at")) null else optString("created_at"),
        original_due_date = if (isNull("original_due_date")) null else optString("original_due_date"),
        is_user_modified = if (optBoolean("is_user_modified", false)) 1 else 0,
    )

private fun JSONObject.toSettingsEntity(): SettingsEntity =
    SettingsEntity(
        key = getString("key"),
        value = getString("value"),
    )

private fun JSONObject.toRuleEntity(): TransactionRuleEntity =
    TransactionRuleEntity(
        id = optInt("id", 0),
        match_text = getString("match_text"),
        category = getString("category"),
        is_active = optInt("is_active", 1),
        priority = optInt("priority", 0),
        created_at = optString("created_at"),
    )

private fun JSONObject.toAssetEntity(): AssetEntity =
    AssetEntity(
        id = optLong("id", 0L),
        name = getString("name"),
        type = optString("type").ifBlank { "Other" },
        balanceCents = optLong("balanceCents", 0L),
        lastUpdated = optString("lastUpdated").ifBlank { "" },
    )

private fun JSONObject.toGoalEntity(): GoalEntity =
    GoalEntity(
        id = optInt("id", 0),
        name = getString("name"),
        targetAmountCents = optLong("targetAmountCents", 0L),
        currentAmountCents = optLong("currentAmountCents", 0L),
        deadline = if (isNull("deadline") || optString("deadline").isBlank()) null else optString("deadline"),
        createdAt = optString("createdAt").ifBlank { "" },
    )

private fun JSONArray?.toCategoryBudgetEntities(): List<CategoryBudgetEntity> =
    this?.let { array ->
        List(array.length()) { index ->
            array.getJSONObject(index).toCategoryBudgetEntity()
        }
    } ?: emptyList()

private fun JSONObject.toCategoryBudgetEntity(): CategoryBudgetEntity =
    CategoryBudgetEntity(
        id = optInt("id", 0),
        category = getString("category"),
        limitCents = optLong("limitCents", 0L),
        enabled = optInt("enabled", 1),
        createdAt = optString("createdAt").ifBlank { "" },
    )
