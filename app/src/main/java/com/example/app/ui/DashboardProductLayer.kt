package com.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.example.app.processing.CategoryBudgetRow
import com.example.app.util.centsToDisplay


@Composable
internal fun DashboardActionCenterCard(
    state: ActionCenterState,
    onPrimaryAction: (DashboardPrimaryAction) -> Unit,
    onApplyRecommendation: ((com.example.app.processing.OverdraftRecommendation) -> Unit)? = null,
) {
    var showAllRecs by rememberSaveable { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier
            .heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight)
            .semantics {
                stateDescription = "Primary action: ${state.primaryActionLabel}. Risk level: ${state.forecastRiskLabel}"
            },
        tint = when {
            !state.forecastUnlocked -> GlassTint.Cyan
            state.safeToSpendCents < 0 -> GlassTint.Error
            else -> GlassTint.Cyan
        },
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
                    if (!state.forecastUnlocked) {
                        Text(
                            "Confirm balance",
                            style = MaterialTheme.typography.headlineSmall,
                            color = GlassTokens.CyanBright,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Unlock trusted safe-to-spend",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextDim,
                        )
                    } else {
                        Text(
                            centsToDisplay(state.safeToSpendCents),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.safeToSpendCents < 0) GlassTokens.ErrorRed else GlassTokens.CyanBright,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            state.safeToSpendCaption,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextDim,
                        )
                    }
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

            if (state.overdraftRecommendations.isNotEmpty()) {
                HorizontalDivider(color = GlassTokens.DividerColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Proactive Action Recommendations (${state.overdraftRecommendations.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextSecondary,
                    )
                    if (state.overdraftRecommendations.size > 1) {
                        TextButton(
                            onClick = { showAllRecs = !showAllRecs },
                        ) {
                            Text(
                                if (showAllRecs) "Show less" else "+${state.overdraftRecommendations.size - 1} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTokens.CyanBright,
                            )
                        }
                    }
                }

                val visibleRecs = if (showAllRecs || state.overdraftRecommendations.size == 1) {
                    state.overdraftRecommendations
                } else {
                    state.overdraftRecommendations.take(1)
                }

                visibleRecs.forEach { rec ->
                    when (rec) {
                        is com.example.app.processing.OverdraftRecommendation.RescheduleBill -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Move ${rec.billName} (${centsToDisplay(rec.amountCents.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = GlassTokens.TextPrimary
                                        )
                                        Text(
                                            "Shift from ${rec.currentDueDate} to ${rec.suggestedDueDate} (after payday) → Drops risk to 0%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GlassTokens.TextSecondary
                                        )
                                    }
                                    if (rec.occurrenceId != null && onApplyRecommendation != null) {
                                        Button(
                                            onClick = { onApplyRecommendation(rec) },
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text("Shift Date", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        is com.example.app.processing.OverdraftRecommendation.CapDailySpend -> {
                            Text(
                                "💡 Spending Pace: Cap daily spending at ${centsToDisplay(rec.suggestedDailyCapCents)}/day to cover the ${centsToDisplay(rec.deficitCents)} dip.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.CyanBright
                            )
                        }
                        is com.example.app.processing.OverdraftRecommendation.TransferFromAsset -> {
                            Text(
                                "🏦 Emergency Transfer: ${centsToDisplay(rec.suggestedTransferCents)} from ${rec.assetName} available to bridge shortfall.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.CyanBright
                            )
                        }
                    }
                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Transaction review",
                    detail = if (items.isEmpty()) "Nothing waiting" else "${items.size} entries need a look",
                )
            }
            if (items.isNotEmpty() && items.size > 1) {
                AppNeutralButton(
                    text = "Approve all (${items.size})",
                    onClick = {
                        items.forEach { onApprove(it.transaction.id) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                centsToDisplay(item.transaction.amount_cents),
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
    val color = when {
        bucket.amountCents < 0 -> GlassTokens.ErrorRed
        bucket.accent == MoneyBucketAccent.Bills -> GlassTokens.VioletLight
        bucket.accent == MoneyBucketAccent.Goals -> GlassTokens.PositiveGreen
        else -> GlassTokens.CyanBright
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(bucket.label, style = MaterialTheme.typography.bodyMedium, color = GlassTokens.TextPrimary)
            Text(centsToDisplay(bucket.amountCents), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
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

@Composable
internal fun OverLimitCategoriesCard(rows: List<CategoryBudgetRow>) {
    val overLimit = rows.filter { it.overLimit }
    if (overLimit.isEmpty()) return

    GlassCard(
        modifier = Modifier.heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight),
        tint = GlassTint.Error,
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(
                title = "Over budget",
                detail = "${overLimit.size} categor${if (overLimit.size == 1) "y" else "ies"} over limit",
            )
            overLimit.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = GlassTokens.DividerColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.category.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.TextPrimary,
                    )
                    Text(
                        "${centsToDisplay(row.spentCents)} / ${centsToDisplay(row.limitCents)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTokens.ErrorRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BalanceDriftBanner(
    driftCents: Int,
    ledgerBalanceCents: Int,
    bankBalanceCents: Int,
    onReconcile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val absDrift = kotlin.math.abs(driftCents)
    val direction = if (driftCents > 0) "above" else "below"
    GlassCard(
        modifier = modifier
            .heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight)
            .semantics {
                stateDescription = "Balances drifted: app total ${centsToDisplay(ledgerBalanceCents)} " +
                    "is ${centsToDisplay(absDrift)} $direction bank balance ${centsToDisplay(bankBalanceCents)}"
            },
        tint = GlassTint.Error,
        surfaceStyle = GlassSurfaceStyle.Hero,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Balances drifted",
                style = MaterialTheme.typography.titleMedium,
                color = GlassTokens.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Your app total (${centsToDisplay(ledgerBalanceCents)}) is " +
                    "${centsToDisplay(absDrift)} $direction your saved bank balance " +
                    "(${centsToDisplay(bankBalanceCents)}).",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.TextSecondary,
            )
            AppPrimaryButton(
                text = "Re-reconcile",
                onClick = onReconcile,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SpendPacingCard(
    pacingResult: com.example.app.processing.BudgetPacingResult?,
    modifier: Modifier = Modifier,
) {
    if (pacingResult == null) return

    val statusColor = when (pacingResult.pacingStatus) {
        com.example.app.processing.PacingStatus.ON_TRACK -> GlassTokens.PositiveGreen
        com.example.app.processing.PacingStatus.WARNING -> GlassTokens.CyanBright
        com.example.app.processing.PacingStatus.CRITICAL -> GlassTokens.ErrorRed
    }

    val statusText = when (pacingResult.pacingStatus) {
        com.example.app.processing.PacingStatus.ON_TRACK -> "On Track"
        com.example.app.processing.PacingStatus.WARNING -> "Velocity Warning (+15%)"
        com.example.app.processing.PacingStatus.CRITICAL -> "Critical Pace Overrun"
    }

    val progress = if (pacingResult.targetDailyVelocityCents > 0) {
        (pacingResult.actualDailyVelocityCents.toFloat() / pacingResult.targetDailyVelocityCents.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    GlassCard(
        modifier = modifier
            .heightIn(min = UiLayoutTokens.DashboardSupportCardMinHeight)
            .semantics {
                stateDescription = "Spending pace: $statusText. Actual ${centsToDisplay(pacingResult.actualDailyVelocityCents.toInt())}/day vs Target ${centsToDisplay(pacingResult.targetDailyVelocityCents.toInt())}/day"
            },
        tint = when (pacingResult.pacingStatus) {
            com.example.app.processing.PacingStatus.CRITICAL -> GlassTint.Error
            com.example.app.processing.PacingStatus.WARNING -> GlassTint.Cyan
            else -> GlassTint.Neutral
        },
        surfaceStyle = GlassSurfaceStyle.Standard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Spend Velocity Pacing",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTokens.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Actual Speed (7-day)", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        "${centsToDisplay(pacingResult.actualDailyVelocityCents.toInt())}/day",
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Target Speed", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextDim)
                    Text(
                        "${centsToDisplay(pacingResult.targetDailyVelocityCents.toInt())}/day",
                        style = MaterialTheme.typography.titleSmall,
                        color = GlassTokens.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                color = statusColor,
                trackColor = GlassTokens.DividerColor,
            )

            Text(
                if (pacingResult.runwayDays.isInfinite()) {
                    "Infinite runway remaining until payday (${pacingResult.daysToPayday}d)"
                } else {
                    "Estimated runway: ${String.format("%.1f", pacingResult.runwayDays)} days remaining (${pacingResult.daysToPayday}d to payday)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = GlassTokens.TextSecondary,
            )
        }
    }
}
