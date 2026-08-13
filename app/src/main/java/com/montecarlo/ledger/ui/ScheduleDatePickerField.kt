package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDatePickerField(
    label: String,
    dateText: String,
    displayText: String = dateText,
    onDateSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val initialMillis = remember(dateText) { dateText.toUtcDateMillisOrNull() }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    OutlinedTextField(
        value = displayText,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick date")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .semantics { contentDescription = "$label date picker" }
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.utcMillisToLocalDateString()?.let(onDateSelected)
                    showPicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

internal fun String.toUtcDateMillisOrNull(): Long? {
    val date = runCatching { LocalDate.parse(trim()) }.getOrNull() ?: return null
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

internal fun Long.utcMillisToLocalDateString(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

/**
 * Formats a yyyy-MM-dd string for display:
 *  - Same year as today  →  MM-dd
 *  - Different year      →  MM-dd-yyyy
 */
internal fun String.formatDateDisplay(): String {
    val date = runCatching { LocalDate.parse(trim()) }.getOrNull() ?: return this
    return date.formatDateDisplay()
}

internal fun LocalDate.formatDateDisplay(): String {
    return if (year == LocalDate.now().year) {
        String.format("%02d-%02d", monthValue, dayOfMonth)
    } else {
        String.format("%02d-%02d-%04d", monthValue, dayOfMonth, year)
    }
}
