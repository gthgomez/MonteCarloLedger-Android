package com.montecarlo.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.ui.BACKUP_SCHEMA_VERSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Skip this step" on the goal step must hide the setup card without ever
 * completing onboarding: the dismissal persists, survives milestone sync and
 * backup restore, and isComplete keeps requiring a real goal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingDismissalTest {

    private fun buildRepo(): Pair<LedgerRepository, AppDatabase> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return LedgerRepository(db) to db
    }

    @Test
    fun dismissal_persistsAndSurvivesMilestoneSync_withoutCompletingOnboarding() = runBlocking {
        val (repo, db) = buildRepo()
        try {
            assertFalse(repo.onboardingProgress.first().dismissed)

            repo.setBankBalance(50_000)
            repo.insertIncome(
                IncomeEntity(
                    id = 0,
                    name = "Salary",
                    amount_cents = 200_000,
                    frequency = "Bi-weekly",
                    day_of_month = null,
                    next_date = "2026-09-04",
                    payType = "FLAT",
                )
            )
            repo.insertPayment(
                PaymentEntity(
                    name = "Rent",
                    amount_cents = 100_000,
                    frequency = "Monthly",
                    day_of_month = 1,
                    next_date = "2026-09-01",
                )
            )
            repo.setOnboardingDismissed()
            // Startup-style resync must not resurrect (or clear) the dismissal.
            repo.syncOnboardingMilestones()

            val progress = repo.onboardingProgress.first()
            assertTrue(progress.dismissed)
            assertFalse(progress.isComplete)
            assertEquals(OnboardingMilestone.FIRST_GOAL, progress.nextActionMilestone())
        } finally {
            db.close()
        }
    }

    @Test
    fun restore_reinstatesTheDismissalFromTheSnapshot() = runBlocking {
        val (repo, db) = buildRepo()
        try {
            val snapshot = LedgerBackupSnapshot(
                schemaVersion = BACKUP_SCHEMA_VERSION,
                exportedAtIso = "2026-09-12T09:00:00",
                bankBalanceCents = 50_000,
                isBalanceReconciled = true,
                onboardingProgress = OnboardingProgress(reconciliationCompleted = true, dismissed = true),
                incomes = emptyList(),
                payments = emptyList(),
                transactions = emptyList(),
                billOccurrences = emptyList(),
            )

            repo.restoreBackup(snapshot)

            val progress = repo.onboardingProgress.first()
            assertTrue(progress.dismissed)
            assertTrue(progress.reconciliationCompleted)
            assertFalse(progress.isComplete)
        } finally {
            db.close()
        }
    }
}
