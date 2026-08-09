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
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.processing.BalanceSeedResolver
import com.montecarlo.ledger.processing.ForecastEngine
import com.montecarlo.ledger.processing.MonteCarloEngine
import com.montecarlo.ledger.processing.MonteCarloParams
import com.montecarlo.ledger.processing.TimelineService
import com.montecarlo.ledger.ui.formatDateDisplay
import com.montecarlo.ledger.util.centsToDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MonteCarloLedgerGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = withContext(Dispatchers.IO) {
            runCatching {
                val db = AppDatabase.getInstance(context)
                val txns = db.transactionDao().getAllTransactionsList()
                val incomes = db.incomeDao().getAllIncomesList()
                val payments = db.paymentDao().getAllPaymentsList()
                val occurrences = db.billOccurrenceDao().getAllOccurrencesList()
                val settings = db.settingsDao().getAllSettingsList().associate { it.key to it.value }

                val bankBalanceCents = settings["bank_balance_cents"]?.toLongOrNull() ?: 0L
                val reconciled = settings["bank_balance_reconciled"] == "1"
                val ledgerBalanceCents = txns.sumOf { it.amount_cents }
                val forecastSeedCents = BalanceSeedResolver.resolve(ledgerBalanceCents, bankBalanceCents, reconciled)

                val today = LocalDate.now()
                val events = TimelineService.generateTimeline(incomes, payments, today, 90, occurrences)
                val forecastSummary = ForecastEngine.calculateForecastSummary(forecastSeedCents, events)

                val safeToSpend = forecastSummary.safeToSpendCents
                val upcomingBills = events.filter { it.type == "bill" }.take(1)
                val nextBillLabel = upcomingBills.firstOrNull()?.let {
                    "${it.description} • ${centsToDisplay(it.amount_cents)} (${it.date.formatDateDisplay()})"
                } ?: "No upcoming bills"

                val mc = MonteCarloEngine(MonteCarloParams(runs = 100, includeDailyPercentiles = false)).runSimulation(forecastSeedCents, events, today)
                val riskLabel = when {
                    safeToSpend < 0 -> "Shortfall Projected"
                    mc.probability_negative_pct >= 25.0 -> "High Risk (${String.format("%.0f", mc.probability_negative_pct)}%)"
                    else -> "Stable Forecast"
                }

                WidgetData(
                    safeToSpendCents = safeToSpend,
                    nextBillLabel = nextBillLabel,
                    riskLabel = riskLabel,
                )
            }.getOrElse {
                WidgetData(
                    safeToSpendCents = 0L,
                    nextBillLabel = "Open app to setup",
                    riskLabel = "Setup required",
                )
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    safeToSpendCents = widgetData.safeToSpendCents,
                    nextBillLabel = widgetData.nextBillLabel,
                    riskLabel = widgetData.riskLabel,
                )
            }
        }
    }

    private data class WidgetData(
        val safeToSpendCents: Long,
        val nextBillLabel: String,
        val riskLabel: String,
    )

    @Composable
    private fun WidgetContent(
        safeToSpendCents: Long,
        nextBillLabel: String,
        riskLabel: String,
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
                text = centsToDisplay(safeToSpendCents),
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
