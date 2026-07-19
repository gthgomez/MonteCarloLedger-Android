package com.example.app.ui

import com.example.app.AppUiState
import com.example.app.data.AssetEntity
import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.CategoryBudgetEntity
import com.example.app.data.GoalEntity
import com.example.app.data.IncomeEntity
import com.example.app.data.OnboardingProgress
import com.example.app.data.PaymentEntity
import com.example.app.data.SettingsEntity
import com.example.app.data.TransactionRuleEntity
import com.example.app.data.TransactionEntity

/** Current export schema: includes assets, goals, and income.payType. */
internal const val BACKUP_SCHEMA_VERSION = 2

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
): String = buildString {
    appendLine("{")
    appendLine("  \"schemaVersion\": $BACKUP_SCHEMA_VERSION,")
    appendJsonStringField("exportedAt", exportedAtIso, indent = "  ")
    appendLine("  \"summary\": {")
    appendJsonNumberField("bankBalanceCents", uiState.bankBalanceCents, indent = "    ")
    appendJsonNumberField("ledgerBalanceCents", uiState.ledgerBalanceCents, indent = "    ")
    appendJsonBooleanField("isBalanceReconciled", uiState.isBalanceReconciled, indent = "    ")
    appendJsonNumberField("safeToSpendCents", uiState.safeToSpendCents, indent = "    ")
    appendJsonNumberField("incomeContributionCents", uiState.incomeContributionCents, indent = "    ")
    appendJsonNumberField("dailyBudgetCents", uiState.dailyBudgetCents, indent = "    ")
    appendJsonNumberField("upcomingBillBurdenCents", uiState.upcomingBillBurdenCents, indent = "    ")
    appendJsonNumberField("monteCarlo10thCents", uiState.monteCarlo10thCents, indent = "    ")
    appendJsonNumberField("monteCarlo50thCents", uiState.monteCarlo50thCents, indent = "    ")
    appendJsonNumberField("monteCarlo90thCents", uiState.monteCarlo90thCents, indent = "    ")
    appendJsonNumberField("probabilityNegativePct", uiState.probabilityNegativePct, indent = "    ")
    appendJsonStringField("projectedTroubleDateLabel", uiState.projectedTroubleDateLabel, indent = "    ")
    appendJsonStringField("firstNegativeDateLabel", uiState.firstNegativeDateLabel, indent = "    ")
    appendJsonStringField("lowestBalanceDateLabel", uiState.lowestBalanceDateLabel, indent = "    ", trailingComma = false)
    appendLine("  },")
    appendLine("  \"onboarding\": {")
    appendJsonBooleanField("firstIncomeCompleted", onboardingProgress.firstIncomeCompleted, indent = "    ")
    appendJsonBooleanField("firstBillCompleted", onboardingProgress.firstBillCompleted, indent = "    ")
    appendJsonBooleanField("firstExpenseCompleted", onboardingProgress.firstExpenseCompleted, indent = "    ")
    appendJsonBooleanField("firstGoalCompleted", onboardingProgress.firstGoalCompleted, indent = "    ")
    appendJsonBooleanField("reconciliationCompleted", onboardingProgress.reconciliationCompleted, indent = "    ", trailingComma = false)
    appendLine("  },")
    appendJsonArray("settings", settings.map { it.toBackupJson() })
    appendJsonArray("rules", rules.map { it.toBackupJson() })
    appendJsonArray("incomes", incomes.map { it.toBackupJson() })
    appendJsonArray("payments", payments.map { it.toBackupJson() })
    appendJsonArray("transactions", transactions.map { it.toBackupJson() })
    appendJsonArray("billOccurrences", billOccurrences.map { it.toBackupJson() })
    appendJsonArray("assets", assets.map { it.toBackupJson() })
    appendJsonArray("goals", goals.map { it.toBackupJson() })
    appendJsonArray("categoryBudgets", categoryBudgets.map { it.toBackupJson() }, trailingComma = false)
    appendLine("}")
}

private fun SettingsEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonStringField("key", key, indent = "    ")
    appendJsonStringField("value", value, indent = "    ", trailingComma = false)
    append("  }")
}

private fun IncomeEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonStringField("name", name, indent = "    ")
    appendJsonNumberField("amount_cents", amount_cents, indent = "    ")
    appendJsonStringField("frequency", frequency, indent = "    ")
    appendJsonNumberField("day_of_month", day_of_month, indent = "    ")
    appendJsonStringField("next_date", next_date, indent = "    ")
    appendJsonNumberField("expectedAmountCents", expectedAmountCents, indent = "    ")
    appendJsonStringField("payType", payType, indent = "    ", trailingComma = false)
    append("  }")
}

private fun PaymentEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonStringField("name", name, indent = "    ")
    appendJsonNumberField("amount_cents", amount_cents, indent = "    ")
    appendJsonStringField("frequency", frequency, indent = "    ")
    appendJsonNumberField("day_of_month", day_of_month, indent = "    ")
    appendJsonStringField("next_date", next_date, indent = "    ")
    appendJsonBooleanField("is_active", is_active == 1, indent = "    ")
    appendJsonBooleanField("isAutoWithdraw", isAutoWithdraw, indent = "    ", trailingComma = false)
    append("  }")
}

private fun TransactionEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonStringField("description", description, indent = "    ")
    appendJsonNumberField("amount_cents", amount_cents, indent = "    ")
    appendJsonStringField("date", date, indent = "    ")
    appendJsonStringField("type", type, indent = "    ")
    appendJsonStringField("category", category, indent = "    ")
    appendJsonStringField("source", source, indent = "    ")
    appendJsonStringField("review_status", review_status, indent = "    ")
    appendJsonStringField("reviewed_at", reviewed_at, indent = "    ", trailingComma = false)
    append("  }")
}

private fun TransactionRuleEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonStringField("match_text", match_text, indent = "    ")
    appendJsonStringField("category", category, indent = "    ")
    appendJsonNumberField("is_active", is_active, indent = "    ")
    appendJsonNumberField("priority", priority, indent = "    ")
    appendJsonStringField("created_at", created_at, indent = "    ", trailingComma = false)
    append("  }")
}

private fun BillOccurrenceEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonNumberField("payment_id", payment_id, indent = "    ")
    appendJsonStringField("due_date", due_date, indent = "    ")
    appendJsonNumberField("amount_cents", amount_cents, indent = "    ")
    appendJsonBooleanField("is_paid", is_paid == 1, indent = "    ")
    appendJsonNumberField("transaction_id", transaction_id, indent = "    ")
    appendJsonStringField("created_at", created_at, indent = "    ")
    appendJsonStringField("original_due_date", original_due_date, indent = "    ")
    appendJsonBooleanField("is_user_modified", is_user_modified == 1, indent = "    ", trailingComma = false)
    append("  }")
}

private fun AssetEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonStringField("name", name, indent = "    ")
    appendJsonStringField("type", type, indent = "    ")
    appendJsonNumberField("balanceCents", balanceCents, indent = "    ")
    appendJsonStringField("lastUpdated", lastUpdated, indent = "    ", trailingComma = false)
    append("  }")
}

private fun GoalEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonStringField("name", name, indent = "    ")
    appendJsonNumberField("targetAmountCents", targetAmountCents, indent = "    ")
    appendJsonNumberField("currentAmountCents", currentAmountCents, indent = "    ")
    appendJsonStringField("deadline", deadline, indent = "    ")
    appendJsonStringField("createdAt", createdAt, indent = "    ", trailingComma = false)
    append("  }")
}

private fun CategoryBudgetEntity.toBackupJson(): String = buildString {
    appendLine("{")
    appendJsonNumberField("id", id, indent = "    ")
    appendJsonStringField("category", category, indent = "    ")
    appendJsonNumberField("limitCents", limitCents, indent = "    ")
    appendJsonNumberField("enabled", enabled, indent = "    ")
    appendJsonStringField("createdAt", createdAt, indent = "    ", trailingComma = false)
    append("  }")
}

private fun StringBuilder.appendJsonArray(name: String, values: List<String>, trailingComma: Boolean = true) {
    append("  \"")
    append(jsonEscape(name))
    append("\": [\n")
    values.forEachIndexed { index, value ->
        append(value)
        if (index < values.lastIndex) append(",")
        append("\n")
    }
    append("  ]")
    if (trailingComma) append(",")
    appendLine()
}

private fun StringBuilder.appendJsonStringField(
    name: String,
    value: String?,
    indent: String,
    trailingComma: Boolean = true,
) {
    append(indent)
    append("\"")
    append(jsonEscape(name))
    append("\": ")
    append("\"")
    append(jsonEscape(value.orEmpty()))
    append("\"")
    if (trailingComma) append(",")
    appendLine()
}

private fun StringBuilder.appendJsonNumberField(
    name: String,
    value: Number?,
    indent: String,
    trailingComma: Boolean = true,
) {
    append(indent)
    append("\"")
    append(jsonEscape(name))
    append("\": ")
    append(value?.toString() ?: "null")
    if (trailingComma) append(",")
    appendLine()
}

private fun StringBuilder.appendJsonBooleanField(
    name: String,
    value: Boolean,
    indent: String,
    trailingComma: Boolean = true,
) {
    append(indent)
    append("\"")
    append(jsonEscape(name))
    append("\": ")
    append(value.toString())
    if (trailingComma) append(",")
    appendLine()
}

private fun jsonEscape(value: String): String {
    val out = StringBuilder(value.length + 8)
    value.forEach { ch ->
        when (ch) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> {
                if (ch < ' ') {
                    out.append("\\u")
                    out.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
    }
    return out.toString()
}
