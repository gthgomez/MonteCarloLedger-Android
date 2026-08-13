package com.montecarlo.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.montecarlo.ledger.GlassTokens

@Composable
fun AppBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = "MonteCarlo Ledger",
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Canvas(modifier = modifier.then(semanticsModifier)) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f

        // The icon stacks three faceted diamonds. We mirror that hierarchy here so the
        // runtime UI feels like a scaled-up version of the launcher mark.
        drawFacetLayer(
            center = Offset(centerX, height * 0.68f),
            width = width * 0.50f,
            height = height * 0.23f,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassTokens.Violet,
                    GlassTokens.VioletDeep,
                )
            ),
            edgeColor = Color.Black.copy(alpha = 0.14f),
        )
        drawFacetLayer(
            center = Offset(centerX, height * 0.50f),
            width = width * 0.50f,
            height = height * 0.23f,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassTokens.Cyan,
                    Color(0xFF0E7490),
                )
            ),
            edgeColor = Color.Black.copy(alpha = 0.10f),
        )
        drawFacetLayer(
            center = Offset(centerX, height * 0.32f),
            width = width * 0.50f,
            height = height * 0.23f,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassTokens.CyanBright,
                    GlassTokens.Cyan,
                )
            ),
            edgeColor = Color.White.copy(alpha = 0.18f),
            highlightColor = Color.White.copy(alpha = 0.24f),
        )
    }
}

@Composable
fun AppBrandBackdrop(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.08f)
        ) {
            val glowTop = Brush.radialGradient(
                colors = listOf(
                    GlassTokens.CyanBright.copy(alpha = GlassTokens.GlowCyanAlpha),
                    Color.Transparent,
                )
            )
            val glowBottom = Brush.radialGradient(
                colors = listOf(
                    GlassTokens.Indigo.copy(alpha = GlassTokens.GlowVioletAlpha),
                    Color.Transparent,
                )
            )

            drawCircle(
                brush = glowTop,
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.10f, size.height * 0.14f),
            )
            drawCircle(
                brush = glowBottom,
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.92f, size.height * 0.84f),
            )
        }
        AppBrandMark(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.10f)
        )
    }
}

private fun DrawScope.drawFacetLayer(
    center: Offset,
    width: Float,
    height: Float,
    brush: Brush,
    edgeColor: Color,
    highlightColor: Color = Color.Transparent,
) {
    val halfWidth = width / 2f
    val halfHeight = height / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - halfHeight)
        lineTo(center.x + halfWidth, center.y)
        lineTo(center.x, center.y + halfHeight)
        lineTo(center.x - halfWidth, center.y)
        close()
    }

    drawPath(path = path, brush = brush)
    drawPath(path = path, color = edgeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.25f))

    if (highlightColor.alpha > 0f) {
        val highlight = Path().apply {
            moveTo(center.x - halfWidth * 0.52f, center.y - halfHeight * 0.10f)
            lineTo(center.x, center.y - halfHeight * 0.56f)
            lineTo(center.x + halfWidth * 0.52f, center.y - halfHeight * 0.10f)
            lineTo(center.x, center.y + halfHeight * 0.10f)
            close()
        }
        drawPath(path = highlight, color = highlightColor)
    }
}
