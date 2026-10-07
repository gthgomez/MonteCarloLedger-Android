package com.montecarlo.ledger.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Contract 2.0 liability amortization (`contracts/debt.md`, MCD-0026).
 *
 * The expected numbers are the exact integer schedule from the normative procedure; each test
 * pins the arithmetic, the strategy ordering, the extra-pool folding, the non-convergence cap, and
 * the schema validations.
 */
class ContractDebtTest {

    private val asOf = LocalDate.parse("2026-01-15")

    private fun liability(
        id: String,
        balance: Long,
        apr: Int,
        minPayment: Long,
        kind: String = "installment",
        minPercentBps: Int = 0,
        minFloor: Long = 0,
    ) = ContractLiability(
        id = id,
        name = null,
        balanceCents = balance,
        aprBasisPoints = apr,
        minPaymentCents = minPayment,
        kind = kind,
        minPaymentPercentBps = minPercentBps,
        minPaymentFloorCents = minFloor,
    )

    // ------------------------------------------------------------------ installment payoff

    @Test
    fun installmentPaysOffAndAmortizesInterestBeforePayment() {
        val result = ContractDebt.amortize(
            asOf = asOf,
            liabilities = listOf(liability("card", 100_000, 1_200, 20_000)),
            strategy = "snowball",
            extraMonthlyPaymentCents = 0,
        )

        assertEquals(6, result.monthsToPayoff)
        assertEquals(3_112, result.totalInterestCents)
        assertEquals(103_112, result.totalPaidCents)
        assertFalse(result.didNotConverge)
        assertEquals(LocalDate.parse("2026-07-15"), result.payoffDate)
        assertEquals(6, result.schedule.size)

        // Interest (round(100000*1200/120000) = 1000) is charged before the payment.
        assertEquals(
            ContractDebtStep(
                month = 1,
                date = LocalDate.parse("2026-02-15"),
                liabilityId = "card",
                startingBalanceCents = 100_000,
                paymentCents = 20_000,
                interestCents = 1_000,
                principalCents = 19_000,
                endingBalanceCents = 81_000,
            ),
            result.schedule.first(),
        )
        // Final month: the payment is capped at the post-interest balance.
        assertEquals(
            ContractDebtStep(
                month = 6,
                date = LocalDate.parse("2026-07-15"),
                liabilityId = "card",
                startingBalanceCents = 3_081,
                paymentCents = 3_112,
                interestCents = 31,
                principalCents = 3_081,
                endingBalanceCents = 0,
            ),
            result.schedule.last(),
        )
    }

    @Test
    fun eachRowKeepsStartingMinusPrincipalEqualsEnding() {
        val result = ContractDebt.amortize(
            asOf = asOf,
            liabilities = listOf(liability("card", 100_000, 1_200, 20_000)),
            strategy = "snowball",
            extraMonthlyPaymentCents = 0,
        )
        result.schedule.forEach { row ->
            assertEquals(row.endingBalanceCents, row.startingBalanceCents - row.principalCents)
            assertEquals(row.liabilityId, "card")
        }
    }

    // ------------------------------------------------------------------ revolving minimum

    @Test
    fun revolvingMinimumUsesThePercentLegNotMinPaymentOrFloor() {
        // min_payment_cents = 999999 is ignored for a revolving liability; floor 100 < 3% percent.
        val result = ContractDebt.amortize(
            asOf = asOf,
            liabilities = listOf(
                liability("rev", 100_000, 600, 999_999, kind = "revolving", minPercentBps = 300, minFloor = 100),
            ),
            strategy = "snowball",
            extraMonthlyPaymentCents = 0,
        )
        // percent = round(100500 * 300 / 10000) = 3015; interest = round(100000*600/120000) = 500.
        assertEquals(3_015, result.schedule.first().paymentCents)
        assertEquals(500, result.schedule.first().interestCents)
        assertEquals(2_515, result.schedule.first().principalCents)
        assertEquals(97_485, result.schedule.first().endingBalanceCents)
        assertEquals(171, result.monthsToPayoff)
        assertEquals(19_534, result.totalInterestCents)
        assertEquals(119_534, result.totalPaidCents)
        assertFalse(result.didNotConverge)
    }

    @Test
    fun revolvingMinimumUsesTheFloorWhenItExceedsThePercent() {
        // percent = round(101500*200/10000) = 2030 < floor 2500, so the floor governs.
        val result = ContractDebt.amortize(
            asOf = asOf,
            liabilities = listOf(
                liability("rev", 100_000, 1_800, 5_000, kind = "revolving", minPercentBps = 200, minFloor = 2_500),
            ),
            strategy = "snowball",
            extraMonthlyPaymentCents = 0,
        )
        assertEquals(2_500, result.schedule.first().paymentCents)
        assertEquals(1_500, result.schedule.first().interestCents)
        assertEquals(99_000, result.schedule.first().endingBalanceCents)
        assertEquals(62, result.monthsToPayoff)
        assertEquals(53_866, result.totalInterestCents)
        assertEquals(153_866, result.totalPaidCents)
    }

    // ------------------------------------------------------------------ strategy ordering

    private val threeLiabilities = listOf(
        liability("A", 50_000, 2_400, 2_000),
        liability("B", 20_000, 1_200, 1_000),
        liability("C", 90_000, 1_800, 3_000),
    )

    @Test
    fun snowballOrdersByAscendingBalance() {
        val result = ContractDebt.amortize(asOf, threeLiabilities, "snowball", 0)
        val month1 = result.schedule.filter { it.month == 1 }
        assertEquals(listOf("B", "A", "C"), month1.map { it.liabilityId })
        assertEquals(
            ContractDebtStep(1, LocalDate.parse("2026-02-15"), "B", 20_000, 1_000, 200, 800, 19_200),
            month1[0],
        )
        assertEquals(
            ContractDebtStep(1, LocalDate.parse("2026-02-15"), "A", 50_000, 2_000, 1_000, 1_000, 49_000),
            month1[1],
        )
        assertEquals(41, result.monthsToPayoff)
    }

    @Test
    fun avalancheOrdersByDescendingApr() {
        val result = ContractDebt.amortize(asOf, threeLiabilities, "avalanche", 0)
        val month1 = result.schedule.filter { it.month == 1 }
        assertEquals(listOf("A", "C", "B"), month1.map { it.liabilityId })
        assertEquals(
            ContractDebtStep(1, LocalDate.parse("2026-02-15"), "C", 90_000, 3_000, 1_350, 1_650, 88_350),
            month1[1],
        )
        assertEquals(41, result.monthsToPayoff)
    }

    @Test
    fun strategySortIsStableSoTiesKeepTheArrayOrder() {
        // Equal balance: snowball keeps the input order.
        val snowball = ContractDebt.amortize(
            asOf,
            listOf(liability("x", 30_000, 1_500, 3_000), liability("y", 30_000, 900, 3_000)),
            "snowball",
            0,
        )
        assertEquals(
            listOf("x", "y"),
            snowball.schedule.filter { it.month == 1 }.map { it.liabilityId },
        )
        // Equal APR: avalanche keeps the input order.
        val avalanche = ContractDebt.amortize(
            asOf,
            listOf(liability("x", 30_000, 1_500, 3_000), liability("y", 20_000, 1_500, 2_000)),
            "avalanche",
            0,
        )
        assertEquals(
            listOf("x", "y"),
            avalanche.schedule.filter { it.month == 1 }.map { it.liabilityId },
        )
    }

    // ------------------------------------------------------------------ extra payment

    @Test
    fun extraPaymentFoldsIntoTheTargetRowsPaymentAndPrincipal() {
        val result = ContractDebt.amortize(
            asOf,
            listOf(liability("A", 50_000, 2_400, 2_000), liability("B", 20_000, 1_200, 1_000)),
            "snowball",
            extraMonthlyPaymentCents = 5_000,
        )
        val month1 = result.schedule.filter { it.month == 1 }
        // B is smallest: minimum 1000 + 5000 extra = 6000, principal 200 + 5000.
        assertEquals(
            ContractDebtStep(1, LocalDate.parse("2026-02-15"), "B", 20_000, 6_000, 200, 5_800, 14_200),
            month1[0],
        )
        assertEquals(
            ContractDebtStep(1, LocalDate.parse("2026-02-15"), "A", 50_000, 2_000, 1_000, 1_000, 49_000),
            month1[1],
        )
        assertEquals(11, result.monthsToPayoff)
        assertEquals(7_582, result.totalInterestCents)
        assertEquals(77_582, result.totalPaidCents)
    }

    // ------------------------------------------------------------------ non-convergence

    @Test
    fun negativeAmortizationDoesNotConvergeAtThe360MonthCap() {
        val result = ContractDebt.amortize(
            asOf,
            listOf(liability("bad", 1_000_000, 2_000, 100)),
            "snowball",
            0,
        )
        assertEquals(360, result.monthsToPayoff)
        assertTrue(result.didNotConverge)
        // Signed principal is negative (interest exceeds the minimum payment).
        assertEquals(-16_567, result.schedule.first().principalCents)
        assertEquals(100, result.schedule.first().paymentCents)
        assertEquals(1_016_567, result.schedule.first().endingBalanceCents)
    }

    @Test
    fun emptyLiabilitiesYieldAZeroedResultDatedAtAsOf() {
        val result = ContractDebt.amortize(asOf, emptyList(), "snowball", 0)
        assertEquals(0, result.monthsToPayoff)
        assertEquals(asOf, result.payoffDate)
        assertEquals(0, result.totalInterestCents)
        assertEquals(0, result.totalPaidCents)
        assertFalse(result.didNotConverge)
        assertTrue(result.schedule.isEmpty())
    }

    // ------------------------------------------------------------------ validation

    @Test
    fun duplicateLiabilityIdIsSchemaInvalid() {
        val dupe = listOf(
            liability("dup", 10_000, 1_200, 1_000),
            liability("dup", 20_000, 1_200, 1_000),
        )
        assertThrows(ContractException::class.java) {
            ContractDebt.amortize(asOf, dupe, "snowball", 0)
        }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }
    }

    @Test
    fun negativeAmountsAreSchemaInvalid() {
        listOf(
            liability("neg-balance", -1, 1_200, 1_000),
            liability("neg-apr", 1_000, -1, 1_000),
            liability("neg-min", 1_000, 1_200, -1),
            liability("neg-pct", 1_000, 1_200, 1_000, kind = "revolving", minPercentBps = -1),
            liability("neg-floor", 1_000, 1_200, 1_000, kind = "revolving", minFloor = -1),
        ).forEach { bad ->
            assertThrows(ContractException::class.java) {
                ContractDebt.amortize(asOf, listOf(bad), "snowball", 0)
            }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }
        }
    }

    @Test
    fun unknownStrategyOrKindIsSchemaInvalid() {
        assertThrows(ContractException::class.java) {
            ContractDebt.amortize(asOf, listOf(liability("a", 1_000, 1_200, 1_000)), "fastest", 0)
        }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }

        assertThrows(ContractException::class.java) {
            ContractDebt.amortize(
                asOf,
                listOf(liability("a", 1_000, 1_200, 1_000, kind = "mortgage")),
                "snowball",
                0,
            )
        }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }
    }

    // ------------------------------------------------------------------ parser / emitter

    private fun scenarioJson(extra: String = ""): String =
        """
        {
          "contract_version": "2.0",
          "scenario_id": "debt-parser",
          "as_of": "2026-01-15",
          "starting_balance_cents": 0,
          "horizon_days": 30,
          "events": []$extra
        }
        """.trimIndent()

    @Test
    fun debtBlockIsEmittedOnlyWhenLiabilitiesIsDeclared() {
        // Absent `liabilities`: a null model field and no `debt` key in the emitted result.
        val absentScenario = ContractScenarioParser.parse(scenarioJson())
        assertNull(absentScenario.liabilities)
        val absent = ContractRunner.run(absentScenario)
        assertNull(absent.debt)
        assertFalse(ContractResultEmitter.toJsonElement(absent).jsonObject.containsKey("debt"))

        // Declared empty: a zeroed `debt` block, payoff_date == as_of.
        val emptyScenario = ContractScenarioParser.parse(scenarioJson(""", "liabilities": []"""))
        assertNotNull(emptyScenario.liabilities)
        val emptyDebt = ContractRunner.run(emptyScenario).debt
        assertNotNull(emptyDebt)
        assertEquals(0, emptyDebt!!.monthsToPayoff)
        assertEquals(LocalDate.parse("2026-01-15"), emptyDebt.payoffDate)
        assertEquals(0, emptyDebt.totalPaidCents)
        assertTrue(emptyDebt.schedule.isEmpty())
        assertTrue(
            ContractResultEmitter.toJsonElement(ContractRunner.run(emptyScenario))
                .jsonObject.containsKey("debt"),
        )
    }

    @Test
    fun declaredLiabilityProducesTheDebtBlockWithTheCanonicalStepKeys() {
        val json = scenarioJson(
            """, "debt_strategy": "avalanche", "extra_monthly_payment_cents": 0,
            "liabilities": [
              {"id": "card", "balance_cents": 100000, "apr_basis_points": 1200, "min_payment_cents": 20000}
            ]""",
        )
        val result = ContractRunner.run(json)
        val debt = result.debt
        assertNotNull(debt)
        assertEquals("avalanche", debt!!.strategy)
        assertEquals(6, debt.monthsToPayoff)

        val debtJson = (ContractResultEmitter.toJsonElement(result) as JsonObject)["debt"]!!.jsonObject
        assertEquals(
            setOf(
                "strategy", "extra_monthly_payment_cents", "months_to_payoff", "payoff_date",
                "total_interest_cents", "total_paid_cents", "did_not_converge", "schedule",
            ),
            debtJson.keys,
        )
        val step = (debtJson["schedule"] as JsonArray)[0].jsonObject
        assertEquals(
            setOf(
                "month", "date", "liability_id", "starting_balance_cents", "payment_cents",
                "interest_cents", "principal_cents", "ending_balance_cents",
            ),
            step.keys,
        )
    }

    @Test
    fun parserRejectsDuplicateLiabilityIdsWithSchemaInvalid() {
        val json = scenarioJson(
            """, "liabilities": [
              {"id": "dup", "balance_cents": 1000, "apr_basis_points": 1200, "min_payment_cents": 100},
              {"id": "dup", "balance_cents": 2000, "apr_basis_points": 1200, "min_payment_cents": 100}
            ]""",
        )
        assertThrows(ContractException::class.java) {
            ContractScenarioParser.parse(json)
        }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }
    }

    @Test
    fun parserRejectsAnUnknownDebtStrategyOrLiabilityKind() {
        assertThrows(ContractException::class.java) {
            ContractScenarioParser.parse(scenarioJson(""", "debt_strategy": "waterfall""""))
        }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }

        assertThrows(ContractException::class.java) {
            ContractScenarioParser.parse(
                scenarioJson(
                    """, "liabilities": [
                      {"id": "a", "balance_cents": 1000, "apr_basis_points": 1200,
                       "min_payment_cents": 100, "kind": "mortgage"}
                    ]""",
                )
            )
        }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }
    }

    @Test
    fun parserRejectsAnUnknownLiabilityProperty() {
        assertThrows(ContractException::class.java) {
            ContractScenarioParser.parse(
                scenarioJson(
                    """, "liabilities": [
                      {"id": "a", "balance_cents": 1000, "apr_basis_points": 1200,
                       "min_payment_cents": 100, "surprise": 1}
                    ]""",
                )
            )
        }.also { assertEquals(ContractErrorCode.SCHEMA_INVALID, it.code) }
    }
}
