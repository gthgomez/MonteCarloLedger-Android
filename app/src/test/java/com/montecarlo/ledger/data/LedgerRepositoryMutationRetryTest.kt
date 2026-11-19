package com.montecarlo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerRepositoryMutationRetryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).build()
        repo = LedgerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun recordExpense(): TransactionEntity {
        repo.setBankBalance(50_000L)
        repo.insertTransaction(
            TransactionEntity(
                description = "Synthetic groceries",
                amount_cents = -1_000L,
                date = "2026-11-10",
                type = "expense",
            ),
        )
        assertEquals(49_000L, repo.getBankBalanceCents())
        return repo.allTransactions.first().single { it.type == "expense" }
    }

    @Test
    fun repeatedDeletionReversesTheBalanceOnlyOnce() = runBlocking {
        val expense = recordExpense()

        repo.deleteTransaction(expense)
        repo.deleteTransaction(expense)

        assertEquals(50_000L, repo.getBankBalanceCents())
        assertTrue(repo.allTransactions.first().none { it.id == expense.id })
    }

    @Test
    fun deletingFromAStaleViewReversesTheStoredAmount() = runBlocking {
        val expense = recordExpense()

        repo.updateTransaction(expense.copy(amount_cents = -2_500L))
        assertEquals(47_500L, repo.getBankBalanceCents())
        repo.deleteTransaction(expense)

        assertEquals(50_000L, repo.getBankBalanceCents())
        assertTrue(repo.allTransactions.first().none { it.id == expense.id })
    }

    @Test
    fun updatingAnAlreadyDeletedTransactionCannotChangeTheBalance() = runBlocking {
        val expense = recordExpense()
        repo.deleteTransaction(expense)

        val failure = runCatching {
            repo.updateTransaction(expense.copy(amount_cents = -2_500L))
        }.exceptionOrNull()

        assertEquals(50_000L, repo.getBankBalanceCents())
        assertTrue(repo.allTransactions.first().none { it.id == expense.id })
        assertTrue(failure is IllegalArgumentException)
    }
}
