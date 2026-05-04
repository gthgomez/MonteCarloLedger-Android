package com.example.app.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app.GlassTokens
import com.example.app.MainViewModel
import kotlin.math.abs

@Composable
fun InsightsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        analysisInsightsSection(
            uiState = uiState,
            onCreateRule = viewModel::saveTransactionRule,
            onTrackAsBill = { name, category, amount ->
                // For now, we'll just open the add bill screen
                // In a real app, we'd pre-fill the name/category/amount
                // This will be handled in AppView
            }
        )
    }
}

internal fun LazyListScope.analysisInsightsSection(
    uiState: com.example.app.AppUiState,
    onCreateRule: (String, String) -> Unit,
    onTrackAsBill: (String, String, Int) -> Unit,
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
                        "+$${String.format("%.2f", uiState.totalInflowCents / 100.0)}",
                        color = GlassTokens.PositiveGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FriendlyTechnicalLabel("Money out", "outflow")
                    Text(
                        "-$${String.format("%.2f", uiState.totalOutflowCents / 100.0)}",
                        color = GlassTokens.ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = GlassTokens.DividerColor)

                val net = uiState.totalInflowCents - uiState.totalOutflowCents
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FriendlyTechnicalLabel("Net change", "net cash flow")
                    Text(
                        "${if (net >= 0) "+" else "-"}$${String.format("%.2f", abs(net) / 100.0)}",
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
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Neutral,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryGlassIcon(category = spend.category, size = 32.dp, iconSize = 16.dp)
                    Text(
                        spend.category.ifBlank { "Uncategorized" }.replaceFirstChar { it.uppercase() },
                        color = GlassTokens.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$${String.format("%.2f", abs(spend.totalCents) / 100.0)}",
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTokens.CyanBright
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
            }
        }
    }

    val (untracked, spendingPatterns) = uiState.recurringCandidates.partition { candidate ->
        uiState.payments.none { payment ->
            payment.name.lowercase().contains(candidate.pattern.lowercase()) ||
            candidate.pattern.lowercase().contains(payment.name.lowercase())
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
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Violet,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
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
                        TextButton(onClick = { onTrackAsBill(candidate.pattern, candidate.category, 0) }, modifier = Modifier.weight(1f)) {
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
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Cyan,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
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
            GlassCard(
                modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                tint = GlassTint.Neutral,
                surfaceStyle = GlassSurfaceStyle.Quiet,
                cornerRadius = 12.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(adj.description, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(adj.date.formatDateDisplay(), style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextDim)
                        Text(
                            "${if (adj.amount_cents >= 0) "+" else "-"}$${String.format("%.2f", abs(adj.amount_cents) / 100.0)}",
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
