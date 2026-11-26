package com.montecarlo.ledger.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.processing.BalanceForecastRow
import com.montecarlo.ledger.processing.MonteCarloResult
import com.montecarlo.ledger.util.centsToDisplay
import dev.chrisbanes.haze.HazeState
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(viewModel: MainViewModel, hazeState: HazeState? = null) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val purchaseSimulation by viewModel.purchaseSimulation.collectAsStateWithLifecycle()
    val paycheckDelaySimulation by viewModel.paycheckDelaySimulation.collectAsStateWithLifecycle()
    val scenarioError by viewModel.scenarioSimulationError.collectAsStateWithLifecycle()
    var showDailySheet by remember { mutableStateOf(false) }

    val fanPoints = remember(uiState.monteCarloDailyPercentiles) {
        uiState.monteCarloDailyPercentiles.map { pt ->
            FanChartPoint(
                dayIndex = pt.dayIndex,
                dateLabel = pt.date.format(rowFormatter),
                worst10Cents = pt.worst10Cents,
                medianCents = pt.medianCents,
                best90Cents = pt.best90Cents,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        // WS6: general-purpose "afford this?" / "what if my paycheck is delayed?" comparisons, run
        // over the same canonical scenario as the forecast below.
        item {
            ScenarioWhatIfCard(
                purchaseSimulation = purchaseSimulation,
                paycheckDelaySimulation = paycheckDelaySimulation,
                error = scenarioError,
                onCheckPurchase = viewModel::simulatePurchase,
                onCheckPaycheckDelay = viewModel::simulatePaycheckDelay,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        forecastSection(
            rows = uiState.forecastRows,
            lowestBalanceCents = uiState.forecastLowestBalanceCents,
            endingBalanceCents = uiState.forecastEndingBalanceCents,
            firstNegativeDateLabel = uiState.firstNegativeDateLabel,
            canonicalForecastStatus = uiState.canonicalForecastStatus,
            canonicalForecastAsOf = uiState.canonicalForecastAsOf,
            unavailableReason = uiState.canonicalForecastError,
            fanPoints = fanPoints,
            mcResult = uiState.monteCarloResult,
            hazeState = hazeState,
            onViewDailyValuesClicked = { showDailySheet = true },
        )
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showDailySheet) {
        ModalBottomSheet(
            onDismissRequest = { showDailySheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "90-Day Daily Projection Values",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    itemsIndexed(fanPoints) { _, pt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Day ${pt.dayIndex} (${pt.dateLabel})",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.TextSecondary,
                            )
                            Text(
                                "10th: ${centsToDisplay(pt.worst10Cents)} | 50th: ${centsToDisplay(pt.medianCents)} | 90th: ${centsToDisplay(pt.best90Cents)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        HorizontalDivider(color = GlassTokens.DividerColor)
                    }
                }
            }
        }
    }
}

/**
 * Presentation view of the authoritative forecast summary.
 *
 * Only raw authoritative values are stored. Labels are formatted at the point of display, and the
 * summary is not recomputed from [rows]: rows hold post-event balances only, so an empty row list
 * (no dated events) would read as `$0` and would ignore the opening balance.
 */
internal data class ForecastSummaryModel(
    val eventCount: Int,
    val lowestBalanceCents: Long?,
    val endingBalanceCents: Long?,
    val hasNegativeBalance: Boolean,
    val staleAsOf: String? = null,
) {
    val isAvailable: Boolean
        get() = lowestBalanceCents != null && endingBalanceCents != null

    val hasDatedEvents: Boolean
        get() = eventCount > 0

    val needsStatusMessage: Boolean
        get() = !isAvailable || !hasDatedEvents

    val staleLabel: String?
        get() = staleAsOf
            ?.takeIf { isAvailable }
            ?.let { "Previous forecast — calculated $it" }
}

internal fun forecastSummaryModel(
    rows: List<BalanceForecastRow>,
    lowestBalanceCents: Long?,
    endingBalanceCents: Long?,
    firstNegativeDateLabel: String?,
    staleAsOf: String? = null,
): ForecastSummaryModel {
    val available = lowestBalanceCents != null && endingBalanceCents != null
    return ForecastSummaryModel(
        eventCount = rows.size,
        lowestBalanceCents = lowestBalanceCents,
        endingBalanceCents = endingBalanceCents,
        hasNegativeBalance = available && firstNegativeDateLabel != null,
        staleAsOf = staleAsOf,
    )
}

internal fun forecastStaleAsOf(
    status: CanonicalForecastStatus,
    asOf: String?,
): String? = asOf?.takeIf { status == CanonicalForecastStatus.Stale }

internal fun LazyListScope.forecastSection(
    rows: List<com.montecarlo.ledger.processing.BalanceForecastRow>,
    lowestBalanceCents: Long?,
    endingBalanceCents: Long?,
    firstNegativeDateLabel: String?,
    canonicalForecastStatus: CanonicalForecastStatus,
    canonicalForecastAsOf: String?,
    unavailableReason: String? = null,
    fanPoints: List<FanChartPoint> = emptyList(),
    mcResult: MonteCarloResult? = null,
    hazeState: HazeState? = null,
    onViewDailyValuesClicked: (() -> Unit)? = null,
) {
    val summary = forecastSummaryModel(
        rows = rows,
        lowestBalanceCents = lowestBalanceCents,
        endingBalanceCents = endingBalanceCents,
        firstNegativeDateLabel = firstNegativeDateLabel,
        staleAsOf = forecastStaleAsOf(canonicalForecastStatus, canonicalForecastAsOf),
    )
    val firstNegativeIdx = rows.indexOfFirst { it.balanceCents < 0 }

    item {
        ForecastSummaryCard(
            summary = summary,
            hazeState = hazeState,
        )
    }

    if (fanPoints.isNotEmpty() && summary.isAvailable) {
        item {
            MonteCarloFanChart(
                points = fanPoints,
                result = mcResult,
                onViewDailyValuesClicked = onViewDailyValuesClicked,
            )
        }
    }

    item { Spacer(Modifier.height(4.dp)) }

    if (summary.needsStatusMessage) {
        item {
            ForecastStatusCard(
                isAvailable = summary.isAvailable,
                unavailableReason = unavailableReason,
            )
        }
    }

    if (summary.isAvailable) {
        itemsIndexed(rows) { index, row ->
            val isNegative = row.balanceCents < 0
            val isFirstNegative = index == firstNegativeIdx

            if (isFirstNegative) {
                WarningDivider()
            }

            ForecastRowCard(row = row, isNegative = isNegative)
        }
    }
}

// ── Summary card ─────────────────────────────────────────────────────────────

@Composable
private fun ForecastSummaryCard(
    summary: ForecastSummaryModel,
    hazeState: HazeState?,
) {
    val summaryTint = when {
        !summary.isAvailable -> GlassTint.Neutral
        summary.hasNegativeBalance -> GlassTint.Error
        else -> GlassTint.Teal
    }
    val eventCountLabel = if (summary.isAvailable) summary.eventCount.toString() else "—"
    val lowestBalanceLabel = summary.lowestBalanceCents?.let(::centsToDisplay) ?: "Unavailable"
    val endingBalanceLabel = summary.endingBalanceCents?.let(::centsToDisplay) ?: "Unavailable"
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
                        .background(
                            when {
                                !summary.isAvailable -> GlassTokens.TextDim
                                summary.hasNegativeBalance -> GlassTokens.ErrorRed
                                else -> GlassTokens.Cyan
                            }
                        )
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

            summary.staleLabel?.let { stale ->
                Text(
                    stale,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetric(
                    friendlyLabel = "Events",
                    technicalLabel = "forecast events",
                    value = eventCountLabel,
                    valueColor = GlassTokens.TextPrimary,
                )
                SummaryMetric(
                    friendlyLabel = "Lowest balance",
                    technicalLabel = "lowest forecast balance",
                    value = lowestBalanceLabel,
                    valueColor = when {
                        !summary.isAvailable -> GlassTokens.TextDim
                        (summary.lowestBalanceCents ?: 0L) < 0L -> GlassTokens.ErrorRed
                        else -> GlassTokens.PositiveGreen
                    },
                )
                SummaryMetric(
                    friendlyLabel = "Ending balance",
                    technicalLabel = "forecast ending balance",
                    value = endingBalanceLabel,
                    valueColor = when {
                        !summary.isAvailable -> GlassTokens.TextDim
                        (summary.endingBalanceCents ?: 0L) < 0L -> GlassTokens.ErrorRed
                        else -> GlassTokens.PositiveGreen
                    },
                )
            }

            if (summary.hasNegativeBalance) {
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
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$friendlyLabel $value"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
private fun ForecastStatusCard(
    isAvailable: Boolean,
    unavailableReason: String?,
) {
    GlassCard(tint = GlassTint.Neutral, surfaceStyle = GlassSurfaceStyle.Quiet) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (isAvailable) "No dated events" else "Forecast unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = GlassTokens.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                if (isAvailable) {
                    "Showing your opening balance. Add income or a bill to project changes."
                } else {
                    "The forecast was withheld, so no balances are shown."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary,
            )
            if (!isAvailable) {
                unavailableReason?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim,
                    )
                }
            }
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
private fun ForecastRowCard(row: BalanceForecastRow, isNegative: Boolean) {
    val accentColor = if (isNegative) GlassTokens.ErrorRed else GlassTokens.Cyan
    val today = java.time.LocalDate.now()

    SolidListSurface {
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
                    contentDescription = if (isNegative) "Negative balance trend" else "Positive balance trend",
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    centsToDisplay(row.balanceCents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        }
    }
}
