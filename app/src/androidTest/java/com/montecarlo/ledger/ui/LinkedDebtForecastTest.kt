package com.montecarlo.ledger.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.montecarlo.ledger.AppTheme
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.util.centsToDisplay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LinkedDebtForecastTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val databaseName = "linked-debt-${UUID.randomUUID()}.db"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @Before
    fun openIsolatedLedger() {
        db = Room.databaseBuilder(app, AppDatabase::class.java, databaseName).build()
    }

    @After
    fun closeIsolatedLedger() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        db.close()
        app.deleteDatabase(databaseName)
    }

    @Test
    fun forecastShowsOneLinkedOutflowButTwoIndependentOutflows() {
        val repo = LedgerRepository(db)
        val bill = PaymentEntity(
            id = 71,
            name = "Synthetic loan bill",
            amount_cents = 30_000L,
            frequency = "One-time",
            day_of_month = null,
            next_date = LocalDate.now().withDayOfMonth(15).toString(),
        )
        val debt = DebtEntity(
            name = bill.name,
            balanceCents = 30_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = 30_000L,
            dueDayOfMonth = 20,
            linkedPaymentId = bill.id,
        )
        // Set up persisted records through the real repository. The exact fixed-date
        // companion runs in ContractAdoptionDashboardTest; here both dates can be overdue.
        val debtId = runBlocking {
            repo.setBankBalance(50_000L)
            repo.insertPayment(bill)
            repo.syncBillOccurrences()
            repo.insertDebt(debt)
        }
        compose.runOnUiThread { viewModel = MainViewModel(app, db) }
        compose.setContent { AppTheme { ForecastScreen(viewModel) } }

        assertDisplayedOutcome(20_000L)
        val forecastList = compose.onNode(hasScrollToIndexAction())
        forecastList.performScrollToNode(hasText("Purchase amount"))
        compose.onNodeWithText("Purchase amount").performTextInput("40")
        forecastList.performScrollToNode(hasText("Can I afford this?"))
        compose.onNodeWithText("Can I afford this?").performClick()
        compose.waitUntil(15_000) { viewModel.purchaseSimulation.value != null }
        val purchase = requireNotNull(viewModel.purchaseSimulation.value)
        assertEquals(20_000L, purchase.baseline.projectedLowPointCents)
        assertEquals(16_000L, purchase.withPurchase.projectedLowPointCents)
        assertEquals(50_000L, runBlocking { repo.getBankBalanceCents() })

        runBlocking { repo.updateDebt(debt.copy(id = debtId, linkedPaymentId = null)) }
        assertDisplayedOutcome(-10_000L)
        assertNull(viewModel.purchaseSimulation.value)
    }

    private fun assertDisplayedOutcome(expectedCents: Long) {
        compose.waitUntil(15_000) {
            viewModel.uiState.value.forecastRows.lastOrNull()?.balanceCents == expectedCents
        }
        val displayedEnding = "Ending balance ${centsToDisplay(expectedCents)}"
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(displayedEnding))
        compose.onNodeWithContentDescription(displayedEnding).assertIsDisplayed()
    }
}
