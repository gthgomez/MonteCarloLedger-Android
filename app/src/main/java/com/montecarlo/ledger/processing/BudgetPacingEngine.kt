package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.util.LedgerDate
import java.time.LocalDate

enum class PacingStatus {
    ON_TRACK,
    WARNING,
    CRITICAL
}

data class BudgetPacingResult(
    val targetDailyVelocityCents: Long,
    val actualDailyVelocityCents: Long,
    val spendingLast7DaysCents: Long,
    val daysToPayday: Int,
    val safeToSpendCents: Long,
    val runwayDays: Double,
    val pacingStatus: PacingStatus,
    val suggestedDailyLimitCents: Long,
)

object BudgetPacingEngine {

    fun clampedRunwayDays(
        safeToSpendCents: Long,
        dailyVelocityCents: Long,
        maxDays: Int = 90,
    ): Int {
        if (dailyVelocityCents <= 0L) return maxDays
        return (safeToSpendCents / dailyVelocityCents)
            .coerceIn(0L, maxDays.toLong())
            .toInt()
    }

    fun calculatePacing(
        safeToSpendCents: Long,
        daysToPayday: Int,
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
    ): BudgetPacingResult {
        val daysUntil = maxOf(1, daysToPayday)
        val targetVelocity = if (safeToSpendCents > 0) safeToSpendCents / daysUntil else 0L

        val sevenDaysAgo = today.minusDays(6)
        val spendingLast7Days = transactions
            .filter { transaction ->
                val date = LedgerDate.parseIsoOrNull(transaction.date)
                date != null && !date.isBefore(sevenDaysAgo) && !date.isAfter(today) && transaction.type == "expense"
            }
            .sumOf { kotlin.math.abs(it.amount_cents).toLong() }

        val actualVelocity = spendingLast7Days / 7L

        val runwayDays = if (actualVelocity > 0L) {
            safeToSpendCents.toDouble() / actualVelocity.toDouble()
        } else {
            Double.POSITIVE_INFINITY
        }

        val status = when {
            safeToSpendCents <= 0L -> PacingStatus.CRITICAL
            runwayDays < 7.0 && daysUntil > 0 -> PacingStatus.CRITICAL
            targetVelocity > 0L && actualVelocity > (targetVelocity * 140L) / 100L -> PacingStatus.CRITICAL
            targetVelocity > 0L && actualVelocity > (targetVelocity * 115L) / 100L -> PacingStatus.WARNING
            else -> PacingStatus.ON_TRACK
        }

        return BudgetPacingResult(
            targetDailyVelocityCents = targetVelocity,
            actualDailyVelocityCents = actualVelocity,
            spendingLast7DaysCents = spendingLast7Days,
            daysToPayday = daysUntil,
            safeToSpendCents = safeToSpendCents,
            runwayDays = runwayDays,
            pacingStatus = status,
            suggestedDailyLimitCents = targetVelocity,
        )
    }
}
