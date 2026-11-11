package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultLocalServerRoundTripTest {

    private lateinit var fakeServer: FakeVaultHttpServer
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    private val vaultId = "synthetic-roundtrip"
    private val passphrase = "round-trip-pass".toCharArray()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fakeServer = FakeVaultHttpServer().also { it.start() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        repo.ensureDefaultAccountSeeded()
        db.transactionDao().insert(
            TransactionEntity(
                id = 1,
                description = "Opening",
                amount_cents = 12_000,
                date = "2026-11-01",
                type = "Adjustment",
                category = "System",
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
        fakeServer.stop()
    }

    @Test
    fun create_thenPush_thenPull_reproducesCoreLedger(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sync = CloudVaultSync(context, repo)
        val revision = sync.createVault(vaultId, passphrase, fakeServer.baseUrl)
        assertEquals(1, revision)
        assertEquals(1, fakeServer.createInvocationCount())
        assertFalse(repo.hasCustomAccounts())

        val pushedRevision = sync.pushVault(vaultId, passphrase, fakeServer.baseUrl)
        assertEquals(2, pushedRevision)
        assertEquals(1, fakeServer.pushInvocationCount())

        db.transactionDao().deleteAllTransactions()
        db.transactionDao().insert(
            TransactionEntity(
                id = 1,
                description = "Mutated",
                amount_cents = 1,
                date = "2026-11-01",
                type = "Adjustment",
                category = "System",
            ),
        )

        val pulledRevision = sync.pullVault(
            vaultId,
            passphrase,
            fakeServer.baseUrl,
            allowLocalOverwrite = true,
        )
        assertEquals(2, pulledRevision)
        assertEquals(1, fakeServer.fetchVaultInvocationCount())

        val txn = db.transactionDao().getAllTransactionsList().single()
        assertEquals("Opening", txn.description)
        assertEquals(12_000, txn.amount_cents)
    }

    @Test
    fun pull_blocksWhenReconciledPrimary_beforeServerRead(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repo.setBankBalance(15_000)
        assertTrue(repo.hasCustomAccounts())
        val sync = CloudVaultSync(context, repo)
        sync.createVault(vaultId, passphrase, fakeServer.baseUrl)
        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = false)
            }
        }
        assertEquals(0, fakeServer.fetchVaultInvocationCount())
        assertEquals(15_000L, repo.getBankBalanceCents())
        assertTrue(db.accountDao().getDefault()!!.isReconciled)
    }
}
