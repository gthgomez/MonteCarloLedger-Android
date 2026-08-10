package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.processing.DebtItem
import com.montecarlo.ledger.processing.DebtPayoffEngine
import com.montecarlo.ledger.processing.DebtSimulationResult
import com.montecarlo.ledger.processing.ForecastEvent
import com.montecarlo.ledger.processing.PayoffStrategy
import com.montecarlo.ledger.util.centsToDisplay
import java.time.LocalDate

@Composable
fun DebtPayoffScreen(
    debts: List<DebtItem>,
    currentBalanceCents: Long,
    forecastEvents: List<ForecastEvent>,
    modifier: Modifier = Modifier,
) {
    var strategy by remember { mutableStateOf(PayoffStrategy.SNOWBALL) }
    var extraPaymentCents by remember { mutableFloatStateOf(10_000f) } // Default $100/mo extra

    val simulationResult: DebtSimulationResult = remember(debts, strategy, extraPaymentCents, currentBalanceCents, forecastEvents) {
        DebtPayoffEngine.runSimulation(
            debts = debts,
            extraMonthlyPaymentCents = extraPaymentCents.toLong(),
            strategy = strategy,
            currentBalanceCents = currentBalanceCents,
            forecastEvents = forecastEvents,
            today = LocalDate.now(),
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Debt Payoff & Cash Flow Simulator",
                style = MaterialTheme.typography.headlineSmall,
                color = GlassTokens.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Accelerate debt repayment and test monthly extra payment limits against your 90-day cash flow forecast.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary,
            )
        }

        // 1. Strategy Selector Tabs
        item {
            GlassCard(
                tint = GlassTint.Cyan,
                surfaceStyle = GlassSurfaceStyle.Standard,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Payoff Strategy",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTokens.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    TabRow(
                        selectedTabIndex = if (strategy == PayoffStrategy.SNOWBALL) 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        contentColor = GlassTokens.CyanBright,
                    ) {
                        Tab(
                            selected = strategy == PayoffStrategy.SNOWBALL,
                            onClick = { strategy = PayoffStrategy.SNOWBALL },
                            text = { Text("Snowball (Lowest Balance)") }
                        )
                        Tab(
                            selected = strategy == PayoffStrategy.AVALANCHE,
                            onClick = { strategy = PayoffStrategy.AVALANCHE },
                            text = { Text("Avalanche (Highest APR)") }
                        )
                    }
                }
            }
        }

        // 2. Extra Payment Slider
        item {
            GlassCard(
                tint = GlassTint.Neutral,
                surfaceStyle = GlassSurfaceStyle.Standard,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Extra Monthly Payment",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${centsToDisplay(extraPaymentCents.toLong())}/mo",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.CyanBright,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = extraPaymentCents,
                        onValueChange = { extraPaymentCents = it },
                        valueRange = 0f..100_000f, // $0 to $1,000/month
                        steps = 39,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Slide to test how much extra payment your monthly cash flow can safely support.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim,
                    )
                }
            }
        }

        // 3. Cash Flow Overdraft Warning Callout (if extra payment risks overdraft)
        if (simulationResult.causesOverdraft && simulationResult.warningMessage != null) {
            item {
                GlassCard(
                    tint = GlassTint.Error,
                    surfaceStyle = GlassSurfaceStyle.Hero,
                    modifier = Modifier.semantics {
                        stateDescription = "Warning: ${simulationResult.warningMessage}"
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "⚠️ Cash Flow Safety Warning",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.ErrorRed,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            simulationResult.warningMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTokens.TextPrimary,
                        )
                        Text(
                            "Consider reducing your extra monthly payment to avoid depleting your safe-to-spend reserve.",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTokens.TextSecondary,
                        )
                    }
                }
            }
        }

        // 4. Comparison Summary Card
        item {
            GlassCard(
                tint = if (simulationResult.causesOverdraft) GlassTint.Error else GlassTint.Cyan,
                surfaceStyle = GlassSurfaceStyle.Standard,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Payoff Forecast Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTokens.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Baseline Payoff", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                            Text(
                                "${simulationResult.baselineSummary.monthsToPayoff} months",
                                style = MaterialTheme.typography.titleSmall,
                                color = GlassTokens.TextSecondary,
                            )
                            Text(
                                centsToDisplay(simulationResult.baselineSummary.totalInterestCents) + " interest",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTokens.TextDim,
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Accelerated Payoff", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                            Text(
                                "${simulationResult.acceleratedSummary.monthsToPayoff} months",
                                style = MaterialTheme.typography.titleSmall,
                                color = GlassTokens.CyanBright,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                centsToDisplay(simulationResult.acceleratedSummary.totalInterestCents) + " interest",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTokens.TextDim,
                            )
                        }
                    }

                    HorizontalDivider(color = GlassTokens.DividerColor)

                    // Visual Progress Comparison Gauge
                    if (simulationResult.baselineSummary.monthsToPayoff > 0) {
                        val payoffRatio = (simulationResult.acceleratedSummary.monthsToPayoff.toFloat() / simulationResult.baselineSummary.monthsToPayoff.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Timeline Reduction",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlassTokens.TextDim,
                                )
                                Text(
                                    "${(100 - payoffRatio * 100).toInt()}% faster",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlassTokens.PositiveGreen,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { payoffRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = GlassTokens.PositiveGreen,
                                trackColor = GlassTokens.DividerColor,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Time Saved", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                            Text(
                                "${simulationResult.monthsSaved} months faster",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTokens.PositiveGreen,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Interest Saved", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                            Text(
                                centsToDisplay(simulationResult.interestSavedCents) + " saved",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTokens.PositiveGreen,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // 5. Debts List
        if (debts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "No debts detected",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.TextPrimary
                        )
                        Text(
                            "Add recurring payments with debt categories (e.g. Credit Card, Student Loan, Car Loan) to simulate payoff schedules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextSecondary
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    "Active Debts (${debts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTokens.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(debts) { debt ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                debt.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTokens.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "APR: ${debt.aprBasisPoints / 100.0}% • Min Payment: ${centsToDisplay(debt.minPaymentCents)}/mo",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.TextSecondary,
                            )
                        }
                        Text(
                            centsToDisplay(debt.balanceCents),
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.CyanBright,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
