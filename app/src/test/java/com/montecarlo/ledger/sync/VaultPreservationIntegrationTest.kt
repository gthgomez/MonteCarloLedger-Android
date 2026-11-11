package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.ui.buildLedgerBackupJson
import com.montecarlo.ledger.ui.parseLedgerBackupJson
import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.OnboardingProgress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultPreservationIntegrationTest {

  private fun openRepo(): Pair<LedgerRepository, AppDatabase> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return LedgerRepository(db) to db
    }

    @Test
    fun pull_blocksWhenAndroidOnlyGoalsExist_withoutOverwrite(): Unit = runBlocking {
        val (repo, db) = openRepo()
        try {
            db.goalDao().insertGoal(GoalEntity(name = "Emergency fund", targetAmountCents = 50_000, currentAmountCents = 10_000))
            val sync = CloudVaultSync(ApplicationProvider.getApplicationContext(), repo)
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    sync.pullVault(
                        vaultId = "demo-vault",
                        passphrase = "secret".toCharArray(),
                        serverUrl = "https://127.0.0.1:9",
                        allowLocalOverwrite = false,
                    )
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun localChanges_detectsGoalEditAfterPreservationBaseline(): Unit = runBlocking {
        val (repo, db) = openRepo()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            val store = VaultSyncStore(context)
            val emptyPreservation = LocalLedgerPreservation.fingerprint(repo)
            store.commitSyncState(
                vaultId = "v",
                serverUrl = VaultSyncStore.DEFAULT_SERVER,
                revision = 1,
                syncedFingerprint = "abc",
                androidPreservationFingerprint = emptyPreservation,
                ancestryEstablished = true,
            )
            db.goalDao().insertGoal(GoalEntity(name = "Rainy day", targetAmountCents = 1_000, currentAmountCents = 0))
            val sync = CloudVaultSync(context, repo)
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    sync.pullVault(
                        vaultId = "v",
                        passphrase = "secret".toCharArray(),
                        serverUrl = "https://127.0.0.1:9",
                        allowLocalOverwrite = false,
                    )
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun backupJsonPattern_includesAccountsLikeAppView(): Unit = runBlocking {
        val accounts = listOf(
            AccountEntity(id = 2, name = "Savings", type = "savings", balanceCents = 25_000, isDefault = false),
        )
        val json = buildLedgerBackupJson(
            exportedAtIso = "2026-11-11T12:00:00",
            uiState = AppUiState(),
            incomes = emptyList(),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            onboardingProgress = OnboardingProgress(),
            settings = emptyList(),
            rules = emptyList(),
            accounts = accounts,
        )
        assertTrue(json.contains("\"accounts\""))
        assertTrue(json.contains("Savings"))
        val snapshot = parseLedgerBackupJson(json)
        val (repo, db) = openRepo()
        try {
            repo.restoreBackup(snapshot)
            val restored = repo.getAllAccountsList()
            assertEquals(1, restored.count { it.name == "Savings" })
        } finally {
            db.close()
        }
    }
}
