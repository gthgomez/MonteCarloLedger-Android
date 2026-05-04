package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BankBalanceDialog(
    initialAmountCents: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    BankBalanceDialogContent(
        initialAmountCents = initialAmountCents,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        useDialog = true,
    )
}

@Composable
internal fun BankBalanceSheetContent(
    initialAmountCents: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    BankBalanceDialogContent(
        initialAmountCents = initialAmountCents,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        useDialog = false,
    )
}

@Composable
private fun BankBalanceDialogContent(
    initialAmountCents: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    useDialog: Boolean,
) {
    var amountText by remember(initialAmountCents) {
        mutableStateOf(String.format(Locale.US, "%.2f", initialAmountCents / 100.0))
    }
    var errorMessage by remember { mutableStateOf("") }

    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Enter the balance your bank app shows right now. We’ll use it as the starting point.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    errorMessage = ""
                },
                label = { Text("Bank balance ($)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.Red
                )
            }
        }
    }

    val save: () -> Unit = {
        val parsed = parseBankBalanceCents(amountText)
        if (parsed == null) {
            errorMessage = "Enter a valid balance."
        } else {
            onConfirm(parsed)
        }
    }

    if (!useDialog) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Confirm bank balance",
                style = MaterialTheme.typography.titleLarge
            )
            body()
            Button(onClick = save, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Confirm bank balance",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = body,
        confirmButton = {
            Button(onClick = save) {
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

internal fun parseBankBalanceCents(value: String): Int? {
    val cleaned = value
        .trim()
        .replace("$", "")
        .replace(",", "")
        .replace(" ", "")

    if (cleaned.isBlank()) return null

    val normalized = if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
        "-${cleaned.substring(1, cleaned.length - 1)}"
    } else {
        cleaned
    }

    return runCatching {
        BigDecimal(normalized)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
    }.getOrNull()
}
