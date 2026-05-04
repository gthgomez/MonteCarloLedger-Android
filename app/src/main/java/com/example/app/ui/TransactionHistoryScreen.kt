package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app.GlassTokens
import com.example.app.MainViewModel
import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.PaymentEntity
import com.example.app.data.TransactionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: MainViewModel,
    onEditTransaction: (TransactionEntity) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val payments by viewModel.allPayments.collectAsStateWithLifecycle()
    val billOccurrences by viewModel.allBillOccurrences.collectAsStateWithLifecycle()
    TransactionHistoryContent(
        transactions = uiState.transactions,
        payments = payments,
        billOccurrences = billOccurrences,
        onEditTransaction = onEditTransaction
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TransactionHistoryContent(
    transactions: List<TransactionEntity>,
    payments: List<PaymentEntity>,
    billOccurrences: List<BillOccurrenceEntity>,
    onEditTransaction: (TransactionEntity) -> Unit
) {
    val sorted = transactions.sortedByDescending { it.date }
    val paymentById = remember(payments) { payments.associateBy { it.id } }
    val linkedOccurrenceByTransactionId = remember(billOccurrences) {
        billOccurrences
            .filter { it.transaction_id != null }
            .associateBy { it.transaction_id!! }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "History",
                            color = GlassTokens.TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "Recent activity",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextDim
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sorted.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
                        tint = GlassTint.Teal,
                        surfaceStyle = GlassSurfaceStyle.Quiet
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "No activity yet.",
                                style = MaterialTheme.typography.titleLarge,
                                color = GlassTokens.TextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text("Add a paycheck, bill, or spending entry and it will show up here.", style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextSecondary)
                        }
                    }
                }
            }
            items(sorted) { txn ->
                val tint = when (txn.type) {
                    "income"  -> GlassTint.Cyan
                    "expense" -> GlassTint.Neutral
                    else      -> GlassTint.Neutral
                }
                val linkedOccurrence = linkedOccurrenceByTransactionId[txn.id]
                val linkedPaymentName = linkedOccurrence?.let { occurrence ->
                    paymentById[occurrence.payment_id]?.name
                }
                GlassCard(
                    modifier = Modifier.heightIn(min = UiLayoutTokens.LedgerListCardMinHeight),
                    tint = tint,
                    surfaceStyle = GlassSurfaceStyle.Quiet,
                    cornerRadius = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryGlassIcon(category = txn.category, size = 44.dp, iconSize = 22.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(txn.description, style = MaterialTheme.typography.titleSmall, color = GlassTokens.TextPrimary)
                            Text(
                                "${if (txn.amount_cents >= 0) "+" else ""}$${String.format("%.2f", txn.amount_cents / 100.0)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (txn.amount_cents >= 0) GlassTokens.PositiveGreen else GlassTokens.ErrorRed
                            )
                            Text(
                                "${txn.type.replaceFirstChar { it.uppercase() }}${if (txn.category.isNotBlank()) "  •  ${txn.category}" else ""}  •  ${txn.date.formatDateDisplay()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.TextDim
                            )
                            if (linkedOccurrence != null && linkedPaymentName != null) {
                                Text(
                                    "Closed bill: $linkedPaymentName • due ${linkedOccurrence.due_date.formatDateDisplay()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = GlassTokens.CyanBright
                                )
                            }
                        }
                        OutlinedButton(onClick = { onEditTransaction(txn) }) {
                            Text("Edit", color = GlassTokens.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
