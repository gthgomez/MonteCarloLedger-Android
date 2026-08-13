package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
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
        val suggestedDailyCapCents: Long,
        val daysToPayday: Int,
        val deficitCents: Long,
    ) : OverdraftRecommendation()

    data class TransferFromAsset(
        val assetId: Long,
        val assetName: String,
        val suggestedTransferCents: Long,
    ) : OverdraftRecommendation()
}

object OverdraftActionEngine {
    fun analyze(
        mcResult: MonteCarloResult,
        windows: List<CashFlowWindow>,
        events: List<ForecastEvent>,
        billOccurrences: List<BillOccurrenceEntity> = emptyList(),
        assets: List<AssetEntity> = emptyList(),
    ): List<OverdraftRecommendation> {
        val recommendations = mutableListOf<OverdraftRecommendation>()

        // 1. Check for bill rescheduling recommendations
        if (mcResult.probability_negative_pct > 0.0) {
            val upcomingBills = events
                .filter { it.type == "bill" }
                .sortedByDescending { it.amount_cents }

            val nextPayday = events
                .filter { it.type == "income" }
                .minByOrNull { it.date }

            if (nextPayday != null && upcomingBills.isNotEmpty()) {
                val targetBill = upcomingBills
                    .filter { it.date.isBefore(nextPayday.date) }
                    .maxByOrNull { it.amount_cents }

                if (targetBill != null) {
                    val occurrence = billOccurrences.firstOrNull {
                        it.due_date == targetBill.date.toString() && it.amount_cents == targetBill.amount_cents
                    }

                    val suggestedDate = nextPayday.date.plusDays(1)
                    recommendations.add(
                        OverdraftRecommendation.RescheduleBill(
                            occurrenceId = occurrence?.id,
                            billName = targetBill.description,
                            currentDueDate = targetBill.date,
                            suggestedDueDate = suggestedDate,
                            amountCents = targetBill.amount_cents,
                            riskReductionPct = minOf(95.0, mcResult.probability_negative_pct * 0.8),
                        )
                    )
                }
            }
        }

        // 2. Calculate daily spend cap recommendation
        val lowestWindow = windows.minByOrNull { it.lowestBalanceCents }
        if (lowestWindow != null && lowestWindow.lowestBalanceCents < 0L) {
            val deficit = -lowestWindow.lowestBalanceCents
            val days = lowestWindow.days.coerceAtLeast(1)
            val discretionaryCents = (lowestWindow.startingBalanceCents - lowestWindow.billCents).coerceAtLeast(0L)
            val suggestedCap = discretionaryCents / days

            recommendations.add(
                OverdraftRecommendation.CapDailySpend(
                    suggestedDailyCapCents = suggestedCap,
                    daysToPayday = days,
                    deficitCents = deficit,
                )
            )
        }

        // 3. Check for available savings assets to cover deficit
        val totalDeficit = mcResult.worst_10_balance_cents.takeIf { it < 0L }?.let { -it } ?: 0L
        if (totalDeficit > 0L && assets.isNotEmpty()) {
            val savingsAsset = assets.firstOrNull {
                it.type.equals("savings", ignoreCase = true) || it.type.equals("investment", ignoreCase = true)
            } ?: assets.firstOrNull()

            if (savingsAsset != null && savingsAsset.balanceCents > 0L) {
                val transferAmount = minOf(totalDeficit, savingsAsset.balanceCents)

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
