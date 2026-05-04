package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.app.data.TransactionEntity
import java.time.LocalDate
import kotlin.math.abs

private val TRANSACTION_TYPES = listOf("income", "expense", "adjustment")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    transaction: TransactionEntity,
    onSave: (TransactionEntity) -> Unit,
    onSaveRule: (String, String) -> Unit = { _, _ -> },
    onDelete: (TransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(transaction.description) }
    // Display positive amount in UI
    var amountDollars by remember { mutableStateOf(String.format("%.2f", abs(transaction.amount_cents) / 100.0)) }
    var type by remember { mutableStateOf(transaction.type) }
    var category by remember { mutableStateOf(transaction.category) }
    var dateIso by remember {
        mutableStateOf(
            runCatching { LocalDate.parse(transaction.date) }.getOrDefault(LocalDate.now()).toString()
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var rememberRule by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction") },
            text = { Text("Remove this \"${transaction.description}\" entry? This will affect your account balance.") },
            confirmButton = {
                AppDestructiveButton(text = "Delete", onClick = { onDelete(transaction) })
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Edit Transaction", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amountDollars,
            onValueChange = { amountDollars = it; errorMessage = "" },
            label = { Text("Amount ($)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = errorMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category") },
            modifier = Modifier.fillMaxWidth()
        )

        ScheduleDatePickerField(
            label = "Date",
            dateText = dateIso,
            displayText = dateIso.formatDateDisplay(),
            onDateSelected = { dateIso = it }
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = type,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TRANSACTION_TYPES.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { type = option; expanded = false }
                    )
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = rememberRule,
                onCheckedChange = { rememberRule = it }
            )
            Text("Remember this category for similar descriptions")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPrimaryButton(
                text = "Save",
                onClick = {
                    val raw = amountDollars.toDoubleOrNull() ?: 0.0
                    if (raw <= 0) {
                        errorMessage = "Amount must be greater than zero."
                    } else if (description.isBlank()) {
                        errorMessage = "Description cannot be empty."
                    } else {
                        val cents = when (type.lowercase()) {
                            "expense" -> -((raw * 100).toInt())
                            else -> (raw * 100).toInt()
                        }
                        val updated = transaction.copy(
                            description = description,
                            amount_cents = cents,
                            type = type,
                            category = category,
                            date = dateIso
                        )
                        onSave(updated)
                        if (rememberRule && category.isNotBlank()) {
                            onSaveRule(description, category)
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
