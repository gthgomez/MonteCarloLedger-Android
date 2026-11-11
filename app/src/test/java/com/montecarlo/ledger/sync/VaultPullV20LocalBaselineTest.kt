package com.montecarlo.ledger.sync

import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun pull_v20Payload_persistsLocalReexportBaseline_repeatGuardedPullSucceeds() {
        val vaultId = "v20-baseline"
        val passphrase = "v20-pass".toCharArray()
        val fakeServer = FakeVaultHttpServer()
        var db: AppDatabase? = null
        try {
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                VaultSyncStore(context).clear()
                fakeServer.start()
                db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                val repo = LedgerRepository(db!!)
                val sync = CloudVaultSync(context, repo)
                repo.ensureDefaultAccountSeeded()
                db!!.transactionDao().insert(
                    TransactionEntity(
                        id = 1,
                        description = "Local placeholder",
                        amount_cents = 1,
                        date = "2026-11-01",
                        type = "Adjustment",
                        category = "System",
                    ),
                )
                seedServerWithV20Payload(
                    fakeServer = fakeServer,
                    vaultId = vaultId,
                    passphrase = passphrase,
                    plaintext = REMOTE_V20_PAYLOAD,
                )

                val remotePayloadFp = VaultSnapshotFingerprint.ofSnapshotJson(REMOTE_V20_PAYLOAD)
                sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = true)
                val store = VaultSyncStore(context)
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
        } finally {
            db?.close()
            fakeServer.stop()
        }
    }

    private fun seedServerWithV20Payload(
        fakeServer: FakeVaultHttpServer,
        vaultId: String,
        passphrase: CharArray,
        plaintext: String,
    ) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val keys = VaultCrypto.deriveKeys(String(passphrase), salt)
        val encrypted = VaultCrypto.encrypt(plaintext, keys.encKey)
        val client = VaultHttpClient(fakeServer.baseUrl)
        client.createVault(
            vaultId = vaultId,
            authToken = keys.authToken,
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ciphertextB64 = encrypted.ciphertextB64,
            nonceB64 = encrypted.nonceB64,
        )
    }

    companion object {
        private val REMOTE_V20_PAYLOAD = """
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
    }
}
