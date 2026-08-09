package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import com.workspace.design.ConfirmDeleteDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.domain.DomainRules
import com.montecarlo.ledger.processing.PaymentSchedule
import java.time.LocalDate
import com.montecarlo.ledger.util.centsToDollarInputString
import com.montecarlo.ledger.util.dollarsToCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPaymentScreen(
    payment: PaymentEntity,
    onSave: (PaymentEntity) -> Unit,
    onDelete: (PaymentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(payment.name) }
    var amountDollars by remember { mutableStateOf(centsToDollarInputString(payment.amount_cents)) }
    var recurrence by remember { mutableStateOf(payment.frequency) }
    var dueDateText by remember { mutableStateOf(payment.next_date) }
    var isAutoWithdraw by remember { mutableStateOf(payment.isAutoWithdraw) }
    var errorMessage by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(true) }

    val recurrences = PaymentSchedule.recurrenceOptions

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete this bill?",
            message = "Remove \"${payment.name}\"? This cannot be undone.",
            onConfirm = {
                onDelete(payment)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Edit Payment", style = MaterialTheme.typography.titleLarge)
                Text("Update the recurrence, due date, and whether this bill auto-withdraws.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Bill Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amountDollars,
            onValueChange = { amountDollars = it; errorMessage = "" },
            label = { Text("Amount ($)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = errorMessage.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Bill amount input field" }
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = recurrence,
                onValueChange = {},
                readOnly = true,
                label = { Text("Recurrence") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
                    .semantics { contentDescription = "Bill recurrence selector" }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                recurrences.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            recurrence = option
                            expanded = false
                        }
                    )
                }
            }
        }

        ScheduleDatePickerField(
            label = "Next due date",
            dateText = dueDateText,
            displayText = dueDateText.formatDateDisplay(),
            onDateSelected = { dueDateText = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-pay from bank", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Bill is automatically withdrawn — won't appear as overdue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }
            Switch(checked = isAutoWithdraw, onCheckedChange = { isAutoWithdraw = it })
        }

        if (errorMessage.isNotBlank()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPrimaryButton(
                text = "Save",
                onClick = {
                    val cents = dollarsToCents(amountDollars)
                    val validation = DomainRules.validatePaymentSign(cents)
                    val dueDate = runCatching { LocalDate.parse(dueDateText.trim()) }.getOrNull()
                    val dueDay = dueDate?.takeIf { recurrence.usesMonthlyAnchor() }?.dayOfMonth
                    if (validation.isFailure) {
                        errorMessage = validation.exceptionOrNull()?.message ?: "Invalid amount"
                    } else if (dueDate == null) {
                        errorMessage = "Enter a valid next due date"
                    } else {
                        val today = LocalDate.now()
                        val nextDate = PaymentSchedule.resolveNextPaymentDate(today, recurrence, dueDay, dueDate)
                        if (nextDate == null) {
                            errorMessage = "Enter a valid next due date"
                        } else {
                            onSave(
                                payment.copy(
                                    name = name,
                                    amount_cents = cents,
                                    frequency = recurrence,
                                    day_of_month = dueDay,
                                    next_date = nextDate,
                                    isAutoWithdraw = isAutoWithdraw
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )

            AppDestructiveButton(
                text = "Delete",
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.weight(1f)
            )

            AppNeutralButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
        }
    }
}

private fun String.usesMonthlyAnchor(): Boolean {
    return lowercase().replace(" ", "").replace("-", "") in setOf("monthly", "bimonthly", "quarterly")
}
