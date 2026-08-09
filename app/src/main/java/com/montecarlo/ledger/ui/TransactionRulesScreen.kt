package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.TransactionRuleEntity
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionRulesScreen(
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    hazeState: HazeState,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<TransactionRuleEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorization Rules", color = GlassTokens.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GlassTokens.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rule", tint = GlassTokens.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Rules automatically assign categories to matching transaction descriptions during CSV import or manual logging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (uiState.transactionRules.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No custom rules created",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTokens.TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Default presets are active for common merchants (Walmart, Starbucks, Netflix, etc.). Click '+' to add custom merchant rules.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTokens.TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(uiState.transactionRules, key = { it.id }) { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Contains \"${rule.match_text}\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GlassTokens.TextPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "→ Category: ${rule.category}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { ruleToDelete = rule }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { matchText, category, applyRetroactively ->
                viewModel.saveTransactionRule(matchText, category, applyRetroactively)
                showAddDialog = false
            }
        )
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Rule") },
            text = { Text("Are you sure you want to delete the rule for \"${rule.match_text}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransactionRule(rule)
                    ruleToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (matchText: String, category: String, applyRetroactively: Boolean) -> Unit
) {
    var matchText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var applyRetroactively by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Categorization Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = matchText,
                    onValueChange = { matchText = it },
                    label = { Text("If description contains") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Set Category to") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = applyRetroactively,
                        onCheckedChange = { applyRetroactively = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Apply to past uncategorized transactions", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(matchText, category, applyRetroactively) },
                enabled = matchText.isNotBlank() && category.isNotBlank()
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
