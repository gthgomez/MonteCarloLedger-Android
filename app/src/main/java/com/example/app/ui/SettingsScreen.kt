package com.example.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.app.GlassTokens
import com.example.app.DashboardConfig
import com.example.app.DashboardWidget
import com.example.app.MainViewModel
import com.example.app.util.centsToDisplay
import com.workspace.design.ConfirmDeleteDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onImportCsv: () -> Unit,
    onImportBills: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onBackupEncrypted: () -> Unit,
    onRestoreEncrypted: () -> Unit,
    onShowReminders: () -> Unit,
    onShowAppLock: () -> Unit,
    appLockPreferences: com.example.app.data.AppLockPreferences,
    viewModel: MainViewModel,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = GlassTokens.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GlassTokens.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = colorScheme.surfaceContainerLow.copy(alpha = 0.54f),
                        tint = HazeTint(Color.Transparent),
                        blurRadius = 24.dp,
                    )
                )
            )
        },
        floatingActionButton = {
            var showAddAssetDialog by remember { mutableStateOf(false) }
            FloatingActionButton(
                onClick = { showAddAssetDialog = true },
                containerColor = GlassTokens.CyanBright,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Asset")
            }

            if (showAddAssetDialog) {
                var name by remember { mutableStateOf("") }
                var balance by remember { mutableStateOf("") }
                var type by remember { mutableStateOf("Stock") }
                val types = listOf("Cash", "Stock", "Crypto", "Property", "Other")

                AlertDialog(
                    onDismissRequest = { showAddAssetDialog = false },
                    title = { Text("Add Asset", color = GlassTokens.TextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Asset Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = balance,
                                onValueChange = { balance = it },
                                label = { Text("Current Balance ($)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Type", style = MaterialTheme.typography.labelSmall)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                types.forEach { t ->
                                    FilterChip(
                                        selected = type == t,
                                        onClick = { type = t },
                                        label = { Text(t) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val cents = (balance.toDoubleOrNull() ?: 0.0) * 100
                                viewModel.addAsset(name, type, cents.toLong())
                                showAddAssetDialog = false
                            },
                            enabled = name.isNotBlank() && balance.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddAssetDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    ) { padding ->
        val uiState by viewModel.uiState.collectAsState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Data Management",
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                SettingsCard(
                    title = "Import Transactions",
                    description = "Load spending data from a CSV file.",
                    icon = Icons.Default.FileUpload,
                    onClick = onImportCsv
                )
            }

            item {
                SettingsCard(
                    title = "Import Bills",
                    description = "Load recurring bills from a CSV file.",
                    icon = Icons.Default.FileUpload,
                    onClick = onImportBills
                )
            }

            item {
                SettingsCard(
                    title = "Encrypted Backup",
                    description = "Export your data as a password-protected local file.",
                    icon = Icons.Default.CloudUpload,
                    onClick = onBackupEncrypted
                )
            }

            item {
                SettingsCard(
                    title = "Import from Cloud",
                    description = "Restore from an encrypted backup file.",
                    icon = Icons.Default.CloudDownload,
                    onClick = onRestoreEncrypted
                )
            }

            item {
                SettingsCard(
                    title = "Standard Export",
                    description = "Plaintext JSON backup (unencrypted).",
                    icon = Icons.Default.FileUpload,
                    onClick = onBackup
                )
            }

            item {
                SettingsCard(
                    title = "Restore Backup",
                    description = "Restore data from a previously saved JSON file.",
                    icon = Icons.Default.Restore,
                    onClick = onRestore
                )
            }

            item {
                Text(
                    "Security & Privacy",
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsCard(
                    title = "App Lock",
                    description = if (appLockPreferences.enabled) {
                        "PIN lock is enabled for this device."
                    } else {
                        "Require a local PIN before opening the ledger."
                    },
                    icon = Icons.Default.Lock,
                    onClick = onShowAppLock
                )
            }

            item {
                Text(
                    "App Preferences",
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsCard(
                    title = "Reminders",
                    description = "Configure bill alerts and weekly check-ins.",
                    icon = Icons.Default.Notifications,
                    onClick = onShowReminders
                )
            }

            item {
                Text(
                    "Customize Dashboard",
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = GlassTint.Neutral,
                    surfaceStyle = GlassSurfaceStyle.Standard,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        val widgetLabel = mapOf(
                            DashboardWidget.ActionCenter to "Action Center",
                            DashboardWidget.ReviewInbox to "Review Inbox",
                            DashboardWidget.MoneyBuckets to "Money Buckets",
                            DashboardWidget.TrustLayer to "Trust Signals",
                            DashboardWidget.Balance to "Balance Card",
                            DashboardWidget.Monitoring to "Monitoring Status",
                            DashboardWidget.NetWorth to "Net Worth",
                            DashboardWidget.Goal to "Savings Goals",
                            DashboardWidget.PlanAhead to "Plan Ahead",
                            DashboardWidget.MonteCarlo to "3-Month Estimate"
                        )
                        DashboardWidget.values().forEach { widget ->
                            val readableName = widgetLabel[widget] ?: widget.name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(readableName, color = GlassTokens.TextPrimary)
                                Switch(
                                    checked = uiState.dashboardConfig.visibleWidgets.contains(widget),
                                    onCheckedChange = { isChecked ->
                                        val newWidgets = if (isChecked) {
                                            uiState.dashboardConfig.visibleWidgets + widget
                                        } else {
                                            uiState.dashboardConfig.visibleWidgets - widget
                                        }
                                        viewModel.updateDashboardConfig(DashboardConfig(newWidgets))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Net Worth Assets",
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            if (uiState.assets.isEmpty()) {
                item {
                    Text(
                        "No assets tracked yet. Add your stocks, property, or crypto to see your total net worth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextDim,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(uiState.assets) { asset ->
                AssetItemCard(
                    asset = asset,
                    onDelete = { viewModel.deleteAsset(asset) }
                )
            }

        }
    }
}

@Composable
private fun AssetItemCard(
    asset: com.example.app.data.AssetEntity,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Quiet,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = GlassTokens.PositiveGreen.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = GlassTokens.PositiveGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(asset.name, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
                Text(
                    "${centsToDisplay(asset.balanceCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextSecondary
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = GlassTokens.ErrorRed)
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete this asset?",
            message = "Remove \"${asset.name}\" from your net worth tracking? This cannot be undone.",
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = GlassTokens.CyanBright.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassTokens.CyanBright.copy(alpha = 0.24f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = GlassTokens.CyanBright, modifier = Modifier.size(24.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = GlassTokens.TextPrimary)
                Text(description, style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextSecondary)
            }

            TextButton(onClick = onClick) {
                Text("Open")
            }
        }
    }
}
