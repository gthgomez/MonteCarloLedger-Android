package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
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

    fun calculatePacing(
        safeToSpendCents: Long,
        daysToPayday: Int,
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
    ): BudgetPacingResult {
        val daysUntil = maxOf(1, daysToPayday)
        val targetVelocity = if (safeToSpendCents > 0) safeToSpendCents / daysUntil else 0L

        val sevenDaysAgo = today.minusDays(7)
        val spendingLast7Days = transactions
            .filter { transaction ->
                val date = runCatching { LocalDate.parse(transaction.date) }.getOrNull()
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
            targetVelocity > 0L && actualVelocity > (targetVelocity * 1.40).toLong() -> PacingStatus.CRITICAL
            targetVelocity > 0L && actualVelocity > (targetVelocity * 1.15).toLong() -> PacingStatus.WARNING
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
