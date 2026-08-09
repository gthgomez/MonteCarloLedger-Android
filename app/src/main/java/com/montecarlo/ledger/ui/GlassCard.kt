package com.montecarlo.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

// Glass components are now provided by the shared DesignSystem library.
// Re-export via typealias so existing imports don't break.
typealias GlassTint = com.workspace.design.GlassTint
typealias GlassSurfaceStyle = com.workspace.design.GlassSurfaceStyle

// GlassCard is a @Composable function — re-export via wrapper so same-package callers still work.
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: GlassTint = com.workspace.design.GlassTint.Neutral,
    surfaceStyle: GlassSurfaceStyle = com.workspace.design.GlassSurfaceStyle.Standard,
    cornerRadius: Dp = when (surfaceStyle) {
        com.workspace.design.GlassSurfaceStyle.Hero -> 20.dp
        com.workspace.design.GlassSurfaceStyle.Standard -> 16.dp
        com.workspace.design.GlassSurfaceStyle.Quiet -> 12.dp
    },
    hazeState: HazeState? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) = com.workspace.design.GlassCard(
    modifier = modifier,
    tint = tint,
    surfaceStyle = surfaceStyle,
    cornerRadius = cornerRadius,
    hazeState = hazeState,
    contentPadding = contentPadding,
    content = content
)
