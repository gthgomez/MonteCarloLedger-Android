package com.example.app.data

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
)
