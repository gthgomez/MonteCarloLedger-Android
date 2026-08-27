package com.montecarlo.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Product-depth ledger rules: pending/posted clearing states and credit-card
 * charge routing into revolving liabilities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerRepositoryProductDepthTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun reconciledBankBalance(cents: Long) {
        repo.setBankBalance(cents)
    }

    private suspend fun creditCardSetup(
        chargeAccountName: String = "Visa",
    ): Pair<AccountEntity, DebtEntity> {
        val accountId = repo.insertAccount(
            AccountEntity(
                name = chargeAccountName,
                type = "credit",
                balanceCents = 0L,
                lastUpdated = LocalDate.now().toString(),
            )
        )
        val account = db.accountDao().getById(accountId)!!
        val debtId = repo.insertDebt(
            DebtEntity(
                name = "$chargeAccountName balance",
                balanceCents = 100_000L,
                aprBasisPoints = 2_400,
                minimumPaymentCents = 2_500L,
                dueDayOfMonth = 15,
                isActive = true,
                kind = DebtKind.REVOLVING,
                statementDayOfMonth = 22,
                minPaymentPercentBps = 300,
                minPaymentFloorCents = 2_500L,
                linkedAccountId = accountId,
            )
        )
        return account to db.debtDao().getAll().first().single { it.id == debtId }
    }

    @Test
    fun creditCharge_raisesLinkedCardDebt_withoutTouchingBankBalance() = runBlocking {
        reconciledBankBalance(500_000L)
        val (_, debt) = creditCardSetup()

        repo.insertTransaction(
            TransactionEntity(
                description = "Groceries on card",
                amount_cents = -6_000L,
                date = LocalDate.now().toString(),
                type = "expense",
                category = "groceries",
                account_id = db.accountDao().getAll().first().single { it.type == "credit" }.id,
            )
        )

        assertEquals(106_000L, db.debtDao().getAll().first().single().balanceCents)
        assertEquals(500_000L, repo.getBankBalanceCents())
    }

    @Test
    fun deletingCreditCharge_restoresCardDebt() = runBlocking {
        reconciledBankBalance(500_000L)
        val (_, debt) = creditCardSetup()

        val charge = TransactionEntity(
            description = "Fuel",
            amount_cents = -4_200L,
            date = LocalDate.now().toString(),
            type = "expense",
            category = "auto",
            account_id = debt.linkedAccountId,
        )
        repo.insertTransaction(charge)
        assertEquals(104_200L, db.debtDao().getAll().first().single().balanceCents)

        repo.deleteTransaction(charge.copy(id = db.transactionDao().getAll().first().single().id))

        assertEquals(100_000L, db.debtDao().getAll().first().single().balanceCents)
        assertEquals(500_000L, repo.getBankBalanceCents())
    }

    @Test
    fun editingChargeToUntagged_movesImpactFromCardDebtBackToBank() = runBlocking {
        reconciledBankBalance(500_000L)
        val (_, debt) = creditCardSetup()

        // Start as a cash-side expense, then tag it to the card.
        repo.insertTransaction(
            TransactionEntity(
                description = "Misc",
                amount_cents = -3_000L,
                date = LocalDate.now().toString(),
                type = "expense",
                category = "shopping",
            )
        )
        assertEquals(497_000L, repo.getBankBalanceCents())

        val stored = db.transactionDao().getAll().first().single()
        repo.updateTransaction(stored.copy(account_id = debt.linkedAccountId))

        // Tagging re-routes: cash is restored, the card liability absorbs the charge.
        assertEquals(103_000L, db.debtDao().getAll().first().single().balanceCents)
        assertEquals(500_000L, repo.getBankBalanceCents())

        repo.updateTransaction(db.transactionDao().getById(stored.id)!!.copy(account_id = null))

        // Untagging releases the liability; the cash pipeline carries the expense again.
        assertEquals(100_000L, db.debtDao().getAll().first().single().balanceCents)
        assertEquals(497_000L, repo.getBankBalanceCents())
    }

    @Test
    fun cashExpenseWithoutAccount_keepsLegacyBankDelta() = runBlocking {
        reconciledBankBalance(300_000L)

        repo.insertTransaction(
            TransactionEntity(
                description = "Cash lunch",
                amount_cents = -1_500L,
                date = LocalDate.now().toString(),
                type = "expense",
                category = "food",
            )
        )

        assertEquals(298_500L, repo.getBankBalanceCents())
        assertTrue(db.debtDao().getAll().first().isEmpty())
    }

    @Test
    fun setTransactionClearingStatus_flipsStateWithoutBalanceImpact() = runBlocking {
        reconciledBankBalance(400_000L)

        repo.insertTransaction(
            TransactionEntity(
                description = "Coffee",
                amount_cents = -650L,
                date = LocalDate.now().toString(),
                type = "expense",
                category = "food",
            )
        )
        val txnId = db.transactionDao().getAll().first().single().id
        val afterInsert = repo.getBankBalanceCents()

        repo.setTransactionClearingStatus(txnId, pending = true)
        var txn = db.transactionDao().getById(txnId)!!
        assertEquals(ClearingStatus.PENDING, ClearingStatus.normalize(txn.clearing_status))
        assertEquals(afterInsert, repo.getBankBalanceCents())

        repo.setTransactionClearingStatus(txnId, pending = false)
        txn = db.transactionDao().getById(txnId)!!
        assertEquals(ClearingStatus.POSTED, ClearingStatus.normalize(txn.clearing_status))
        assertEquals(afterInsert, repo.getBankBalanceCents())
    }

    @Test
    fun creditAccountWithoutLinkedDebt_neitherCashNorLiabilityMoves() = runBlocking {
        reconciledBankBalance(250_000L)
        val accountId = repo.insertAccount(
            AccountEntity(
                name = "Amex",
                type = "credit",
                balanceCents = 0L,
                lastUpdated = LocalDate.now().toString(),
            )
        )

        repo.insertTransaction(
            TransactionEntity(
                description = "Untethered charge",
                amount_cents = -9_000L,
                date = LocalDate.now().toString(),
                type = "expense",
                category = "travel",
                account_id = accountId,
            )
        )

        // No guessing: reconciliation surfaces the gap instead of moving either number.
        assertEquals(250_000L, repo.getBankBalanceCents())
    }

    @Test
    fun creditRefund_smallerThanBalance_reducesCardDebt() = runBlocking {
        reconciledBankBalance(500_000L)
        val (_, debt) = creditCardSetup()

        repo.insertTransaction(
            TransactionEntity(
                description = "Card refund",
                amount_cents = 4_000L,
                date = LocalDate.now().toString(),
                type = "adjustment",
                category = "refunds",
                account_id = debt.linkedAccountId,
            )
        )

        assertEquals(96_000L, db.debtDao().getAll().first().single().balanceCents)
        assertEquals(500_000L, repo.getBankBalanceCents())
    }

    @Test
    fun creditRefund_largerThanBalance_failsLoudlyWithoutMutatingAnything() = runBlocking {
        reconciledBankBalance(500_000L)
        val (_, debt) = creditCardSetup()

        try {
            repo.insertTransaction(
                TransactionEntity(
                    description = "Oversized refund",
                    amount_cents = 500_000L,
                    date = LocalDate.now().toString(),
                    type = "adjustment",
                    category = "refunds",
                    account_id = debt.linkedAccountId,
                )
            )
            fail("a refund larger than the outstanding balance must be rejected, not drive debt negative")
        } catch (_: IllegalArgumentException) {
            // Expected: explicit failure over silent negative liability.
        }

        // Nothing may have been half-mutated: debt and cash are untouched.
        assertEquals(100_000L, db.debtDao().getAll().first().single().balanceCents)
        assertEquals(500_000L, repo.getBankBalanceCents())
        assertTrue("rejected transaction must not be persisted", db.transactionDao().getAll().first().isEmpty())
    }
}
