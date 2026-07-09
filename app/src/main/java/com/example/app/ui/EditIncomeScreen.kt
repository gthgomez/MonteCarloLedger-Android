package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.app.data.IncomeEntity
import com.example.app.domain.DomainRules
import java.time.LocalDate
import com.example.app.util.dollarsToCents
import java.math.BigDecimal
import java.math.RoundingMode

private enum class EditPayType { HOURLY, FLAT, PER_PROJECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditIncomeScreen(
    income: IncomeEntity,
    onSave: (IncomeEntity) -> Unit,
    onDelete: (IncomeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val initialPayType = remember(income.payType) {
        when (income.payType) {
            "HOURLY" -> EditPayType.HOURLY
            "PER_PROJECT" -> EditPayType.PER_PROJECT
            else -> EditPayType.FLAT
        }
    }

    var name by remember { mutableStateOf(income.name) }
    var payType by remember { mutableStateOf(initialPayType) }

    // Hourly inputs — reconstruct best-guess hourly rate from stored amount if pay type is hourly
    val initialDollars = income.amount_cents / 100.0
    var hourlyRate by remember {
        mutableStateOf(
            if (initialPayType == EditPayType.HOURLY) "" else ""
        )
    }
    var hoursPerWeek by remember { mutableStateOf("") }

    // Flat / per-project
    var flatAmount by remember {
        mutableStateOf(
            if (initialPayType != EditPayType.HOURLY) String.format("%.2f", initialDollars) else ""
        )
    }

    var frequency by remember { mutableStateOf(income.frequency) }
    val initialNextPayday = remember(income.next_date) {
        runCatching { LocalDate.parse(income.next_date) }.getOrDefault(LocalDate.now())
    }
    var nextPaydayIso by remember { mutableStateOf(initialNextPayday.toString()) }
    var expectedAmountDollars by remember {
        mutableStateOf(income.expectedAmountCents?.let { String.format("%.2f", it / 100.0) } ?: "")
    }
    var errorMessage by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var showNextCheckOverride by remember { mutableStateOf(income.expectedAmountCents != null) }

    val frequencies = when (payType) {
        EditPayType.HOURLY -> listOf("Weekly", "Bi-weekly", "Semi-monthly", "Monthly")
        EditPayType.FLAT -> listOf("Weekly", "Bi-weekly", "Semi-monthly", "Monthly", "Bi-monthly", "Quarterly", "One-time")
        EditPayType.PER_PROJECT -> listOf("One-time", "Weekly", "Bi-weekly", "Monthly")
    }

    val computedCents by remember {
        derivedStateOf {
            when (payType) {
                EditPayType.HOURLY -> {
                    val rate = runCatching { BigDecimal(hourlyRate.trim()) }.getOrDefault(BigDecimal.ZERO)
                    val hours = runCatching { BigDecimal(hoursPerWeek.trim()) }.getOrDefault(BigDecimal.ZERO)
                    val weeklyCents = rate.multiply(hours).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
                    when (frequency) {
                        "Weekly" -> weeklyCents.toInt()
                        "Bi-weekly" -> weeklyCents.multiply(BigDecimal(2)).toInt()
                        "Semi-monthly" -> weeklyCents.multiply(BigDecimal(52).divide(BigDecimal(24), 10, RoundingMode.HALF_UP)).toInt()
                        "Monthly" -> weeklyCents.multiply(BigDecimal(52).divide(BigDecimal(12), 10, RoundingMode.HALF_UP)).toInt()
                        else -> weeklyCents.toInt()
                    }
                }
                EditPayType.FLAT, EditPayType.PER_PROJECT -> {
                    dollarsToCents(flatAmount)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete this income entry?",
            message = "Remove \"${income.name}\"? This cannot be undone.",
            onConfirm = {
                onDelete(income)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Edit Income Source", style = MaterialTheme.typography.titleLarge)
                Text("Update the source details, payday cadence, and optional projected amount.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Income Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("How are you paid?", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = payType == EditPayType.HOURLY,
                onClick = {
                    payType = EditPayType.HOURLY
                    if (frequency !in listOf("Weekly", "Bi-weekly", "Semi-monthly", "Monthly")) {
                        frequency = "Weekly"
                    }
                    errorMessage = ""
                },
                label = { Text("Hourly") }
            )
            FilterChip(
                selected = payType == EditPayType.FLAT,
                onClick = { payType = EditPayType.FLAT; errorMessage = "" },
                label = { Text("Fixed amount") }
            )
            FilterChip(
                selected = payType == EditPayType.PER_PROJECT,
                onClick = {
                    payType = EditPayType.PER_PROJECT
                    frequency = "One-time"
                    errorMessage = ""
                },
                label = { Text("Per project") }
            )
        }

        when (payType) {
            EditPayType.HOURLY -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourlyRate,
                        onValueChange = { hourlyRate = it; errorMessage = "" },
                        label = { Text("Hourly rate ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hoursPerWeek,
                        onValueChange = { hoursPerWeek = it; errorMessage = "" },
                        label = { Text("Hours / week") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (computedCents > 0) {
                    Text(
                        "≈ \$${String.format("%.2f", computedCents / 100.0)} per $frequency paycheck",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            EditPayType.FLAT -> {
                OutlinedTextField(
                    value = flatAmount,
                    onValueChange = { flatAmount = it; errorMessage = "" },
                    label = { Text("Amount per paycheck ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            EditPayType.PER_PROJECT -> {
                OutlinedTextField(
                    value = flatAmount,
                    onValueChange = { flatAmount = it; errorMessage = "" },
                    label = { Text("Amount per project ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = when (payType) {
                    EditPayType.PER_PROJECT -> if (frequency == "One-time") "One-time (single project)" else frequency
                    else -> frequency
                },
                onValueChange = {},
                readOnly = true,
                label = { Text(if (payType == EditPayType.PER_PROJECT) "How often do you get projects?" else "Paid how often?") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                frequencies.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when {
                                    payType == EditPayType.PER_PROJECT && option == "One-time" -> "One-time (single project)"
                                    else -> option
                                }
                            )
                        },
                        onClick = { frequency = option; expanded = false }
                    )
                }
            }
        }

        ScheduleDatePickerField(
            label = if (payType == EditPayType.PER_PROJECT) "Expected payment date" else "Next payday",
            dateText = nextPaydayIso,
            displayText = nextPaydayIso.formatDateDisplay(),
            onDateSelected = { nextPaydayIso = it }
        )

        if (payType != EditPayType.PER_PROJECT) {
            TextButton(onClick = { showNextCheckOverride = !showNextCheckOverride }) {
                Text(if (showNextCheckOverride) "Hide next-check override" else "Next check is a different amount?")
            }
            if (showNextCheckOverride) {
                OutlinedTextField(
                    value = expectedAmountDollars,
                    onValueChange = { expectedAmountDollars = it },
                    label = { Text("Next check amount (\$) — one-time override") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPrimaryButton(
                text = "Save",
                onClick = {
                    val cents = computedCents
                    val validation = DomainRules.validateIncomeSign(cents)
                    val nextPayday = runCatching { LocalDate.parse(nextPaydayIso) }.getOrNull()
                    val expectedAmount = dollarsToCents(expectedAmountDollars).takeIf { expectedAmountDollars.isNotBlank() }
                    if (payType == EditPayType.HOURLY && (hourlyRate.toDoubleOrNull() ?: 0.0) <= 0) {
                        errorMessage = "Enter a valid hourly rate"
                    } else if (payType == EditPayType.HOURLY && (hoursPerWeek.toDoubleOrNull() ?: 0.0) <= 0) {
                        errorMessage = "Enter hours worked per week"
                    } else if (validation.isFailure || cents <= 0) {
                        errorMessage = validation.exceptionOrNull()?.message ?: "Invalid income amount"
                    } else if (nextPayday == null) {
                        errorMessage = "Please pick a next payday date"
                    } else {
                        onSave(
                            income.copy(
                                name = name,
                                amount_cents = cents,
                                frequency = frequency,
                                day_of_month = nextPayday.dayOfMonth,
                                next_date = nextPayday.toString(),
                                expectedAmountCents = if (payType == EditPayType.PER_PROJECT) null else expectedAmount,
                                payType = payType.name
                            )
                        )
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
