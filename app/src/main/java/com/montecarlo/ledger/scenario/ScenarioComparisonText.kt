package com.montecarlo.ledger.scenario

import com.montecarlo.ledger.util.centsToDisplay
import java.time.LocalDate
import java.util.Locale

/**
 * Display-only rendering of the WS6 scenario comparisons.
 *
 * These functions format values that already came out of the contract engine — they perform no
 * financial arithmetic of their own, so the numbers shown beside a comparison can never disagree
 * with the canonical [ScenarioHeadline] they were built from. Kept free of Compose types so the
 * wording is unit-testable.
 */
object ScenarioComparisonText {

    /** Plain-language verdict for a purchase comparison. */
    fun verdict(simulation: PurchaseSimulation): String {
        val safeDelta = simulation.change.safeToSpendCents
            ?: return "No risk model ran for this comparison, so only the low point is available."
        return when {
            safeDelta < 0 ->
                "Not covered: this purchase drops safe to spend by ${centsToDisplay(-safeDelta)}."
            safeDelta == 0L -> "Covered: this purchase does not move safe to spend."
            else -> "Covered: safe to spend still improves by ${centsToDisplay(safeDelta)}."
        }
    }

    /** Plain-language verdict for a paycheck-delay comparison. */
    fun verdict(simulation: PaycheckDelaySimulation): String {
        if (simulation.delayDays == 0) return "No delay applied — the schedules are identical."
        val safeDelta = simulation.change.safeToSpendCents
        val risk = when {
            safeDelta == null -> "safe to spend is not modelled"
            safeDelta < 0 -> "safe to spend falls by ${centsToDisplay(-safeDelta)}"
            safeDelta == 0L -> "safe to spend does not move"
            else -> "safe to spend improves by ${centsToDisplay(safeDelta)}"
        }
        val lowPoint = when {
            simulation.change.projectedLowPointCents < 0 ->
                "the projected low point drops by ${centsToDisplay(-simulation.change.projectedLowPointCents)}"
            simulation.change.projectedLowPointCents == 0L -> "the projected low point does not move"
            else ->
                "the projected low point rises by ${centsToDisplay(simulation.change.projectedLowPointCents)}"
        }
        return "A ${simulation.delayDays}-day delay means $risk and $lowPoint."
    }

    /** Label/value rows for the purchase comparison card. */
    fun rows(simulation: PurchaseSimulation): List<Pair<String, String>> =
        listOf(
            "Safe to spend" to compare(
                simulation.baseline.safeToSpendCents,
                simulation.withPurchase.safeToSpendCents,
                ::centsToDisplay,
            ),
            "Projected low point" to compare(
                simulation.baseline.projectedLowPointCents,
                simulation.withPurchase.projectedLowPointCents,
                ::centsToDisplay,
            ),
            "Odds of going negative" to compare(
                simulation.baseline.probabilityNegativePpm,
                simulation.withPurchase.probabilityNegativePpm,
                ::percentFromPpm,
            ),
            "First negative day" to compare(
                simulation.baseline.firstNegativeDate,
                simulation.withPurchase.firstNegativeDate,
                LocalDate::toString,
            ),
        ) + deltaRows(simulation.change) +
            Pair("Purchase", "${centsToDisplay(simulation.amountCents)} on ${simulation.purchaseDate}")

    /** Label/value rows for the paycheck-delay comparison card. */
    fun rows(simulation: PaycheckDelaySimulation): List<Pair<String, String>> =
        listOf(
            "Safe to spend" to
                compare(simulation.baseline.safeToSpendCents, simulation.delayed.safeToSpendCents, ::centsToDisplay),
            "Projected low point" to compare(
                simulation.baseline.projectedLowPointCents,
                simulation.delayed.projectedLowPointCents,
                ::centsToDisplay,
            ),
            "Odds of going negative" to compare(
                simulation.baseline.probabilityNegativePpm,
                simulation.delayed.probabilityNegativePpm,
                ::percentFromPpm,
            ),
            "First negative day" to compare(
                simulation.baseline.firstNegativeDate,
                simulation.delayed.firstNegativeDate,
                LocalDate::toString,
            ),
        ) + deltaRows(simulation.change) +
            Pair("Delay", "${simulation.delayDays} day(s)")

    private fun deltaRows(change: ScenarioHeadlineDelta): List<Pair<String, String>> = listOf(
        "Low point change" to signedCents(change.projectedLowPointCents),
        "Safe to spend change" to (change.safeToSpendCents?.let(::signedCents) ?: "—"),
        "First negative change" to (change.firstNegativeDateShiftDays?.let(::signedDays) ?: "—"),
    )

    private fun <T> compare(baseline: T?, modified: T?, format: (T) -> String): String {
        if (baseline == null || modified == null) return "—"
        return "${format(baseline)} → ${format(modified)}"
    }

    private fun signedCents(cents: Long): String = when {
        cents > 0 -> "+${centsToDisplay(cents)}"
        else -> centsToDisplay(cents)
    }

    private fun signedDays(days: Long): String = when {
        days > 0 -> "+$days day(s) later"
        days < 0 -> "${-days} day(s) earlier"
        else -> "no change"
    }

    private fun percentFromPpm(ppm: Long): String = String.format(Locale.US, "%.1f%%", ppm / 10_000.0)
}
