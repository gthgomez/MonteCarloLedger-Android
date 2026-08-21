package com.montecarlo.ledger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.ui.GlassTint
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.data.ReminderPreferences
import com.montecarlo.ledger.data.SettingsEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.OnboardingMilestone
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.util.centsToDisplay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalDate


@Composable
internal fun OnboardingProgressWidget(
    progress: OnboardingProgress,
    onClick: () -> Unit,
) {
    val completed = progress.completedCount
    val total = 4
    val contentLabel = if (progress.isComplete) {
        "Setup complete"
    } else {
        "Setup $completed of $total"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = GlassTokens.CyanBright.copy(alpha = 0.12f),
        contentColor = GlassTokens.TextPrimary,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            GlassTokens.CyanBright.copy(alpha = 0.26f)
        ),
        modifier = Modifier.widthIn(min = 92.dp, max = 154.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(total) { index ->
                    val filled = index < completed
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (filled) GlassTokens.CyanBright else GlassTokens.TextDim,
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                }
            }
            Text(
                contentLabel,
                style = MaterialTheme.typography.labelSmall,
                color = GlassTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AdaptiveNavigationRail(
    section: AppSection,
    onSelectSection: (AppSection) -> Unit,
    railWidth: Dp,
    colorScheme: androidx.compose.material3.ColorScheme,
) {
    NavigationRail(
        containerColor = Color.Transparent,
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(
                colorScheme.surfaceContainerHigh.copy(alpha = 0.60f)
            )
            .drawWithContent {
                drawContent()
                // Right-edge border separates rail from content area
                drawRect(
                    color = GlassTokens.NavBorderTop,
                    topLeft = Offset(size.width - 1.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height),
                )
            }
    ) {
        AppSection.primaryNav.forEach { item ->
            NavigationRailItem(
                selected = section == item,
                onClick = { onSelectSection(item) },
                icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                modifier = Modifier,
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    selectedIconColor = GlassTokens.CyanBright,
                    selectedTextColor = GlassTokens.CyanBright,
                    indicatorColor = GlassTokens.NavIndicator,
                    unselectedIconColor = GlassTokens.TextSecondary,
                    unselectedTextColor = GlassTokens.TextSecondary
                )
            )
        }
    }

}

internal enum class AppSection(val label: String, val shortLabel: String, val title: String) {
    Dashboard("Home", "H", "Home"),
    Ledger("Entries", "E", "Entries"),
    Planning("Plan", "P", "Planning"),
    Review("Review", "R", "Command Center"),
    Analysis("Forecast", "F", "Forecast"),
    DebtPayoff("Debt", "D", "Debt Payoff");

    companion object {
        /** Primary bottom/rail destinations — Debt Payoff is reached from Dashboard. */
        val primaryNav: List<AppSection> = entries.filter { it != DebtPayoff }
    }

    val icon: ImageVector
        get() = when (this) {
            Dashboard -> Icons.Filled.Dashboard
            Ledger -> Icons.AutoMirrored.Filled.List
            Planning -> Icons.Filled.Timeline
            Review -> Icons.Filled.History
            Analysis -> Icons.Filled.Assessment
            DebtPayoff -> Icons.Filled.Insights
        }
}

internal enum class AddKind {
    Income,
    Bill,
    Transaction,
    Goal,
}

private data class AddActionOption(
    val kind: AddKind,
    val isReviewBalance: Boolean = false,
    val title: String,
    val technicalLabel: String,
    val description: String,
    val buttonText: String
)

internal fun RecurringCandidate.toBillPrefill(): BillPrefill {
    val lastSeen = runCatching { LocalDate.parse(lastSeenDate) }.getOrElse { LocalDate.now() }
    val recurrence = when (cadenceLabel.lowercase()) {
        "weekly" -> "Weekly"
        "bi-weekly" -> "Bi-weekly"
        "quarterly" -> "Quarterly"
        "yearly" -> "Yearly"
        else -> "Monthly"
    }
    val nextDate = when (recurrence) {
        "Weekly" -> lastSeen.plusWeeks(1)
        "Bi-weekly" -> lastSeen.plusWeeks(2)
        "Quarterly" -> lastSeen.plusMonths(3)
        "Yearly" -> lastSeen.plusYears(1)
        else -> lastSeen.plusMonths(1)
    }
    return BillPrefill(
        name = pattern.replaceFirstChar { it.uppercase() },
        suggestedCategory = category,
        recurrence = recurrence,
        nextDate = nextDate.toString(),
    )
}

@Composable
internal fun AddActionsScreen(
    nextActionMilestone: OnboardingMilestone?,
    onAddIncome: () -> Unit,
    onAddPayment: () -> Unit,
    onAddTransaction: () -> Unit,
    onReviewBalance: () -> Unit,
    onAddGoal: () -> Unit,
) {
    val options = buildList {
        if (nextActionMilestone == OnboardingMilestone.RECONCILIATION) {
            add(
                AddActionOption(
                    kind = AddKind.Transaction,
                    isReviewBalance = true,
                    title = "Enter your bank balance",
                    technicalLabel = "bank check-in",
                    description = "What does your bank say right now? This grounds every number in the app.",
                    buttonText = "Enter bank balance"
                )
            )
        }
        addAll(
            listOf(
                AddActionOption(
                    kind = AddKind.Income,
                    title = "Add your first paycheck",
                    technicalLabel = "income entry",
                    description = "Add what you earn so the app knows how much money comes in.",
                    buttonText = "Log paycheck"
                ),
                AddActionOption(
                    kind = AddKind.Bill,
                    title = "Add your first bill",
                    technicalLabel = "bill entry",
                    description = "Add rent, utilities, or subscriptions so due dates show up.",
                    buttonText = "Add bill"
                ),
                AddActionOption(
                    kind = AddKind.Goal,
                    title = "Set a savings goal",
                    technicalLabel = "goal",
                    description = "Name something you’re saving for and set a target amount.",
                    buttonText = "Set a goal"
                ),
            )
        )
    }.sortedBy { option ->
        when {
            option.isReviewBalance -> 0
            nextActionMilestone == OnboardingMilestone.FIRST_INCOME && option.kind == AddKind.Income -> 0
            nextActionMilestone == OnboardingMilestone.FIRST_BILL && option.kind == AddKind.Bill -> 0
            nextActionMilestone == OnboardingMilestone.FIRST_GOAL && option.kind == AddKind.Goal -> 0
            else -> 1
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(tint = GlassTint.Cyan, surfaceStyle = GlassSurfaceStyle.Standard) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (nextActionMilestone) {
                        OnboardingMilestone.RECONCILIATION -> "Start here"
                        OnboardingMilestone.FIRST_INCOME -> "Next up"
                        OnboardingMilestone.FIRST_BILL -> "Almost there"
                        OnboardingMilestone.FIRST_GOAL -> "Last step"
                        OnboardingMilestone.FIRST_EXPENSE -> "One more thing"
                        null -> "Choose what to add"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.TextSecondary
                )
                Text(
                    when (nextActionMilestone) {
                        OnboardingMilestone.RECONCILIATION -> "What does your bank account say right now?"
                        OnboardingMilestone.FIRST_INCOME -> "Add your first paycheck"
                        OnboardingMilestone.FIRST_BILL -> "Add your first bill"
                        OnboardingMilestone.FIRST_GOAL -> "What are you saving for?"
                        OnboardingMilestone.FIRST_EXPENSE -> "Record your first expense"
                        null -> "Add the next thing you want to track"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = GlassTokens.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    when (nextActionMilestone) {
                        OnboardingMilestone.RECONCILIATION -> "This grounds every number — the forecast starts from your real balance, not zero."
                        OnboardingMilestone.FIRST_GOAL -> "Even a rough target helps you see how far your money goes."
                        else -> "We’ll only ask for what matters."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary
                )
            }
        }
        options.forEach { option ->
            GlassCard(
                tint = if (
                    (nextActionMilestone == OnboardingMilestone.RECONCILIATION && option.isReviewBalance) ||
                    (nextActionMilestone == OnboardingMilestone.FIRST_INCOME && option.kind == AddKind.Income) ||
                    (nextActionMilestone == OnboardingMilestone.FIRST_BILL && option.kind == AddKind.Bill) ||
                    (nextActionMilestone == OnboardingMilestone.FIRST_GOAL && option.kind == AddKind.Goal)
                ) GlassTint.Cyan else GlassTint.Neutral,
                surfaceStyle = GlassSurfaceStyle.Quiet
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FriendlyTechnicalLabel(
                        friendly = option.title,
                        technical = option.technicalLabel
                    )
                    Text(
                        option.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextSecondary
                    )
                    val isRecommended = when (nextActionMilestone) {
                        OnboardingMilestone.RECONCILIATION -> option.isReviewBalance
                        OnboardingMilestone.FIRST_INCOME -> option.kind == AddKind.Income
                        OnboardingMilestone.FIRST_BILL -> option.kind == AddKind.Bill
                        OnboardingMilestone.FIRST_GOAL -> option.kind == AddKind.Goal
                        OnboardingMilestone.FIRST_EXPENSE -> option.kind == AddKind.Transaction
                        null -> false
                    }
                    if (isRecommended) {
                        AppPrimaryButton(
                            text = option.buttonText,
                            onClick = when (option.kind) {
                                AddKind.Income -> onAddIncome
                                AddKind.Bill -> onAddPayment
                                AddKind.Goal -> onAddGoal
                                AddKind.Transaction -> if (option.isReviewBalance) onReviewBalance else onAddTransaction
                            }
                        )
                    } else {
                        AppNeutralButton(
                            text = option.buttonText,
                            onClick = when (option.kind) {
                                AddKind.Income -> onAddIncome
                                AddKind.Bill -> onAddPayment
                                AddKind.Goal -> onAddGoal
                                AddKind.Transaction -> onAddTransaction
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonitoringModeIntroScreen(
    uiState: com.montecarlo.ledger.AppUiState,
    onboardingProgress: OnboardingProgress,
    hazeState: HazeState,
    onContinue: () -> Unit,
) {
    AppBrandBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .hazeSource(hazeState)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = GlassTint.Cyan,
            surfaceStyle = GlassSurfaceStyle.Hero
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Setup complete",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.TextSecondary
                )
                Text(
                    "You're ready",
                    style = MaterialTheme.typography.headlineSmall,
                    color = GlassTokens.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "You finished setup. The app will keep watching your balance and forecast in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            FriendlyTechnicalLabel("Bank balance", "bank check-in")
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
                                "Ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTokens.PositiveGreen
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            FriendlyTechnicalLabel("Okay to spend today", "forecast-safe amount")
                            Text(
                                if (uiState.safeToSpendCents < 0) {
                                    "Overdraft projected"
                                } else {
                                    "${centsToDisplay(uiState.safeToSpendCents)}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (uiState.safeToSpendCents < 0) GlassTokens.ErrorRed else GlassTokens.PositiveGreen
                            )
                        }
                    }
                }
                Text(
                    "Add more paychecks, bills, or spending whenever life changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextDim
                )
                AppPrimaryButton(text = "Enter dashboard", onClick = onContinue)
            }
        }
    }
}

