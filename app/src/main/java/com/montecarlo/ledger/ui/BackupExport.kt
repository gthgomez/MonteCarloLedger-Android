package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.isAppLockSettingKey
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Current export schema: serializer-based writer, same field layout as v3 plus version bump. */
internal const val BACKUP_SCHEMA_VERSION = 5

private val BACKUP_JSON = Json { prettyPrint = true }

internal fun buildLedgerBackupJson(
    exportedAtIso: String,
    uiState: AppUiState,
    incomes: List<IncomeEntity>,
    payments: List<PaymentEntity>,
    transactions: List<TransactionEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
    onboardingProgress: OnboardingProgress,
    settings: List<SettingsEntity>,
    rules: List<TransactionRuleEntity>,
    assets: List<AssetEntity> = uiState.assets,
    goals: List<GoalEntity> = uiState.goals,
    categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
    debts: List<DebtEntity> = uiState.debts,
    accounts: List<AccountEntity> = emptyList(),
): String {
    val root = buildJsonObject {
        put("schemaVersion", BACKUP_SCHEMA_VERSION)
        put("exportedAt", exportedAtIso)
        put("summary", buildJsonObject {
            put("bankBalanceCents", uiState.bankBalanceCents)
            put("ledgerBalanceCents", uiState.ledgerBalanceCents)
            put("isBalanceReconciled", uiState.isBalanceReconciled)
            put("safeToSpendCents", uiState.safeToSpendCents)
            put("incomeContributionCents", uiState.incomeContributionCents)
            put("dailyBudgetCents", uiState.dailyBudgetCents)
            put("upcomingBillBurdenCents", uiState.upcomingBillBurdenCents)
            put("monteCarlo10thCents", uiState.monteCarlo10thCents)
            put("monteCarlo50thCents", uiState.monteCarlo50thCents)
            put("monteCarlo90thCents", uiState.monteCarlo90thCents)
            put("probabilityNegativePct", uiState.probabilityNegativePct)
            uiState.projectedTroubleDateLabel?.let { put("projectedTroubleDateLabel", it) }
            uiState.firstNegativeDateLabel?.let { put("firstNegativeDateLabel", it) }
            uiState.lowestBalanceDateLabel?.let { put("lowestBalanceDateLabel", it) }
        })
        put("onboarding", buildJsonObject {
            put("firstIncomeCompleted", onboardingProgress.firstIncomeCompleted)
            put("firstBillCompleted", onboardingProgress.firstBillCompleted)
            put("firstExpenseCompleted", onboardingProgress.firstExpenseCompleted)
            put("firstGoalCompleted", onboardingProgress.firstGoalCompleted)
            put("reconciliationCompleted", onboardingProgress.reconciliationCompleted)
        })
        put("settings", JsonArray(settings.filterNot { isAppLockSettingKey(it.key) }.map { it.toJsonElement() }))
        put("rules", JsonArray(rules.map { it.toJsonElement() }))
        put("incomes", JsonArray(incomes.map { it.toJsonElement() }))
        put("payments", JsonArray(payments.map { it.toJsonElement() }))
        put("transactions", JsonArray(transactions.map { it.toJsonElement() }))
        put("billOccurrences", JsonArray(billOccurrences.map { it.toJsonElement() }))
        put("assets", JsonArray(assets.map { it.toJsonElement() }))
        put("goals", JsonArray(goals.map { it.toJsonElement() }))
        put("categoryBudgets", JsonArray(categoryBudgets.map { it.toJsonElement() }))
        put("debts", JsonArray(debts.map { it.toJsonElement() }))
        put("accounts", JsonArray(accounts.map { it.toJsonElement() }))
    }
    return BACKUP_JSON.encodeToString(JsonElement.serializer(), root)
}

internal fun parseBackupJsonToElement(jsonText: String): JsonObject =
    Json.parseToJsonElement(jsonText).let { element ->
        element as? JsonObject
            ?: throw IllegalArgumentException("Backup root must be a JSON object.")
    }

// ── entity encoders ──────────────────────────────────────────────

private fun SettingsEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("key", key)
    put("value", value)
}

private fun IncomeEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("amount_cents", amount_cents)
    put("frequency", frequency)
    putNullableInt("day_of_month", day_of_month)
    put("next_date", next_date)
    putNullableLong("expectedAmountCents", expectedAmountCents)
    put("payType", payType)
}

private fun PaymentEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("amount_cents", amount_cents)
    put("frequency", frequency)
    putNullableInt("day_of_month", day_of_month)
    put("next_date", next_date)
    put("is_active", is_active == 1)
    put("isAutoWithdraw", isAutoWithdraw)
}

private fun TransactionEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("description", description)
    put("amount_cents", amount_cents)
    put("date", date)
    put("type", type)
    put("category", category)
    put("source", source)
    put("review_status", review_status)
    putNullableString("reviewed_at", reviewed_at)
}

private fun TransactionRuleEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("match_text", match_text)
    put("category", category)
    put("is_active", is_active)
    put("priority", priority)
    put("created_at", created_at)
}

private fun BillOccurrenceEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("payment_id", payment_id)
    put("due_date", due_date)
    put("amount_cents", amount_cents)
    put("is_paid", is_paid == 1)
    putNullableInt("transaction_id", transaction_id)
    putNullableString("created_at", created_at)
    putNullableString("original_due_date", original_due_date)
    put("is_user_modified", is_user_modified == 1)
}

private fun AccountEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("type", type)
    put("balanceCents", balanceCents)
    put("isReconciled", isReconciled)
    put("isDefault", isDefault)
    put("lastUpdated", lastUpdated)
}

private fun AssetEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("type", type)
    put("balanceCents", balanceCents)
    put("lastUpdated", lastUpdated)
}

private fun GoalEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("targetAmountCents", targetAmountCents)
    put("currentAmountCents", currentAmountCents)
    putNullableString("deadline", deadline)
    put("createdAt", createdAt)
}

private fun CategoryBudgetEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("category", category)
    put("limitCents", limitCents)
    put("enabled", enabled)
    put("createdAt", createdAt)
}

private fun DebtEntity.toJsonElement(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("balanceCents", balanceCents)
    put("aprBasisPoints", aprBasisPoints)
    put("minimumPaymentCents", minimumPaymentCents)
    put("dueDayOfMonth", dueDayOfMonth)
    putNullableInt("linkedPaymentId", linkedPaymentId)
    put("isActive", isActive)
}

// -- shared primitives -------------------------------------------

internal fun JsonObject.intOr(name: String): Int =
    (this[name] as? JsonPrimitive)?.content?.toIntOrNull()
        ?: throw IllegalArgumentException("Missing required int field: `$name")

internal fun JsonObject.longOr(name: String): Long =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull()
        ?: throw IllegalArgumentException("Missing required long field: `$name")

internal fun JsonObject.intOrFallback(name: String, fallback: Int): Int =
    (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback

internal fun JsonObject.longOrFallback(name: String, fallback: Long): Long =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull() ?: fallback

internal fun JsonObject.optBool(name: String, fallback: Boolean): Boolean =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: fallback

internal fun JsonObject.str(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull
        ?: throw IllegalArgumentException("Missing required string field: `$name")
internal fun JsonObject.optStr(name: String, fallback: String = ""): String =
    (this[name] as? JsonPrimitive)?.contentOrNull ?: fallback

internal fun JsonObject.intOrThrow(name: String): Int =
    (this[name] as? JsonPrimitive)?.content?.toIntOrNull()
        ?: throw IllegalArgumentException("Missing required int field: $name")

internal fun JsonObject.longOrThrow(name: String): Long =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull()
        ?: throw IllegalArgumentException("Missing required long field: $name")

internal fun JsonObject.boolOrThrow(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
        ?: throw IllegalArgumentException("Missing required boolean field: $name")

internal fun JsonObject.nullableInt(name: String): Int? =
    when (val v = this[name]) {
        null, is JsonNull -> null
        is JsonPrimitive -> v.contentOrNull?.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid int field: $name")
        else -> throw IllegalArgumentException("Invalid int field: $name")
    }

internal fun JsonObject.nullableLong(name: String): Long? =
    when (val v = this[name]) {
        null, is JsonNull -> null
        is JsonPrimitive -> v.contentOrNull?.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid long field: $name")
        else -> throw IllegalArgumentException("Invalid long field: $name")
    }

internal fun JsonObject.nullableString(name: String): String? =
    when (val v = this[name]) {
        null, is JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        else -> throw IllegalArgumentException("Invalid string field: $name")
    }

internal fun JsonObject.array(name: String): List<JsonObject> =
    ((this[name] as? JsonArray) ?: JsonArray(emptyList())).map { element ->
        element as? JsonObject
            ?: throw IllegalArgumentException("Expected a JSON object in array '$name'.")
    }

private fun JsonObjectBuilder.putNullableInt(name: String, value: Int?) =
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)

private fun JsonObjectBuilder.putNullableLong(name: String, value: Long?) =
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)

private fun JsonObjectBuilder.putNullableString(name: String, value: String?) =
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
