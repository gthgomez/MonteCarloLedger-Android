package com.montecarlo.ledger.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun TransactionRuleConfirmationDialog(
    description: String,
    category: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply category rule?") },
        text = {
            Text(
                "Create a rule for \"$description\" and apply it to existing matching transactions?\n\nCategory: $category"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Apply rule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
