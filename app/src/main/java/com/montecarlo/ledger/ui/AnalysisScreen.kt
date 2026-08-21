package com.montecarlo.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.RecurringCandidate
import dev.chrisbanes.haze.HazeState

@Composable
fun AnalysisScreen(
    viewModel: MainViewModel,
    hazeState: HazeState? = null,
    onTrackAsBill: (RecurringCandidate) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingRule by remember { mutableStateOf<Pair<String, String>?>(null) }
    var expandedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    AnalysisContent(
        uiState = uiState,
        hazeState = hazeState,
        onCreateRule = { description, category -> pendingRule = description to category },
        onTrackAsBill = onTrackAsBill,
        expandedCategory = expandedCategory,
        onToggleCategory = { category ->
            expandedCategory = if (expandedCategory == category) null else category
        },
    )
    pendingRule?.let { (description, category) ->
        TransactionRuleConfirmationDialog(
            description = description,
            category = category,
            onConfirm = {
                viewModel.saveTransactionRule(description, category, applyRetroactively = true)
                pendingRule = null
            },
            onDismiss = { pendingRule = null },
        )
    }
}

@Composable
fun AnalysisContent(
    uiState: AppUiState,
    hazeState: HazeState? = null,
    onCreateRule: (String, String) -> Unit = { _, _ -> },
    onTrackAsBill: (RecurringCandidate) -> Unit = {},
    expandedCategory: String? = null,
    onToggleCategory: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Analysis",
                    style = MaterialTheme.typography.headlineSmall,
                    color = GlassTokens.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Everything you need to understand spending and the next 90 days lives here on one scroll.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary
                )
            }
        }

        analysisInsightsSection(
            uiState = uiState,
            onCreateRule = onCreateRule,
            onTrackAsBill = onTrackAsBill,
            expandedCategory = expandedCategory,
            onToggleCategory = onToggleCategory,
        )

        item {
            Text(
                "Forecast",
                style = MaterialTheme.typography.titleLarge,
                color = GlassTokens.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }

        forecastSection(rows = uiState.forecastRows, hazeState = hazeState)

        item { Spacer(Modifier.height(16.dp)) }
    }
}
