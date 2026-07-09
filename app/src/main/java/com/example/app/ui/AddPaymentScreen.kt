package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
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
import com.example.app.data.PaymentEntity
import com.example.app.domain.DomainRules
import com.example.app.processing.PaymentSchedule
import java.time.LocalDate
import com.example.app.util.dollarsToCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentScreen(
    onSave: (PaymentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountDollars by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("Monthly") }
    var dueDateText by remember { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var isAutoWithdraw by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Add a bill or subscription.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Bill or subscription") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Bill name input field" }
        )

        OutlinedTextField(
            value = amountDollars,
            onValueChange = {
                amountDollars = it
                errorMessage = ""
            },
            label = { Text("Amount ($)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Bill amount input field" }
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
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
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                PaymentSchedule.recurrenceOptions.forEach { option ->
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
            Text(
                text = errorMessage,
                color = androidx.compose.ui.graphics.Color.Red,
                modifier = Modifier.semantics { contentDescription = "Error message" }
            )
        }

        AppPrimaryButton(
            text = "Save",
            onClick = {
                val cents = dollarsToCents(amountDollars)
                val validation = DomainRules.validatePaymentSign(cents)
                val dueDate = runCatching { LocalDate.parse(dueDateText.trim()) }.getOrNull()
                val dueDay = dueDate?.takeIf { recurrence.usesMonthlyAnchor() }?.dayOfMonth

                if (name.isBlank()) {
                    errorMessage = "Bill name is required"
                } else if (cents <= 0 || validation.isFailure) {
                    errorMessage = validation.exceptionOrNull()?.message ?: "Invalid payment amount"
                } else if (dueDate == null) {
                    errorMessage = "Enter a valid next due date"
                } else {
                    val today = LocalDate.now()
                    val nextDate = PaymentSchedule.resolveNextPaymentDate(today, recurrence, dueDay, dueDate)
                    if (nextDate == null) {
                        errorMessage = "Enter a valid next due date"
                    } else {
                        onSave(
                            PaymentEntity(
                                name = name,
                                amount_cents = cents,
                                frequency = recurrence,
                                day_of_month = dueDay,
                                next_date = nextDate,
                                is_active = 1,
                                isAutoWithdraw = isAutoWithdraw
                            )
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Save payment button" }
        )

        AppNeutralButton(
            text = "Cancel",
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Cancel button" }
        )
    }
}

private fun String.usesMonthlyAnchor(): Boolean {
    return lowercase().replace(" ", "").replace("-", "") in setOf("monthly", "bimonthly", "quarterly")
}
