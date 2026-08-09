package com.montecarlo.ledger

import androidx.compose.runtime.Composable

// GlassTokens is now provided by the shared DesignSystem library.
typealias GlassTokens = com.workspace.design.GlassTokens

// AppTheme is now provided by the shared DesignSystem library.
@Composable
fun AppTheme(content: @Composable () -> Unit) =
    com.workspace.design.AppTheme(content = content)
