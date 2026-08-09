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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.app.data.IncomeEntity
import com.example.app.domain.DomainRules
import java.time.LocalDate
import com.example.app.util.centsToDisplay
import com.example.app.util.dollarsToCents
import java.math.BigDecimal
import java.math.RoundingMode

private enum class PayType { HOURLY, FLAT, PER_PROJECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncomeScreen(
    onCancel: (() -> Unit)? = null,
    onSave: (IncomeEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var payType by remember { mutableStateOf(PayType.HOURLY) }

    // Hourly inputs
    var hourlyRate by remember { mutableStateOf("") }
    var hoursPerWeek by remember { mutableStateOf("") }

    // Flat / per-project inputs
    var flatAmount by remember { mutableStateOf("") }

    var frequency by remember { mutableStateOf("Weekly") }
    var nextPaydayIso by remember { mutableStateOf(LocalDate.now().toString()) }
    var errorMessage by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showExpectedAmount by remember { mutableStateOf(false) }
    var expectedAmountDollars by remember { mutableStateOf("") }

    // Frequencies shown depend on pay type: per-project shows a cleaner label set
    val frequencies = when (payType) {
        PayType.HOURLY -> listOf("Weekly", "Bi-weekly", "Semi-monthly", "Monthly")
        PayType.FLAT -> listOf("Weekly", "Bi-weekly", "Semi-monthly", "Monthly", "Bi-monthly", "Quarterly", "One-time")
        PayType.PER_PROJECT -> listOf("One-time", "Weekly", "Bi-weekly", "Monthly")
    }

    // Derived amount in cents from whichever pay type is active
    val computedCents by remember {
        derivedStateOf {
            when (payType) {
                PayType.HOURLY -> {
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
                PayType.FLAT, PayType.PER_PROJECT -> {
                    dollarsToCents(flatAmount)
                }
            }
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Add a paycheck or income source.",
            style = MaterialTheme.typography.bodyMedium,
            color = com.example.app.GlassTokens.TextSecondary
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Income source (e.g. \"Day job\", \"Freelance\")") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Income name input field" }
        )

        // Pay type selector
        Text("How are you paid?", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = payType == PayType.HOURLY,
                onClick = {
                    payType = PayType.HOURLY
                    if (frequency !in listOf("Weekly", "Bi-weekly", "Semi-monthly", "Monthly")) {
                        frequency = "Weekly"
                    }
                    errorMessage = ""
                },
                label = { Text("Hourly") }
            )
            FilterChip(
                selected = payType == PayType.FLAT,
                onClick = { payType = PayType.FLAT; errorMessage = "" },
                label = { Text("Fixed amount") }
            )
            FilterChip(
                selected = payType == PayType.PER_PROJECT,
                onClick = {
                    payType = PayType.PER_PROJECT
                    frequency = "One-time"
                    errorMessage = ""
                },
                label = { Text("Per project") }
            )
        }

        when (payType) {
            PayType.HOURLY -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourlyRate,
                        onValueChange = { hourlyRate = it; errorMessage = "" },
                        label = { Text("Hourly rate ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Hourly rate input field" }
                    )
                    OutlinedTextField(
                        value = hoursPerWeek,
                        onValueChange = { hoursPerWeek = it; errorMessage = "" },
                        label = { Text("Hours / week") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Hours per week input field" }
                    )
                }
                if (computedCents > 0) {
                    Text(
                        "≈ ${centsToDisplay(computedCents)} per $frequency paycheck",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            PayType.FLAT -> {
                OutlinedTextField(
                    value = flatAmount,
                    onValueChange = { flatAmount = it; errorMessage = "" },
                    label = { Text("Amount per paycheck ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Income amount input field" }
                )
            }
            PayType.PER_PROJECT -> {
                OutlinedTextField(
                    value = flatAmount,
                    onValueChange = { flatAmount = it; errorMessage = "" },
                    label = { Text("Amount per project ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Per-project amount input field" }
                )
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = when (payType) {
                    PayType.PER_PROJECT -> if (frequency == "One-time") "One-time (single project)" else frequency
                    else -> frequency
                },
                onValueChange = {},
                readOnly = true,
                label = { Text(if (payType == PayType.PER_PROJECT) "How often do you get projects?" else "Paid how often?") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
                    .semantics { contentDescription = "Income frequency selector" }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                frequencies.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when {
                                    payType == PayType.PER_PROJECT && option == "One-time" -> "One-time (single project)"
                                    else -> option
                                }
                            )
                        },
                        onClick = {
                            frequency = option
                            expanded = false
                        }
                    )
                }
            }
        }

        // Frequency hint — updates live with the selected option
        if (payType != PayType.PER_PROJECT) {
            Text(
                when (frequency) {
                    "Weekly" -> "52 paychecks per year"
                    "Bi-weekly" -> "26 paychecks per year — common for salaried jobs"
                    "Semi-monthly" -> "24 paychecks — paid on two fixed dates each month (e.g. 1st and 15th)"
                    "Monthly" -> "12 paychecks per year"
                    "Bi-monthly" -> "6 paychecks per year"
                    "Quarterly" -> "4 paychecks per year"
                    "One-time" -> "Single payment — won't repeat"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = com.example.app.GlassTokens.TextDim
            )
        }

        ScheduleDatePickerField(
            label = if (payType == PayType.PER_PROJECT) "Expected payment date" else "Next payday",
            dateText = nextPaydayIso,
            displayText = nextPaydayIso.formatDateDisplay(),
            onDateSelected = { nextPaydayIso = it }
        )

        // Optional: override for next paycheck if it will differ
        if (payType != PayType.PER_PROJECT) {
            TextButton(onClick = { showExpectedAmount = !showExpectedAmount }) {
                Text(if (showExpectedAmount) "Hide next-check override" else "Next check is a different amount?")
            }
            if (showExpectedAmount) {
                OutlinedTextField(
                    value = expectedAmountDollars,
                    onValueChange = { expectedAmountDollars = it },
                    label = { Text("Next check amount ($) — one-time override") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Expected income amount input field" }
                )
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = "Error message" }
            )
        }

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (onCancel != null) {
                androidx.compose.material3.TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }
            AppPrimaryButton(
                text = "Save",
                onClick = {
                    val cents = computedCents
                    val validation = DomainRules.validateIncomeSign(cents)
                    val nextPayday = runCatching { LocalDate.parse(nextPaydayIso) }.getOrNull()
                    val expectedAmount = dollarsToCents(expectedAmountDollars).takeIf { expectedAmountDollars.isNotBlank() }

                    if (name.isBlank()) {
                        errorMessage = "Income name is required"
                    } else if (payType == PayType.HOURLY && (hourlyRate.toDoubleOrNull() ?: 0.0) <= 0) {
                        errorMessage = "Enter a valid hourly rate"
                    } else if (payType == PayType.HOURLY && (hoursPerWeek.toDoubleOrNull() ?: 0.0) <= 0) {
                        errorMessage = "Enter hours worked per week"
                    } else if (validation.isFailure || cents <= 0) {
                        errorMessage = validation.exceptionOrNull()?.message ?: "Invalid income amount"
                    } else if (nextPayday == null) {
                        errorMessage = "Please pick a next payday date"
                    } else {
                        onSave(
                            IncomeEntity(
                                name = name,
                                amount_cents = cents,
                                frequency = frequency,
                                day_of_month = nextPayday.dayOfMonth,
                                next_date = nextPayday.toString(),
                                expectedAmountCents = if (payType == PayType.PER_PROJECT) null else expectedAmount,
                                payType = payType.name
                            )
                        )
                    }
                },
                modifier = Modifier
                    .weight(if (onCancel != null) 1f else 2f)
                    .semantics { contentDescription = "Save income entry" }
            )
        }
    }
}
