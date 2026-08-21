package com.montecarlo.ledger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.ui.GlassTint
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.data.ReminderPreferences
import com.montecarlo.ledger.data.SettingsEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.OnboardingMilestone
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.util.centsToDisplay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalDate


@Composable
internal fun RestoreBackupDialog(
    snapshot: LedgerBackupSnapshot,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This will replace the current local data with the backup contents.")
                Text(
                    "Incomes: ${snapshot.incomes.size}, bills: ${snapshot.payments.size}, " +
                        "transactions: ${snapshot.transactions.size}, bill occurrences: ${snapshot.billOccurrences.size}"
                )
                Text(
                    "Assets: ${snapshot.assets.size}, goals: ${snapshot.goals.size}, " +
                        "settings: ${snapshot.settings.size}"
                )
                Text(
                    "Bank balance: ${centsToDisplay(snapshot.bankBalanceCents)}"
                )
                Text(
                    if (snapshot.isBalanceReconciled) {
                        "The backup was saved with a reconciled bank balance."
                    } else {
                        "The backup was saved before bank balance reconciliation."
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onRestore) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy policy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MonteCarlo Ledger is local-only. Your ledger stays on this device.")
                Text("The app does not use the INTERNET permission and does not send your data to our servers.")
                Text("Standard export and encrypted backup use Android's file picker — you choose when and where files are saved.")
                Text("App Lock PIN is stored as a salted hash on-device only. It is not included in plaintext or encrypted backup files.")
                Text("Full policy URL: TODO_PRIVACY_URL")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
internal fun ReminderSettingsDialog(
    preferences: ReminderPreferences,
    onDismiss: () -> Unit,
    onSave: (ReminderPreferences) -> Unit,
) {
    var enabled by remember { mutableStateOf(preferences.enabled) }
    var weeklyEnabled by remember { mutableStateOf(preferences.weeklyCheckInEnabled) }
    var billEnabled by remember { mutableStateOf(preferences.billRemindersEnabled) }
    var daysBefore by remember { mutableStateOf(preferences.billReminderDaysBefore.coerceIn(1, 14)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Keep reminders sparse and useful.")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable reminders")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Weekly check-in")
                    Switch(checked = weeklyEnabled, onCheckedChange = { weeklyEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bill reminders")
                    Switch(checked = billEnabled, onCheckedChange = { billEnabled = it })
                }
                Text("Bill reminder window")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 7).forEach { option ->
                        TextButton(onClick = { daysBefore = option }) {
                            Text(if (daysBefore == option) "$option days" else option.toString())
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(ReminderPreferences(enabled, weeklyEnabled, billEnabled, daysBefore)) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun AppLockSettingsDialog(
    preferences: com.montecarlo.ledger.data.AppLockPreferences,
    onDismiss: () -> Unit,
    onEnable: (String) -> Unit,
    onDisable: () -> Unit,
    onLockNow: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val pinValid = pin.length >= 4 && pin.all { it.isDigit() } && pin == confirmPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Lock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preferences.enabled) {
                    Text("App lock is enabled on this device.")
                    Text("Lock now to test the unlock flow, or disable the local PIN gate.")
                } else {
                    Text("Require a local PIN before the ledger opens.")
                    androidx.compose.material3.OutlinedTextField(
                        value = pin,
                        onValueChange = { next -> pin = next.filter { it.isDigit() }.take(12) },
                        label = { Text("New PIN") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { next -> confirmPin = next.filter { it.isDigit() }.take(12) },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        isError = confirmPin.isNotBlank() && pin != confirmPin,
                        supportingText = {
                            if (confirmPin.isNotBlank() && pin != confirmPin) {
                                Text("PINs do not match.")
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            if (preferences.enabled) {
                Button(onClick = onLockNow) {
                    Text("Lock now")
                }
            } else {
                Button(
                    onClick = { onEnable(pin) },
                    enabled = pinValid,
                ) {
                    Text("Enable")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preferences.enabled) {
                    TextButton(onClick = onDisable) {
                        Text("Disable")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
internal fun PasswordDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
