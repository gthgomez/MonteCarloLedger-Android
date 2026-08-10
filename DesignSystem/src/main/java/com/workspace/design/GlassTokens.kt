package com.workspace.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

object GlassTokens {
    // Borders — theme-aware so glass stays crisp in both dark and light modes.
    var CardBorder by mutableStateOf(Color.White.copy(alpha = 0.20f))
    var CardBorderDim by mutableStateOf(Color.White.copy(alpha = 0.12f))

    // Text — flipped by AppTheme so light mode does not collapse into washed-out white.
    var TextPrimary by mutableStateOf(Color.White.copy(alpha = 0.95f))
    var TextSecondary by mutableStateOf(Color.White.copy(alpha = 0.66f))
    var TextDim by mutableStateOf(Color.White.copy(alpha = 0.50f))
    var DividerColor by mutableStateOf(Color.White.copy(alpha = 0.14f))

    // Brand accents
    val Cyan = Color(0xFF06B6D4)
    val CyanBright = Color(0xFF67E8F9)
    val Violet = Color(0xFF7C3AED)
    val VioletLight = Color(0xFFA78BFA)
    val VioletSoft = Color(0xFF8B5CF6)
    val VioletDeep = Color(0xFF4C1D95)
    val Indigo = Color(0xFF6366F1)
    val Teal = Color(0xFF14B8A6)
    val TealDeep = Color(0xFF0F766E)
    val ErrorRed = Color(0xFFFF6B6B)
    val PositiveGreen = Color(0xFF4ADE80)

    // Top-edge shimmer stops for Hero cards
    var ShimmerEdge by mutableStateOf(Color.White.copy(alpha = 0.22f))
    var ShimmerMid by mutableStateOf(Color.White.copy(alpha = 0.06f))

    // Ambient background glow alphas
    var GlowCyanAlpha by mutableStateOf(0.24f)
    var GlowVioletAlpha by mutableStateOf(0.18f)
    var GlowTealAlpha by mutableStateOf(0.10f)

    // Nav indicator pill
    var NavIndicator by mutableStateOf(Color(0xFF22D3EE).copy(alpha = 0.24f))
    var NavBorderTop by mutableStateOf(Color.White.copy(alpha = 0.14f))

    fun applyForTheme(darkTheme: Boolean) {
        if (darkTheme) {
            CardBorder = Color.White.copy(alpha = 0.20f)
            CardBorderDim = Color.White.copy(alpha = 0.12f)
            TextPrimary = Color.White.copy(alpha = 0.95f)
            TextSecondary = Color.White.copy(alpha = 0.66f)
            TextDim = Color.White.copy(alpha = 0.50f)
            DividerColor = Color.White.copy(alpha = 0.14f)
            ShimmerEdge = Color.White.copy(alpha = 0.22f)
            ShimmerMid = Color.White.copy(alpha = 0.06f)
            GlowCyanAlpha = 0.24f
            GlowVioletAlpha = 0.10f
            GlowTealAlpha = 0.14f
            NavIndicator = Color(0xFF22D3EE).copy(alpha = 0.24f)
            NavBorderTop = Color.White.copy(alpha = 0.14f)
        } else {
            CardBorder = Color(0xFF9FB0C7).copy(alpha = 0.34f)
            CardBorderDim = Color(0xFF94A3B8).copy(alpha = 0.18f)
            TextPrimary = Color(0xFF0F172A)
            TextSecondary = Color(0xFF334155)
            TextDim = Color(0xFF64748B)
            DividerColor = Color(0xFFCBD5E1).copy(alpha = 0.90f)
            ShimmerEdge = Color.White.copy(alpha = 0.42f)
            ShimmerMid = Color.White.copy(alpha = 0.12f)
            GlowCyanAlpha = 0.14f
            GlowVioletAlpha = 0.05f
            GlowTealAlpha = 0.08f
            NavIndicator = Color(0xFF22D3EE).copy(alpha = 0.18f)
            NavBorderTop = Color(0xFF94A3B8).copy(alpha = 0.24f)
        }
    }
}

private val BrandGlassDark = darkColorScheme(
    primary = GlassTokens.CyanBright,
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF155E75),
    onPrimaryContainer = GlassTokens.TextPrimary,
    secondary = GlassTokens.Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = GlassTokens.TextPrimary,
    tertiary = GlassTokens.Teal,
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = GlassTokens.TextPrimary,
    background = Color(0xFF0B1120),
    onBackground = GlassTokens.TextPrimary,
    surface = Color(0xFF111827),
    onSurface = GlassTokens.TextPrimary,
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = GlassTokens.TextSecondary,
    surfaceTint = GlassTokens.CyanBright,
    surfaceContainerLowest = Color(0xFF0F172A),
    surfaceContainerLow = Color(0xFF111827),
    surfaceContainer = Color(0xFF172033),
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerHighest = Color(0xFF263244),
    outline = Color.White.copy(alpha = 0.20f),
    outlineVariant = Color.White.copy(alpha = 0.10f),
    error = GlassTokens.ErrorRed,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color.White,
    onError = Color.White,
)

private val BrandGlassLight = lightColorScheme(
    primary = GlassTokens.Cyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F8FD),
    onPrimaryContainer = Color(0xFF083344),
    secondary = GlassTokens.Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = GlassTokens.TealDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F7F3),
    onTertiaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    surfaceTint = GlassTokens.Cyan,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = Color(0xFFF1F5FA),
    surfaceContainerHigh = Color(0xFFE8EEF6),
    surfaceContainerHighest = Color(0xFFDDE6F0),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = GlassTokens.ErrorRed,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    onError = Color.White,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = if (darkTheme) BrandGlassDark else BrandGlassLight
    SideEffect {
        GlassTokens.applyForTheme(darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
