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
internal fun NetWorthCard(
    uiState: AppUiState,
    onOpenDebtPayoff: () -> Unit,
) {
    val totalAssets = uiState.assets.sumOf { it.balanceCents }
    val netWorth = uiState.totalNetWorthCents

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total Net Worth",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.TextSecondary
                )
                Text(
                    if (netWorth < 0) "Negative" else "Growth",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (netWorth < 0) GlassTokens.ErrorRed else GlassTokens.PositiveGreen
                )
            }
            Text(
                "${centsToDisplay(netWorth)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = GlassTokens.TextPrimary
            )
            HorizontalDivider(color = GlassTokens.DividerColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Liquid", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        "${centsToDisplay(uiState.ledgerBalanceCents)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.CyanBright
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Assets", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        "${centsToDisplay(totalAssets)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.PositiveGreen
                    )
                }
            }
            TextButton(
                onClick = onOpenDebtPayoff,
                modifier = Modifier.testTag(DashboardTestTags.DEBT_PAYOFF_LINK),
            ) {
                Text("Debt payoff simulator")
            }
        }
    }
}
@Composable
internal fun OnboardingProgressCard(
    progress: OnboardingProgress,
    onAddIncome: () -> Unit,
    onAddPayment: () -> Unit,
    onAddTransaction: () -> Unit,
    onCheckBalance: () -> Unit,
    onAddGoal: () -> Unit,
) {
    val steps = progress.steps()
    val nextActionMilestone = progress.nextActionMilestone()
    val stepLabel = when (progress.completedCount) {
        0 -> "Step 1 of 4"
        1 -> "Step 2 of 4"
        2 -> "Step 3 of 4"
        3 -> "Last step"
        else -> "Setup complete"
    }
    val headline = when (nextActionMilestone) {
        OnboardingMilestone.RECONCILIATION -> "What does your bank account say right now?"
        OnboardingMilestone.FIRST_INCOME -> "Add your first paycheck"
        OnboardingMilestone.FIRST_BILL -> "Add your first bill"
        OnboardingMilestone.FIRST_GOAL -> "What are you saving for?"
        OnboardingMilestone.FIRST_EXPENSE -> "Record your first expense"
        null -> "You're all set"
    }
    val body = when (nextActionMilestone) {
        OnboardingMilestone.RECONCILIATION -> "Enter your current bank balance. This grounds every number — the forecast starts from your real balance, not zero."
        OnboardingMilestone.FIRST_INCOME -> "Add what you earn so the app knows how much money comes in. You can enter an hourly rate or a flat amount."
        OnboardingMilestone.FIRST_BILL -> "Add rent, a subscription, or any regular payment. More bills can be added any time."
        OnboardingMilestone.FIRST_GOAL -> "Even a rough target helps you see how far your money goes. You can update it later."
        OnboardingMilestone.FIRST_EXPENSE -> "Log a purchase to start tracking your spending."
        null -> "Setup is complete. Your balance, income, and bills are in place."
    }
    val primaryActionText = when (nextActionMilestone) {
        OnboardingMilestone.RECONCILIATION -> "Enter bank balance"
        OnboardingMilestone.FIRST_INCOME -> "Add paycheck"
        OnboardingMilestone.FIRST_BILL -> "Add bill"
        OnboardingMilestone.FIRST_GOAL -> "Set a goal"
        OnboardingMilestone.FIRST_EXPENSE -> "Record spending"
        null -> "Continue"
    }
    val primaryAction: () -> Unit = when (nextActionMilestone) {
        OnboardingMilestone.RECONCILIATION -> onCheckBalance
        OnboardingMilestone.FIRST_INCOME -> onAddIncome
        OnboardingMilestone.FIRST_BILL -> onAddPayment
        OnboardingMilestone.FIRST_GOAL -> onAddGoal
        OnboardingMilestone.FIRST_EXPENSE -> onAddTransaction
        null -> onCheckBalance
    }
    // Secondary actions are tappable shortcuts to jump to any other step
    val secondaryActions: List<Pair<String, () -> Unit>> = when (nextActionMilestone) {
        OnboardingMilestone.RECONCILIATION -> listOf(
            "Add paycheck first" to onAddIncome,
            "Add a bill first" to onAddPayment,
        )
        OnboardingMilestone.FIRST_INCOME -> listOf(
            "Add a bill instead" to onAddPayment,
            "Skip to savings goal" to onAddGoal,
        )
        OnboardingMilestone.FIRST_BILL -> listOf(
            "Add paycheck instead" to onAddIncome,
            "Skip to savings goal" to onAddGoal,
        )
        OnboardingMilestone.FIRST_GOAL -> listOf(
            "Skip this step" to {},
        )
        OnboardingMilestone.FIRST_EXPENSE -> listOf(
            "Skip this step" to {},
        )
        null -> emptyList()
    }

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Cyan,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stepLabel,
                style = MaterialTheme.typography.labelMedium,
                color = GlassTokens.TextSecondary
            )
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                color = GlassTokens.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            // Step checklist
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                steps.forEach { step ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            if (step.completed) "✓" else "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (step.completed) GlassTokens.PositiveGreen else GlassTokens.TextDim
                        )
                        Text(
                            step.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (step.completed) GlassTokens.PositiveGreen else GlassTokens.TextSecondary
                        )
                    }
                }
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary
            )
            AppPrimaryButton(text = primaryActionText, onClick = primaryAction)
            if (secondaryActions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    secondaryActions.forEach { (label, action) ->
                        androidx.compose.material3.TextButton(
                            onClick = action,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.TextDim
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun SetupCompleteCard(
    uiState: AppUiState,
    actionCenterVisible: Boolean,
) {
    val dailyBudget = uiState.dailyBudgetCents
    val safeToSpend = uiState.safeToSpendCents
    val nextPayday = uiState.nextPaydayLabel
    val billBurden = uiState.upcomingBillBurdenCents
    val reconciled = uiState.isBalanceReconciled
    val overPlan = reconciled && safeToSpend < 0

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = if (overPlan && !actionCenterVisible) GlassTint.Error else GlassTint.Cyan,
        surfaceStyle = if (actionCenterVisible) GlassSurfaceStyle.Standard else GlassSurfaceStyle.Hero,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Here's where you stand",
                style = MaterialTheme.typography.titleMedium,
                color = GlassTokens.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            when {
                actionCenterVisible && !reconciled -> {
                    Text(
                        "Upcoming bills total ${centsToDisplay(billBurden)}. " +
                            "Numbers below use your app total until you confirm bank balance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextSecondary,
                    )
                }
                actionCenterVisible && overPlan -> {
                    val trouble = uiState.firstNegativeDateLabel?.let { " around $it" }.orEmpty()
                    Text(
                        "Upcoming bills total ${centsToDisplay(billBurden)}$trouble.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextSecondary,
                    )
                }
                actionCenterVisible -> {
                    Text(
                        "Upcoming bills (${centsToDisplay(billBurden)}) before your next payday.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextSecondary,
                    )
                }
                !reconciled -> {
                    Text(
                        "Confirm your bank balance to unlock a trusted safe-to-spend figure. " +
                            "Numbers below use your app total and may look low until you do.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextSecondary,
                    )
                }
                overPlan -> {
                    val trouble = uiState.firstNegativeDateLabel?.let { " around $it" }.orEmpty()
                    Text(
                        "Based on upcoming bills (${centsToDisplay(billBurden)}), " +
                            "your balance could dip short by ${centsToDisplay(-safeToSpend)}$trouble.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.ErrorRed,
                    )
                }
                else -> {
                    Text(
                        "After your upcoming bills (${centsToDisplay(billBurden)}), " +
                            "you have about ${centsToDisplay(safeToSpend)} you can spend or save.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextSecondary,
                    )
                }
            }
            HorizontalDivider(color = GlassTokens.DividerColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!actionCenterVisible) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when {
                                !reconciled -> "Provisional safe-to-spend"
                                overPlan -> "Could dip short by"
                                else -> "Safe to spend"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTokens.TextDim
                        )
                        Text(
                            if (overPlan) {
                                "${centsToDisplay(-safeToSpend)}"
                            } else {
                                "${centsToDisplay(safeToSpend)}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (overPlan) GlassTokens.ErrorRed else GlassTokens.PositiveGreen
                        )
                    }
                }
                Column(
                    modifier = if (actionCenterVisible) Modifier.fillMaxWidth() else Modifier,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = if (actionCenterVisible) {
                        androidx.compose.ui.Alignment.Start
                    } else {
                        androidx.compose.ui.Alignment.End
                    }
                ) {
                    Text("Daily budget", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        if (!reconciled) "—" else "${centsToDisplay(dailyBudget)} / day",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = GlassTokens.CyanBright
                    )
                }
            }
            Text(
                nextPayday,
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextDim
            )
        }
    }
}
@Composable
internal fun MonitoringModeCard(
    uiState: AppUiState,
) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Cyan,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Setup complete",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTokens.TextSecondary
            )
            Text(
                "Tracking is on",
                style = MaterialTheme.typography.titleMedium,
                color = GlassTokens.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "The app will watch your balance and forecast without the setup checklist.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FriendlyTechnicalLabel("Bank balance", "reconciled balance")
                        Text(
                            "${centsToDisplay(uiState.bankBalanceCents)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.PositiveGreen
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FriendlyTechnicalLabel("App total", "ledger balance")
                        Text(
                            "${centsToDisplay(uiState.ledgerBalanceCents)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.TextPrimary
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FriendlyTechnicalLabel("Starting point", "forecast seed")
                        Text(
                            if (uiState.isBalanceReconciled) "Bank balance" else "App balance",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTokens.PositiveGreen
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FriendlyTechnicalLabel(
                            if (uiState.isBalanceReconciled) "Safe to spend" else "Provisional",
                            "forecast-safe amount",
                        )
                        Text(
                            when {
                                !uiState.isBalanceReconciled -> "Confirm balance"
                                uiState.safeToSpendCents < 0 ->
                                    "Short ${centsToDisplay(-uiState.safeToSpendCents)}"
                                else -> "${centsToDisplay(uiState.safeToSpendCents)}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                !uiState.isBalanceReconciled -> GlassTokens.CyanBright
                                uiState.safeToSpendCents < 0 -> GlassTokens.ErrorRed
                                else -> GlassTokens.PositiveGreen
                            }
                        )
                    }
                }
            }
            Text(
                "You can still add new paychecks, bills, or spending whenever life changes.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextDim
            )
        }
    }
}
