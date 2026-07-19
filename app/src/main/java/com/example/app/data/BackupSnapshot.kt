package com.example.app.data

/**
 * Portable ledger snapshot for backup/restore.
 *
 * - Schema 1: core ledger only (no assets/goals, income.payType may be missing).
 * - Schema 2: includes assets, goals, and income.payType. Preferred export version.
 */
data class LedgerBackupSnapshot(
    val schemaVersion: Int,
    val exportedAtIso: String?,
    val bankBalanceCents: Int,
    val isBalanceReconciled: Boolean,
    val onboardingProgress: OnboardingProgress,
    val settings: List<SettingsEntity> = emptyList(),
    val rules: List<TransactionRuleEntity> = emptyList(),
    val incomes: List<IncomeEntity>,
    val payments: List<PaymentEntity>,
    val transactions: List<TransactionEntity>,
    val billOccurrences: List<BillOccurrenceEntity>,
    val assets: List<AssetEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
)
