package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun pull_blocksWhenAndroidOnlyGoalsPresent_evenIfPreservationFingerprintMatches(): Unit = runBlocking {
        val (repo, db) = openRepo()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            db.goalDao().insertGoal(
                GoalEntity(
                    name = "Emergency",
                    targetAmountCents = 10_000,
                    currentAmountCents = 0,
                    deadline = "2026-12-01",
                    createdAt = "2026-11-01",
                ),
            )
            val vaultFp = VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp"))
            val preservationFp = LocalLedgerPreservation.fingerprint(repo)
            assertEquals(preservationFp, LocalLedgerPreservation.fingerprint(repo))
            VaultSyncStore(context).commitSyncState(
                vaultId = "v",
                serverUrl = VaultSyncStore.DEFAULT_SERVER,
                revision = 1,
                syncedFingerprint = vaultFp,
                androidPreservationFingerprint = preservationFp,
                ancestryEstablished = true,
            )
            val sync = CloudVaultSync(context, repo)
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    sync.pullVault("v", "secret".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun pull_blocksWhenCoreLedgerEditedSinceSync_withoutAndroidOnlyRows(): Unit = runBlocking {
        val (repo, db) = openRepo()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Opening",
                    amount_cents = 5_000,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            val vaultFp = VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp"))
            VaultSyncStore(context).commitSyncState(
                vaultId = "v",
                serverUrl = VaultSyncStore.DEFAULT_SERVER,
                revision = 1,
                syncedFingerprint = vaultFp,
                androidPreservationFingerprint = LocalLedgerPreservation.fingerprint(repo),
                ancestryEstablished = true,
            )
            db.transactionDao().deleteAllTransactions()
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Opening",
                    amount_cents = 7_500,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            val sync = CloudVaultSync(context, repo)
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    sync.pullVault("v", "x".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun pull_blocksWhenSavedVaultFingerprintMissing_noAndroidOnlyRows(): Unit = runBlocking {
        val (repo, db) = openRepo()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            val txBefore = db.transactionDao().getAllTransactionsList()
            assertTrue(txBefore.isEmpty())
            VaultSyncStore(context).seedEstablishedAncestryForTests(
                vaultId = "v",
                revision = 1,
                omitSyncedFingerprint = true,
            )
            val sync = CloudVaultSync(context, repo)
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    sync.pullVault("v", "x".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
                }
            }
            assertEquals(txBefore, db.transactionDao().getAllTransactionsList())
        } finally {
            db.close()
        }
    }

    @Test
    fun pull_blocksWhenLocalVaultFingerprintExportFails_noAndroidOnlyRows(): Unit = runBlocking {
        val (repo, db) = openRepo()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            db.debtDao().insert(
                DebtEntity(
                    name = "Bad",
                    balanceCents = 1,
                    aprBasisPoints = 0,
                    minimumPaymentCents = 1,
                    dueDayOfMonth = 0,
                ),
            )
            val debtsBefore = db.debtDao().getAll().first()
            assertEquals(1, debtsBefore.size)
            VaultSyncStore(context).seedEstablishedAncestryForTests(
                vaultId = "v",
                revision = 1,
                syncedFingerprint = "stale-baseline",
            )
            val sync = CloudVaultSync(context, repo)
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    sync.pullVault("v", "x".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
                }
            }
            assertEquals(debtsBefore, db.debtDao().getAll().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun pull_passesCoreFingerprintGuardWhenBaselinesMatch_noAndroidOnlyRows(): Unit = runBlocking {
        val (repo, db) = openRepo()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Opening",
                    amount_cents = 100,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            val vaultFp = VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp"))
            VaultSyncStore(context).commitSyncState(
                vaultId = "v",
                serverUrl = VaultSyncStore.DEFAULT_SERVER,
                revision = 1,
                syncedFingerprint = vaultFp,
                androidPreservationFingerprint = LocalLedgerPreservation.fingerprint(repo),
                ancestryEstablished = true,
            )
            val txsBefore = db.transactionDao().getAllTransactionsList()
            val sync = CloudVaultSync(context, repo)
            try {
                sync.pullVault("v", "secret".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
            } catch (e: VaultLocalChangesException) {
                fail("Matching core baselines must pass the pull guard: ${e.message}")
            } catch (_: Exception) {
                // Guard passed; expected without a vault server
            }
            assertEquals(txsBefore, db.transactionDao().getAllTransactionsList())
        } finally {
            db.close()
        }
    }

    @Test
    fun push_guardSmoke_doesNotThrowVaultLocalChangesOnPendingCoreEdits(): Unit = runBlocking {
        val (repo, db) = openRepo()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Opening",
                    amount_cents = 1_000,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            val vaultFp = VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp"))
            VaultSyncStore(context).commitSyncState(
                vaultId = "v",
                serverUrl = VaultSyncStore.DEFAULT_SERVER,
                revision = 1,
                syncedFingerprint = vaultFp,
                androidPreservationFingerprint = LocalLedgerPreservation.fingerprint(repo),
                ancestryEstablished = true,
            )
            db.transactionDao().deleteAllTransactions()
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Edited",
                    amount_cents = 2_000,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            val sync = CloudVaultSync(context, repo)
            try {
                sync.pushVault("v", "secret".toCharArray(), "https://127.0.0.1:9")
            } catch (e: VaultLocalChangesException) {
                fail("Push must not use destructive-pull guards: ${e.message}")
            } catch (_: Exception) {
                // Guard smoke only: network/server errors are expected; not a successful upload E2E.
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun vaultRestore_wipesAndroidOnlyRecordsFromRoom(): Unit = runBlocking {
        val (repo, db) = openRepo()
        try {
            db.goalDao().insertGoal(GoalEntity(name = "Fund", targetAmountCents = 1, currentAmountCents = 0))
            db.categoryBudgetDao().insert(
                CategoryBudgetEntity(
                    category = "Food",
                    limitCents = 5000,
                    enabled = 1,
                    createdAt = "2026-11-01",
                ),
            )
            val vaultJson = repo.exportCrossPlatformVaultJson("2026-11-11")
            repo.restoreCrossPlatformVaultJson(vaultJson)
            assertTrue(db.goalDao().getAllGoals().first().isEmpty())
            assertTrue(db.categoryBudgetDao().getAll().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun preservationFingerprint_changesWhenGoalDeadlineChanges(): Unit = runBlocking {
        val (repo, db) = openRepo()
        try {
            val goal = GoalEntity(
                name = "Trip",
                targetAmountCents = 100,
                currentAmountCents = 0,
                deadline = "2026-06-01",
                createdAt = "2026-01-01",
            )
            db.goalDao().insertGoal(goal)
            val before = LocalLedgerPreservation.fingerprint(repo)
            val beforeJson = JSONObject(LocalLedgerPreservation.preservationSnapshotJson(repo))
            val goals = db.goalDao().getAllGoals().first()
            db.goalDao().updateGoal(goals.single().copy(deadline = "2026-07-01"))
            val after = LocalLedgerPreservation.fingerprint(repo)
            val afterJson = JSONObject(LocalLedgerPreservation.preservationSnapshotJson(repo))
            assertNotEquals(before, after)
            assertEquals("2026-06-01", beforeJson.getJSONArray("goals").getJSONObject(0).getString("deadline"))
            assertEquals("2026-07-01", afterJson.getJSONArray("goals").getJSONObject(0).getString("deadline"))
        } finally {
            db.close()
        }
    }
}
