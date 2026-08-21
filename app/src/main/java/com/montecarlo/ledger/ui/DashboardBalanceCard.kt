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
internal fun BalanceCard(
    reconciled: Boolean,
    bankBalanceCents: Long,
    ledgerBalanceCents: Long,
    uiState: AppUiState,
    showForecastCards: Boolean,
    onboardingProgress: OnboardingProgress,
    actionCenterVisible: Boolean,
    hazeState: HazeState?,
    onCheckBalance: () -> Unit,
    onAddIncome: () -> Unit,
    onAddPayment: () -> Unit,
    onAddTransaction: () -> Unit,
    onAddGoal: () -> Unit = {},
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier
            .testTag(DashboardTestTags.BALANCE_CARD)
            .semantics {
                heading()
                stateDescription = buildString {
                    append(if (reconciled) "Bank balance confirmed. " else "Bank balance not confirmed yet. ")
                    append("Bank balance ${centsToDollarInputString(bankBalanceCents)}. ")
                    append("App balance ${centsToDollarInputString(ledgerBalanceCents)}. ")
                    if (showForecastCards) {
                        append(if (reconciled) "Forecast starts from your bank balance. " else "Forecast starts from your app balance until you confirm. ")
                        if (!actionCenterVisible) {
                            if (!reconciled) {
                                append("Safe to spend is provisional until you confirm bank balance.")
                            } else if (uiState.safeToSpendCents < 0) {
                                append("Balance could dip short over the forecast window.")
                            } else {
                                append("Safe to spend ${centsToDollarInputString(uiState.safeToSpendCents)}.")
                            }
                        }
                    } else {
                        append("Add a paycheck or a bill to start the forecast. ")
                    }
                }
            },
        tint = GlassTint.Cyan,
        surfaceStyle = GlassSurfaceStyle.Hero,
        hazeState = hazeState,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBrandMark(modifier = Modifier.size(44.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (reconciled) "Bank balance" else "Bank balance not confirmed",
                            style = MaterialTheme.typography.labelLarge,
                            color = GlassTokens.TextSecondary
                        )
                        Text(
                            "${centsToDisplay(bankBalanceCents)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = GlassTokens.CyanBright
                        )
                    }
                }
                IconButton(
                    onClick = { showHelpDialog = true },
                    modifier = Modifier.minimumIconButtonTouchTarget(),
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "How to read these numbers",
                        tint = GlassTokens.TextSecondary
                    )
                }
            }
            Text(
                if (reconciled) {
                    "This is the bank balance you confirmed."
                } else {
                    if (showForecastCards) {
                        "Forecasts use your app balance until you confirm a bank balance."
                    } else {
                        "Add a paycheck or a bill to start the forecast."
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextDim
            )
            HorizontalDivider(color = GlassTokens.DividerColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FriendlyTechnicalLabel(
                            friendly = "App total",
                            technical = "ledger balance",
                        )
                    Text(
                        "${centsToDisplay(ledgerBalanceCents)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTokens.TextPrimary
                    )
                }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (showForecastCards) {
                        FriendlyTechnicalLabel(
                            friendly = "Starting point",
                            technical = "forecast seed",
                        )
                            Text(
                                if (reconciled) "Bank balance" else "App balance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GlassTokens.PositiveGreen
                            )
                    } else {
                        FriendlyTechnicalLabel(
                            friendly = "Forecast locked",
                            technical = "forecast disabled",
                        )
                        Text(
                            "Add a paycheck or a bill",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GlassTokens.PositiveGreen
                        )
                    }
                }
            }
            // Legend moved to showHelpDialog
            if (showForecastCards && uiState.ledgerBalanceCents != uiState.bankBalanceCents) {
                val differenceCents = ledgerBalanceCents - bankBalanceCents
                val diffLabel = if (differenceCents >= 0) "ledger above bank" else "bank above ledger"
                Text(
                    text = "Difference: ${centsToDisplay(kotlin.math.abs(differenceCents))} $diffLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.ErrorRed
                )
            }
            if (showForecastCards && uiState.upcomingBillBurdenCents > 0) {
                Text(
                    "Scheduled bills in forecast: ${centsToDisplay(uiState.upcomingBillBurdenCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.VioletLight
                )
            }
            AppNeutralButton(
                text = if (reconciled) "Update bank balance" else "Confirm bank balance",
                onClick = onCheckBalance,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showForecastCards && !actionCenterVisible) {
                when {
                    !reconciled -> {
                        Text(
                            "Confirm your bank balance to unlock a trusted safe-to-spend number. " +
                                "Forecast below is provisional from your app total.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTokens.TextSecondary,
                        )
                    }
                    uiState.safeToSpendCents < 0 -> {
                        val trouble = uiState.firstNegativeDateLabel?.let { " around $it" }.orEmpty()
                        Text(
                            "Based on upcoming bills, your balance could dip short by " +
                                "${centsToDisplay(-uiState.safeToSpendCents)}$trouble.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTokens.ErrorRed,
                        )
                    }
                    else -> {
                        Text(
                            "Safe to spend: ${centsToDisplay(uiState.safeToSpendCents)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTokens.PositiveGreen,
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            } else if (showForecastCards && !reconciled) {
                Text(
                    "Confirm your bank balance to unlock a trusted safe-to-spend number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else if (showForecastCards) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showForecastCards) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Financial Runway", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextSecondary)
                        Text("${uiState.runwayDays} days", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextPrimary)
                    }
                    LinearProgressIndicator(
                        progress = { (uiState.runwayDays / 90f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = if (uiState.runwayDays > 30) GlassTokens.PositiveGreen else if (uiState.runwayDays > 14) GlassTokens.CyanBright else GlassTokens.ErrorRed,
                        trackColor = GlassTokens.DividerColor,
                    )
                }

                if (uiState.currentMonthPacing.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Spending Pace (vs Avg)", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextSecondary)
                        PaceSparkline(
                            currentPoints = uiState.currentMonthPacing,
                            avgPoints = uiState.avgMonthPacing,
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        )
                    }
                }
                uiState.firstNegativeDateLabel?.let {
                    Text(
                        "Balance goes negative on $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.ErrorRed
                    )
                }
                uiState.lowestBalanceDateLabel?.let {
                    Text(
                        "Lowest projected balance on $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim
                    )
                }
                if (uiState.incomeContributionCents > 0) {
                    Text(
                        "(Includes ${centsToDisplay(uiState.incomeContributionCents)} projected income)",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.TextDim
                    )
                }
            } else {
                Text(
                    "Once you have income or bills in place, the forecast and safe-to-spend numbers will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextDim
                )
            }
            if (!showForecastCards) {
                val nextStep = when (onboardingProgress.nextActionMilestone()) {
                    OnboardingMilestone.FIRST_INCOME -> "Log paycheck"
                    OnboardingMilestone.FIRST_BILL -> "Add bill"
                    OnboardingMilestone.FIRST_GOAL -> "Set a savings goal"
                    OnboardingMilestone.FIRST_EXPENSE -> "Record spending"
                    OnboardingMilestone.RECONCILIATION -> "Confirm balance"
                    null -> "Open Add"
                }
                val nextAction = when (onboardingProgress.nextActionMilestone()) {
                    OnboardingMilestone.FIRST_INCOME -> onAddIncome
                    OnboardingMilestone.FIRST_BILL -> onAddPayment
                    OnboardingMilestone.FIRST_GOAL -> onAddGoal
                    OnboardingMilestone.FIRST_EXPENSE -> onAddTransaction
                    OnboardingMilestone.RECONCILIATION -> onCheckBalance
                    null -> onAddTransaction
                }
                AppPrimaryButton(text = nextStep, onClick = nextAction)
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("How to read these numbers", color = GlassTokens.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FriendlyTechnicalLabel(friendly = "Bank balance", technical = "bank balance")
                        Text(
                            "What your bank says you have right now. Update this after each paycheck or large purchase.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTokens.TextSecondary
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FriendlyTechnicalLabel(friendly = "App total", technical = "ledger balance")
                        Text(
                            "What the app calculates by adding up all the paychecks, bills, and spending you've recorded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTokens.TextSecondary
                        )
                    }
                    if (showForecastCards) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FriendlyTechnicalLabel(friendly = "Starting point", technical = "forecast seed")
                            Text(
                                "Where the 3-month estimate starts. Uses your confirmed bank balance if available, otherwise uses the app total.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTokens.TextSecondary
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FriendlyTechnicalLabel(friendly = "Okay to spend today", technical = "forecast-safe amount")
                            Text(
                                "How much you can spend right now without running out of money before your next bill or paycheck.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTokens.TextSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

