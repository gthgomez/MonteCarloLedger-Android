package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.sync.VaultLocalChangesException
import com.montecarlo.ledger.sync.VaultSyncStore
import kotlinx.coroutines.launch

@Composable
fun CloudVaultDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var vaultId by remember { mutableStateOf(viewModel.storedVaultId().orEmpty()) }
    var serverUrl by remember {
        mutableStateOf(viewModel.storedVaultServerUrl().ifBlank { VaultSyncStore.DEFAULT_SERVER })
    }
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmOverwritePull by remember { mutableStateOf(false) }

    fun runPull(forceOverwrite: Boolean) {
        busy = true
        scope.launch {
            val result = runCatching {
                viewModel.pullCloudVault(
                    vaultId.trim(),
                    passphrase,
                    serverUrl.trim(),
                    allowOverwrite = forceOverwrite,
                )
            }
            status = result.fold(
                onSuccess = { "Pull complete · revision $it" },
                onFailure = {
                    if (it is VaultLocalChangesException && !forceOverwrite) {
                        confirmOverwritePull = true
                        "Local ledger has unsynchronized data. Confirm replace to join this vault."
                    } else {
                        it.message ?: "Pull failed"
                    }
                },
            )
            busy = false
        }
    }

  if (confirmOverwritePull) {
        AlertDialog(
            onDismissRequest = { confirmOverwritePull = false },
            title = { Text("Replace local ledger?") },
            text = {
                Text(
                    "Pulling will replace this device’s financial records with the vault copy. " +
                        "Export an encrypted backup first if you need to keep unsynchronized edits.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOverwritePull = false
                        runPull(forceOverwrite = true)
                    },
                ) { Text("Replace and pull") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOverwritePull = false }) { Text("Cancel") }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypted cloud sync (optional)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Financial data is encrypted on this device before any upload. " +
                        "Sync only when you choose Create, Pull, or Push.",
                )
                viewModel.lastVaultSyncSummary()?.let { Text(it) }
                OutlinedTextField(
                    value = vaultId,
                    onValueChange = { vaultId = it },
                    label = { Text("Vault ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL (HTTPS)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Vault passphrase") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                status?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && vaultId.isNotBlank() && passphrase.isNotBlank(),
                onClick = { runPull(forceOverwrite = false) },
            ) { Text("Pull") }
        },
        dismissButton = {
            Column {
                TextButton(
                    enabled = !busy && vaultId.isNotBlank() && passphrase.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = runCatching {
                                viewModel.createCloudVault(vaultId.trim(), passphrase, serverUrl.trim())
                            }
                            status = result.fold(
                                onSuccess = { "Vault ready · revision $it (create or join)" },
                                onFailure = { it.message ?: "Create failed" },
                            )
                            busy = false
                        }
                    },
                ) { Text("Create / join") }
                TextButton(
                    enabled = !busy && vaultId.isNotBlank() && passphrase.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = runCatching {
                                viewModel.pushCloudVault(vaultId.trim(), passphrase, serverUrl.trim())
                            }
                            status = result.fold(
                                onSuccess = { "Push complete · revision $it" },
                                onFailure = { it.message ?: "Push failed" },
                            )
                            busy = false
                        }
                    },
                ) { Text("Push") }
            }
        },
    )
}
