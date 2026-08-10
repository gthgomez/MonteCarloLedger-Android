package com.montecarlo.ledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

@Composable
fun SolidListSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

internal fun Modifier.minimumIconButtonTouchTarget(): Modifier =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)
