package com.montecarlo.ledger.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DestructiveVaultPullConfirmDialog(
    onConfirmReplace: () -> Unit,
    onCancel: () -> Unit,
    body: String = VaultDestructivePullMessaging.destructiveOverwriteDialogBody(),
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(VaultDestructivePullMessaging.OVERWRITE_DIALOG_TITLE) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirmReplace) { Text("Replace and pull") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
