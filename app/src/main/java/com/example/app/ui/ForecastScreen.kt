package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app.GlassTokens
import com.example.app.MainViewModel
import com.example.app.processing.BalanceForecastRow
import dev.chrisbanes.haze.HazeState
import java.time.format.DateTimeFormatter

@Composable
fun ForecastScreen(viewModel: MainViewModel, hazeState: HazeState? = null) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        forecastSection(uiState.forecastRows, hazeState)
        item { Spacer(Modifier.height(16.dp)) }
    }
}

internal fun LazyListScope.forecastSection(
    rows: List<com.example.app.processing.BalanceForecastRow>,
    hazeState: HazeState? = null,
) {
    val lowestBalance = rows.minOfOrNull { it.balanceCents } ?: 0
    val endingBalance = rows.lastOrNull()?.balanceCents ?: 0
    val firstNegativeIdx = rows.indexOfFirst { it.balanceCents < 0 }
    val hasNegative = firstNegativeIdx >= 0

    item {
        ForecastSummaryCard(
            rowCount = rows.size,
            lowestBalanceCents = lowestBalance,
            endingBalanceCents = endingBalance,
            hasNegative = hasNegative,
            hazeState = hazeState,
        )
    }

    item { Spacer(Modifier.height(4.dp)) }

    if (rows.isEmpty()) {
        item {
            ForecastEmptyState()
        }
    }

    itemsIndexed(rows) { index, row ->
        val isNegative = row.balanceCents < 0
        val isFirstNegative = index == firstNegativeIdx
        val tint = when {
            isNegative -> GlassTint.Error
            else -> GlassTint.Teal
        }

        if (isFirstNegative) {
            WarningDivider()
        }

        ForecastRowCard(row = row, tint = tint, isNegative = isNegative)
    }
}

// ── Summary card ─────────────────────────────────────────────────────────────

@Composable
private fun ForecastSummaryCard(
    rowCount: Int,
    lowestBalanceCents: Int,
    endingBalanceCents: Int,
    hasNegative: Boolean,
    hazeState: HazeState?,
) {
    val summaryTint = if (hasNegative) GlassTint.Error else GlassTint.Teal
    GlassCard(tint = summaryTint, surfaceStyle = GlassSurfaceStyle.Hero, hazeState = hazeState) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Accent dot matching tint
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hasNegative) GlassTokens.ErrorRed else GlassTokens.Cyan)
                )
                Text(
                    "90-day forecast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTokens.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
            }

            HorizontalDivider(color = GlassTokens.DividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetric(
                    friendlyLabel = "Events",
                    technicalLabel = "forecast events",
                    value = "$rowCount",
                    valueColor = GlassTokens.TextPrimary,
                )
                SummaryMetric(
                    friendlyLabel = "Lowest balance",
                    technicalLabel = "lowest forecast balance",
                    value = formatCents(lowestBalanceCents),
                    valueColor = if (lowestBalanceCents < 0) GlassTokens.ErrorRed else GlassTokens.PositiveGreen,
                )
                SummaryMetric(
                    friendlyLabel = "Ending balance",
                    technicalLabel = "forecast ending balance",
                    value = formatCents(endingBalanceCents),
                    valueColor = if (endingBalanceCents < 0) GlassTokens.ErrorRed else GlassTokens.PositiveGreen,
                )
            }

            if (hasNegative) {
                Text(
                    "Balance may dip below zero. Review scheduled bills.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.ErrorRed.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(friendlyLabel: String, technicalLabel: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        FriendlyTechnicalLabel(
            friendly = friendlyLabel,
            technical = technicalLabel,
        )
    }
}

@Composable
private fun ForecastEmptyState() {
    GlassCard(tint = GlassTint.Neutral, surfaceStyle = GlassSurfaceStyle.Quiet) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Nothing to forecast yet.",
                style = MaterialTheme.typography.titleMedium,
                color = GlassTokens.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Add a paycheck and at least one bill, then this screen will show the next 90 days of forecast events and the ending balance.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary,
            )
        }
    }
}

// ── Warning divider between positive and negative rows ───────────────────────

@Composable
private fun WarningDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassTokens.ErrorRed.copy(alpha = 0.55f))
        )
        Text(
            "Crosses below zero",
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.ErrorRed.copy(alpha = 0.90f),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassTokens.ErrorRed.copy(alpha = 0.55f))
        )
    }
}

// ── Individual forecast row ───────────────────────────────────────────────────

private val rowFormatter = DateTimeFormatter.ofPattern("MMM d")
private val yearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
private fun ForecastRowCard(row: BalanceForecastRow, tint: GlassTint, isNegative: Boolean) {
    val accentColor = if (isNegative) GlassTokens.ErrorRed else GlassTokens.Cyan
    val today = java.time.LocalDate.now()

    GlassCard(tint = tint, surfaceStyle = GlassSurfaceStyle.Quiet, cornerRadius = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: date + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Vertical accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    accentColor,
                                    accentColor.copy(alpha = 0.15f),
                                )
                            )
                        )
                )
                Column {
                    Text(
                        if (row.date.year == today.year) row.date.format(rowFormatter) else row.date.format(yearFormatter),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTokens.TextPrimary,
                    )
                    Text(
                        "Projected balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim,
                    )
                }
            }

            // Right: balance + trend icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isNegative) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    formatCents(row.balanceCents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatCents(cents: Int): String {
    val abs = Math.abs(cents) / 100.0
    return if (cents < 0) "-$${String.format("%.2f", abs)}" else "$${String.format("%.2f", abs)}"
}
