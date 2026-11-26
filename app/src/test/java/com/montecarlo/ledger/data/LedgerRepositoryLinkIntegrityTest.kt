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
 * Restore-time and repository-time integrity checks for bill links and debt links.
 *
 * The encrypted Android backup predates the canonical-vault checks. These tests pin the
 * fail-closed subset that is now shared: malformed exact links, duplicate original slots and
 * duplicate active debt links. Legacy dangling links stay repairable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerRepositoryLinkIntegrityTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = LedgerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapshot(
        payments: List<PaymentEntity> = emptyList(),
        transactions: List<TransactionEntity> = emptyList(),
        occurrences: List<BillOccurrenceEntity> = emptyList(),
        debts: List<DebtEntity> = emptyList(),
    ) = LedgerBackupSnapshot(
        schemaVersion = 6,
        exportedAtIso = "2026-11-20T09:00:00",
        bankBalanceCents = 0L,
        isBalanceReconciled = false,
        onboardingProgress = OnboardingProgress(),
        incomes = emptyList(),
        payments = payments,
        transactions = transactions,
        billOccurrences = occurrences,
        debts = debts,
    )

    private fun monthlyPayment(id: Int = 1, amountCents: Long = 10_000L, active: Int = 1) =
        PaymentEntity(
            id = id,
            name = "Synthetic card bill",
            amount_cents = amountCents,
            frequency = "Monthly",
            day_of_month = 15,
            next_date = "2026-03-15",
            is_active = active,
        )

    private fun occurrence(
        id: Int = 1,
        paymentId: Int = 1,
        amountCents: Long = 10_000L,
        paid: Boolean = true,
        transactionId: Int? = null,
        dueDate: String = "2026-03-15",
        originalDueDate: String? = dueDate,
        modified: Boolean = false,
    ) = BillOccurrenceEntity(
        id = id,
        payment_id = paymentId,
        due_date = dueDate,
        amount_cents = amountCents,
        is_paid = if (paid) 1 else 0,
        transaction_id = transactionId,
        created_at = "2026-03-01T00:00:00",
        original_due_date = originalDueDate,
        is_user_modified = if (modified) 1 else 0,
    )

    private fun expense(id: Int, amountCents: Long = 10_000L) =
        TransactionEntity(
            id = id,
            description = "Synthetic settlement",
            amount_cents = -amountCents,
            date = "2026-03-15",
            type = "expense",
            category = "bills",
        )

    @Test
    fun restoreRejectsUnpaidOccurrenceLinkedToExistingTransactionAndLeavesLedgerIntact() = runBlocking {
        repo.setBankBalance(50_000L)
        repo.insertIncome(
            IncomeEntity(
                name = "Existing salary",
                amount_cents = 100_000L,
                frequency = "Monthly",
                day_of_month = 1,
                next_date = "2026-12-01",
            )
        )

        val failure = runCatching {
            repo.restoreBackup(
                snapshot(
                    payments = listOf(monthlyPayment()),
                    transactions = listOf(expense(20)),
                    occurrences = listOf(occurrence(paid = false, transactionId = 20)),
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(50_000L, repo.getBankBalanceCents())
        assertEquals("Existing salary", repo.allIncome.first().single().name)
    }

    @Test
    fun restoreRejectsExistingTransactionWithWrongAmount() = runBlocking {
        val failure = runCatching {
            repo.restoreBackup(
                snapshot(
                    payments = listOf(monthlyPayment()),
                    transactions = listOf(expense(20, amountCents = 9_999L)),
                    occurrences = listOf(occurrence(transactionId = 20)),
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("amount"))
        assertTrue(repo.allBillOccurrences.first().isEmpty())
    }

    @Test
    fun restoreRejectsExistingTransactionWithWrongType() = runBlocking {
        val failure = runCatching {
            repo.restoreBackup(
                snapshot(
                    payments = listOf(monthlyPayment()),
                    transactions = listOf(
                        TransactionEntity(
                            id = 20,
                            description = "Not an expense",
                            amount_cents = 10_000L,
                            date = "2026-03-15",
                            type = "income",
                        )
                    ),
                    occurrences = listOf(occurrence(transactionId = 20)),
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("Expense"))
        assertTrue(repo.allBillOccurrences.first().isEmpty())
    }

    @Test
    fun restoreRepairsPaidOccurrenceWhoseTransactionIsGenuinelyMissing() = runBlocking {
        repo.restoreBackup(
            snapshot(
                payments = listOf(monthlyPayment()),
                occurrences = listOf(occurrence(transactionId = 999)),
            )
        )

        val restored = repo.allBillOccurrences.first().single()
        assertEquals(0, restored.is_paid)
        assertNull(restored.transaction_id)
    }

    @Test
    fun restoreRejectsDuplicateOriginalSlotsForTheSameBill() = runBlocking {
        val failure = runCatching {
            repo.restoreBackup(
                snapshot(
                    payments = listOf(monthlyPayment()),
                    occurrences = listOf(
                        occurrence(
                            id = 1,
                            paid = false,
                            dueDate = "2026-03-20",
                            originalDueDate = "2026-03-15",
                            modified = true,
                        ),
                        occurrence(
                            id = 2,
                            paid = false,
                            dueDate = "2026-03-25",
                            originalDueDate = "2026-03-15",
                            modified = true,
                        ),
                    ),
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("duplicate bill occurrence"))
        assertTrue(repo.allBillOccurrences.first().isEmpty())
    }

    @Test
    fun restoreRejectsOneTransactionSettlingTwoBillSlots() = runBlocking {
        val failure = runCatching {
            repo.restoreBackup(
                snapshot(
                    payments = listOf(monthlyPayment()),
                    transactions = listOf(expense(20)),
                    occurrences = listOf(
                        occurrence(
                            id = 1,
                            transactionId = 20,
                            dueDate = "2026-03-15",
                            originalDueDate = "2026-03-15",
                        ),
                        occurrence(
                            id = 2,
                            transactionId = 20,
                            dueDate = "2026-04-15",
                            originalDueDate = "2026-04-15",
                        ),
                    ),
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("multiple bill occurrences"))
        assertTrue(repo.allBillOccurrences.first().isEmpty())
    }

    @Test
    fun restoreKeepsMovedLinkAndHistoricalSettledAmountWhenTemplateWasEdited() = runBlocking {
        repo.restoreBackup(
            snapshot(
                payments = listOf(monthlyPayment(amountCents = 10_000L)),
                transactions = listOf(expense(20, amountCents = 15_000L)),
                occurrences = listOf(
                    occurrence(
                        amountCents = 15_000L,
                        transactionId = 20,
                        dueDate = "2026-03-20",
                        originalDueDate = "2026-03-15",
                        modified = true,
                    )
                ),
            )
        )

        val restored = repo.allBillOccurrences.first().single()
        assertEquals("2026-03-20", restored.due_date)
        assertEquals("2026-03-15", restored.original_due_date)
        assertEquals(15_000L, restored.amount_cents)
        assertEquals(1, restored.is_paid)
        assertEquals(20, restored.transaction_id)
        val failure = runCatching {
            repo.exportCrossPlatformVaultJson("2026-11-20T09:00:00Z")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("Use the encrypted Android backup"))
        assertEquals(restored, repo.allBillOccurrences.first().single())
    }

    @Test
    fun vaultRoundTripRetainsMovedSkippedSlotWithoutCreatingCash() = runBlocking {
        repo.restoreBackup(
            snapshot(
                payments = listOf(monthlyPayment()),
                occurrences = listOf(
                    occurrence(
                        paid = false,
                        dueDate = "2026-03-20",
                        originalDueDate = "2026-03-15",
                        modified = true,
                    )
                ),
            )
        )
        repo.skipBillOccurrence(1)
        val stamp = "2026-11-20T09:00:00Z"
        val exported = repo.exportCrossPlatformVaultJson(stamp)
        repo.restoreCrossPlatformVaultJson(exported)
        val restored = repo.allBillOccurrences.first().single()
        assertEquals("2026-03-15", restored.original_due_date)
        assertEquals("2026-03-20", restored.due_date)
        assertEquals(1, restored.is_paid)
        assertNull(restored.transaction_id)
        assertTrue(repo.allTransactions.first().isEmpty())
        assertEquals(exported, repo.exportCrossPlatformVaultJson(stamp))
    }

    @Test
    fun linkedBillCannotBeEditedToAnUnsupportedScheduleUntilUnlinked() = runBlocking {
        val bill = monthlyPayment()
        repo.insertPayment(bill)
        val original = debt(bill.id)
        val debtId = repo.insertDebt(original)
        val weekly = bill.copy(frequency = "Weekly", day_of_month = null)

        val failure = runCatching { repo.updatePayment(weekly) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("linked to an active debt"))
        assertEquals(bill, repo.allPayments.first().single())

        repo.updateDebt(original.copy(id = debtId, linkedPaymentId = null))
        repo.updatePayment(weekly)
        assertEquals(weekly, repo.allPayments.first().single())
    }

    @Test
    fun editingMissingDebtDoesNotReportSuccess() = runBlocking {
        val failure = runCatching {
            repo.updateDebt(debt(link = null).copy(id = 99L))
        }.exceptionOrNull()
        assertEquals("This debt no longer exists.", failure?.message)
    }

    @Test
    fun restoreRejectsDuplicateActiveDebtLinks() = runBlocking {
        val failure = runCatching {
            repo.restoreBackup(
                snapshot(
                    payments = listOf(monthlyPayment()),
                    debts = listOf(
                        DebtEntity(
                            name = "Active card",
                            balanceCents = 50_000L,
                            aprBasisPoints = 1_800,
                            minimumPaymentCents = 1_000L,
                            dueDayOfMonth = 15,
                            linkedPaymentId = 1,
                            isActive = true,
                        ),
                        DebtEntity(
                            name = "Second active card",
                            balanceCents = 20_000L,
                            aprBasisPoints = 900,
                            minimumPaymentCents = 500L,
                            dueDayOfMonth = 15,
                            linkedPaymentId = 1,
                            isActive = true,
                        ),
                    ),
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("multiple active debts"))
        assertTrue(repo.allDebts.first().isEmpty())
    }

    @Test
    fun restoreAllowsOneArchivedDebtToShareAnActiveLink() = runBlocking {
        repo.restoreBackup(
            snapshot(
                payments = listOf(monthlyPayment()),
                debts = listOf(
                    DebtEntity(
                        name = "Active card",
                        balanceCents = 50_000L,
                        aprBasisPoints = 1_800,
                        minimumPaymentCents = 1_000L,
                        dueDayOfMonth = 15,
                        linkedPaymentId = 1,
                        isActive = true,
                    ),
                    DebtEntity(
                        name = "Archived card",
                        balanceCents = 0L,
                        aprBasisPoints = 0,
                        minimumPaymentCents = 0L,
                        dueDayOfMonth = 15,
                        linkedPaymentId = 1,
                        isActive = false,
                    ),
                ),
            )
        )

        assertEquals(2, repo.allDebts.first().size)
    }

    private suspend fun insertBill(
        frequency: String,
        dayOfMonth: Int?,
        nextDate: String = "2026-03-15",
        active: Int = 1,
    ): PaymentEntity {
        val payment = PaymentEntity(
            name = "Synthetic bill $frequency",
            amount_cents = 10_000L,
            frequency = frequency,
            day_of_month = dayOfMonth,
            next_date = nextDate,
            is_active = active,
        )
        val id = db.paymentDao().insert(payment)
        return payment.copy(id = id.toInt())
    }

    private fun debt(link: Int?, active: Boolean = true, name: String = "Synthetic debt") = DebtEntity(
        name = name,
        balanceCents = 50_000L,
        aprBasisPoints = 1_800,
        minimumPaymentCents = 1_000L,
        dueDayOfMonth = 15,
        linkedPaymentId = link,
        isActive = active,
    )

    @Test
    fun insertDebtRejectsAnActiveLinkToAWeeklyBill() = runBlocking {
        val weekly = insertBill(frequency = "Weekly", dayOfMonth = null)

        val failure = runCatching { repo.insertDebt(debt(weekly.id)) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(repo.allDebts.first().isEmpty())
    }

    @Test
    fun insertDebtRejectsMonthlyBillWithoutDueDayAndInactiveBill() = runBlocking {
        val noAnchor = insertBill(frequency = "Monthly", dayOfMonth = null)
        val inactive = insertBill(frequency = "Monthly", dayOfMonth = 15, active = 0)

        assertTrue(runCatching { repo.insertDebt(debt(noAnchor.id)) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repo.insertDebt(debt(inactive.id)) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(repo.allDebts.first().isEmpty())
    }

    @Test
    fun insertDebtAcceptsMonthlyAndDatedOneTimeLinkAnchors() = runBlocking {
        val monthly = insertBill(frequency = "Monthly", dayOfMonth = 15)
        val oneTime = insertBill(frequency = "One-time", dayOfMonth = null, nextDate = "2026-03-20")

        repo.insertDebt(debt(monthly.id, name = "Monthly debt"))
        repo.insertDebt(debt(oneTime.id, name = "One-time debt"))

        assertEquals(2, repo.allDebts.first().size)
    }

    @Test
    fun insertDebtRejectsASecondActiveLinkToTheSameBill() = runBlocking {
        val monthly = insertBill(frequency = "Monthly", dayOfMonth = 15)
        repo.insertDebt(debt(monthly.id, name = "First"))

        val failure = runCatching {
            repo.insertDebt(debt(monthly.id, name = "Second"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("already linked"))
        assertEquals(1, repo.allDebts.first().size)
    }

    @Test
    fun insertDebtAllowsAnArchivedDebtToReuseAnActiveLink() = runBlocking {
        val monthly = insertBill(frequency = "Monthly", dayOfMonth = 15)
        repo.insertDebt(debt(monthly.id, active = true, name = "Active"))
        repo.insertDebt(debt(monthly.id, active = false, name = "Archived"))

        assertEquals(2, repo.allDebts.first().size)
    }

    @Test
    fun updateDebtKeepsAnUnchangedLegacyUnsupportedLinkEditable() = runBlocking {
        val weekly = insertBill(frequency = "Weekly", dayOfMonth = null)
        val legacy = debt(weekly.id, name = "Legacy debt")
        val id = db.debtDao().insert(legacy)

        repo.updateDebt(legacy.copy(id = id, name = "Renamed legacy debt"))

        assertEquals("Renamed legacy debt", repo.allDebts.first().single().name)
    }

    @Test
    fun updateDebtRejectsActivatingAnUnsupportedLegacyLink() = runBlocking {
        val weekly = insertBill(frequency = "Weekly", dayOfMonth = null)
        val legacy = debt(weekly.id, active = false, name = "Legacy debt")
        val id = db.debtDao().insert(legacy)

        val failure = runCatching {
            repo.updateDebt(legacy.copy(id = id, isActive = true))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(repo.allDebts.first().single().isActive)
    }

    @Test
    fun updateDebtRejectsMovingOntoABillAlreadyLinkedToAnotherActiveDebt() = runBlocking {
        val firstBill = insertBill(frequency = "Monthly", dayOfMonth = 10)
        val secondBill = insertBill(frequency = "Monthly", dayOfMonth = 20)
        repo.insertDebt(debt(firstBill.id, name = "First"))
        val secondId = repo.insertDebt(debt(secondBill.id, name = "Second"))

        val failure = runCatching {
            repo.updateDebt(debt(firstBill.id, name = "Second").copy(id = secondId))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            secondBill.id,
            repo.allDebts.first().single { it.id == secondId }.linkedPaymentId,
        )
    }

    @Test
    fun updateTransactionRejectsStaleExpectedOriginal() = runBlocking {
        repo.insertTransaction(
            TransactionEntity(
                description = "Original",
                amount_cents = 1_000L,
                date = "2026-11-10",
                type = "income",
            )
        )
        val original = repo.allTransactions.first().single()
        repo.updateTransaction(original.copy(description = "Current"))

        val failure = runCatching {
            repo.updateTransaction(
                original.copy(description = "Stale edit"),
                expectedOriginal = original,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("Current", repo.allTransactions.first().single().description)
    }

    @Test
    fun deleteTransactionRejectsStaleExpectedOriginalAndStaysIdempotentWhenMissing() = runBlocking {
        repo.insertTransaction(
            TransactionEntity(
                description = "Original",
                amount_cents = 1_000L,
                date = "2026-11-10",
                type = "income",
            )
        )
        val original = repo.allTransactions.first().single()
        repo.updateTransaction(original.copy(description = "Current"))
        val current = repo.allTransactions.first().single()

        val staleFailure = runCatching {
            repo.deleteTransaction(current, expectedOriginal = original)
        }.exceptionOrNull()
        assertTrue(staleFailure is IllegalArgumentException)
        assertNotNull(db.transactionDao().getById(original.id))

        repo.deleteTransaction(current, expectedOriginal = current)
        assertNull(db.transactionDao().getById(original.id))
        // A repeated delete with the same expected snapshot stays a no-op, not an error.
        repo.deleteTransaction(current, expectedOriginal = current)
    }
}
