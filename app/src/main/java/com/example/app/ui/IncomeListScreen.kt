package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app.GlassTokens
import com.example.app.MainViewModel
import com.example.app.data.IncomeEntity
import com.example.app.util.centsToDisplay
import com.example.app.util.dollarsToCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeListScreen(
    viewModel: MainViewModel,
    onEditIncome: (IncomeEntity) -> Unit,
    onAddIncome: (() -> Unit)? = null
) {
    val incomeList by viewModel.allIncome.collectAsStateWithLifecycle()
    var paydayTarget by remember { mutableStateOf<IncomeEntity?>(null) }
    var paydayAmount by remember { mutableStateOf("") }

    paydayTarget?.let { income ->
        AlertDialog(
            onDismissRequest = { paydayTarget = null; paydayAmount = "" },
            title = { Text("Record Paycheck: ${income.name}", color = GlassTokens.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default: ${centsToDisplay(income.expectedAmountCents ?: income.amount_cents)}", color = GlassTokens.TextSecondary)
                    OutlinedTextField(
                        value = paydayAmount,
                        onValueChange = { paydayAmount = it },
                        label = { Text("Actual amount received (\$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val rawCents = dollarsToCents(paydayAmount)
                    val cents = if (rawCents > 0) rawCents
                                else income.expectedAmountCents ?: income.amount_cents
                    viewModel.processPayday(income, cents)
                    paydayTarget = null
                    paydayAmount = ""
                }) { Text("Confirm") }
            },
            dismissButton = {
                OutlinedButton(onClick = { paydayTarget = null; paydayAmount = "" }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Paychecks",
                            color = GlassTokens.TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "(income entries)",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextDim
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (onAddIncome != null) {
                FloatingActionButton(
                    onClick = onAddIncome,
                    containerColor = GlassTokens.Cyan,
                    contentColor = Color(0xFF083344)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Income")
                }
            }
        }
    ) { padding ->
        if (incomeList.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassCard(
                    modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
                    tint = GlassTint.Cyan,
                    surfaceStyle = GlassSurfaceStyle.Quiet
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "No paychecks yet.",
                            style = MaterialTheme.typography.titleLarge,
                            color = GlassTokens.TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text("Add a paycheck or income source to unlock the budget and forecast cards. These are your income entries.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
                    }
                }
                AppPrimaryButton(text = "Add paycheck", onClick = { onAddIncome?.invoke() ?: Unit })
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(incomeList) { income ->
                    GlassCard(
                        modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
                        tint = GlassTint.Cyan,
                        surfaceStyle = GlassSurfaceStyle.Quiet
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(income.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassTokens.TextPrimary)
                                Text(
                                    "${centsToDisplay(income.amount_cents)}  •  ${income.frequency}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GlassTokens.CyanBright
                                )
                                income.expectedAmountCents?.let {
                                    Text("Expected: ${centsToDisplay(it)}", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextDim)
                                }
                                Text("Next payday: ${income.next_date.formatDateDisplay()}", style = MaterialTheme.typography.bodySmall, color = GlassTokens.TextDim)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { onEditIncome(income) }) { Text("Edit", color = GlassTokens.TextSecondary) }
                                AppSuccessButton(text = "Paid", onClick = { paydayAmount = ""; paydayTarget = income })
                            }
                        }
                    }
                }
            }
        }
    }
}
