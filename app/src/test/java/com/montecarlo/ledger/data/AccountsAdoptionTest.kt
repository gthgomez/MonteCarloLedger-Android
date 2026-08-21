package com.montecarlo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Adoption contract for the accounts surface: the default account row must stay in
 * lockstep with the primary balance pipeline, restores must re-sync it, and the
 * pipeline can never be left without a default account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountsAdoptionTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    private suspend fun defaultAccount(): AccountEntity = db.accountDao().getDefault().let {
        assertNotNull("default account must exist", it)
        it!!
    }

    @Test
    fun seeding_isIdempotentAndUsesLegacySettingsValues() = runBlocking {
        val first = defaultAccount()
        repo.ensureDefaultAccountSeeded()
        assertEquals(first.id, defaultAccount().id)
        assertEquals(0L, first.balanceCents)
    }

    @Test
    fun settingBankBalance_dualWritesTheDefaultAccountRow() = runBlocking {
        repo.setBankBalance(42_500)

        val account = defaultAccount()
        assertEquals(42_500L, account.balanceCents)
        assertTrue(account.isReconciled)
        assertEquals(42_500L, repo.getBankBalanceCents())
    }

    @Test
    fun deletingTheDefaultAccountIsBlocked() = runBlocking {
        val result = runCatching { repo.deleteAccountSafe(defaultAccount()) }

        assertTrue(result.isFailure)
        assertNotNull(db.accountDao().getDefault())
    }

    @Test
    fun setDefaultAccount_swapsTheFlagAndAdoptsItsBalance() = runBlocking {
        repo.setBankBalance(10_000L)
        val savingsId = repo.insertAccount(
            AccountEntity(name = "Savings", type = "savings", balanceCents = 90_000L, isDefault = false, lastUpdated = "2026-08-21")
        )

        repo.setDefaultAccount(savingsId)

        val accounts = db.accountDao().getAll().first()
        val promoted = accounts.single { it.isDefault }
        assertEquals(savingsId, promoted.id)
        assertEquals("Primary account", accounts.single { !it.isDefault }.name)
        // The promoted account drives the primary pipeline now.
        assertEquals(90_000L, repo.getBankBalanceCents())
    }

    @Test
    fun legacyRestore_resyncsDefaultAccountFromRestoredSettings() = runBlocking {
        repo.setBankBalance(10_000L)
        val v4SnapshotJson = """{
          "schemaVersion": 4,
          "summary": {"bankBalanceCents": 77700, "isBalanceReconciled": true},
          "settings": [],
          "rules": [], "incomes": [], "payments": [], "transactions": [],
          "billOccurrences": [], "assets": [], "goals": [],
          "categoryBudgets": [], "debts": []
        }"""

        repo.restoreBackup(
            com.montecarlo.ledger.ui.parseLedgerBackupJson(v4SnapshotJson)
        )
        repo.ensureDefaultAccountSeeded()

        assertEquals(77_700L, repo.getBankBalanceCents())
        val account = defaultAccount()
        assertEquals(77_700L, account.balanceCents)
        assertTrue(account.isReconciled)
    }
}
