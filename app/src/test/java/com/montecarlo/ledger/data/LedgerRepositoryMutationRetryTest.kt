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

    private suspend fun seedLinkedPayment(
        paymentId: Int = 7,
        occurrenceId: Int = 41,
        transactionId: Int = 91,
        dueDate: String = "2026-03-15",
        originalDueDate: String = dueDate,
        isUserModified: Int = 0,
    ): TransactionEntity {
        db.paymentDao().insert(
            PaymentEntity(
                id = paymentId,
                name = "Card bill",
                amount_cents = 30_000L,
                frequency = "Monthly",
                day_of_month = LocalDate.parse(dueDate).dayOfMonth,
                next_date = dueDate,
                is_active = 1,
                isAutoWithdraw = true,
            ),
        )
        val transaction = TransactionEntity(
            id = transactionId,
            description = "Card bill",
            amount_cents = -30_000L,
            date = dueDate,
            type = "expense",
            category = "bills",
        )
        db.transactionDao().insert(transaction)
        db.billOccurrenceDao().insert(
            BillOccurrenceEntity(
                id = occurrenceId,
                payment_id = paymentId,
                due_date = dueDate,
                amount_cents = 30_000L,
                is_paid = 1,
                transaction_id = transactionId,
                created_at = "2026-03-01T00:00:00",
                original_due_date = originalDueDate,
                is_user_modified = isUserModified,
            ),
        )
        repo.setBankBalance(50_000L)
        return transaction
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

    @Test
    fun deletingLinkedPaymentReopensOccurrenceAndRepeatedDeleteIsIdempotent() = runBlocking {
        val transaction = seedLinkedPayment(
            dueDate = "2026-03-25",
            originalDueDate = "2026-03-15",
            isUserModified = 1,
        )
        val occurrenceBefore = db.billOccurrenceDao().getById(41)!!

        repo.deleteTransaction(transaction)

        assertEquals(null, db.transactionDao().getById(91))
        val reopened = db.billOccurrenceDao().getById(41)!!
        assertEquals(0, reopened.is_paid)
        assertEquals(null, reopened.transaction_id)
        assertEquals(occurrenceBefore.due_date, reopened.due_date)
        assertEquals(occurrenceBefore.original_due_date, reopened.original_due_date)
        assertEquals(occurrenceBefore.is_user_modified, reopened.is_user_modified)
        assertEquals(occurrenceBefore.amount_cents, reopened.amount_cents)
        assertEquals(occurrenceBefore.created_at, reopened.created_at)
        assertEquals(80_000L, repo.getBankBalanceCents())

        repo.deleteTransaction(transaction)

        assertEquals(80_000L, repo.getBankBalanceCents())
        assertEquals(0, db.billOccurrenceDao().getById(41)!!.is_paid)
        assertEquals(null, db.billOccurrenceDao().getById(41)!!.transaction_id)

        val replacementId = repo.insertTransaction(
            TransactionEntity(
                description = "Card bill",
                amount_cents = -30_000L,
                date = "2026-03-16",
                type = "expense",
            ),
            41,
        )
        assertTrue(replacementId != 91L)
        assertEquals(1, repo.allTransactions.first().count { it.type == "expense" })
        assertEquals(replacementId.toInt(), db.billOccurrenceDao().getById(41)!!.transaction_id)
        assertEquals(1, db.billOccurrenceDao().getById(41)!!.is_paid)
        assertEquals(50_000L, repo.getBankBalanceCents())

        val repeatedId = repo.insertTransaction(
            TransactionEntity(
                description = "Card bill",
                amount_cents = -30_000L,
                date = "2026-03-16",
                type = "expense",
            ),
            41,
        )
        assertEquals(replacementId, repeatedId)
        assertEquals(1, repo.allTransactions.first().count { it.type == "expense" })
        assertEquals(50_000L, repo.getBankBalanceCents())

        repo.deleteTransaction(db.transactionDao().getById(replacementId.toInt())!!)

        assertEquals(80_000L, repo.getBankBalanceCents())
        assertEquals(0, db.billOccurrenceDao().getById(41)!!.is_paid)
        assertEquals(null, db.billOccurrenceDao().getById(41)!!.transaction_id)
    }

    @Test
    fun linkedPaymentEditRejectsAmountTypeAndAccountButAllowsNoteDateAndCategory() = runBlocking {
        val transaction = seedLinkedPayment()

        val amountFailure = runCatching {
            repo.updateTransaction(transaction.copy(amount_cents = -40_000L))
        }.exceptionOrNull()
        assertTrue(amountFailure is IllegalArgumentException)
        assertTrue(amountFailure!!.message!!.contains("linked to a bill"))
        assertEquals(transaction, db.transactionDao().getById(91))
        assertEquals(50_000L, repo.getBankBalanceCents())

        val typeFailure = runCatching {
            repo.updateTransaction(transaction.copy(type = "adjustment"))
        }.exceptionOrNull()
        assertTrue(typeFailure is IllegalArgumentException)
        assertEquals(transaction, db.transactionDao().getById(91))

        val accountFailure = runCatching {
            repo.updateTransaction(transaction.copy(account_id = 1L))
        }.exceptionOrNull()
        assertTrue(accountFailure is IllegalArgumentException)
        assertEquals(transaction, db.transactionDao().getById(91))

        repo.updateTransaction(
            transaction.copy(
                description = "Card bill (corrected)",
                date = "2026-03-16",
                category = "card",
            )
        )
        val saved = db.transactionDao().getById(91)!!
        assertEquals("Card bill (corrected)", saved.description)
        assertEquals("2026-03-16", saved.date)
        assertEquals("card", saved.category)
        assertEquals(-30_000L, saved.amount_cents)
        assertEquals(91, db.billOccurrenceDao().getById(41)!!.transaction_id)
        assertEquals(50_000L, repo.getBankBalanceCents())
    }

    @Test
    fun skipAndRescheduleCannotSilentlyDetachOrMoveARecordedPayment() = runBlocking {
        val transaction = seedLinkedPayment()

        val skipFailure = runCatching { repo.skipBillOccurrence(41) }.exceptionOrNull()
        assertTrue(skipFailure is IllegalArgumentException)
        assertEquals(1, db.billOccurrenceDao().getById(41)!!.is_paid)
        assertEquals(91, db.billOccurrenceDao().getById(41)!!.transaction_id)
        assertEquals(50_000L, repo.getBankBalanceCents())

        val moveFailure = runCatching {
            repo.rescheduleBillOccurrence(41, "2026-03-20")
        }.exceptionOrNull()
        assertTrue(moveFailure is IllegalArgumentException)
        assertEquals("2026-03-15", db.billOccurrenceDao().getById(41)!!.due_date)

        repo.deleteTransaction(transaction)
        repo.skipBillOccurrence(41)
        val skipped = db.billOccurrenceDao().getById(41)!!
        assertEquals(1, skipped.is_paid)
        assertEquals(null, skipped.transaction_id)
        assertEquals(80_000L, repo.getBankBalanceCents())
    }

    @Test
    fun occurrenceSyncReopensLegacyPaidRowsWhoseSettlingTransactionIsMissing() = runBlocking {
        val dueDate = LocalDate.now().plusDays(1).toString()
        val transaction = seedLinkedPayment(
            paymentId = 8,
            occurrenceId = 42,
            transactionId = 93,
            dueDate = dueDate,
        )
        db.transactionDao().delete(transaction)
        assertEquals(1, db.billOccurrenceDao().getById(42)!!.is_paid)
        assertEquals(93, db.billOccurrenceDao().getById(42)!!.transaction_id)

        repo.syncBillOccurrences()

        val repaired = db.billOccurrenceDao().getById(42)!!
        assertEquals(0, repaired.is_paid)
        assertEquals(null, repaired.transaction_id)
        assertEquals(dueDate, repaired.due_date)
        assertEquals(dueDate, repaired.original_due_date)
    }
}
