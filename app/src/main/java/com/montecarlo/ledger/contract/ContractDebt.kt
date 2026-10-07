package com.montecarlo.ledger.contract

import java.math.BigInteger
import java.time.LocalDate

/**
 * A scenario liability (contract 2.0, `contracts/debt.md`).
 *
 * `kind` is `installment` or `revolving`; the two revolving legs (`min_payment_percent_bps` and
 * `min_payment_floor_cents`) are only consulted for a revolving liability. `due_day_of_month` is
 * carried for parity with the schema even though the amortization procedure does not use it.
 */
data class ContractLiability(
    val id: String,
    val name: String?,
    val balanceCents: Long,
    val aprBasisPoints: Int,
    val minPaymentCents: Long,
    val kind: String = "installment",
    val minPaymentPercentBps: Int = 0,
    val minPaymentFloorCents: Long = 0,
    val dueDayOfMonth: Int = 1,
)

/** One amortization row (`schemas/result.schema.json#/$defs/debt_step`). */
data class ContractDebtStep(
    val month: Int,
    val date: LocalDate,
    val liabilityId: String,
    val startingBalanceCents: Long,
    val paymentCents: Long,
    val interestCents: Long,
    val principalCents: Long,
    val endingBalanceCents: Long,
)

/** The canonical `debt` block (`schemas/result.schema.json#/$defs/debt`). */
data class ContractDebtResult(
    val strategy: String,
    val extraMonthlyPaymentCents: Long,
    val monthsToPayoff: Int,
    val payoffDate: LocalDate,
    val totalInterestCents: Long,
    val totalPaidCents: Long,
    val didNotConverge: Boolean,
    val schedule: List<ContractDebtStep>,
)

/**
 * Deterministic liability amortization (contract 2.0, `contracts/debt.md`).
 *
 * Independent of the cash-ledger forecast: this never reads a clock and never touches the Monte
 * Carlo overlay. All arithmetic is integer-only; products feeding `round_half_away` go through
 * [BigInteger] so an intermediate product cannot wrap a `Long`.
 */
object ContractDebt {

    /** Accepted `debt_strategy` values. */
    val STRATEGIES = setOf("snowball", "avalanche")

    /** Accepted liability `kind` values. */
    val KINDS = setOf("installment", "revolving")

    /** The loop cap from `contracts/debt.md` (`months < 360`). */
    private const val MAX_MONTHS = 360

    private val INTEREST_DEN = BigInteger.valueOf(120_000L)
    private val PERCENT_DEN = BigInteger.valueOf(10_000L)

    /**
     * Validates the strategy name (`snowball` | `avalanche`). Raised as
     * [ContractErrorCode.SCHEMA_INVALID].
     */
    fun validateStrategy(strategy: String) {
        if (strategy !in STRATEGIES) {
            schema("debt_strategy must be one of ${STRATEGIES.sorted()}")
        }
    }

    /**
     * Validates a liability array: unique `id`, non-negative amounts, a known `kind`, and a
     * `due_day_of_month` in `1..31`. Every violation is [ContractErrorCode.SCHEMA_INVALID].
     */
    fun validateLiabilities(liabilities: List<ContractLiability>) {
        val seen = HashSet<String>()
        for (l in liabilities) {
            if (!seen.add(l.id)) schema("duplicate liability id '${l.id}'")
            if (l.balanceCents < 0) schema("liability '${l.id}'.balance_cents must be >= 0")
            if (l.aprBasisPoints < 0) schema("liability '${l.id}'.apr_basis_points must be >= 0")
            if (l.minPaymentCents < 0) schema("liability '${l.id}'.min_payment_cents must be >= 0")
            if (l.minPaymentPercentBps < 0) {
                schema("liability '${l.id}'.min_payment_percent_bps must be >= 0")
            }
            if (l.minPaymentFloorCents < 0) {
                schema("liability '${l.id}'.min_payment_floor_cents must be >= 0")
            }
            if (l.kind !in KINDS) schema("liability '${l.id}'.kind must be one of ${KINDS.sorted()}")
            if (l.dueDayOfMonth !in 1..31) {
                schema("liability '${l.id}'.due_day_of_month must be 1..31")
            }
        }
    }

    /**
     * Runs the normative amortization loop of `contracts/debt.md`.
     *
     * An empty [liabilities] list yields a zeroed result whose `payoff_date` is [asOf]. Ties after
     * the strategy sort keep the [liabilities] array order (the sort is stable). The extra pool is
     * applied after the minimums, in the same order, and folded into that liability's month row.
     */
    fun amortize(
        asOf: LocalDate,
        liabilities: List<ContractLiability>,
        strategy: String,
        extraMonthlyPaymentCents: Long,
    ): ContractDebtResult {
        validateStrategy(strategy)
        if (extraMonthlyPaymentCents < 0) {
            schema("extra_monthly_payment_cents must be >= 0")
        }
        validateLiabilities(liabilities)

        val balances = LinkedHashMap<String, Long>(liabilities.size)
        for (l in liabilities) balances[l.id] = l.balanceCents

        val schedule = ArrayList<ContractDebtStep>()
        var months = 0
        var totalInterest = 0L
        var totalPaid = 0L
        var overflowed = false

        fun anyOutstanding(): Boolean = balances.values.any { it > 0L }

        while (anyOutstanding() && months < MAX_MONTHS && !overflowed) {
            months += 1
            val date = asOf.plusMonths(months.toLong())

            // (a) target order: active liabilities only, stable-sorted by strategy key.
            val active = liabilities.filter { (balances[it.id] ?: 0L) > 0L }
            val order = when (strategy) {
                // Stable sort keeps the array order for equal balances.
                "snowball" -> active.sortedBy { balances[it.id]!! }
                // Stable sort keeps the array order for equal APRs.
                else -> active.sortedByDescending { it.aprBasisPoints }
            }

            var extraPool = extraMonthlyPaymentCents
            val rowIndex = HashMap<String, Int>(order.size)

            // (b) interest + minimum payment, in `order`.
            for (l in order) {
                val before = balances[l.id]!!
                val interestBig = monthlyInterest(before, l.aprBasisPoints)
                // Overflow guard: `interest > MAX_MONEY - balance`.
                if (interestBig > BigInteger.valueOf(ContractMoney.MAX_CENTS - before)) {
                    overflowed = true
                    break
                }
                val interest = interestBig.toLong()
                val afterInterest = before + interest
                balances[l.id] = afterInterest
                totalInterest += interest

                val minimum = minimumPaymentCents(l, afterInterest)
                val principal = minimum - interest
                val starting = afterInterest - interest
                balances[l.id] = afterInterest - minimum
                totalPaid += minimum
                rowIndex[l.id] = schedule.size
                schedule.add(
                    ContractDebtStep(
                        month = months,
                        date = date,
                        liabilityId = l.id,
                        startingBalanceCents = starting,
                        paymentCents = minimum,
                        interestCents = interest,
                        principalCents = principal,
                        endingBalanceCents = balances[l.id]!!,
                    )
                )
            }

            // (c) extra pool in the same `order`, folded into that debt's month row.
            for (l in order) {
                if (extraPool <= 0L) break
                val balance = balances[l.id]!!
                if (balance <= 0L) continue
                val index = rowIndex[l.id] ?: continue
                val extra = minOf(extraPool, balance)
                val newBalance = balance - extra
                balances[l.id] = newBalance
                extraPool -= extra
                totalPaid += extra
                val row = schedule[index]
                schedule[index] = row.copy(
                    paymentCents = row.paymentCents + extra,
                    principalCents = row.principalCents + extra,
                    endingBalanceCents = newBalance,
                )
            }
        }

        return ContractDebtResult(
            strategy = strategy,
            extraMonthlyPaymentCents = extraMonthlyPaymentCents,
            monthsToPayoff = months,
            payoffDate = asOf.plusMonths(months.toLong()),
            totalInterestCents = totalInterest,
            totalPaidCents = totalPaid,
            didNotConverge = anyOutstanding() || overflowed,
            schedule = schedule,
        )
    }

    /** `round_half_away(balance * apr_bps, 120000)` with an exact (non-wrapping) product. */
    private fun monthlyInterest(balanceCents: Long, aprBasisPoints: Int): BigInteger =
        ContractMoney.roundHalfAway(
            BigInteger.valueOf(balanceCents).multiply(BigInteger.valueOf(aprBasisPoints.toLong())),
            INTEREST_DEN,
        )

    /**
     * `minimum_payment(l, balance_after_interest)` from `contracts/debt.md`. An installment returns
     * the flat floor capped by the balance; a revolving liability uses the percent leg with the flat
     * floor, capped by the balance. Public so product adapters can delegate to the one canonical
     * formula instead of re-deriving it.
     */
    fun minimumPaymentCents(liability: ContractLiability, balanceAfterInterestCents: Long): Long {
        if (balanceAfterInterestCents <= 0L) return 0L
        if (liability.kind != "revolving") return minOf(liability.minPaymentCents, balanceAfterInterestCents)
        val percent = ContractMoney.roundHalfAway(
            BigInteger.valueOf(balanceAfterInterestCents)
                .multiply(BigInteger.valueOf(liability.minPaymentPercentBps.toLong())),
            PERCENT_DEN,
        )
        return percent
            .max(BigInteger.valueOf(liability.minPaymentFloorCents))
            .min(BigInteger.valueOf(balanceAfterInterestCents))
            .toLong()
    }

    private fun schema(message: String): Nothing =
        throw ContractException(ContractErrorCode.SCHEMA_INVALID, message)
}
