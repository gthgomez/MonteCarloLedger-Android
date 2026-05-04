package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.PaymentEntity
import java.time.LocalDate

private data class TransactionTypeOption(
    val value: String,
    val label: String,
)

private val TRANSACTION_TYPES = listOf(
    TransactionTypeOption("income", "Income"),
    TransactionTypeOption("expense", "Expense"),
    TransactionTypeOption("adjustment", "Adjustment"),
)

private val BASIC_TRANSACTION_TYPES = TRANSACTION_TYPES.filter { it.value == "expense" }

internal data class BillLinkOption(
    val occurrenceId: Int,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    payments: List<PaymentEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
    externalErrorMessage: String? = null,
    onSave: (description: String, amountCents: Int, type: String, linkedOccurrenceId: Int?, category: String, date: String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amountDollars by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TRANSACTION_TYPES[1].value) }
    var category by remember { mutableStateOf("") }
    var dateIso by remember { mutableStateOf(LocalDate.now().toString()) }
    var expanded by remember { mutableStateOf(false) }
    var linkExpanded by remember { mutableStateOf(false) }
    var selectedOccurrenceId by remember { mutableStateOf<Int?>(null) }
    var localErrorMessage by remember { mutableStateOf("") }
    var showAdvancedTypes by remember { mutableStateOf(false) }

    val amountCents = remember(amountDollars) {
        amountDollars.toDoubleOrNull()?.let { (it * 100).toInt() } ?: 0
    }

    val billLinkOptions = remember(payments, billOccurrences, amountCents, type) {
        buildBillLinkOptions(
            payments = payments,
            billOccurrences = billOccurrences,
            amountCents = amountCents,
            type = type
        )
    }

    LaunchedEffect(billLinkOptions) {
        if (selectedOccurrenceId != null && billLinkOptions.none { it.occurrenceId == selectedOccurrenceId }) {
            selectedOccurrenceId = null
        }
    }

    LaunchedEffect(showAdvancedTypes) {
        if (!showAdvancedTypes && type != "expense") {
            type = "expense"
        }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Start with a spending entry. Income and adjustments are available if you need them.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("What was this for?") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amountDollars,
            onValueChange = { amountDollars = it },
            label = { Text("Amount (\$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        ScheduleDatePickerField(
            label = "Date",
            dateText = dateIso,
            displayText = dateIso.formatDateDisplay(),
            onDateSelected = { dateIso = it }
        )

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            val visibleTypes = if (showAdvancedTypes) TRANSACTION_TYPES else BASIC_TRANSACTION_TYPES
            OutlinedTextField(
                value = visibleTypes.firstOrNull { it.value == type }?.label ?: type.replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Entry type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                visibleTypes.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            type = option.value
                            expanded = false
                            selectedOccurrenceId = null
                        }
                    )
                }
            }
        }

        TextButton(onClick = { showAdvancedTypes = !showAdvancedTypes }) {
            Text(if (showAdvancedTypes) "Hide advanced entry types" else "Show advanced entry types")
        }

        if (type == "expense") {
            ExposedDropdownMenuBox(expanded = linkExpanded, onExpandedChange = { linkExpanded = !linkExpanded }) {
                OutlinedTextField(
                    value = billLinkOptions.firstOrNull { it.occurrenceId == selectedOccurrenceId }?.label ?: "No bill linked",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bill match (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = linkExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth()
                        .semantics { contentDescription = "Bill link selector" }
                )
                ExposedDropdownMenu(expanded = linkExpanded, onDismissRequest = { linkExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("No bill linked") },
                        onClick = {
                            selectedOccurrenceId = null
                            linkExpanded = false
                        }
                    )
                    if (billLinkOptions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No matching unpaid bills") },
                            onClick = { linkExpanded = false },
                            enabled = false
                        )
                    } else {
                        billLinkOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedOccurrenceId = option.occurrenceId
                                    linkExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Text(
                text = if (billLinkOptions.isEmpty()) {
                    "No unpaid bill matches the amount yet."
                } else {
                    "If this expense paid a bill, link the matching unpaid occurrence to close it."
                }
            )
        }

        AppPrimaryButton(
            text = "Save",
            onClick = {
                localErrorMessage = ""
                val raw = amountDollars.toDoubleOrNull()
                if (description.isBlank()) {
                    localErrorMessage = "Description is required."
                    return@AppPrimaryButton
                }
                if (raw == null || raw <= 0.0) {
                    localErrorMessage = "Enter a valid amount greater than zero."
                    return@AppPrimaryButton
                }
                // income/adjustment: positive cents; expense: negative cents (matches domain_rules.py sign convention)
                val cents = when (type) {
                    "expense" -> -((raw * 100).toInt())
                    else -> (raw * 100).toInt()
                }
                val linkedOccurrenceId = if (type == "expense") selectedOccurrenceId else null
                onSave(description, cents, type, linkedOccurrenceId, category, dateIso)
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (!externalErrorMessage.isNullOrBlank() || localErrorMessage.isNotBlank()) {
            Text(
                text = externalErrorMessage ?: localErrorMessage,
                color = androidx.compose.ui.graphics.Color.Red
            )
        }
    }
}

private fun buildBillLinkOptions(
    payments: List<PaymentEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
    amountCents: Int,
    type: String,
): List<BillLinkOption> {
    if (type != "expense" || amountCents <= 0) return emptyList()
    val paymentById = payments.associateBy { it.id }
    return billOccurrences.asSequence()
        .filter { it.is_paid == 0 && it.amount_cents == amountCents }
        .sortedBy { runCatching { LocalDate.parse(it.due_date) }.getOrNull() ?: LocalDate.MAX }
        .mapNotNull { occurrence ->
            val payment = paymentById[occurrence.payment_id] ?: return@mapNotNull null
            BillLinkOption(
                occurrenceId = occurrence.id,
                label = "${payment.name} • due ${occurrence.due_date.formatDateDisplay()} • $${String.format("%.2f", occurrence.amount_cents / 100.0)}"
            )
        }
        .toList()
}
