package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.data.TransactionRuleEntity

/** Backup JSON construction used by [com.montecarlo.ledger.ui.AppView] (kept out of composable for tests). */
internal fun buildAppViewBackupPayload(
    exportedAtIso: String,
    uiState: AppUiState,
    incomes: List<IncomeEntity>,
    payments: List<PaymentEntity>,
    transactions: List<TransactionEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
    accounts: List<AccountEntity>,
    onboardingProgress: OnboardingProgress,
    settings: List<SettingsEntity>,
    rules: List<TransactionRuleEntity>,
    assets: List<AssetEntity> = uiState.assets,
    goals: List<GoalEntity> = uiState.goals,
    categoryBudgets: List<CategoryBudgetEntity> = uiState.categoryBudgets,
): String = buildLedgerBackupJson(
    exportedAtIso = exportedAtIso,
    uiState = uiState,
    incomes = incomes,
    payments = payments,
    transactions = transactions,
    billOccurrences = billOccurrences,
    onboardingProgress = onboardingProgress,
    settings = settings,
    rules = rules,
    assets = assets,
    goals = goals,
    categoryBudgets = categoryBudgets,
    accounts = accounts,
)
