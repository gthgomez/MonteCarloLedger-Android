package com.workspace.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ErrorInline(
    message: String,
    modifier: Modifier = Modifier,
) {
    if (message.isNotBlank()) {
        Text(
            text = message,
            color = GlassTokens.ErrorRed,
            modifier = modifier
        )
    }
}
