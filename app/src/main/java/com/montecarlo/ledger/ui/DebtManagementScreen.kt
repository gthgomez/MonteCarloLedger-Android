package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.util.DollarParseResult
import com.montecarlo.ledger.util.parseDollars
import com.montecarlo.ledger.util.centsToDisplay
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun DebtManagementScreen(
    debts: List<DebtEntity>,
    payments: List<PaymentEntity>,
    onAdd: (DebtEntity) -> Unit,
    onUpdate: (DebtEntity) -> Unit,
    onDelete: (DebtEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingDebt by remember { mutableStateOf<DebtEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your debts", color = GlassTokens.TextPrimary)
                    Text(
                        "Enter balances and APRs from your lender statements. Bills remain separate cash-flow records.",
                        color = GlassTokens.TextSecondary,
                    )
                }
                Button(onClick = { editingDebt = null; showEditor = true }) { Text("Add debt") }
            }
        }
        if (debts.isEmpty()) {
            item {
                GlassCard(tint = GlassTint.Cyan, surfaceStyle = GlassSurfaceStyle.Quiet) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No debts added", color = GlassTokens.TextPrimary)
                        Text("Add an authoritative balance, APR, and minimum payment to unlock payoff projections.", color = GlassTokens.TextSecondary)
                    }
                }
            }
        } else {
            items(debts, key = { it.id }) { debt ->
                SolidListSurface {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(debt.name, color = GlassTokens.TextPrimary)
                            Text("${centsToDisplay(debt.balanceCents)} • ${debt.aprBasisPoints / 100.0}% APR", color = GlassTokens.TextSecondary)
                            Text("Minimum ${centsToDisplay(debt.minimumPaymentCents)}/mo • Due day ${debt.dueDayOfMonth}", color = GlassTokens.TextSecondary)
                            payments.firstOrNull { it.id == debt.linkedPaymentId }?.let { linkedBill ->
                                if (linkedBill.amount_cents != debt.minimumPaymentCents) {
                                    Text("Linked bill differs from debt minimum; update the bill to match.", color = GlassTokens.ErrorRed)
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = { editingDebt = debt; showEditor = true }) { Text("Edit") }
                            TextButton(onClick = { onDelete(debt) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        DebtEditorDialog(
            initial = editingDebt,
            payments = payments,
            onDismiss = { showEditor = false },
            onSave = { debt ->
                if (editingDebt == null) onAdd(debt) else onUpdate(debt)
                showEditor = false
            },
        )
    }
}

@Composable
private fun DebtEditorDialog(
    initial: DebtEntity?,
    payments: List<PaymentEntity>,
    onDismiss: () -> Unit,
    onSave: (DebtEntity) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var balance by remember(initial) { mutableStateOf(initial?.let { centsToInput(it.balanceCents) }.orEmpty()) }
    var apr by remember(initial) { mutableStateOf(initial?.let { (it.aprBasisPoints / 100.0).toString() }.orEmpty()) }
    var minimum by remember(initial) { mutableStateOf(initial?.let { centsToInput(it.minimumPaymentCents) }.orEmpty()) }
    var dueDay by remember(initial) { mutableStateOf(initial?.dueDayOfMonth?.toString() ?: "1") }
    var linkedPaymentId by remember(initial) { mutableStateOf(initial?.linkedPaymentId) }
    var paymentsExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add debt" else "Edit debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Debt name") }, singleLine = true)
                OutlinedTextField(balance, { balance = it }, label = { Text("Balance ($)") }, singleLine = true)
                OutlinedTextField(apr, { apr = it }, label = { Text("APR (%)") }, singleLine = true)
                OutlinedTextField(minimum, { minimum = it }, label = { Text("Minimum payment ($)") }, singleLine = true)
                OutlinedTextField(dueDay, { dueDay = it }, label = { Text("Due day (1–31)") }, singleLine = true)
                Column {
                    OutlinedButton(onClick = { paymentsExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(payments.firstOrNull { it.id == linkedPaymentId }?.name ?: "Optional linked bill")
                    }
                    DropdownMenu(expanded = paymentsExpanded, onDismissRequest = { paymentsExpanded = false }) {
                        DropdownMenuItem(text = { Text("No linked bill") }, onClick = { linkedPaymentId = null; paymentsExpanded = false })
                        payments.forEach { payment ->
                            DropdownMenuItem(text = { Text(payment.name) }, onClick = { linkedPaymentId = payment.id; paymentsExpanded = false })
                        }
                    }
                }
                error?.let { Text(it, color = GlassTokens.ErrorRed) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val balanceParsed = parseDollars(balance)
                val minimumParsed = parseDollars(minimum)
                val balanceCents = when (val p = balanceParsed) { is DollarParseResult.Valid -> p.cents; else -> 0L }
                val minimumCents = when (val p = minimumParsed) { is DollarParseResult.Valid -> p.cents; else -> 0L }
                val aprBasisPoints = runCatching {
                    BigDecimal(apr.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
                }.getOrNull()
                val day = dueDay.toIntOrNull()
                when {
                    name.isBlank() -> error = "Debt name is required"
                    balanceParsed is DollarParseResult.Invalid || balanceCents <= 0L -> error = "Enter a valid balance greater than zero"
                    aprBasisPoints == null || aprBasisPoints !in 0..99_999 -> error = "APR must be between 0% and 999.99%"
                    minimumParsed is DollarParseResult.Invalid || minimumCents <= 0L -> error = "Enter a valid minimum payment greater than zero"
                    day !in 1..31 -> error = "Due day must be between 1 and 31"
                    else -> onSave(DebtEntity(initial?.id ?: 0L, name.trim(), balanceCents, aprBasisPoints, minimumCents, day!!, linkedPaymentId, initial?.isActive ?: true))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun centsToInput(cents: Long): String = BigDecimal.valueOf(cents).movePointLeft(2).setScale(2).toPlainString()
