package com.montecarlo.ledger.data

enum class OnboardingMilestone {
    RECONCILIATION,   // Step 1: bank balance — anchors every number
    FIRST_INCOME,     // Step 2: first paycheck
    FIRST_BILL,       // Step 3: first bill
    FIRST_GOAL,       // Step 4: savings goal
    FIRST_EXPENSE,    // Legacy — kept so existing code and backups don't break
}

data class OnboardingStepState(
    val milestone: OnboardingMilestone,
    val label: String,
    val completed: Boolean,
)

data class OnboardingProgress(
    val reconciliationCompleted: Boolean = false,
    val firstIncomeCompleted: Boolean = false,
    val firstBillCompleted: Boolean = false,
    val firstGoalCompleted: Boolean = false,
    // Legacy field — kept so existing backups deserialise without crashing.
    // Not surfaced in the new onboarding UI.
    val firstExpenseCompleted: Boolean = false,
) {
    val isComplete: Boolean
        get() = reconciliationCompleted && firstIncomeCompleted && firstBillCompleted && firstGoalCompleted

    val completedCount: Int
        get() = listOf(reconciliationCompleted, firstIncomeCompleted, firstBillCompleted, firstGoalCompleted).count { it }

    fun nextActionMilestone(): OnboardingMilestone? = when {
        !reconciliationCompleted -> OnboardingMilestone.RECONCILIATION
        !firstIncomeCompleted -> OnboardingMilestone.FIRST_INCOME
        !firstBillCompleted -> OnboardingMilestone.FIRST_BILL
        !firstGoalCompleted -> OnboardingMilestone.FIRST_GOAL
        else -> null
    }

    fun steps(): List<OnboardingStepState> = listOf(
        OnboardingStepState(
            milestone = OnboardingMilestone.RECONCILIATION,
            label = "Enter your bank balance",
            completed = reconciliationCompleted,
        ),
        OnboardingStepState(
            milestone = OnboardingMilestone.FIRST_INCOME,
            label = "Add your first paycheck",
            completed = firstIncomeCompleted,
        ),
        OnboardingStepState(
            milestone = OnboardingMilestone.FIRST_BILL,
            label = "Add your first bill",
            completed = firstBillCompleted,
        ),
        OnboardingStepState(
            milestone = OnboardingMilestone.FIRST_GOAL,
            label = "Set a savings goal",
            completed = firstGoalCompleted,
        ),
    )
}
