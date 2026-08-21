package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.processing.CategoryDrillDown
import com.montecarlo.ledger.util.centsToDisplay
import java.util.Locale
import kotlin.math.abs

@Composable
fun InsightsScreen(
    viewModel: MainViewModel,
    onTrackAsBill: (RecurringCandidate) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        analysisInsightsSection(
            uiState = uiState,
            onCreateRule = viewModel::saveTransactionRule,
            onTrackAsBill = onTrackAsBill,
            expandedCategory = expandedCategory,
            onToggleCategory = { category ->
                expandedCategory = if (expandedCategory == category) null else category
            },
        )
    }
}

internal fun LazyListScope.analysisInsightsSection(
    uiState: com.montecarlo.ledger.AppUiState,
    onCreateRule: (String, String) -> Unit,
    onTrackAsBill: (RecurringCandidate) -> Unit,
    expandedCategory: String? = null,
    onToggleCategory: (String) -> Unit = {},
) {
    item {
        Text(
            "Cash flow overview",
            style = MaterialTheme.typography.headlineSmall,
            color = GlassTokens.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
    }

    item {
        GlassCard(
            modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
            tint = GlassTint.Teal,
            surfaceStyle = GlassSurfaceStyle.Hero
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Last 30 days of cash flow",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTokens.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FriendlyTechnicalLabel("Money in", "inflow")
                    Text(
                        "+${centsToDisplay(uiState.totalInflowCents)}",
                        color = GlassTokens.PositiveGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FriendlyTechnicalLabel("Money out", "outflow")
                    Text(
                        "-${centsToDisplay(uiState.totalOutflowCents)}",
                        color = GlassTokens.ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = GlassTokens.DividerColor)

                val net = uiState.totalInflowCents - uiState.totalOutflowCents
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FriendlyTechnicalLabel("Net change", "net cash flow")
                    Text(
                        "${if (net >= 0) "+" else ""}${centsToDisplay(net)}",
                        color = if (net >= 0) GlassTokens.PositiveGreen else GlassTokens.ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    item {
        Text(
            "Spending by category (30d)",
            style = MaterialTheme.typography.titleLarge,
            color = GlassTokens.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
    }

    if (uiState.forecastInsights.isNotEmpty()) {
        item {
            Text(
                "Why your 3-month estimate looks like this",
                style = MaterialTheme.typography.labelLarge,
                color = GlassTokens.TextSecondary,
                modifier = Modifier.semantics { heading() }
            )
        }
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                tint = GlassTint.Teal,
                surfaceStyle = GlassSurfaceStyle.Standard,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.forecastInsights.forEach { insight ->
                        Column {
                            Text(insight.label, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
                            Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
                        }
                    }
                }
            }
        }
    }

    if (uiState.categorySpend.isEmpty()) {
        item {
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Neutral,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No spending breakdown yet.", style = MaterialTheme.typography.titleMedium, color = GlassTokens.TextPrimary)
                    Text("Record a few spending transactions and the app will group them by category here as category spend.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
                }
            }
        }
    } else {
        items(uiState.categorySpend) { spend ->
            val categoryKey = spend.category.ifBlank { "uncategorized" }
            val isExpanded = expandedCategory == categoryKey
            SolidListSurface(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                onClick = { onToggleCategory(categoryKey) },
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryGlassIcon(category = spend.category, size = 32.dp, iconSize = 16.dp)
                        Text(
                            categoryKey.replaceFirstChar { it.uppercase() },
                            color = GlassTokens.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${centsToDisplay(abs(spend.totalCents))}",
                            fontWeight = FontWeight.SemiBold,
                            color = GlassTokens.CyanBright
                        )
                        Text(
                            if (isExpanded) "▲" else "▼",
                            color = GlassTokens.TextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Pacing Bar
                    val today = java.time.LocalDate.now()
                    val monthProgress = today.dayOfMonth.toFloat() / today.lengthOfMonth()
                    val spendShare = abs(spend.totalCents).toFloat() / abs(uiState.totalOutflowCents).coerceAtLeast(1)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        LinearProgressIndicator(
                            progress = { spendShare.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = if (spendShare > monthProgress) GlassTokens.ErrorRed.copy(alpha = 0.7f) else GlassTokens.CyanBright.copy(alpha = 0.5f),
                            trackColor = GlassTokens.DividerColor,
                        )
                        Text(
                            if (spendShare > monthProgress) "Over-pacing for this month" else "Under-pacing for this month",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (spendShare > monthProgress) GlassTokens.ErrorRed else GlassTokens.TextDim
                        )
                    }

                    if (isExpanded) {
                        CategoryDrillDownPanel(
                            drillDown = com.montecarlo.ledger.processing.CategoryDrillDownDeriver.build(
                                transactions = uiState.transactions,
                                category = categoryKey,
                                today = today,
                            ),
                        )
                    }
                }
            }
        }
    }

    val (untracked, spendingPatterns) = uiState.recurringCandidates.partition { candidate ->
        uiState.payments.none { payment ->
            payment.name.lowercase(Locale.ROOT).contains(candidate.pattern.lowercase(Locale.ROOT)) ||
            candidate.pattern.lowercase(Locale.ROOT).contains(payment.name.lowercase(Locale.ROOT))
        }
    }

    item {
        Text(
            "Untracked subscriptions",
            style = MaterialTheme.typography.titleLarge,
            color = GlassTokens.TextPrimary,
            modifier = Modifier.semantics { heading() }.padding(top = 8.dp)
        )
    }

    if (untracked.isEmpty()) {
        item {
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Cyan,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
            ) {
                Text("All detected recurring payments are currently tracked as bills.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
            }
        }
    } else {
        items(untracked) { candidate ->
            SolidListSurface(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryGlassIcon(category = candidate.category, size = 36.dp, iconSize = 18.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                candidate.pattern.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                                color = GlassTokens.TextPrimary
                            )
                            Text("Untracked", style = MaterialTheme.typography.labelSmall, color = GlassTokens.ErrorRed)
                        }
                    }
                    Text("${candidate.cadenceLabel} • ${candidate.occurrenceCount} instances found", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onTrackAsBill(candidate) }, modifier = Modifier.weight(1f)) {
                            Text("Track as Bill")
                        }
                        TextButton(onClick = { onCreateRule(candidate.pattern, candidate.category) }, modifier = Modifier.weight(1f)) {
                            Text("Auto-Categorize")
                        }
                    }
                }
            }
        }
    }

    item {
        Text(
            "Spending patterns",
            style = MaterialTheme.typography.titleLarge,
            color = GlassTokens.TextPrimary,
            modifier = Modifier.semantics { heading() }.padding(top = 8.dp)
        )
    }

    if (spendingPatterns.isEmpty()) {
        item {
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Cyan,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
            ) {
                Text("No other recurring patterns found.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
            }
        }
    } else {
        items(spendingPatterns) { candidate ->
            SolidListSurface(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryGlassIcon(category = candidate.category, size = 32.dp, iconSize = 16.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(candidate.pattern.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
                            Text("${candidate.cadenceLabel} • ${candidate.occurrenceCount} txns • Suggested: ${candidate.category}", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
                        }
                    }
                    TextButton(onClick = { onCreateRule(candidate.pattern, candidate.category) }) {
                        Text("Create rule")
                    }
                }
            }
        }
    }

    item {
        Text(
            "Recent manual adjustments",
            style = MaterialTheme.typography.titleLarge,
            color = GlassTokens.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
    }

    if (uiState.adjustments.isEmpty()) {
        item {
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Neutral,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No manual adjustments yet.", style = MaterialTheme.typography.titleMedium, color = GlassTokens.TextPrimary)
                    Text("This is where balance corrections and other manual fixes will appear as adjustment history.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
                }
            }
        }
    } else {
        items(uiState.adjustments.take(10)) { adj ->
            SolidListSurface(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(adj.description, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(adj.date.formatDateDisplay(), style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextDim)
                        Text(
                            "${if (adj.amount_cents >= 0) "+" else ""}${centsToDisplay(adj.amount_cents)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (adj.amount_cents >= 0) GlassTokens.PositiveGreen else GlassTokens.ErrorRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDrillDownPanel(drillDown: CategoryDrillDown?) {
    if (drillDown == null) return
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalDivider(color = GlassTokens.DividerColor)

        DrillDownRow("Total (${drillDown.windowDays}d)", centsToDisplay(drillDown.totalCents))
        DrillDownRow(
            "Trend",
            drillDown.trendLabel ?: "No earlier window to compare",
        )
        DrillDownRow(
            "Per charge",
            "${drillDown.transactionCount} charges • ${centsToDisplay(drillDown.averageCents)} avg",
        )
        DrillDownRow("Largest charge", centsToDisplay(drillDown.largestCents))

        if (drillDown.topMerchants.isNotEmpty()) {
            HorizontalDivider(color = GlassTokens.DividerColor)
            Text(
                "Where it went",
                style = MaterialTheme.typography.labelLarge,
                color = GlassTokens.TextSecondary,
                modifier = Modifier.semantics { heading() }
            )
            drillDown.topMerchants.forEach { merchant ->
                DrillDownRow(
                    merchant.label,
                    "${centsToDisplay(merchant.totalCents)} • ${merchant.count}×",
                )
            }
        }

        Text(
            "Tap again to collapse.",
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.TextDim
        )
    }
}

@Composable
private fun DrillDownRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextPrimary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = GlassTokens.TextSecondary
        )
    }
}
