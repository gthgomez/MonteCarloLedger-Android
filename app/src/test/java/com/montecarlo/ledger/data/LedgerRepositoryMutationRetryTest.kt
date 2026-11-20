package com.montecarlo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.DebtEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
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

    @Test
    fun concurrentRetriesForOneBillOccurrenceReuseTheSameTransaction() = runBlocking {
        repo.setBankBalance(50_000L)
        val dueDate = LocalDate.now().plusDays(1).toString()
        db.paymentDao().insert(
            PaymentEntity(
                id = 31,
                name = "Synthetic rent",
                amount_cents = 1_000L,
                frequency = "one-time",
                day_of_month = null,
                next_date = dueDate,
                is_active = 1,
                isAutoWithdraw = false,
            ),
        )
        repo.syncBillOccurrences()
        val occurrence = db.billOccurrenceDao().getForPayment(31).first().single()
        val expense = TransactionEntity(
            description = "Synthetic rent payment",
            amount_cents = -1_000L,
            date = dueDate,
            type = "expense",
        )

        val transactionIds = coroutineScope {
            (1..3).map {
                async(Dispatchers.IO) { repo.insertTransaction(expense, occurrence.id) }
            }.awaitAll()
        }

        assertTrue(transactionIds.distinct().size == 1)
        assertEquals(1, repo.allTransactions.first().count { it.description == expense.description })
        assertEquals(transactionIds.first().toInt(), db.billOccurrenceDao().getById(occurrence.id)?.transaction_id)
        assertEquals(1, db.billOccurrenceDao().getById(occurrence.id)?.is_paid)
        assertEquals(49_000L, repo.getBankBalanceCents())
    }

    @Test
    fun metadataWritesPreserveTheLatestAmountAndCashBalance() = runBlocking {
        val expense = recordExpense()
        repo.updateTransaction(expense.copy(amount_cents = -2_500L))

        repo.approveTransactionReview(expense.id)
        repo.setTransactionClearingStatus(expense.id, pending = true)

        val saved = db.transactionDao().getById(expense.id)!!
        assertEquals(-2_500L, saved.amount_cents)
        assertEquals("approved", saved.review_status)
        assertEquals("pending", saved.clearing_status)
        assertEquals(47_500L, repo.getBankBalanceCents())
    }

    @Test
    fun creatingReviewRuleForDeletedTransactionFailsBeforeWritingRule() = runBlocking {
        val expense = recordExpense()
        repo.deleteTransaction(expense)

        val failure = runCatching {
            repo.createRuleFromTransactionReview(expense.id, "groceries")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(repo.allTransactionRules.first().isEmpty())
        assertEquals(50_000L, repo.getBankBalanceCents())
    }

    @Test
    fun failedTransactionUpdateRollsBackTheRowAndLinkedDebtBalance() = runBlocking {
        val accountId = repo.insertAccount(
            AccountEntity(
                name = "Synthetic card",
                type = "credit",
                balanceCents = 0L,
                lastUpdated = "2026-11-01",
            ),
        )
        repo.insertDebt(
            DebtEntity(
                name = "Synthetic card debt",
                balanceCents = 10_000L,
                aprBasisPoints = 0,
                minimumPaymentCents = 1_000L,
                dueDayOfMonth = 15,
                linkedAccountId = accountId,
            ),
        )
        repo.insertTransaction(
            TransactionEntity(
                description = "Synthetic card charge",
                amount_cents = -5_000L,
                date = "2026-11-10",
                type = "expense",
                account_id = accountId,
            ),
        )
        val original = repo.allTransactions.first().single()
        assertEquals(15_000L, db.debtDao().getActive().first().single().balanceCents)

        val failure = runCatching {
            repo.updateTransaction(
                original.copy(amount_cents = 10_001L, type = "adjustment")
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(original, db.transactionDao().getById(original.id))
        assertEquals(15_000L, db.debtDao().getActive().first().single().balanceCents)
    }
}
