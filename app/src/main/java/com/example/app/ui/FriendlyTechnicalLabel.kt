package com.example.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.app.GlassTokens

@Composable
fun FriendlyTechnicalLabel(
    friendly: String,
    technical: String,
    showTechnical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            friendly,
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showTechnical) {
            Text(
                "($technical)",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
