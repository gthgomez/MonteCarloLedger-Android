package com.montecarlo.ledger.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.montecarlo.ledger.FeatureFlags
import com.montecarlo.ledger.adoption.ContractDashboardMapper
import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.processing.BalanceSeedResolver
import com.montecarlo.ledger.processing.ForecastEngine
import com.montecarlo.ledger.processing.MonteCarloEngine
import com.montecarlo.ledger.processing.MonteCarloParams
import com.montecarlo.ledger.processing.TimelineService
import com.montecarlo.ledger.ui.formatDateDisplay
import com.montecarlo.ledger.util.centsToDisplay
import com.montecarlo.ledger.util.toPersistedBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MonteCarloLedgerGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = withContext(Dispatchers.IO) {
            runCatching {
                val db = AppDatabase.getInstance(context)
                val settings = db.settingsDao().getAllSettingsList().associate { it.key to it.value }
                val lockEnabled = settings["app_lock_enabled"].toPersistedBoolean() &&
                    !settings["app_lock_pin_salt"].isNullOrBlank() &&
                    !settings["app_lock_pin_hash"].isNullOrBlank()
                if (lockEnabled) {
                    return@runCatching WidgetData(
                        safeToSpendCents = 0L,
                        nextBillLabel = "Unlock in the app",
                        riskLabel = "Locked",
                        loadFailed = false,
                        locked = true,
                    )
                }

                val txns = db.transactionDao().getAllTransactionsList()
                val incomes = db.incomeDao().getAllIncomesList()
                val payments = db.paymentDao().getAllPaymentsList()
                val occurrences = db.billOccurrenceDao().getAllOccurrencesList()

                val bankBalanceCents = settings["bank_balance_cents"]?.toLongOrNull() ?: 0L
                val reconciled = settings["bank_balance_reconciled"].toPersistedBoolean()
                val ledgerBalanceCents = txns.sumOf { it.amount_cents }
                val forecastSeedCents = BalanceSeedResolver.resolve(ledgerBalanceCents, bankBalanceCents, reconciled)

                val today = LocalDate.now()
                val events = TimelineService.generateTimeline(incomes, payments, today, 90, occurrences)

                // MC-06b: headline numbers come from the canonical contract engine when the
                // adoption flag is on; the native engines remain a fallback only.
                val adoptedResult = if (FeatureFlags.contractForecastEnabled) {
                    runCatching {
                        val built = ContractScenarioBridge.build(
                            ContractScenarioBridge.Inputs(
                                startingBalanceCents = forecastSeedCents,
                                asOf = today,
                                incomes = incomes,
                                payments = payments,
                                billOccurrences = occurrences,
                                simulation = ContractSimulationParams(runs = 100),
                            )
                        )
                        // Contract MCD-0023: do not invent a 0% result for an empty ledger.
                        val scenario = built
                        ContractRunner.run(scenario)
                    }.getOrNull()
                } else {
                    null
                }
                val forecastSummary = adoptedResult
                    ?.let(ContractDashboardMapper::toForecastSummary)
                    ?: ForecastEngine.calculateForecastSummary(forecastSeedCents, events)

                val safeToSpend = forecastSummary.safeToSpendCents
                val upcomingBills = events.filter { it.type == "bill" }.take(1)
                val nextBillLabel = upcomingBills.firstOrNull()?.let {
                    "${it.description} • ${centsToDisplay(it.amount_cents)} (${it.date.formatDateDisplay()})"
                } ?: "No upcoming bills"

                val probabilityNegativePct = adoptedResult?.risk
                    ?.let { it.negativeBalanceProbabilityPpm / 10_000.0 }
                    ?: MonteCarloEngine(MonteCarloParams(runs = 100, includeDailyPercentiles = false))
                        .runSimulation(forecastSeedCents, events, today)
                        .probability_negative_pct
                val riskLabel = when {
                    safeToSpend < 0 -> "Shortfall Projected"
                    probabilityNegativePct >= 25.0 -> "High Risk (${String.format("%.0f", probabilityNegativePct)}%)"
                    else -> "Stable Forecast"
                }

                WidgetData(
                    safeToSpendCents = safeToSpend,
                    nextBillLabel = nextBillLabel,
                    riskLabel = riskLabel,
                    loadFailed = false,
                    locked = false,
                )
            }.getOrElse {
                WidgetData(
                    safeToSpendCents = 0L,
                    nextBillLabel = "Unable to refresh",
                    riskLabel = "Open the app",
                    loadFailed = true,
                    locked = false,
                )
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    safeToSpendCents = widgetData.safeToSpendCents,
                    nextBillLabel = widgetData.nextBillLabel,
                    riskLabel = widgetData.riskLabel,
                    loadFailed = widgetData.loadFailed,
                    locked = widgetData.locked,
                )
            }
        }
    }

    private data class WidgetData(
        val safeToSpendCents: Long,
        val nextBillLabel: String,
        val riskLabel: String,
        val loadFailed: Boolean,
        val locked: Boolean,
    )

    @Composable
    private fun WidgetContent(
        safeToSpendCents: Long,
        nextBillLabel: String,
        riskLabel: String,
        loadFailed: Boolean,
        locked: Boolean,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF0F172A)))
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "Safe-to-Spend",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF94A3B8)),
                        fontSize = 12.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = riskLabel,
                    style = TextStyle(
                        color = ColorProvider(if (safeToSpendCents < 0) Color(0xFFEF4444) else Color(0xFF06B6D4)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = when {
                    locked -> "•••"
                    loadFailed -> "—"
                    else -> centsToDisplay(safeToSpendCents)
                },
                style = TextStyle(
                    color = ColorProvider(if (safeToSpendCents < 0) Color(0xFFEF4444) else Color(0xFF22D3EE)),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            Text(
                text = "Next Bill",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF64748B)),
                    fontSize = 10.sp,
                ),
            )

            Text(
                text = nextBillLabel,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFF8FAFC)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
