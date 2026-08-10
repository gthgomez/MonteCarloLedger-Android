package com.montecarlo.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.GlassTokens
import com.montecarlo.ledger.processing.MonteCarloResult
import com.montecarlo.ledger.util.centsToDisplay

data class FanChartPoint(
    val dayIndex: Int,
    val dateLabel: String,
    val worst10Cents: Long,
    val medianCents: Long,
    val best90Cents: Long,
)

@Composable
fun LegendBadge(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.height(8.dp).padding(end = 4.dp)) {
            drawCircle(color = color, radius = 4.dp.toPx())
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.TextDim,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonteCarloFanChart(
    points: List<FanChartPoint>,
    result: MonteCarloResult?,
    modifier: Modifier = Modifier,
    onPointSelected: ((FanChartPoint?) -> Unit)? = null,
    onViewDailyValuesClicked: (() -> Unit)? = null,
) {
    if (points.isEmpty()) return

    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    val selectedPoint = selectedPointIndex?.let { index -> points.getOrNull(index) }

    val minVal = points.minOf { minOf(it.worst10Cents, it.medianCents, it.best90Cents) }.coerceAtMost(0L)
    val maxVal = points.maxOf { maxOf(it.worst10Cents, it.medianCents, it.best90Cents) }.coerceAtLeast(10_000L)
    val valRange = (maxVal - minVal).toFloat().coerceAtLeast(1f)
    val medianLineColor = MaterialTheme.colorScheme.onSurface

    val chartSummary = remember(points, result) {
        val first = points.first()
        val last = points.last()
        "90-Day stochastic forecast chart starting at ${centsToDisplay(first.medianCents)} and ending at ${centsToDisplay(last.medianCents)}. Risk of overdraft: ${result?.probability_negative_pct ?: 0.0}%."
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartSummary },
        tint = if (result != null && result.probability_negative_pct > 0) GlassTint.Error else GlassTint.Teal,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "90-Day Cash Trajectory & Risk Band",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTokens.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Monte Carlo 500-Run Stochastic Projection",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim,
                    )
                }

                if (result != null) {
                    val riskColor = if (result.probability_negative_pct > 0) GlassTokens.ErrorRed else GlassTokens.PositiveGreen
                    Text(
                        "${String.format("%.1f", result.probability_negative_pct)}% Overdraft Risk",
                        style = MaterialTheme.typography.titleSmall,
                        color = riskColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Responsive legend indicators
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LegendBadge(label = "90th (Best)", color = GlassTokens.CyanBright)
                LegendBadge(label = "50th (Typical)", color = medianLineColor)
                LegendBadge(label = "10th (Worst)", color = GlassTokens.ErrorRed)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                val index = (offset.x / stepX).toInt().coerceIn(0, points.size - 1)
                                selectedPointIndex = index
                                onPointSelected?.invoke(points.getOrNull(index))
                            }
                        }
                        .pointerInput(points) {
                            detectDragGestures { change, _ ->
                                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                val index = (change.position.x / stepX).toInt().coerceIn(0, points.size - 1)
                                selectedPointIndex = index
                                onPointSelected?.invoke(points.getOrNull(index))
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val stepX = width / (points.size - 1).coerceAtLeast(1)

                    fun getY(value: Long): Float {
                        val normalized = (value - minVal) / valRange
                        return height - (normalized * height)
                    }

                    // Zero line
                    if (minVal < 0) {
                        val zeroY = getY(0L)
                        drawLine(
                            color = GlassTokens.ErrorRed.copy(alpha = 0.5f),
                            start = Offset(0f, zeroY),
                            end = Offset(width, zeroY),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    // Shaded confidence interval band (between 10th and 90th percentile)
                    val bandPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y90 = getY(pt.best90Cents)
                        if (i == 0) bandPath.moveTo(x, y90) else bandPath.lineTo(x, y90)
                    }
                    for (i in points.indices.reversed()) {
                        val pt = points[i]
                        val x = i * stepX
                        val y10 = getY(pt.worst10Cents)
                        bandPath.lineTo(x, y10)
                    }
                    bandPath.close()

                    drawPath(
                        path = bandPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GlassTokens.CyanBright.copy(alpha = 0.25f),
                                GlassTokens.ErrorRed.copy(alpha = 0.15f),
                            )
                        )
                    )

                    // 90th percentile line (cyan dotted)
                    val path90 = Path()
                    points.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = getY(pt.best90Cents)
                        if (i == 0) path90.moveTo(x, y) else path90.lineTo(x, y)
                    }
                    drawPath(
                        path90,
                        color = GlassTokens.CyanBright,
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
                    )

                    // 10th percentile line (red dashed)
                    val path10 = Path()
                    points.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = getY(pt.worst10Cents)
                        if (i == 0) path10.moveTo(x, y) else path10.lineTo(x, y)
                    }
                    drawPath(
                        path10,
                        color = GlassTokens.ErrorRed,
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f))
                    )

                    // 50th median line (theme-aware solid)
                    val path50 = Path()
                    points.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = getY(pt.medianCents)
                        if (i == 0) path50.moveTo(x, y) else path50.lineTo(x, y)
                    }
                    drawPath(path50, color = medianLineColor, style = Stroke(width = 3f))

                    // Crosshair touch indicator
                    selectedPointIndex?.let { idx ->
                        val x = idx * stepX
                        drawLine(
                            color = GlassTokens.CyanBright,
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 2f,
                        )
                        val selectedY = getY(points[idx].medianCents)
                        drawCircle(
                            color = GlassTokens.CyanBright,
                            radius = 6.dp.toPx(),
                            center = Offset(x, selectedY)
                        )
                    }
                }
            }

            val selectedPointDescription = selectedPoint?.let { pt ->
                "Selected Day ${pt.dayIndex}, ${pt.dateLabel}. 10th percentile worst: ${centsToDisplay(pt.worst10Cents)}, 50th percentile typical: ${centsToDisplay(pt.medianCents)}, 90th percentile best: ${centsToDisplay(pt.best90Cents)}."
            } ?: "No day selected. Drag or tap on the chart to inspect daily cash projections."

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = selectedPointDescription
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedPoint != null) {
                    Text(
                        "Day ${selectedPoint.dayIndex} (${selectedPoint.dateLabel}):",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.TextSecondary,
                    )
                    Text(
                        "10th: ${centsToDisplay(selectedPoint.worst10Cents)} | 50th: ${centsToDisplay(selectedPoint.medianCents)} | 90th: ${centsToDisplay(selectedPoint.best90Cents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        "Drag or tap on the chart to inspect daily cash projections.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextDim,
                    )
                }
            }

            if (onViewDailyValuesClicked != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onViewDailyValuesClicked,
                    ) {
                        Text("View daily projection values", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
