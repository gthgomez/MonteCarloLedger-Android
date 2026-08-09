package com.example.app.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app.AppUiState
import com.example.app.GlassTokens
import com.example.app.MainViewModel
import dev.chrisbanes.haze.HazeState

@Composable
fun AnalysisScreen(viewModel: MainViewModel, hazeState: HazeState? = null) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalysisContent(uiState = uiState, hazeState = hazeState)
}

@Composable
fun AnalysisContent(uiState: AppUiState, hazeState: HazeState? = null) {
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
            onCreateRule = { _, _ -> },
            onTrackAsBill = { _, _, _ -> },
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
