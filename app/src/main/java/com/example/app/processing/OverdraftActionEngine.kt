package com.example.app.processing

import com.example.app.data.AssetEntity
import com.example.app.data.BillOccurrenceEntity
import java.time.LocalDate

sealed class OverdraftRecommendation {
    data class RescheduleBill(
        val occurrenceId: Int?,
        val billName: String,
        val currentDueDate: LocalDate,
        val suggestedDueDate: LocalDate,
        val amountCents: Long,
        val riskReductionPct: Double,
    ) : OverdraftRecommendation()

    data class CapDailySpend(
        val suggestedDailyCapCents: Int,
        val daysToPayday: Int,
        val deficitCents: Int,
    ) : OverdraftRecommendation()

    data class TransferFromAsset(
        val assetId: Long,
        val assetName: String,
        val suggestedTransferCents: Int,
    ) : OverdraftRecommendation()
}

object OverdraftActionEngine {
    fun analyze(
        mcResult: MonteCarloResult,
        windows: List<CashFlowWindow>,
        events: List<ForecastEvent>,
        billOccurrences: List<BillOccurrenceEntity> = emptyList(),
        assets: List<AssetEntity> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): List<OverdraftRecommendation> {
        if (mcResult.probability_negative_pct <= 0.0 && mcResult.worst_10_balance_cents >= 0) {
            return emptyList()
        }

        val recommendations = mutableListOf<OverdraftRecommendation>()

        // 1. Find upcoming bill occurrence that causes or deepens the deficit before next payday
        val nextPayday = events.firstOrNull { it.type == "income" && it.date.isAfter(today) }?.date
        val troubleWindowEnd = nextPayday ?: today.plusDays(30)

        val prePaydayBills = events.filter {
            it.type == "bill" && !it.date.isBefore(today) && it.date.isBefore(troubleWindowEnd)
        }

        if (prePaydayBills.isNotEmpty()) {
            val largestBill = prePaydayBills.maxByOrNull { it.amount_cents }
            if (largestBill != null) {
                val targetDate = troubleWindowEnd.plusDays(1)
                val occurrenceId = billOccurrences.firstOrNull {
                    it.due_date == largestBill.date.toString() && it.is_paid == 0
                }?.id

                recommendations.add(
                    OverdraftRecommendation.RescheduleBill(
                        occurrenceId = occurrenceId,
                        billName = largestBill.description,
                        currentDueDate = largestBill.date,
                        suggestedDueDate = targetDate,
                        amountCents = largestBill.amount_cents,
                        riskReductionPct = mcResult.probability_negative_pct,
                    )
                )
            }
        }

        // 2. Calculate daily spend cap recommendation
        val lowestWindow = windows.minByOrNull { it.lowestBalanceCents }
        if (lowestWindow != null && lowestWindow.lowestBalanceCents < 0) {
            val deficit = -lowestWindow.lowestBalanceCents
            val days = lowestWindow.days.coerceAtLeast(1)
            val suggestedCap = (lowestWindow.startingBalanceCents / days).coerceAtLeast(0)

            recommendations.add(
                OverdraftRecommendation.CapDailySpend(
                    suggestedDailyCapCents = suggestedCap,
                    daysToPayday = days,
                    deficitCents = deficit,
                )
            )
        }

        // 3. Check for available savings assets to cover deficit
        val totalDeficit = mcResult.worst_10_balance_cents.takeIf { it < 0 }?.let { -it } ?: 0L
        if (totalDeficit > 0 && assets.isNotEmpty()) {
            val savingsAsset = assets.firstOrNull {
                it.type.equals("savings", ignoreCase = true) || it.type.equals("investment", ignoreCase = true)
            } ?: assets.firstOrNull()

            if (savingsAsset != null && savingsAsset.balanceCents > 0) {
                val transferAmount = minOf(totalDeficit, savingsAsset.balanceCents.toLong())
                    .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

                recommendations.add(
                    OverdraftRecommendation.TransferFromAsset(
                        assetId = savingsAsset.id,
                        assetName = savingsAsset.name,
                        suggestedTransferCents = transferAmount,
                    )
                )
            }
        }

        return recommendations
    }
}
