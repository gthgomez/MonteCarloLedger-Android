package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.app.GlassTokens
import com.example.app.MainViewModel
import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.PaymentEntity
import com.example.app.processing.PaymentSchedule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentListScreen(
    viewModel: MainViewModel,
    onEditPayment: (PaymentEntity) -> Unit,
    onAddPayment: (() -> Unit)? = null
) {
    val paymentList by viewModel.allPayments.collectAsStateWithLifecycle()
    val billOccurrences by viewModel.allBillOccurrences.collectAsStateWithLifecycle()
    PaymentListContent(
        payments = paymentList,
        billOccurrences = billOccurrences,
        onEditPayment = onEditPayment,
        onAddPayment = onAddPayment,
        onMarkOccurrencePaid = viewModel::markOccurrencePaid,
        onSkipOccurrence = viewModel::skipBillOccurrence,
        onRescheduleOccurrence = viewModel::rescheduleBillOccurrence,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentListContent(
    payments: List<PaymentEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
    onEditPayment: (PaymentEntity) -> Unit,
    onAddPayment: (() -> Unit)? = null,
    onMarkOccurrencePaid: (Int) -> Unit,
    onSkipOccurrence: (Int) -> Unit,
    onRescheduleOccurrence: (Int, String) -> Unit,
) {
    val activePayments = payments.filter { it.is_active == 1 }
    val paymentById = payments.associateBy { it.id }
    val timelineEntries = buildBillTimelineEntries(paymentById, billOccurrences)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Bills",
                            color = GlassTokens.TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "Scheduled payments",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextDim
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (onAddPayment != null) {
                FloatingActionButton(
                    onClick = onAddPayment,
                    containerColor = GlassTokens.Cyan,
                    contentColor = Color(0xFF083344)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Payment")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BillTimelineCard(
                    entries = timelineEntries,
                    onMarkOccurrencePaid = onMarkOccurrencePaid,
                    onSkipOccurrence = onSkipOccurrence,
                    onRescheduleOccurrence = onRescheduleOccurrence,
                )
            }

            if (activePayments.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassCard(
                            modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
                            tint = GlassTint.Neutral,
                            surfaceStyle = GlassSurfaceStyle.Quiet
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "No bills yet.",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = GlassTokens.TextPrimary,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    "Add a bill or subscription so due dates and cash-flow risk show up in the dashboard.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GlassTokens.TextSecondary
                                )
                            }
                        }
                        AppPrimaryButton(text = "Add bill", onClick = { onAddPayment?.invoke() ?: Unit })
                    }
                }
            } else {
                items(activePayments) { payment ->
                    PaymentCard(
                        payment = payment,
                        onEditPayment = onEditPayment,
                    )
                }
            }
        }
    }
}

private data class BillTimelineEntry(
    val occurrenceId: Int,
    val paymentName: String,
    val dueDate: LocalDate,
    val amountCents: Int,
    val isPaid: Boolean,
    val recurrenceLabel: String,
    val isMissingPayment: Boolean,
)

private fun buildBillTimelineEntries(
    paymentById: Map<Int, PaymentEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
): List<BillTimelineEntry> {
    val today = LocalDate.now()
    val startWindow = today.minusDays(30)
    val endWindow = today.plusDays(30)

    return billOccurrences.asSequence()
        .mapNotNull { occurrence ->
            val dueDate = runCatching { LocalDate.parse(occurrence.due_date) }.getOrNull() ?: return@mapNotNull null
            if (dueDate.isBefore(startWindow) || dueDate.isAfter(endWindow)) return@mapNotNull null
            val payment = paymentById[occurrence.payment_id]
            BillTimelineEntry(
                occurrenceId = occurrence.id,
                paymentName = payment?.name ?: "Removed bill",
                dueDate = dueDate,
                amountCents = occurrence.amount_cents,
                isPaid = occurrence.is_paid != 0,
                recurrenceLabel = payment?.let {
                    PaymentSchedule.recurrenceSummary(
                        recurrence = it.frequency,
                        dayOfMonth = it.day_of_month,
                        nextDate = it.next_date
                    )
                } ?: "Bill occurrence",
                isMissingPayment = payment == null
            )
        }
        .sortedWith(compareBy<BillTimelineEntry> { it.dueDate }.thenBy { it.paymentName })
        .toList()
}

@Composable
private fun BillTimelineCard(
    entries: List<BillTimelineEntry>,
    onMarkOccurrencePaid: (Int) -> Unit,
    onSkipOccurrence: (Int) -> Unit,
    onRescheduleOccurrence: (Int, String) -> Unit,
) {
    val today = LocalDate.now()
    val overdueCount = entries.count { !it.isPaid && it.dueDate.isBefore(today) }
    val dueSoonCount = entries.count { !it.isPaid && !it.dueDate.isBefore(today) }

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = if (overdueCount > 0) GlassTint.Error else GlassTint.Teal,
        surfaceStyle = GlassSurfaceStyle.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Bill timeline",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTokens.TextPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        "Next 30 days and overdue items",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$overdueCount overdue",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdueCount > 0) GlassTokens.ErrorRed else GlassTokens.TextSecondary,
                    )
                    Text(
                        "$dueSoonCount due soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim,
                    )
                }
            }

            if (entries.isEmpty()) {
                Text(
                    "Your bill occurrences will appear here once bills are added and synced.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(color = GlassTokens.DividerColor)
                    }
                    BillTimelineRow(
                        entry = entry,
                        onMarkOccurrencePaid = onMarkOccurrencePaid,
                        onSkipOccurrence = onSkipOccurrence,
                        onRescheduleOccurrence = onRescheduleOccurrence,
                    )
                }
            }
        }
    }
}

@Composable
private fun BillTimelineRow(
    entry: BillTimelineEntry,
    onMarkOccurrencePaid: (Int) -> Unit,
    onSkipOccurrence: (Int) -> Unit,
    onRescheduleOccurrence: (Int, String) -> Unit,
) {
    val today = LocalDate.now()
    val isOverdue = !entry.isPaid && entry.dueDate.isBefore(today)
    val isDueToday = !entry.isPaid && entry.dueDate == today
    var showMoveDate by remember(entry.occurrenceId) { mutableStateOf(false) }
    var moveDate by remember(entry.occurrenceId, entry.dueDate) { mutableStateOf(entry.dueDate.toString()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (entry.isMissingPayment) "Removed bill" else entry.paymentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTokens.TextPrimary,
                    )
                    when {
                        entry.isPaid -> {
                            Badge(containerColor = GlassTokens.PositiveGreen.copy(alpha = 0.22f)) {
                                Text("Paid", color = GlassTokens.PositiveGreen)
                            }
                        }
                        isOverdue -> {
                            Badge(containerColor = GlassTokens.ErrorRed.copy(alpha = 0.22f)) {
                                Text("Overdue", color = GlassTokens.ErrorRed)
                            }
                        }
                        isDueToday -> {
                            Badge(containerColor = GlassTokens.Cyan.copy(alpha = 0.22f)) {
                                Text("Today", color = GlassTokens.CyanBright)
                            }
                        }
                    }
                }
                Text(
                    entry.recurrenceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextSecondary
                )
                Text(
                    "Due ${entry.dueDate.formatDateDisplay()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) GlassTokens.ErrorRed else GlassTokens.TextDim
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "$${String.format("%.2f", entry.amountCents / 100.0)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.isPaid) GlassTokens.TextDim else GlassTokens.CyanBright
                )
                if (entry.isPaid) {
                    Text(
                        "Recorded",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim
                    )
                } else {
                    Button(onClick = { onMarkOccurrencePaid(entry.occurrenceId) }) {
                        Text("Paid")
                    }
                    TextButton(onClick = { onSkipOccurrence(entry.occurrenceId) }) {
                        Text("Skip")
                    }
                    TextButton(onClick = { showMoveDate = !showMoveDate }) {
                        Text("Move")
                    }
                }
            }
        }

        if (showMoveDate && !entry.isPaid) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleDatePickerField(
                    label = "Move due date",
                    dateText = moveDate,
                    displayText = moveDate.formatDateDisplay(),
                    onDateSelected = { moveDate = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPrimaryButton(
                        text = "Save move",
                        onClick = {
                            onRescheduleOccurrence(entry.occurrenceId, moveDate)
                            showMoveDate = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    AppNeutralButton(
                        text = "Cancel",
                        onClick = { showMoveDate = false },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Moves this bill's next due date and refreshes future occurrences.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextDim
                )
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentEntity,
    onEditPayment: (PaymentEntity) -> Unit,
) {
    val isOverdue = runCatching {
        LocalDate.parse(payment.next_date).isBefore(LocalDate.now())
    }.getOrDefault(false)
    val tint = if (isOverdue) GlassTint.Error else GlassTint.Neutral

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
        tint = tint,
        surfaceStyle = GlassSurfaceStyle.Quiet
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(payment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassTokens.TextPrimary)
                    if (payment.isAutoWithdraw) {
                        Badge(containerColor = GlassTokens.Cyan.copy(alpha = 0.3f)) {
                            Text("Auto", color = GlassTokens.CyanBright)
                        }
                    }
                    if (isOverdue) {
                        Badge(containerColor = GlassTokens.ErrorRed.copy(alpha = 0.3f)) {
                            Text("Overdue", color = GlassTokens.ErrorRed)
                        }
                    }
                }
                Text(
                    "$${String.format("%.2f", payment.amount_cents / 100.0)}  •  ${
                        PaymentSchedule.recurrenceSummary(
                            recurrence = payment.frequency,
                            dayOfMonth = payment.day_of_month,
                            nextDate = payment.next_date
                        )
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOverdue) GlassTokens.ErrorRed else GlassTokens.CyanBright
                )
                Text(
                    "Next due: ${payment.next_date.formatDateDisplay()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) GlassTokens.ErrorRed else GlassTokens.TextDim
                )
                if (payment.isAutoWithdraw) {
                    Text("Auto withdraw enabled", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextDim)
                }
            }
            OutlinedButton(onClick = { onEditPayment(payment) }) {
                Text("Edit", color = GlassTokens.TextSecondary)
            }
        }
    }
}
