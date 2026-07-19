package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app.GlassTokens
import com.example.app.MainViewModel
import com.example.app.data.CategoryBudgetEntity
import com.example.app.data.GoalEntity
import com.workspace.design.ConfirmDeleteDialog
import com.example.app.processing.CategoryBudgetRow
import com.example.app.processing.CashFlowWindow
import com.example.app.processing.MonthlySpendingPlan
import com.example.app.processing.MonthlySpendingPlanCalculator
import com.example.app.processing.TimelineService
import com.example.app.util.centsToDisplay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale


@Composable
fun PlanningScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val incomes by viewModel.allIncome.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val billOccurrences by viewModel.allBillOccurrences.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showAddBudgetDialog by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val monthStart = today.withDayOfMonth(1)
    val daysInMonth = today.lengthOfMonth()
    val monthEnd = monthStart.plusMonths(1)
    val monthlyEvents = remember(incomes, payments, billOccurrences, monthStart) {
        TimelineService.generateTimeline(
            incomes = incomes,
            payments = payments,
            startDate = monthStart,
            daysAhead = daysInMonth,
            paidOccurrences = billOccurrences,
        ).filter { it.date.isBefore(monthEnd) }
    }
    val monthTransactions = remember(transactions, monthStart, monthEnd) {
        transactions.filter { transaction ->
            val date = runCatching { LocalDate.parse(transaction.date) }.getOrNull()
            date != null && !date.isBefore(monthStart) && date.isBefore(monthEnd)
        }
    }

    val plan = remember(monthlyEvents, monthTransactions, billOccurrences, uiState.goals, today) {
        MonthlySpendingPlanCalculator.compute(
            monthlyEvents = monthlyEvents,
            monthTransactions = monthTransactions,
            billOccurrences = billOccurrences,
            goals = uiState.goals,
            today = today,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddGoalDialog = true },
                containerColor = GlassTokens.CyanBright,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add goal")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SpendingPlanCard(
                    plan = plan,
                    monthLabel = "${today.month.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }} ${today.year}",
                )
            }
            item {
                PaycheckWindowCard(windows = uiState.cashFlowWindows.take(4))
            }
            item {
                GoalSummaryCard(
                    goals = uiState.goals,
                    monthlyGoalPlanCents = plan.goalPlanCents,
                    leftAfterPlanCents = plan.leftAfterPlanCents,
                )
            }
            if (uiState.goals.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
                        tint = GlassTint.Neutral,
                        surfaceStyle = GlassSurfaceStyle.Quiet,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No goals yet.", style = MaterialTheme.typography.titleMedium, color = GlassTokens.TextPrimary)
                            Text("Add savings targets here so the planning surface can keep them visible beside bills and spending.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
                        }
                    }
                }
            } else {
                items(uiState.goals) { goal ->
                    PlanningGoalCard(
                        goal = goal,
                        onDelete = { viewModel.deleteGoal(goal) },
                    )
                }
            }
            // Category watchlists
            item {
                CategoryWatchlistSection(
                    rows = uiState.categoryBudgetRows,
                    onAdd = { showAddBudgetDialog = true },
                    onDelete = { row -> viewModel.deleteCategoryBudget(CategoryBudgetEntity(id = row.budgetId, category = row.category, limitCents = row.limitCents)) },
                )
            }
        }
    }

    if (showAddGoalDialog) {
        AddGoalDialog(
            onDismiss = { showAddGoalDialog = false },
            onSave = { goal ->
                viewModel.addGoal(goal)
                showAddGoalDialog = false
            },
        )
    }

    if (showAddBudgetDialog) {
        AddCategoryBudgetDialog(
            onDismiss = { showAddBudgetDialog = false },
            onSave = { category, limitCents ->
                viewModel.upsertCategoryBudget(
                    CategoryBudgetEntity(
                        category = category.trim().lowercase(java.util.Locale.ROOT),
                        limitCents = limitCents,
                        enabled = 1,
                    )
                )
                showAddBudgetDialog = false
            },
        )
    }
}

@Composable
private fun PaycheckWindowCard(windows: List<CashFlowWindow>) {
    GlassCard(
        modifier = Modifier.heightIn(min = 220.dp),
        tint = if (windows.any { it.shortfallCents > 0 }) GlassTint.Error else GlassTint.Cyan,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Paycheck windows", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
            Text(
                "Each row reserves bills before the next paycheck, then shows the safe daily amount for that stretch.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextSecondary,
            )
            if (windows.isEmpty()) {
                Text("Add income and bills to build paycheck windows.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
            } else {
                windows.forEachIndexed { index, window ->
                    if (index > 0) HorizontalDivider(color = GlassTokens.DividerColor)
                    PaycheckWindowRow(window)
                }
            }
        }
    }
}

@Composable
private fun PaycheckWindowRow(window: CashFlowWindow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${window.startDate.formatDateDisplay()}-${window.endDate.minusDays(1).formatDateDisplay()}",
                style = MaterialTheme.typography.titleSmall,
                color = GlassTokens.TextPrimary,
            )
            Text(
                "Start ${centsToDisplay(window.startingBalanceCents)} + income ${centsToDisplay(window.incomeCents)} - bills ${centsToDisplay(window.billCents)}",
                style = MaterialTheme.typography.labelSmall,
                color = GlassTokens.TextDim,
            )
            Text(
                if (window.shortfallCents > 0) {
                    "Short by ${centsToDisplay(window.shortfallCents)}"
                } else {
                    "Lowest balance ${centsToDisplay(window.lowestBalanceCents)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (window.shortfallCents > 0) GlassTokens.ErrorRed else GlassTokens.TextSecondary,
            )
        }
        Text(
            "${centsToDisplay(window.dailySafeSpendCents)}/day",
            style = MaterialTheme.typography.titleSmall,
            color = if (window.shortfallCents > 0) GlassTokens.ErrorRed else GlassTokens.CyanBright,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SpendingPlanCard(
    plan: MonthlySpendingPlan,
    monthLabel: String,
) {
    val overPlan = plan.isOverPlan
    GlassCard(
        modifier = Modifier.heightIn(min = 240.dp),
        tint = if (overPlan) GlassTint.Error else GlassTint.Teal,
        surfaceStyle = GlassSurfaceStyle.Hero,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Monthly Spending Plan", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
                    Text(monthLabel, style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextDim)
                }
                Text(
                    centsToDisplay(plan.leftAfterPlanCents),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (overPlan) GlassTokens.ErrorRed else GlassTokens.PositiveGreen,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (overPlan) {
                Text(
                    "You are over plan this month. Reduce bills/variable spend, or add income.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.ErrorRed,
                )
            } else {
                Text(
                    "Left to spend after bills, other spending, and goal funding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextSecondary,
                )
            }
            SpendingPlanRow("Expected income", plan.expectedIncomeCents, "Scheduled paychecks this month")
            SpendingPlanRow("Received so far", plan.actualIncomeCents, "Posted income in the ledger")
            HorizontalDivider(color = GlassTokens.DividerColor)
            SpendingPlanRow(
                "Remaining bills",
                -plan.remainingBillsCents,
                "Unpaid obligations still on the calendar",
            )
            if (plan.paidBillsCents > 0) {
                SpendingPlanRow(
                    "Bills already paid",
                    -plan.paidBillsCents,
                    "Paid this month (not double-counted in other spending)",
                )
            }
            SpendingPlanRow(
                "Other spending",
                -plan.variableSpendCents,
                "Non-bill expenses recorded this month",
            )
            SpendingPlanRow(
                "Goal funding plan",
                -plan.goalPlanCents,
                if (plan.goalPlanCents > 0) {
                    "Suggested monthly amount toward open goals"
                } else {
                    "No open goal gap this month"
                },
            )
            HorizontalDivider(color = GlassTokens.DividerColor)
            SpendingPlanRow(
                if (overPlan) "Over plan by" else "Left this month",
                if (overPlan) -plan.leftAfterPlanCents else plan.leftAfterPlanCents,
                "Income − remaining bills − other spending − goals",
            )
        }
    }
}

@Composable
private fun SpendingPlanRow(label: String, amountCents: Int, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextPrimary)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
        }
        Text(
            centsToDisplay(amountCents),
            style = MaterialTheme.typography.bodyMedium,
            color = if (amountCents < 0) GlassTokens.ErrorRed else GlassTokens.CyanBright,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GoalSummaryCard(
    goals: List<GoalEntity>,
    monthlyGoalPlanCents: Int,
    leftAfterPlanCents: Int,
) {
    val totalTarget = goals.sumOf { it.targetAmountCents }
    val totalCurrent = goals.sumOf { it.currentAmountCents }
    val progress = if (totalTarget > 0) totalCurrent.toFloat() / totalTarget else 0f
    val canFund = leftAfterPlanCents >= 0 && monthlyGoalPlanCents > 0

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = if (leftAfterPlanCents < 0 && monthlyGoalPlanCents > 0) GlassTint.Error else GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Goal funding", style = MaterialTheme.typography.titleMedium, color = GlassTokens.TextPrimary)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                color = GlassTokens.PositiveGreen,
                trackColor = GlassTokens.DividerColor,
            )
            Text(
                "${centsToDisplay(totalCurrent)} of ${centsToDisplay(totalTarget)} funded",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary,
            )
            if (monthlyGoalPlanCents > 0) {
                Text(
                    if (canFund) {
                        "Plan includes ${centsToDisplay(monthlyGoalPlanCents)} toward goals this month."
                    } else {
                        "Goals need ${centsToDisplay(monthlyGoalPlanCents)} this month, but the plan is already short."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canFund) GlassTokens.TextDim else GlassTokens.ErrorRed,
                )
            }
        }
    }
}

@Composable
private fun PlanningGoalCard(
    goal: GoalEntity,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val remaining = (goal.targetAmountCents - goal.currentAmountCents).coerceAtLeast(0)
    val progress = if (goal.targetAmountCents > 0) goal.currentAmountCents.toFloat() / goal.targetAmountCents else 0f

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Quiet,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = GlassTokens.PositiveGreen)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(goal.name, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
                    Text(centsToDisplay(remaining), style = MaterialTheme.typography.labelMedium, color = GlassTokens.TextDim)
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    color = GlassTokens.PositiveGreen,
                    trackColor = GlassTokens.DividerColor,
                )
                Text(
                    "${centsToDisplay(goal.currentAmountCents)} of ${centsToDisplay(goal.targetAmountCents)}${goal.deadline?.let { " by ${it.formatDateDisplay()}" }.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextSecondary,
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete goal", tint = GlassTokens.ErrorRed)
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete this goal?",
            message = "Remove \"${goal.name}\" from your savings goals? This cannot be undone.",
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
internal fun AddGoalDialog(
    onDismiss: () -> Unit,
    onSave: (GoalEntity) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    val targetCents = parseMoneyCents(target)
    val currentCents = parseMoneyCents(current) ?: 0
    val deadlineValue = deadline.trim().takeIf { it.isNotBlank() }
    val deadlineValid = deadlineValue == null || runCatching { LocalDate.parse(deadlineValue) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add savings goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal name") }, singleLine = true)
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target amount") }, singleLine = true)
                OutlinedTextField(value = current, onValueChange = { current = it }, label = { Text("Current amount") }, singleLine = true)
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("Deadline (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = !deadlineValid,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        GoalEntity(
                            name = name.trim(),
                            targetAmountCents = requireNotNull(targetCents),
                            currentAmountCents = currentCents,
                            deadline = deadlineValue,
                        )
                    )
                },
                enabled = name.isNotBlank() && targetCents != null && targetCents > 0 && deadlineValid,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CategoryWatchlistSection(
    rows: List<CategoryBudgetRow>,
    onAdd: () -> Unit,
    onDelete: (CategoryBudgetRow) -> Unit,
) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Category watchlists", style = MaterialTheme.typography.titleMedium, color = GlassTokens.TextPrimary, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAdd) {
                    Text("+ Add", color = GlassTokens.CyanBright)
                }
            }
            if (rows.isEmpty()) {
                Text(
                    "Set monthly spending limits per category. You'll see spend vs limit here — no hard block, just awareness.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary,
                )
            } else {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = GlassTokens.DividerColor)
                    CategoryWatchlistRow(row = row, onDelete = { onDelete(row) })
                }
            }
        }
    }
}

@Composable
private fun CategoryWatchlistRow(
    row: CategoryBudgetRow,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val progress = if (row.limitCents > 0) row.spentCents.toFloat() / row.limitCents else 0f
    val overLimit = row.overLimit

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.category.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (overLimit) GlassTokens.ErrorRed else GlassTokens.TextPrimary,
                    )
                    if (!row.enabled) {
                        Text(
                            "paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTokens.TextDim,
                        )
                    }
                }
                Text(
                    "${centsToDisplay(row.spentCents)} of ${centsToDisplay(row.limitCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (overLimit) "Over by ${centsToDisplay(-row.remaining)}" else centsToDisplay(row.remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overLimit) GlassTokens.ErrorRed else GlassTokens.PositiveGreen,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete watchlist", tint = GlassTokens.ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape),
            color = if (overLimit) GlassTokens.ErrorRed else GlassTokens.CyanBright,
            trackColor = GlassTokens.DividerColor,
        )
        if (overLimit) {
            Text(
                "Over monthly limit — consider reducing spend in this category.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassTokens.ErrorRed,
            )
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Remove watchlist?",
            message = "Stop tracking \"${row.category}\" against its monthly limit? This won't delete any transactions.",
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun AddCategoryBudgetDialog(
    onDismiss: () -> Unit,
    onSave: (category: String, limitCents: Int) -> Unit,
) {
    var category by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    val limitCents = parseMoneyCents(limit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add category watchlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. dining, groceries)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("Monthly limit amount") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, requireNotNull(limitCents)) },
                enabled = category.isNotBlank() && limitCents != null && limitCents > 0,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseMoneyCents(value: String): Int? {
    val cleaned = value.trim().replace("$", "").replace(",", "")
    if (cleaned.isBlank()) return null
    return runCatching {
        BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
    }.getOrNull()
}
