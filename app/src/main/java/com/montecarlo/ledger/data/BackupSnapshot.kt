package com.montecarlo.ledger.data

/**
 * Portable ledger snapshot for backup/restore.
 *
 * - Schema 1: core ledger only (no assets/goals, income.payType may be missing).
 * - Schema 2: includes assets, goals, and income.payType.
 * - Schema 3: includes persisted debt records.
 * - Schema 4: serializer-based writer (field layout unchanged).
 * - Schema 5: includes bank accounts. Preferred export version.
 */
data class LedgerBackupSnapshot(
    val schemaVersion: Int,
    val exportedAtIso: String?,
    val bankBalanceCents: Long,
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
    val debts: List<DebtEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)
