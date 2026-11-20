package com.montecarlo.ledger.ui

import com.montecarlo.ledger.data.AccountEntity
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal fun parseLedgerBackupJson(jsonText: String): LedgerBackupSnapshot {
    val root = parseBackupJsonToElement(jsonText)
    val schemaVersion = root.intOrThrow("schemaVersion")
    require(schemaVersion in 1..BACKUP_SCHEMA_VERSION) {
        "Unsupported backup schema version: $schemaVersion"
    }

    val summary = root.objectSection("summary")
    val onboarding = root.objectSection("onboarding", required = schemaVersion >= 6)

    return LedgerBackupSnapshot(
        schemaVersion = schemaVersion,
        exportedAtIso = root.nullableString("exportedAt")?.takeIf { it.isNotBlank() },
        bankBalanceCents = summary.longOrThrow("bankBalanceCents"),
        isBalanceReconciled = summary.boolOrThrow("isBalanceReconciled"),
        onboardingProgress = OnboardingProgress(
            firstIncomeCompleted = onboarding.optBool( "firstIncomeCompleted", false),
            firstBillCompleted = onboarding.optBool( "firstBillCompleted", false),
            firstExpenseCompleted = onboarding.optBool( "firstExpenseCompleted", false),
            firstGoalCompleted = onboarding.optBool( "firstGoalCompleted", false),
            reconciliationCompleted = onboarding.optBool( "reconciliationCompleted", false),
            dismissed = onboarding.optBool( "dismissed", false),
        ),
        settings = root.arraySection("settings").map { it.toSettingsEntity() },
        rules = root.arraySection("rules").map { it.toRuleEntity() },
        incomes = root.arraySection("incomes").map { it.toIncomeEntity() },
        payments = root.arraySection("payments").map { it.toPaymentEntity() },
        transactions = root.arraySection("transactions").map { it.toTransactionEntity() },
        billOccurrences = root.arraySection("billOccurrences").map { it.toBillOccurrenceEntity() },
        assets = root.arraySection("assets", required = schemaVersion >= 3).map { it.toAssetEntity() },
        goals = root.arraySection("goals", required = schemaVersion >= 3).map { it.toGoalEntity() },
        categoryBudgets = root.arraySection("categoryBudgets", required = schemaVersion >= 3).map { it.toCategoryBudgetEntity() },
        debts = root.arraySection("debts", required = schemaVersion >= 3).map { it.toDebtEntity() },
        accounts = root.arraySection("accounts", required = schemaVersion >= 5).map { it.toAccountEntity() },
    )
}

private fun JsonObject.objectSection(name: String, required: Boolean = true): JsonObject {
    val value = this[name]
    if (value == null && !required) return JsonObject(emptyMap())
    return value as? JsonObject
        ?: throw IllegalArgumentException("Backup is missing a valid '$name' object.")
}

private fun JsonObject.arraySection(name: String, required: Boolean = true): List<JsonObject> {
    val value = this[name]
    if (value == null && !required) return emptyList()
    val items = value as? JsonArray
        ?: throw IllegalArgumentException("Backup is missing a valid '$name' array.")
    return items.map { element ->
        element as? JsonObject
            ?: throw IllegalArgumentException("Expected a JSON object in array '$name'.")
    }
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
        // Schema <= 5 backups predate clearing states; statement history is posted.
        clearing_status = com.montecarlo.ledger.data.ClearingStatus.normalize(optStr("clearing_status")),
        account_id = nullableLong("account_id"),
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

private fun JsonObject.toAccountEntity(): AccountEntity =
    AccountEntity(
        id = longOrThrow("id"),
        name = str("name"),
        type = optStr("type").ifBlank { "checking" },
        balanceCents = longOrThrow("balanceCents"),
        isReconciled = optBool("isReconciled", false),
        isDefault = optBool("isDefault", false),
        lastUpdated = optStr("lastUpdated"),
    )
private fun JsonObject.toAssetEntity(): AssetEntity =
    AssetEntity(
        id = longOrThrow("id"),
        name = str("name"),
        type = optStr("type").ifBlank { "Other" },
        balanceCents = longOrThrow("balanceCents"),
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
        kind = com.montecarlo.ledger.data.DebtKind.normalize(optStr("kind")),
        statementDayOfMonth = nullableInt("statementDayOfMonth"),
        minPaymentPercentBps = intOrFallback("minPaymentPercentBps", 0),
        minPaymentFloorCents = longOrFallback("minPaymentFloorCents", 0L),
        linkedAccountId = nullableLong("linkedAccountId"),
    )

private fun JsonObject.toCategoryBudgetEntity(): CategoryBudgetEntity =
    CategoryBudgetEntity(
        id = intOr("id"),
        category = str("category"),
        limitCents = longOr("limitCents"),
        enabled = intOrFallback("enabled", 1),
        createdAt = optStr("createdAt"),
    )
