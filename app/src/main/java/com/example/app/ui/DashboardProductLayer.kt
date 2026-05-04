package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.app.ActionCenterState
import com.example.app.DashboardPrimaryAction
import com.example.app.GlassTokens
import com.example.app.MoneyBucketAccent
import com.example.app.MoneyBucketState
import com.example.app.TransactionReviewItem
import com.example.app.TrustSignal
import com.example.app.TrustSignalLevel
import com.example.app.data.TransactionEntity
import kotlin.math.abs

@Composable
internal fun DashboardActionCenterCard(
    state: ActionCenterState,
    onPrimaryAction: (DashboardPrimaryAction) -> Unit,
) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = if (state.safeToSpendCents < 0) GlassTint.Error else GlassTint.Cyan,
        surfaceStyle = GlassSurfaceStyle.Hero,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTokens.TextSecondary,
                    )
                    Text(
                        formatCurrency(state.safeToSpendCents),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (state.safeToSpendCents < 0) GlassTokens.ErrorRed else GlassTokens.CyanBright,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Safe to spend",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.TextDim,
                    )
                }
                AppPrimaryButton(
                    text = state.primaryActionLabel,
                    onClick = { onPrimaryAction(state.primaryAction) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionMetric(
                    label = "Needs review",
                    value = state.needsReviewCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                ActionMetric(
                    label = "Next bill",
                    value = state.nextBillLabel,
                    modifier = Modifier.weight(1f),
                )
                ActionMetric(
                    label = "Forecast risk",
                    value = state.forecastRiskLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTokens.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TransactionReviewInboxCard(
    items: List<TransactionReviewItem>,
    onApprove: (Int) -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onCreateRule: (Int, String) -> Unit,
) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = if (items.isEmpty()) GlassTint.Neutral else GlassTint.Teal,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(
                title = "Transaction review",
                detail = if (items.isEmpty()) "Nothing waiting" else "${items.size} entries need a look",
            )
            if (items.isEmpty()) {
                Text(
                    "Imported and newly categorized transactions will appear here before they affect your habits and rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.TextSecondary,
                )
            } else {
                items.take(3).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = GlassTokens.DividerColor)
                    ReviewRow(
                        item = item,
                        onApprove = onApprove,
                        onEdit = onEdit,
                        onCreateRule = onCreateRule,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(
    item: TransactionReviewItem,
    onApprove: (Int) -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onCreateRule: (Int, String) -> Unit,
) {
    var category by remember(item.transaction.id, item.suggestedCategory) {
        mutableStateOf(item.suggestedCategory)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.transaction.description,
                    style = MaterialTheme.typography.titleSmall,
                    color = GlassTokens.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.reason} • ${item.transaction.date.formatDateDisplay()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTokens.TextDim,
                )
            }
            Text(
                formatCurrency(item.transaction.amount_cents),
                style = MaterialTheme.typography.titleSmall,
                color = GlassTokens.ErrorRed,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category or rule") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppNeutralButton(
                text = "Approve",
                onClick = { onApprove(item.transaction.id) },
                modifier = Modifier.weight(1f),
            )
            AppNeutralButton(
                text = "Edit",
                onClick = { onEdit(item.transaction) },
                modifier = Modifier.weight(1f),
            )
            AppPrimaryButton(
                text = "Rule",
                onClick = { onCreateRule(item.transaction.id, category) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun MoneyBucketsCard(buckets: List<MoneyBucketState>) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(title = "Money buckets", detail = "What is already spoken for")
            buckets.forEachIndexed { index, bucket ->
                if (index > 0) HorizontalDivider(color = GlassTokens.DividerColor)
                BucketRow(bucket)
            }
        }
    }
}

@Composable
private fun BucketRow(bucket: MoneyBucketState) {
    val color = when (bucket.accent) {
        MoneyBucketAccent.Bills -> GlassTokens.VioletLight
        MoneyBucketAccent.Goals -> GlassTokens.PositiveGreen
        MoneyBucketAccent.Available -> GlassTokens.CyanBright
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(bucket.label, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextPrimary)
            Text(formatCurrency(bucket.amountCents), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { bucket.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape),
            color = color,
            trackColor = GlassTokens.DividerColor,
        )
        Text(bucket.detail, style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
    }
}

@Composable
internal fun TrustLayerCard(signals: List<TrustSignal>) {
    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Neutral,
        surfaceStyle = GlassSurfaceStyle.Quiet,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(title = "Trust layer", detail = "Data, privacy, and assumptions")
            signals.forEachIndexed { index, signal ->
                if (index > 0) HorizontalDivider(color = GlassTokens.DividerColor)
                TrustRow(signal)
            }
        }
    }
}

@Composable
private fun TrustRow(signal: TrustSignal) {
    val color = when (signal.level) {
        TrustSignalLevel.Good -> GlassTokens.PositiveGreen
        TrustSignalLevel.Attention -> GlassTokens.CyanBright
        TrustSignalLevel.Warning -> GlassTokens.ErrorRed
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(signal.label, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextPrimary)
            Text(signal.detail, style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
        }
        Text(signal.value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = GlassTokens.TextPrimary, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
    }
}

private fun formatCurrency(cents: Int): String {
    val sign = if (cents < 0) "-" else ""
    return "$sign\$${String.format("%.2f", abs(cents) / 100.0)}"
}
