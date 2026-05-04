package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.example.app.data.GoalEntity
import com.example.app.processing.CashFlowWindow
import com.example.app.processing.TimelineService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

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

    val today = LocalDate.now()
    val monthStart = today.withDayOfMonth(1)
    val daysInMonth = today.lengthOfMonth()
    val monthEnd = monthStart.plusDays(daysInMonth.toLong())
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

    val expectedIncomeCents = monthlyEvents.filter { it.type == "income" }.sumOf { it.amount_cents }
    val billPlanCents = monthlyEvents.filter { it.type == "bill" }.sumOf { it.amount_cents }
    val actualIncomeCents = monthTransactions.filter { it.type == "income" }.sumOf { it.amount_cents }
    val actualExpenseCents = abs(monthTransactions.filter { it.type == "expense" }.sumOf { it.amount_cents })
    val variableSpendCents = (actualExpenseCents - billPlanCents).coerceAtLeast(0)
    val leftAfterPlanCents = expectedIncomeCents - billPlanCents - variableSpendCents

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
                    expectedIncomeCents = expectedIncomeCents,
                    actualIncomeCents = actualIncomeCents,
                    billPlanCents = billPlanCents,
                    variableSpendCents = variableSpendCents,
                    leftAfterPlanCents = leftAfterPlanCents,
                    monthLabel = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}",
                )
            }
            item {
                PaycheckWindowCard(windows = uiState.cashFlowWindows.take(4))
            }
            item {
                GoalSummaryCard(goals = uiState.goals)
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
                "Start ${formatCurrency(window.startingBalanceCents)} + income ${formatCurrency(window.incomeCents)} - bills ${formatCurrency(window.billCents)}",
                style = MaterialTheme.typography.labelSmall,
                color = GlassTokens.TextDim,
            )
            Text(
                if (window.shortfallCents > 0) {
                    "Short by ${formatCurrency(window.shortfallCents)}"
                } else {
                    "Lowest balance ${formatCurrency(window.lowestBalanceCents)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (window.shortfallCents > 0) GlassTokens.ErrorRed else GlassTokens.TextSecondary,
            )
        }
        Text(
            "${formatCurrency(window.dailySafeSpendCents)}/day",
            style = MaterialTheme.typography.titleSmall,
            color = if (window.shortfallCents > 0) GlassTokens.ErrorRed else GlassTokens.CyanBright,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SpendingPlanCard(
    expectedIncomeCents: Int,
    actualIncomeCents: Int,
    billPlanCents: Int,
    variableSpendCents: Int,
    leftAfterPlanCents: Int,
    monthLabel: String,
) {
    GlassCard(
        modifier = Modifier.heightIn(min = 240.dp),
        tint = if (leftAfterPlanCents < 0) GlassTint.Error else GlassTint.Teal,
        surfaceStyle = GlassSurfaceStyle.Hero,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Monthly Spending Plan", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
                    Text(monthLabel, style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextDim)
                }
                Text(
                    formatCurrency(leftAfterPlanCents),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (leftAfterPlanCents < 0) GlassTokens.ErrorRed else GlassTokens.PositiveGreen,
                    fontWeight = FontWeight.Bold,
                )
            }
            SpendingPlanRow("Expected income", expectedIncomeCents, "Scheduled paychecks this month")
            SpendingPlanRow("Received so far", actualIncomeCents, "Posted income in the ledger")
            HorizontalDivider(color = GlassTokens.DividerColor)
            SpendingPlanRow("Bills & subscriptions", -billPlanCents, "Known obligations this month")
            SpendingPlanRow("Other spending", -variableSpendCents, "Current-month expenses not already covered by bills")
            HorizontalDivider(color = GlassTokens.DividerColor)
            SpendingPlanRow("Left this month", leftAfterPlanCents, "Income minus planned bills and observed spending")
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
            formatCurrency(amountCents),
            style = MaterialTheme.typography.bodyMedium,
            color = if (amountCents < 0) GlassTokens.ErrorRed else GlassTokens.CyanBright,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GoalSummaryCard(goals: List<GoalEntity>) {
    val totalTarget = goals.sumOf { it.targetAmountCents }
    val totalCurrent = goals.sumOf { it.currentAmountCents }
    val progress = if (totalTarget > 0) totalCurrent.toFloat() / totalTarget else 0f

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Neutral,
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
                "${formatCurrency(totalCurrent)} of ${formatCurrency(totalTarget)} funded",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary,
            )
        }
    }
}

@Composable
private fun PlanningGoalCard(
    goal: GoalEntity,
    onDelete: () -> Unit,
) {
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
                    Text(formatCurrency(remaining), style = MaterialTheme.typography.labelMedium, color = GlassTokens.TextDim)
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    color = GlassTokens.PositiveGreen,
                    trackColor = GlassTokens.DividerColor,
                )
                Text(
                    "${formatCurrency(goal.currentAmountCents)} of ${formatCurrency(goal.targetAmountCents)}${goal.deadline?.let { " by ${it.formatDateDisplay()}" }.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete goal", tint = GlassTokens.ErrorRed)
            }
        }
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

private fun parseMoneyCents(value: String): Int? {
    val cleaned = value.trim().replace("$", "").replace(",", "")
    if (cleaned.isBlank()) return null
    return runCatching {
        BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
    }.getOrNull()
}

private fun formatCurrency(cents: Int): String {
    val sign = if (cents < 0) "-" else ""
    return "$sign\$${String.format("%.2f", abs(cents) / 100.0)}"
}
