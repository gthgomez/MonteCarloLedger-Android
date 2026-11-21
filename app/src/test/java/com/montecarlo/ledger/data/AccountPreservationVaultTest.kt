package com.montecarlo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.dashboard.DashboardDeriver
import com.montecarlo.ledger.dashboard.ReportingPackage
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.sync.CloudVaultSync
import com.montecarlo.ledger.sync.LocalLedgerPreservation
import com.montecarlo.ledger.sync.VaultLocalChangesException
import com.montecarlo.ledger.sync.VaultSnapshotFingerprint
import com.montecarlo.ledger.sync.VaultSyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountPreservationVaultTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
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

    private suspend fun primaryAccount(): AccountEntity =
        db.accountDao().getDefault() ?: error("default account missing")

    private fun AccountEntity.snapshot() =
        listOf(id, name, type, balanceCents, isReconciled, isDefault, lastUpdated)

    @Test
    fun hasCustomAccounts_falseForPristinePrimaryOnly() = runBlocking {
        val before = primaryAccount().snapshot()
        assertFalse(repo.hasCustomAccounts())
        assertFalse(LocalLedgerPreservation.hasPreservationRisk(repo))
        assertEquals(before, primaryAccount().snapshot())
    }

    @Test
    fun hasCustomAccounts_trueWhenPrimaryHasReconciledNonZeroBalance() = runBlocking {
        val beforeId = primaryAccount().id
        repo.setBankBalance(42_500)
        val after = primaryAccount()
        assertEquals(beforeId, after.id)
        assertEquals(42_500L, after.balanceCents)
        assertTrue(after.isReconciled)
        assertTrue(repo.hasCustomAccounts())
    }

    @Test
    fun hasCustomAccounts_trueWhenTransactionLinkedToLonePrimary() = runBlocking {
        val primary = primaryAccount()
        assertEquals(0L, primary.balanceCents)
        repo.insertTransaction(
            TransactionEntity(
                description = "Tagged",
                amount_cents = -100,
                date = "2026-11-01",
                type = "expense",
                category = "misc",
                account_id = primary.id,
            ),
        )
        assertTrue(repo.hasCustomAccounts())
    }

    @Test
    fun pull_blocksOnReconciledPrimary_withoutGoalsOrFingerprintMismatch() = runBlocking {
        repo.setBankBalance(50_000)
        val accountBefore = primaryAccount()
        val vaultFp = VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp"))
        assertEquals(vaultFp, VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp")))
        VaultSyncStore(context).commitSyncState(
            vaultId = "v",
            serverUrl = VaultSyncStore.DEFAULT_SERVER,
            revision = 1,
            syncedFingerprint = vaultFp,
            androidPreservationFingerprint = LocalLedgerPreservation.fingerprint(repo),
            ancestryEstablished = true,
        )
        val sync = CloudVaultSync(context, repo)
        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                sync.pullVault("v", "secret".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
            }
        }
        val accountAfter = primaryAccount()
        assertEquals(accountBefore.snapshot(), accountAfter.snapshot())
        assertEquals(accountBefore.balanceCents, accountAfter.balanceCents)
        assertEquals(accountBefore.isReconciled, accountAfter.isReconciled)
    }

    @Test
    fun vaultRestore_reseedsPrimaryAndDropsReconciledState_whenInvokedDirectly() = runBlocking {
        repo.setBankBalance(33_000)
        val before = primaryAccount()
        assertTrue(before.isReconciled)
        assertEquals(33_000L, before.balanceCents)
        val vaultJson = repo.exportCrossPlatformVaultJson("2026-11-11")
        repo.restoreCrossPlatformVaultJson(vaultJson)
        val after = db.accountDao().getDefault()
        assertNotNull(after)
        assertEquals("Primary account", after!!.name)
        assertEquals(0L, after.balanceCents)
        assertFalse(after.isReconciled)
    }
    @Test
    fun vaultRoundTripPreservesLinkedDebtAndIndependentForecastOracle() = runBlocking {
        val fixture = javaClass.getResourceAsStream("/households/vault-payment-identity.json")
            ?.bufferedReader()?.use { JSONObject(it.readText()) } ?: error("Missing household fixture")
        repo.restoreCrossPlatformVaultJson(fixture.getJSONObject("ledger").toString())
        val exported = repo.exportCrossPlatformVaultJson("2028-02-01T00:00:00Z")
        repeat(2) { repo.restoreCrossPlatformVaultJson(exported) }
        val debts = db.debtDao().getAll().first().associateBy { it.name }
        val payment = db.paymentDao().getAllPaymentsList().single()
        assertEquals(payment.id, debts.getValue("Car loan").linkedPaymentId)
        assertFalse(debts.getValue("Archived").isActive)
        val snapshot = JSONObject(exported)
        assertEquals("payment-car", snapshot.getJSONArray("payments").getJSONObject(0).getString("id"))
        assertEquals("opening", snapshot.getJSONArray("transactions").getJSONObject(0).getString("id"))
        val debtRows = snapshot.getJSONArray("liabilities")
        val linkedDebt = (0 until debtRows.length()).map { debtRows.getJSONObject(it) }
            .single { it.getString("id") == "debt-car" }
        assertEquals("payment-car", linkedDebt.getString("linked_payment_id"))
        val inputs = ContractScenarioBridge.Inputs(
            startingBalanceCents = db.transactionDao().getTotalBalanceCents().first()!!,
            asOf = LocalDate.parse(fixture.getString("as_of")),
            horizonDays = fixture.getInt("horizon_days"),
            simulation = ContractSimulationParams(
                seed = 7L, surpriseProbabilityPpm = 0, reserveCents = fixture.getLong("reserve_cents"),
            ),
            incomes = db.incomeDao().getAllIncomesList(),
            payments = db.paymentDao().getAllPaymentsList(),
            billOccurrences = db.billOccurrenceDao().getAllOccurrencesList(),
            debts = debts.values.toList(),
        )
        val linked = ContractRunner.run(ContractScenarioBridge.build(inputs))
        assertEquals(70_000L, linked.forecast.endingBalanceCents)
        assertEquals(60_000L, requireNotNull(linked.risk).safeToSpendCents)
        val invalid = JSONObject(exported)
        val liabilities = invalid.getJSONArray("liabilities")
        for (index in 0 until liabilities.length()) {
            liabilities.getJSONObject(index).put("linked_payment_id", "payment-car")
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.restoreCrossPlatformVaultJson(invalid.toString()) }
        }
        assertEquals(exported, repo.exportCrossPlatformVaultJson("2028-02-01T00:00:00Z"))

        val fractional = JSONObject(exported).put("current_balance_cents", 120_000.5)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.restoreCrossPlatformVaultJson(fractional.toString()) }
        }
        val unsupported = JSONObject(exported).put("accounts", org.json.JSONArray())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.restoreCrossPlatformVaultJson(unsupported.toString()) }
        }
        assertEquals(exported, repo.exportCrossPlatformVaultJson("2028-02-01T00:00:00Z"))

        val invalidSettlement = JSONObject(exported).put(
            "bill_occurrences",
            org.json.JSONArray().put(
                JSONObject()
                    .put("id", "invalid-occurrence")
                    .put("payment_id", "payment-car")
                    .put("due_date", "2028-02-15")
                    .put("paid", false)
                    .put("transaction_id", "opening"),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.restoreCrossPlatformVaultJson(invalidSettlement.toString()) }
        }
        assertEquals(exported, repo.exportCrossPlatformVaultJson("2028-02-01T00:00:00Z"))

        linkedDebt.remove("linked_payment_id")
        repo.restoreCrossPlatformVaultJson(snapshot.toString())
        val unlinked = ContractRunner.run(ContractScenarioBridge.build(inputs.copy(
            debts = db.debtDao().getAll().first(),
        )))
        assertEquals(20_000L, unlinked.forecast.endingBalanceCents)
        assertEquals(10_000L, requireNotNull(unlinked.risk).safeToSpendCents)
    }
    @Test
    fun vaultTimingRetainsRuleOnlyBillAndExactPaydayDates() = runBlocking {
        val fixture = javaClass.getResourceAsStream("/households/vault-timing.json")!!
            .bufferedReader().use { JSONObject(it.readText()) }
        repo.restoreCrossPlatformVaultJson(fixture.getJSONObject("ledger").toString())
        val out = JSONObject(repo.exportCrossPlatformVaultJson("2027-02-01T00:00:00Z"))
        val incomeRows = out.getJSONArray("incomes")
        val income = (0 until incomeRows.length()).map { incomeRows.getJSONObject(it) }
            .single { it.getString("id") == "income-month-end" }
        val bills = out.getJSONArray("payments")
        val bill = (0 until bills.length()).map { bills.getJSONObject(it) }
            .single { it.getString("id") == "bill-rule" }
        assertEquals(listOf("2027-01-31", "2027-02-28", null), listOf(
            income.getString("last_payday"),
            income.getString("next_payday"),
            if (bill.isNull("due_date")) null else bill.getString("due_date"),
        ))
    }
    @Test
    fun legacyClampedPaydayWithoutLastDateCannotSilentlyLoseItsAnchor() = runBlocking {
        repo.insertIncome(IncomeEntity(
            name = "Legacy month-end income", amount_cents = 30_000L, frequency = "Monthly",
            day_of_month = 31, next_date = "2027-02-28",
        ))
        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.exportCrossPlatformVaultJson("2027-02-01T00:00:00Z") }
        }
        assertTrue(requireNotNull(failure.message).contains("encrypted Android backup"))
        assertEquals(31, repo.allIncome.first().single().day_of_month)
    }

    @Test
    fun recordingIdenticalPaydaysUsesScheduleIdentityAndKeepsMonthEnd() = runBlocking {
        repeat(2) {
            repo.insertIncome(IncomeEntity(
                name = "Paycheck", amount_cents = 30_000L, frequency = "Monthly", day_of_month = 31,
                next_date = "2027-02-28", last_date = "2027-01-31",
            ))
        }
        val paydays = repo.allIncome.first()
        paydays.forEach { repo.processPayday(it, 30_000L) }
        paydays.forEach { repo.processPayday(it, 30_000L) }
        assertEquals(listOf(30_000L, 30_000L), repo.allTransactions.first().map { it.amount_cents })
        assertEquals(listOf("2027-03-31", "2027-03-31"), repo.allIncome.first().map { it.next_date })
        assertEquals(listOf("2027-02-28", "2027-02-28"), repo.allIncome.first().map { it.last_date })
    }

    @Test
    fun datedRoomHouseholdSurvivesReloadAndLegacyRoundTrip() = runBlocking {
        val json = javaClass.getResourceAsStream("/households/vault-timing.json")!!
            .bufferedReader().use { it.readText() }
        val name = "vault-timing-room"
        db.close()
        context.deleteDatabase(name)
        try {
            for ((year, version) in listOf("2027" to "2.2", "2028" to "2.1", "2027" to "2.0")) {
                val fixture = JSONObject(if (year == "2028") {
                    json.replace("2027-02-28", "2028-02-29").replace("2027-", "2028-")
                } else json)
                val snapshot = fixture.getJSONObject("ledger").put("version", version)
                db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
                repo = LedgerRepository(db)
                repo.restoreCrossPlatformVaultJson(snapshot.toString())
                db.close()

                db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
                repo = LedgerRepository(db)
                val exported = repo.exportCrossPlatformVaultJson("2027-02-01T00:00:00Z")
                repo.restoreCrossPlatformVaultJson(exported)
                val asOf = LocalDate.parse(fixture.getString("as_of"))
                val pack = ReportingPackage(
                    incomes = repo.allIncome.first(), payments = repo.allPayments.first(),
                    txns = repo.allTransactions.first(), accounts = repo.allAccounts.first(),
                    balanceState = repo.balanceState.first(), billOccurrences = repo.allBillOccurrences.first(),
                    assets = emptyList(), goals = emptyList(), categoryBudgets = emptyList(),
                    debts = emptyList(), rules = emptyList(), dashboardConfig = DashboardConfig(),
                )
                val result = DashboardDeriver().derive(pack, asOf, contractForecastEnabled = true)
                val scenario = ContractScenarioBridge.build(requireNotNull(result.scenarioInputs).copy(
                    horizonDays = fixture.getInt("horizon_days"), simulation = null,
                ))
                val forecast = ContractRunner.run(scenario).forecast
                assertEquals(10_000L, scenario.startingBalanceCents)
                assertEquals(-10_000L, forecast.minimumBalanceCents)
                assertEquals(asOf.plusDays(2), forecast.minimumBalanceDate)
                assertEquals(204_500L, forecast.endingBalanceCents)
                val expected = fixture.getJSONObject("expected").getJSONArray("event_dates_and_cents")
                val expectedEvents = (0 until expected.length()).map { i ->
                    val row = expected.getJSONArray(i)
                    "${row.getString(0)}:${row.getLong(1)}"
                }.sorted()
                val actual = ContractEngine.inWindowBaseEvents(scenario)
                    .map { "${it.date}:${it.amountCents}" }.sorted()
                assertEquals(expectedEvents, actual)

                val malformed = JSONObject(exported)
                malformed.getJSONArray("incomes").getJSONObject(0).put("next_payday", "$year-02-30")
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repo.restoreCrossPlatformVaultJson(malformed.toString()) }
                }
                assertEquals(exported, repo.exportCrossPlatformVaultJson("2027-02-01T00:00:00Z"))
                db.close()
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
