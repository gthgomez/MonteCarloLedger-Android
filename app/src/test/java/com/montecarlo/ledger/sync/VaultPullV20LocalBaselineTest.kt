package com.montecarlo.ledger.sync

import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.LedgerRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Remote v2.0 payloads normalize to local 2.1 export on restore. Sync baseline must be the in-tx local
 * re-export fingerprint, not the remote JSON hash (version/field/id drift).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultPullV20LocalBaselineTest {

    private lateinit var fakeServer: FakeVaultHttpServer
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var sync: CloudVaultSync
    private val vaultId = "v20-baseline"
    private val passphrase = "v20-pass".toCharArray()

    private val remoteV20Payload = """
        {
          "version": "2.0",
          "updated_at": "2026-01-01T00:00:00Z",
          "current_balance_cents": 100000,
          "incomes": [{
            "id": 42,
            "name": "Job",
            "amount_cents": 50000,
            "frequency": "Monthly",
            "last_payday": "2026-01-15",
            "next_payday": "2026-02-15"
          }],
          "payments": [],
          "transactions": [{
            "id": 1,
            "amount_cents": 100000,
            "category": "System",
            "type": "Adjustment",
            "date": "2026-11-01",
            "description": "Opening"
          }],
          "bill_occurrences": [],
          "liabilities": [{
            "id": "stable-remote-id-7",
            "name": "Card",
            "balance_cents": 1000,
            "apr_basis_points": 1999,
            "min_payment_cents": 2500,
            "due_day_of_month": 15,
            "kind": "revolving"
          }]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            fakeServer = FakeVaultHttpServer().also { it.start() }
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repo = LedgerRepository(db)
            sync = CloudVaultSync(context, repo)
            repo.ensureDefaultAccountSeeded()
            sync.createVault(vaultId, passphrase, fakeServer.baseUrl)
            pushPlaintextToServer(remoteV20Payload)
        }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        if (::fakeServer.isInitialized) fakeServer.stop()
    }

    private suspend fun pushPlaintextToServer(plaintext: String) {
        val store = VaultSyncStore(ApplicationProvider.getApplicationContext())
        val client = VaultHttpClient(fakeServer.baseUrl)
        val metadata = client.fetchMetadata(vaultId)!!
        val salt = Base64.decode(metadata.kdfSaltB64, Base64.DEFAULT)
        val keys = VaultCrypto.deriveKeys(String(passphrase), salt)
        val encrypted = VaultCrypto.encrypt(plaintext, keys.encKey)
        client.pushVault(
            vaultId = vaultId,
            authToken = keys.authToken,
            expectedRevision = store.revision,
            ciphertextB64 = encrypted.ciphertextB64,
            nonceB64 = encrypted.nonceB64,
        )
    }

    @Test
    fun pull_v20Payload_persistsLocalReexportBaseline_repeatGuardedPullSucceeds() = runBlocking {
        val remotePayloadFp = VaultSnapshotFingerprint.ofSnapshotJson(remoteV20Payload)
        sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = true)
        val store = VaultSyncStore(ApplicationProvider.getApplicationContext())
        val localFp = VaultDestructivePullGuard.currentCoreFingerprint(repo)
        assertNotNull(localFp)
        assertEquals(localFp, store.syncedFingerprint)
        assertNotEquals(remotePayloadFp, store.syncedFingerprint)
        val reExportFp = VaultSnapshotFingerprint.ofSnapshotJson(
            repo.exportCrossPlatformVaultJson("verify"),
        )
        assertEquals(reExportFp, store.syncedFingerprint)
        sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = false)
    }
}
