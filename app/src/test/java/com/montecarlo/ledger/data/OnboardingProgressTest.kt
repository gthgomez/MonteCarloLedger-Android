package com.montecarlo.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingProgressTest {

    @Test
    fun nextActionMilestone_startsWithReconciliation() {
        val progress = OnboardingProgress()

        assertEquals(OnboardingMilestone.RECONCILIATION, progress.nextActionMilestone())
    }

    @Test
    fun dismissed_doesNotCompleteOnboarding() {
        val progress = OnboardingProgress(
            reconciliationCompleted = true,
            firstIncomeCompleted = true,
            firstBillCompleted = true,
            dismissed = true,
        )

        org.junit.Assert.assertFalse(progress.isComplete)
        assertEquals(OnboardingMilestone.FIRST_GOAL, progress.nextActionMilestone())
    }

    @Test
    fun isComplete_requiresTheGoalMilestoneEvenWhenDismissed() {
        val progress = OnboardingProgress(
            reconciliationCompleted = true,
            firstIncomeCompleted = true,
            firstBillCompleted = true,
            firstGoalCompleted = true,
            dismissed = true,
        )

        org.junit.Assert.assertTrue(progress.isComplete)
    }
}
