package com.montecarlo.ledger.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.security.SecurityUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountEncryptedBackupRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repo = LedgerRepository(db)
            repo.ensureDefaultAccountSeeded()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun encryptedAppViewBackup_preservesAccountAndTransactionLink() = runBlocking {
        repo.setBankBalance(88_000)
        val savingsId = repo.insertAccount(
            AccountEntity(
                name = "Savings",
                type = "savings",
                balanceCents = 120_000,
                isDefault = false,
                isReconciled = true,
                lastUpdated = "2026-11-11",
            ),
        )
        db.transactionDao().insert(
            TransactionEntity(
                description = "Transfer",
                amount_cents = -5_000,
                date = "2026-11-10",
                type = "expense",
                category = "transfer",
                account_id = savingsId,
            ),
        )

        val accountsBefore = db.accountDao().getAll().first().sortedBy { it.id }
        assertEquals("fixture must include primary + savings", 2, accountsBefore.size)
        val txBefore = db.transactionDao().getAllTransactionsList().single { it.description == "Transfer" }

        val plainJson = buildAppViewBackupPayload(
            exportedAtIso = "2026-11-11T12:00:00",
            uiState = AppUiState(bankBalanceCents = repo.getBankBalanceCents()),
            incomes = repo.allIncome.first(),
            payments = repo.allPayments.first(),
            transactions = repo.allTransactions.first(),
            billOccurrences = repo.allBillOccurrences.first(),
            accounts = accountsBefore,
            onboardingProgress = OnboardingProgress(),
            settings = repo.allSettings.first(),
            rules = repo.allTransactionRules.first(),
        )
        val password = "synthetic-ui-backup".toCharArray()
        val encrypted = SecurityUtils.encryptWithHmac(plainJson, password)
        val decrypted = SecurityUtils.decrypt(encrypted, password)
        val parsedBeforeRestore = parseLedgerBackupJson(decrypted)
        assertEquals("backup JSON must carry both accounts", 2, parsedBeforeRestore.accounts.size)

        repo.setBankBalance(1)
        db.accountDao().deleteAll()

        repo.restoreBackup(parseLedgerBackupJson(decrypted))
        repo.ensureDefaultAccountSeeded()

        val accountsAfter = db.accountDao().getAll().first().sortedBy { it.id }
        assertEquals(2, accountsAfter.size)
        val restoredSavings = accountsAfter.single { it.name == "Savings" }
        assertEquals(120_000L, restoredSavings.balanceCents)
        assertTrue(restoredSavings.isReconciled)
        val restoredPrimary = accountsAfter.single { it.isDefault }
        assertEquals(88_000L, restoredPrimary.balanceCents)

        val txAfter = db.transactionDao().getAllTransactionsList().single { it.description == "Transfer" }
        assertEquals(
            "transaction must stay linked to savings after restore",
            restoredSavings.id,
            txAfter.account_id,
        )
        assertEquals(txBefore.amount_cents, txAfter.amount_cents)
    }

    @Test
    fun writeBackupBytes_nullStream_throwsBeforeEncryptionRoundTrip() {
        val out = ByteArrayOutputStream()
        writeBackupBytes(out, byteArrayOf(9))
        assertThrows(IllegalArgumentException::class.java) {
            writeBackupBytes(null, byteArrayOf(1))
        }
        assertEquals(9, out.toByteArray().single().toInt())
    }
}
