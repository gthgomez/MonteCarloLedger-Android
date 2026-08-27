package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.util.centsToDisplay
import com.montecarlo.ledger.util.centsToDisplayWhole
import com.montecarlo.ledger.util.centsToDollarInputString
import com.montecarlo.ledger.DashboardPrimaryAction
import com.montecarlo.ledger.DashboardWidget
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.OnboardingMilestone
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.processing.CategoryBudgetRow
import dev.chrisbanes.haze.HazeState


@Composable
internal fun PlanAheadCard(uiState: AppUiState) {
    var showHelp by remember { mutableStateOf(false) }
    val currentWindow = uiState.cashFlowWindows.firstOrNull()
    val paDailyBudgetStr = "${centsToDisplay(uiState.dailyBudgetCents)}"
    GlassCard(
        modifier = Modifier
            .heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight)
            .semantics {
                stateDescription = "Daily budget: $paDailyBudgetStr per day until ${uiState.nextPaydayLabel}"
            },
        tint = if ((currentWindow?.shortfallCents ?: 0) > 0) GlassTint.Error else GlassTint.Cyan,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Until next payday",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.semantics { heading() }
                )
                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.minimumIconButtonTouchTarget(),
                ) {
                    Icon(Icons.Default.Info, "How this works", tint = GlassTokens.TextDim)
                }
            }
            Text(
                "${centsToDisplay(uiState.dailyBudgetCents)} / day",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if ((currentWindow?.shortfallCents ?: 0) > 0) GlassTokens.ErrorRed else GlassTokens.CyanBright
            )
            Text(uiState.nextPaydayLabel, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
            currentWindow?.let { window ->
                Text(
                    if (window.shortfallCents > 0) {
                        "Short by ${centsToDisplay(window.shortfallCents)} before the next paycheck."
                    } else {
                        "Reserves ${centsToDisplay(window.billCents)} in bills before then."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (window.shortfallCents > 0) GlassTokens.ErrorRed else GlassTokens.TextDim
                )
                Text(
                    "Window ${window.startDate.formatDateDisplay()}-${window.endDate.minusDays(1).formatDateDisplay()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextDim
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("How we calculate your daily budget", color = GlassTokens.TextPrimary) },
            text = { Text("We look at your current balance minus all upcoming bills until your next paycheck. That remaining amount, divided by the days until payday, gives you a safe daily spend. If your bills exceed your balance, we show the projected shortfall instead.", color = GlassTokens.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Got it") }
            }
        )
    }
}
@Composable
internal fun MonteCarloCard(uiState: AppUiState) {
    var showHelp by remember { mutableStateOf(false) }
    val mcLowerStr = "${centsToDisplayWhole(uiState.monteCarlo10thCents)}"
    val mcTypicalStr = "${centsToDisplayWhole(uiState.monteCarlo50thCents)}"
    val mcHigherStr = "${centsToDisplayWhole(uiState.monteCarlo90thCents)}"
    val mcRiskStr = String.format("%.1f", uiState.probabilityNegativePct)
    GlassCard(
        modifier = Modifier
            .heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight)
            .semantics {
                stateDescription = "3-month estimate: lower $mcLowerStr, typical $mcTypicalStr, higher $mcHigherStr. Risk of running low: $mcRiskStr%"
            },
        tint = GlassTint.Teal,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "3-month estimate",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.semantics { heading() }
                )
                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.minimumIconButtonTouchTarget(),
                ) {
                    Icon(Icons.Default.Info, "How this works", tint = GlassTokens.TextDim)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Lower", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        "${centsToDisplayWhole(uiState.monteCarlo10thCents)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.monteCarlo10thCents < 0) GlassTokens.ErrorRed else GlassTokens.Cyan
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Typical", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        "${centsToDisplayWhole(uiState.monteCarlo50thCents)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTokens.TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Higher", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        "${centsToDisplayWhole(uiState.monteCarlo90thCents)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTokens.PositiveGreen
                    )
                }
            }
            Text(
                "Risk of running low: ${String.format("%.1f", uiState.probabilityNegativePct)}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.probabilityNegativePct > 25) GlassTokens.ErrorRed
                else GlassTokens.TextSecondary
            )
            uiState.monteCarloBasisLabel?.takeIf { it.isNotBlank() }?.let { basis ->
                Text(
                    basis,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextDim
                )
            }
            uiState.projectedTroubleDateLabel?.let {
                Text("Most likely first negative-balance date: $it", style = MaterialTheme.typography.labelSmall, color = GlassTokens.ErrorRed)
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("How the 3-month estimate works", color = GlassTokens.TextPrimary) },
            text = {
                Text(
                    "We run your upcoming income and bills through 500 different scenarios in the app (the home-screen widget uses a lighter 100-run sample). Each scenario adds random variation to your income and expenses calibrated from your spending history — or default assumptions until there is enough history — plus occasional surprise expenses. The results show you the range of possible outcomes — from worst case (10th percentile) to typical (median) to best case (90th percentile). Overdraft risk is the chance your projected balance goes below \$0 at any point in the next 90 days.\n\nThese estimates are for planning only and are not financial advice.",
                    color = GlassTokens.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Got it") }
            }
        )
    }
}
@Composable
internal fun UpcomingBillsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Upcoming Bills",
            style = MaterialTheme.typography.titleMedium,
            color = GlassTokens.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Text("The next few due dates.", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
    }
}
@Composable
internal fun UpcomingBillsCard(upcomingBills: List<String>) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Violet,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UpcomingBillsHeader()
            if (upcomingBills.isEmpty()) {
                Text(
                    "No upcoming bills in the forecast window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary
                )
            } else {
                upcomingBills.take(5).forEachIndexed { index, bill ->
                    if (index > 0) {
                        HorizontalDivider(color = GlassTokens.DividerColor)
                    }
                    Text(bill, style = MaterialTheme.typography.bodyLarge, color = GlassTokens.TextPrimary)
                }
            }
        }
    }
}
@Composable
internal fun GoalCard(
    goal: com.montecarlo.ledger.data.GoalEntity,
) {
    val progress = if (goal.targetAmountCents > 0) {
        goal.currentAmountCents.toFloat() / goal.targetAmountCents.toFloat()
    } else 0f

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Cyan,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(64.dp),
                    color = GlassTokens.CyanBright,
                    trackColor = GlassTokens.DividerColor,
                    strokeWidth = 6.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTokens.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Target: ${centsToDisplay(goal.targetAmountCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextSecondary
                )
                Text(
                    "Current: ${centsToDisplay(goal.currentAmountCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.CyanBright,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
internal fun normalizedSparklineX(index: Int, pointCount: Int, width: Float): Float {
    if (pointCount <= 1) return 0f
    return index.toFloat() / (pointCount - 1) * width
}
@Composable
internal fun PaceSparkline(
    currentPoints: List<Long>,
    avgPoints: List<Long>,
    modifier: Modifier = Modifier
) {
    val maxVal = (currentPoints.maxOrNull() ?: 0L).coerceAtLeast(avgPoints.maxOrNull() ?: 1L).toFloat()
    
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Draw Avg Line (Dashed)
        if (avgPoints.size > 1) {
            val path = androidx.compose.ui.graphics.Path()
            avgPoints.forEachIndexed { index, value ->
                val x = normalizedSparklineX(index, avgPoints.size, width)
                val y = height - (value.toFloat() / maxVal * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = GlassTokens.TextDim.copy(alpha = 0.3f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }
        
        // Draw Current Line
        if (currentPoints.size > 1) {
            val path = androidx.compose.ui.graphics.Path()
            currentPoints.forEachIndexed { index, value ->
                val x = normalizedSparklineX(index, currentPoints.size, width)
                val y = height - (value.toFloat() / maxVal * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = if ((currentPoints.lastOrNull() ?: 0) > (avgPoints.lastOrNull() ?: 0)) GlassTokens.ErrorRed else GlassTokens.CyanBright,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}
