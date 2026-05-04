package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.GlassTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

// Tint variants map to the brand hierarchy: cyan leads, violet adds depth.
enum class GlassTint {
    Neutral,  // white glass — general purpose
    Cyan,     // cyan tint — income / positive
    Violet,   // blue-violet tint — bills / outflow
    Teal,     // teal tint — forecast / analytics
    Error,    // red tint — overdue / negative
}

enum class GlassSurfaceStyle {
    Hero,      // cornerRadius=20dp, prominent — alpha 0.52, blur 24dp
    Standard,  // cornerRadius=16dp, normal    — alpha 0.64, blur 20dp
    Quiet,     // cornerRadius=12dp, recessed  — alpha 0.78, blur 12dp
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: GlassTint = GlassTint.Neutral,
    surfaceStyle: GlassSurfaceStyle = GlassSurfaceStyle.Standard,
    cornerRadius: Dp = when (surfaceStyle) {
        GlassSurfaceStyle.Hero -> 20.dp
        GlassSurfaceStyle.Standard -> 16.dp
        GlassSurfaceStyle.Quiet -> 12.dp
    },
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius)
    val isDarkBackdrop = scheme.background.luminance() < 0.45f

    val baseSurface = when (tint) {
        GlassTint.Neutral -> scheme.surfaceContainerLow
        GlassTint.Cyan    -> scheme.primaryContainer
        GlassTint.Violet  -> scheme.secondaryContainer
        GlassTint.Teal    -> scheme.tertiaryContainer
        GlassTint.Error   -> scheme.errorContainer
    }

    val surfaceAlpha = when (surfaceStyle) {
        GlassSurfaceStyle.Hero -> if (isDarkBackdrop) 0.44f else 0.56f
        GlassSurfaceStyle.Standard -> if (isDarkBackdrop) 0.56f else 0.68f
        GlassSurfaceStyle.Quiet -> if (isDarkBackdrop) 0.68f else 0.80f
    }

    val blurRadius = when (surfaceStyle) {
        GlassSurfaceStyle.Hero     -> 22.dp
        GlassSurfaceStyle.Standard -> 16.dp
        GlassSurfaceStyle.Quiet    -> 10.dp
    }

    val strokeAlpha = when (tint) {
        GlassTint.Neutral -> when (surfaceStyle) {
            GlassSurfaceStyle.Hero     -> if (isDarkBackdrop) 0.24f else 0.18f
            GlassSurfaceStyle.Standard -> if (isDarkBackdrop) 0.18f else 0.14f
            GlassSurfaceStyle.Quiet    -> if (isDarkBackdrop) 0.14f else 0.10f
        }
        GlassTint.Cyan -> when (surfaceStyle) {
            GlassSurfaceStyle.Hero     -> if (isDarkBackdrop) 0.28f else 0.22f
            GlassSurfaceStyle.Standard -> if (isDarkBackdrop) 0.20f else 0.16f
            GlassSurfaceStyle.Quiet    -> if (isDarkBackdrop) 0.14f else 0.11f
        }
        GlassTint.Violet -> when (surfaceStyle) {
            GlassSurfaceStyle.Hero     -> if (isDarkBackdrop) 0.20f else 0.16f
            GlassSurfaceStyle.Standard -> if (isDarkBackdrop) 0.14f else 0.11f
            GlassSurfaceStyle.Quiet    -> if (isDarkBackdrop) 0.09f else 0.08f
        }
        GlassTint.Teal -> when (surfaceStyle) {
            GlassSurfaceStyle.Hero     -> if (isDarkBackdrop) 0.24f else 0.18f
            GlassSurfaceStyle.Standard -> if (isDarkBackdrop) 0.16f else 0.12f
            GlassSurfaceStyle.Quiet    -> if (isDarkBackdrop) 0.10f else 0.08f
        }
        GlassTint.Error -> when (surfaceStyle) {
            GlassSurfaceStyle.Hero     -> if (isDarkBackdrop) 0.30f else 0.24f
            GlassSurfaceStyle.Standard -> if (isDarkBackdrop) 0.22f else 0.16f
            GlassSurfaceStyle.Quiet    -> if (isDarkBackdrop) 0.16f else 0.12f
        }
    }

    val strokeColor = when (tint) {
        GlassTint.Neutral -> scheme.outlineVariant.copy(alpha = strokeAlpha)
        GlassTint.Cyan    -> scheme.primary.copy(alpha = strokeAlpha)
        GlassTint.Violet  -> scheme.secondary.copy(alpha = strokeAlpha)
        GlassTint.Teal    -> scheme.tertiary.copy(alpha = strokeAlpha)
        GlassTint.Error   -> scheme.error.copy(alpha = strokeAlpha)
    }

    val backgroundColor = baseSurface.copy(
        alpha = if (hazeState != null) surfaceAlpha else (surfaceAlpha + 0.04f).coerceAtMost(0.92f)
    )

    // Hero shimmer: horizontal gradient along the top edge to simulate a light source above the glass
    val isHero = surfaceStyle == GlassSurfaceStyle.Hero
    val shimmerBrush = Brush.horizontalGradient(
        colors = listOf(
            GlassTokens.ShimmerEdge,
            GlassTokens.ShimmerMid,
            GlassTokens.ShimmerEdge,
        )
    )

    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (hazeState != null) {
                // HazeTint is set to Transparent — the backgroundColor handles the tint.
                // Passing the same color as both backgroundColor AND HazeTint doubles the
                // opacity and produces muddy/over-darkened surfaces.
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = backgroundColor,
                        tint = HazeTint(Color.Transparent),
                        blurRadius = blurRadius,
                    )
                )
            } else {
                Modifier.background(color = backgroundColor)
            }
        )
        .border(width = 1.dp, color = strokeColor, shape = shape)
        .then(
            if (isHero) {
                Modifier.drawWithContent {
                    drawContent()
                    drawRect(
                        brush = shimmerBrush,
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                    )
                }
            } else Modifier
        )
        .padding(16.dp)

    Box(modifier = base, content = content)
}
