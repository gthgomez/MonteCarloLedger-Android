package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.scenario.PaycheckDelaySimulation
import com.montecarlo.ledger.scenario.PurchaseSimulation
import com.montecarlo.ledger.scenario.ScenarioComparisonText
import com.montecarlo.ledger.util.DollarParseResult
import com.montecarlo.ledger.util.parseDollars
import java.time.LocalDate

/**
 * Minimal WS6 affordance: "can I afford this purchase?" and "what if my next paycheck is late?".
 *
 * The card only collects the two hypotheticals and renders whatever the canonical contract
 * comparison returned — [ScenarioComparisonText] formats values that already came out of the
 * contract engine, so the numbers shown here cannot be a second, independent forecast. Results are
 * deliberately stateless here: they live in [com.montecarlo.ledger.MainViewModel] so a recomposition
 * does not re-run the engine.
 */
@Composable
internal fun ScenarioWhatIfCard(
    purchaseSimulation: PurchaseSimulation?,
    paycheckDelaySimulation: PaycheckDelaySimulation?,
    error: String?,
    onCheckPurchase: (Long, LocalDate) -> Unit,
    onCheckPaycheckDelay: (Int) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var purchaseDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var delayDaysText by rememberSaveable { mutableStateOf("3") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    GlassCard(tint = GlassTint.Cyan, surfaceStyle = GlassSurfaceStyle.Standard) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "What if?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlassTokens.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Runs the same forecast engine as your dashboard twice — once for the plan you have, " +
                    "once for the change you are considering — and compares the two results.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextSecondary,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    inputError = null
                },
                label = { Text("Purchase amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            ScheduleDatePickerField(
                label = "Purchase date",
                dateText = purchaseDateText,
                onDateSelected = { purchaseDateText = it },
            )
            AppNeutralButton(
                text = "Can I afford this?",
                onClick = {
                    val parsed = parseDollars(amountText)
                    val date = runCatching { LocalDate.parse(purchaseDateText) }.getOrNull()
                    when {
                        parsed !is DollarParseResult.Valid ->
                            inputError = "Enter an amount like 250 or 250.00."
                        parsed.cents <= 0L -> inputError = "Enter an amount greater than zero."
                        date == null -> inputError = "Pick a purchase date."
                        else -> {
                            inputError = null
                            onCheckPurchase(parsed.cents, date)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            purchaseSimulation?.let { simulation ->
                ComparisonResult(
                    verdict = ScenarioComparisonText.verdict(simulation),
                    rows = ScenarioComparisonText.rows(simulation),
                )
            }

            HorizontalDivider(color = GlassTokens.DividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = delayDaysText,
                    onValueChange = {
                        delayDaysText = it
                        inputError = null
                    },
                    label = { Text("Paycheck delay (days)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                AppNeutralButton(
                    text = "What if it's late?",
                    onClick = {
                        val days = delayDaysText.trim().toIntOrNull()
                        if (days == null || days < 0) {
                            inputError = "Enter a delay in whole days (0 or more)."
                        } else {
                            inputError = null
                            onCheckPaycheckDelay(days)
                        }
                    },
                )
            }

            paycheckDelaySimulation?.let { simulation ->
                ComparisonResult(
                    verdict = ScenarioComparisonText.verdict(simulation),
                    rows = ScenarioComparisonText.rows(simulation),
                )
            }

            (inputError ?: error)?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.ErrorRed,
                )
            }
        }
    }
}

@Composable
private fun ComparisonResult(verdict: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            verdict,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = GlassTokens.TextPrimary,
        )
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.TextDim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
