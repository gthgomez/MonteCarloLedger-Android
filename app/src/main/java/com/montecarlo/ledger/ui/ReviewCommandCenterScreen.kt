package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.TransactionReviewItem
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.CategoryRulePresets
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.util.centsToDisplay
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun ReviewCommandCenterScreen(
    viewModel: MainViewModel,
    onEditTransaction: (TransactionEntity) -> Unit,
    onTrackAsBill: (RecurringCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val billOccurrences by viewModel.allBillOccurrences.collectAsStateWithLifecycle()
    val rules by viewModel.allTransactionRules.collectAsStateWithLifecycle()
    var pendingRule by remember { mutableStateOf<Pair<String, String>?>(null) }

    val paymentById = remember(payments) { payments.associateBy { it.id } }
    val today = LocalDate.now()
    val billAttention = remember(billOccurrences, paymentById, today) {
        billOccurrences
            .mapNotNull { occurrence ->
                val dueDate = runCatching { LocalDate.parse(occurrence.due_date) }.getOrNull() ?: return@mapNotNull null
                if (occurrence.is_paid != 0 || dueDate.isAfter(today.plusDays(7))) return@mapNotNull null
                occurrence to paymentById[occurrence.payment_id]
            }
            .sortedBy { it.first.due_date }
    }
    val untrackedSubscriptions = remember(uiState.recurringCandidates, payments) {
        uiState.recurringCandidates.filter { candidate ->
            payments.none { payment ->
                payment.name.contains(candidate.pattern, ignoreCase = true) ||
                    candidate.pattern.contains(payment.name, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ReviewSummaryCard(
                reviewCount = uiState.transactionReviewItems.size,
                subscriptionCount = untrackedSubscriptions.size,
                billAttentionCount = billAttention.size,
                ruleCount = rules.size,
            )
        }

        item {
            Text("Transaction review", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
        }
        if (uiState.transactionReviewItems.isEmpty()) {
            item {
                EmptyCommandCard("No transactions need review.", "Imported, uncategorized, and newly recurring transactions will land here.")
            }
        } else {
            items(uiState.transactionReviewItems) { item ->
                CommandReviewRow(
                    item = item,
                    onApprove = viewModel::approveTransactionReview,
                    onCreateRule = viewModel::createRuleFromTransactionReview,
                    onEdit = onEditTransaction,
                )
            }
        }

        item {
            Text("Subscriptions and recurring spend", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
        }
        if (untrackedSubscriptions.isEmpty()) {
            item {
                EmptyCommandCard("No untracked subscriptions found.", "Recurring spending that is not already a bill will appear here.")
            }
        } else {
            items(untrackedSubscriptions) { candidate ->
                SubscriptionCommandRow(
                    candidate = candidate,
                    onTrackAsBill = onTrackAsBill,
                    onCreateRule = { description, category -> pendingRule = description to category },
                )
            }
        }

        item {
            Text("Bills needing attention", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
        }
        if (billAttention.isEmpty()) {
            item {
                EmptyCommandCard("No bills need attention.", "Overdue and next-7-day unpaid bills will appear here.")
            }
        } else {
            items(billAttention) { (occurrence, payment) ->
                BillAttentionRow(
                    occurrence = occurrence,
                    payment = payment,
                    onMarkPaid = { viewModel.markOccurrencePaid(occurrence.id) },
                    onSkip = { viewModel.skipBillOccurrence(occurrence.id) },
                    onReschedule = { dueDate -> viewModel.rescheduleBillOccurrence(occurrence.id, dueDate) },
                )
            }
        }

        item {
            Text("Category rules", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
        }
        item {
            CategoryPresetCard(
                ruleCount = rules.size,
                onInstall = viewModel::installCategoryRulePresets,
            )
        }
        if (rules.isEmpty()) {
            item {
                EmptyCommandCard("No category rules yet.", "Create rules from reviewed transactions to speed up future imports.")
            }
        } else {
            items(rules) { rule ->
                RuleCommandRow(
                    rule = rule,
                    onDelete = { viewModel.deleteTransactionRule(rule) },
                )
            }
        }
    }

    pendingRule?.let { (description, category) ->
        TransactionRuleConfirmationDialog(
            description = description,
            category = category,
            onConfirm = {
                viewModel.saveTransactionRule(description, category, applyRetroactively = true)
                pendingRule = null
            },
            onDismiss = { pendingRule = null },
        )
    }
}

@Composable
private fun CategoryPresetCard(
    ruleCount: Int,
    onInstall: () -> Unit,
) {
    val presetCount = CategoryRulePresets.totalKeywordCount
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
        tint = GlassTint.Teal,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keyword presets", style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
            Text(
                "$presetCount built-in keywords can categorize common spending automatically. Install them if you want visible, editable rules.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onInstall, modifier = Modifier.weight(1f)) {
                    Text(if (ruleCount == 0) "Install presets" else "Refresh presets")
                }
                Text(
                    "$ruleCount active",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextDim,
                )
            }
        }
    }
}

@Composable
private fun ReviewSummaryCard(
    reviewCount: Int,
    subscriptionCount: Int,
    billAttentionCount: Int,
    ruleCount: Int,
) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = if (reviewCount + subscriptionCount + billAttentionCount > 0) GlassTint.Teal else GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Hero,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Command center", style = MaterialTheme.typography.titleLarge, color = GlassTokens.TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandMetric("Review", reviewCount.toString(), Modifier.weight(1f))
                CommandMetric("Subscriptions", subscriptionCount.toString(), Modifier.weight(1f))
                CommandMetric("Bills", billAttentionCount.toString(), Modifier.weight(1f))
                CommandMetric("Rules", ruleCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CommandMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = GlassTokens.CyanBright, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CommandReviewRow(
    item: TransactionReviewItem,
    onApprove: (Int) -> Unit,
    onCreateRule: (Int, String) -> Unit,
    onEdit: (TransactionEntity) -> Unit,
) {
    var category by remember(item.transaction.id, item.suggestedCategory) { mutableStateOf(item.suggestedCategory) }
    SolidListSurface(
        modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.transaction.description, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.reason} • ${item.transaction.date.formatDateDisplay()}", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                }
                Text(centsToDisplay(item.transaction.amount_cents), color = GlassTokens.ErrorRed, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCreateRule(item.transaction.id, category) }, modifier = Modifier.weight(1f)) {
                    Text("Rule")
                }
                TextButton(onClick = { onApprove(item.transaction.id) }, modifier = Modifier.weight(1f)) {
                    Text("Approve")
                }
                TextButton(onClick = { onEdit(item.transaction) }, modifier = Modifier.weight(1f)) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCommandRow(
    candidate: RecurringCandidate,
    onTrackAsBill: (RecurringCandidate) -> Unit,
    onCreateRule: (String, String) -> Unit,
) {
    SolidListSurface(
        modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(candidate.pattern.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
            Text("${candidate.cadenceLabel} • ${candidate.occurrenceCount} instances • suggested category ${candidate.category}", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTrackAsBill(candidate) }, modifier = Modifier.weight(1f)) {
                    Text("Track bill")
                }
                TextButton(
                    onClick = { onCreateRule(candidate.pattern, candidate.category) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Create rule")
                }
            }
        }
    }
}

@Composable
private fun BillAttentionRow(
    occurrence: BillOccurrenceEntity,
    payment: PaymentEntity?,
    onMarkPaid: () -> Unit,
    onSkip: () -> Unit,
    onReschedule: (String) -> Unit,
) {
    val dueDate = occurrence.due_date.formatDateDisplay()
    var showMoveDate by remember(occurrence.id) { mutableStateOf(false) }
    var moveDate by remember(occurrence.id, occurrence.due_date) { mutableStateOf(occurrence.due_date) }
    SolidListSurface(
        modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(payment?.name ?: "Removed bill", style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
                    Text("Due $dueDate • ${centsToDisplay(-occurrence.amount_cents)}", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onMarkPaid) {
                        Text("Paid")
                    }
                    TextButton(onClick = onSkip) {
                        Text("Skip")
                    }
                    TextButton(onClick = { showMoveDate = !showMoveDate }) {
                        Text("Move")
                    }
                }
            }
            if (showMoveDate) {
                ScheduleDatePickerField(
                    label = "Move due date",
                    dateText = moveDate,
                    displayText = moveDate.formatDateDisplay(),
                    onDateSelected = { moveDate = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onReschedule(moveDate)
                            showMoveDate = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save move")
                    }
                    TextButton(onClick = { showMoveDate = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCommandRow(
    rule: TransactionRuleEntity,
    onDelete: () -> Unit,
) {
    SolidListSurface(
        modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = GlassTokens.CyanBright)
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.match_text, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
                Text(rule.category, style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete rule", tint = GlassTokens.ErrorRed)
            }
        }
    }
}

@Composable
private fun EmptyCommandCard(title: String, detail: String) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.AnalysisListCardMinHeight),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Quiet,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
        }
    }
}
