package com.example.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.app.GlassTokens

// Primary CTA — Cyan glass fill with white edge highlight
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = UiLayoutTokens.ActionButtonMinHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary.copy(alpha = 0.86f),
            contentColor = colorScheme.onPrimary
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// Neutral action — glass ghost with visible border so it reads as a distinct tap target
@Composable
fun AppNeutralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = UiLayoutTokens.ActionButtonMinHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.surfaceContainerHigh.copy(alpha = 0.68f),
            contentColor = GlassTokens.TextPrimary
        ),
        border = BorderStroke(1.dp, GlassTokens.CardBorderDim),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// Success/positive action — green glass fill
@Composable
fun AppSuccessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = UiLayoutTokens.ActionButtonMinHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlassTokens.PositiveGreen.copy(alpha = 0.86f),
            contentColor = colorScheme.onPrimary
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// Destructive action — red glass fill used on delete/error flows
@Composable
fun AppDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = UiLayoutTokens.ActionButtonMinHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlassTokens.ErrorRed.copy(alpha = 0.82f),
            contentColor = colorScheme.onPrimary
        ),
        border = BorderStroke(1.dp, GlassTokens.ErrorRed.copy(alpha = 0.42f)),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
