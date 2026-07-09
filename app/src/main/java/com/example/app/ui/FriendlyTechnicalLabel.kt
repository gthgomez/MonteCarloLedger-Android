package com.example.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// FriendlyTechnicalLabel is now provided by the shared DesignSystem library.
// Re-export via wrapper function so same-package callers still work.

@Composable
fun FriendlyTechnicalLabel(
    friendly: String,
    technical: String,
    showTechnical: Boolean = false,
    modifier: Modifier = Modifier,
) = com.workspace.design.FriendlyTechnicalLabel(friendly, technical, showTechnical, modifier)
