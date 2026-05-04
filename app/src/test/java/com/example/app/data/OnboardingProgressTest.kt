package com.example.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingProgressTest {

    @Test
    fun nextActionMilestone_startsWithReconciliation() {
        val progress = OnboardingProgress()

        assertEquals(OnboardingMilestone.RECONCILIATION, progress.nextActionMilestone())
    }
}
