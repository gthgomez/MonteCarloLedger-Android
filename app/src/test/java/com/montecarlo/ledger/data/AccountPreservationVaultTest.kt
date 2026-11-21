package com.montecarlo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.sync.CloudVaultSync
import com.montecarlo.ledger.sync.LocalLedgerPreservation
import com.montecarlo.ledger.sync.VaultLocalChangesException
import com.montecarlo.ledger.sync.VaultSnapshotFingerprint
import com.montecarlo.ledger.sync.VaultSyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountPreservationVaultTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        repo.ensureDefaultAccountSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun primaryAccount(): AccountEntity =
        db.accountDao().getDefault() ?: error("default account missing")

    private fun AccountEntity.snapshot() =
        listOf(id, name, type, balanceCents, isReconciled, isDefault, lastUpdated)

    @Test
    fun hasCustomAccounts_falseForPristinePrimaryOnly() = runBlocking {
        val before = primaryAccount().snapshot()
        assertFalse(repo.hasCustomAccounts())
        assertFalse(LocalLedgerPreservation.hasPreservationRisk(repo))
        assertEquals(before, primaryAccount().snapshot())
    }

    @Test
    fun hasCustomAccounts_trueWhenPrimaryHasReconciledNonZeroBalance() = runBlocking {
        val beforeId = primaryAccount().id
        repo.setBankBalance(42_500)
        val after = primaryAccount()
        assertEquals(beforeId, after.id)
        assertEquals(42_500L, after.balanceCents)
        assertTrue(after.isReconciled)
        assertTrue(repo.hasCustomAccounts())
    }

    @Test
    fun hasCustomAccounts_trueWhenTransactionLinkedToLonePrimary() = runBlocking {
        val primary = primaryAccount()
        assertEquals(0L, primary.balanceCents)
        repo.insertTransaction(
            TransactionEntity(
                description = "Tagged",
                amount_cents = -100,
                date = "2026-11-01",
                type = "expense",
                category = "misc",
                account_id = primary.id,
            ),
        )
        assertTrue(repo.hasCustomAccounts())
    }

    @Test
    fun pull_blocksOnReconciledPrimary_withoutGoalsOrFingerprintMismatch() = runBlocking {
        repo.setBankBalance(50_000)
        val accountBefore = primaryAccount()
        val vaultFp = VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp"))
        assertEquals(vaultFp, VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp")))
        VaultSyncStore(context).commitSyncState(
            vaultId = "v",
            serverUrl = VaultSyncStore.DEFAULT_SERVER,
            revision = 1,
            syncedFingerprint = vaultFp,
            androidPreservationFingerprint = LocalLedgerPreservation.fingerprint(repo),
            ancestryEstablished = true,
        )
        val sync = CloudVaultSync(context, repo)
        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                sync.pullVault("v", "secret".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
            }
        }
        val accountAfter = primaryAccount()
        assertEquals(accountBefore.snapshot(), accountAfter.snapshot())
        assertEquals(accountBefore.balanceCents, accountAfter.balanceCents)
        assertEquals(accountBefore.isReconciled, accountAfter.isReconciled)
    }

    @Test
    fun vaultRestore_reseedsPrimaryAndDropsReconciledState_whenInvokedDirectly() = runBlocking {
        repo.setBankBalance(33_000)
        val before = primaryAccount()
        assertTrue(before.isReconciled)
        assertEquals(33_000L, before.balanceCents)
        val vaultJson = repo.exportCrossPlatformVaultJson("2026-11-11")
        repo.restoreCrossPlatformVaultJson(vaultJson)
        val after = db.accountDao().getDefault()
        assertNotNull(after)
        assertEquals("Primary account", after!!.name)
        assertEquals(0L, after.balanceCents)
        assertFalse(after.isReconciled)
    }
    @Test
    fun vaultRoundTripPreservesLinkedDebtAndIndependentForecastOracle() = runBlocking {
        val fixture = javaClass.getResourceAsStream("/households/vault-payment-identity.json")
            ?.bufferedReader()?.use { JSONObject(it.readText()) } ?: error("Missing household fixture")
        repo.restoreCrossPlatformVaultJson(fixture.getJSONObject("ledger").toString())
        val exported = repo.exportCrossPlatformVaultJson("2028-02-01T00:00:00Z")
        repeat(2) { repo.restoreCrossPlatformVaultJson(exported) }
        val debts = db.debtDao().getAll().first().associateBy { it.name }
        val payment = db.paymentDao().getAllPaymentsList().single()
        assertEquals(payment.id, debts.getValue("Car loan").linkedPaymentId)
        assertFalse(debts.getValue("Archived").isActive)
        val snapshot = JSONObject(exported)
        assertEquals("payment-car", snapshot.getJSONArray("payments").getJSONObject(0).getString("id"))
        assertEquals("opening", snapshot.getJSONArray("transactions").getJSONObject(0).getString("id"))
        val debtRows = snapshot.getJSONArray("liabilities")
        val linkedDebt = (0 until debtRows.length()).map { debtRows.getJSONObject(it) }
            .single { it.getString("id") == "debt-car" }
        assertEquals("payment-car", linkedDebt.getString("linked_payment_id"))
        val inputs = ContractScenarioBridge.Inputs(
            startingBalanceCents = db.transactionDao().getTotalBalanceCents().first()!!,
            asOf = LocalDate.parse(fixture.getString("as_of")),
            horizonDays = fixture.getInt("horizon_days"),
            simulation = ContractSimulationParams(
                seed = 7L, surpriseProbabilityPpm = 0, reserveCents = fixture.getLong("reserve_cents"),
            ),
            incomes = db.incomeDao().getAllIncomesList(),
            payments = db.paymentDao().getAllPaymentsList(),
            billOccurrences = db.billOccurrenceDao().getAllOccurrencesList(),
            debts = debts.values.toList(),
        )
        val linked = ContractRunner.run(ContractScenarioBridge.build(inputs))
        assertEquals(70_000L, linked.forecast.endingBalanceCents)
        assertEquals(60_000L, requireNotNull(linked.risk).safeToSpendCents)
        val invalid = JSONObject(exported)
        val liabilities = invalid.getJSONArray("liabilities")
        for (index in 0 until liabilities.length()) {
            liabilities.getJSONObject(index).put("linked_payment_id", "payment-car")
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.restoreCrossPlatformVaultJson(invalid.toString()) }
        }
        assertEquals(exported, repo.exportCrossPlatformVaultJson("2028-02-01T00:00:00Z"))

        linkedDebt.remove("linked_payment_id")
        repo.restoreCrossPlatformVaultJson(snapshot.toString())
        val unlinked = ContractRunner.run(ContractScenarioBridge.build(inputs.copy(
            debts = db.debtDao().getAll().first(),
        )))
        assertEquals(20_000L, unlinked.forecast.endingBalanceCents)
        assertEquals(10_000L, requireNotNull(unlinked.risk).safeToSpendCents)
    }
}
