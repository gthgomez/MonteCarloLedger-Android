package com.montecarlo.ledger.ui

import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.serialization.json.JsonObject

internal fun parseLedgerBackupJson(jsonText: String): LedgerBackupSnapshot {
    val root = parseBackupJsonToElement(jsonText)
    val schemaVersion = root.intOrThrow("schemaVersion")
    require(schemaVersion in 1..BACKUP_SCHEMA_VERSION) {
        "Unsupported backup schema version: $schemaVersion"
    }

    val summary = (root["summary"] as? JsonObject) ?: JsonObject(emptyMap())
    val onboarding = (root["onboarding"] as? JsonObject) ?: JsonObject(emptyMap())

    return LedgerBackupSnapshot(
        schemaVersion = schemaVersion,
        exportedAtIso = root.nullableString("exportedAt")?.takeIf { it.isNotBlank() },
        bankBalanceCents = summary.nullableLong( "bankBalanceCents") ?: 0L,
        isBalanceReconciled = summary.optBool( "isBalanceReconciled", false),
        onboardingProgress = OnboardingProgress(
            firstIncomeCompleted = onboarding.optBool( "firstIncomeCompleted", false),
            firstBillCompleted = onboarding.optBool( "firstBillCompleted", false),
            firstExpenseCompleted = onboarding.optBool( "firstExpenseCompleted", false),
            firstGoalCompleted = onboarding.optBool( "firstGoalCompleted", false),
            reconciliationCompleted = onboarding.optBool( "reconciliationCompleted", false),
        ),
        settings = root.array("settings").map { it.toSettingsEntity() },
        rules = root.array("rules").map { it.toRuleEntity() },
        incomes = root.array("incomes").map { it.toIncomeEntity() },
        payments = root.array("payments").map { it.toPaymentEntity() },
        transactions = root.array("transactions").map { it.toTransactionEntity() },
        billOccurrences = root.array("billOccurrences").map { it.toBillOccurrenceEntity() },
        assets = root.array("assets").map { it.toAssetEntity() },
        goals = root.array("goals").map { it.toGoalEntity() },
        categoryBudgets = root.array("categoryBudgets").map { it.toCategoryBudgetEntity() },
        debts = root.array("debts").map { it.toDebtEntity() },
    )
}

private fun JsonObject.toIncomeEntity(): IncomeEntity =
    IncomeEntity(
        id = intOr("id"),
        name = str("name"),
        amount_cents = longOr("amount_cents"),
        frequency = str("frequency"),
        day_of_month = nullableInt("day_of_month"),
        next_date = str("next_date"),
        expectedAmountCents = nullableLong("expectedAmountCents"),
        payType = optStr("payType").ifBlank { "FLAT" },
    )

private fun JsonObject.toPaymentEntity(): PaymentEntity =
    PaymentEntity(
        id = intOr("id"),
        name = str("name"),
        amount_cents = longOr("amount_cents"),
        frequency = str("frequency"),
        day_of_month = nullableInt("day_of_month"),
        next_date = str("next_date"),
        is_active = if (optBool("is_active", true)) 1 else 0,
        isAutoWithdraw = optBool("isAutoWithdraw", false),
    )

private fun JsonObject.toTransactionEntity(): TransactionEntity =
    TransactionEntity(
        id = intOr("id"),
        description = str("description"),
        amount_cents = longOr("amount_cents"),
        date = str("date"),
        type = str("type"),
        category = optStr("category").ifBlank { "uncategorized" },
        source = optStr("source").ifBlank { "manual" },
        review_status = optStr("review_status").ifBlank { "approved" },
        reviewed_at = nullableString("reviewed_at")?.ifBlank { null },
    )

private fun JsonObject.toBillOccurrenceEntity(): BillOccurrenceEntity =
    BillOccurrenceEntity(
        id = intOr("id"),
        payment_id = intOr("payment_id"),
        due_date = str("due_date"),
        amount_cents = longOr("amount_cents"),
        is_paid = if (optBool("is_paid", false)) 1 else 0,
        transaction_id = nullableInt("transaction_id"),
        created_at = nullableString("created_at"),
        original_due_date = nullableString("original_due_date"),
        is_user_modified = if (optBool("is_user_modified", false)) 1 else 0,
    )

private fun JsonObject.toSettingsEntity(): SettingsEntity =
    SettingsEntity(
        key = str("key"),
        value = str("value"),
    )

private fun JsonObject.toRuleEntity(): TransactionRuleEntity =
    TransactionRuleEntity(
        id = intOr("id"),
        match_text = str("match_text"),
        category = str("category"),
        is_active = intOrFallback("is_active", 1),
        priority = intOrFallback("priority", 0),
        created_at = optStr("created_at"),
    )

private fun JsonObject.toAssetEntity(): AssetEntity =
    AssetEntity(
        id = longOrFallback("id", 0L),
        name = str("name"),
        type = optStr("type").ifBlank { "Other" },
        balanceCents = longOrFallback("balanceCents", 0L),
        lastUpdated = optStr("lastUpdated"),
    )

private fun JsonObject.toGoalEntity(): GoalEntity =
    GoalEntity(
        id = intOr("id"),
        name = str("name"),
        targetAmountCents = longOr("targetAmountCents"),
        currentAmountCents = longOr("currentAmountCents"),
        deadline = nullableString("deadline")?.ifBlank { null },
        createdAt = optStr("createdAt"),
    )

private fun JsonObject.toDebtEntity(): DebtEntity =
    DebtEntity(
        id = longOrFallback("id", 0L),
        name = str("name"),
        balanceCents = longOr("balanceCents"),
        aprBasisPoints = intOrFallback("aprBasisPoints", 0),
        minimumPaymentCents = longOrFallback("minimumPaymentCents", 0L),
        dueDayOfMonth = intOrFallback("dueDayOfMonth", 1),
        linkedPaymentId = nullableInt("linkedPaymentId"),
        isActive = optBool("isActive", true),
    )

private fun JsonObject.toCategoryBudgetEntity(): CategoryBudgetEntity =
    CategoryBudgetEntity(
        id = intOr("id"),
        category = str("category"),
        limitCents = longOr("limitCents"),
        enabled = intOrFallback("enabled", 1),
        createdAt = optStr("createdAt"),
    )
