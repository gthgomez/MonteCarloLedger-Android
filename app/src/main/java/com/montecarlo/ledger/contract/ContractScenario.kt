package com.montecarlo.ledger.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Canonical scenario model from `schemas/scenario.schema.json`.
 *
 * Parsing is intentionally defensive: a structural violation is [ContractErrorCode.SCHEMA_INVALID];
 * an amount whose sign contradicts its type is [ContractErrorCode.INVALID_AMOUNT]; a negative
 * horizon is [ContractErrorCode.INVALID_HORIZON]; `runs < 1` is [ContractErrorCode.INVALID_RUNS].
 */
data class ContractEvent(
    val id: String?,
    val name: String?,
    val date: LocalDate,
    val amountCents: Long,
    val type: String,
    /** Explicit `sequence` from the scenario, or null to use the type default. */
    val sequence: Int?,
    /** Position of the event in the scenario's `events` array (expanded recurrences continue after). */
    val inputIndex: Int,
    /** Optional expense category, used for contract 1.2 per-category variation. */
    val category: String? = null,
) {
    /** Contract default: income sorts first (0), expense/adjustment after (1). */
    val effectiveSequence: Int get() = sequence ?: if (type == "income") 0 else 1
}

data class ContractRecurrence(
    val id: String,
    val name: String?,
    val type: String,
    val amountCents: Long,
    val frequency: String,
    val startDate: LocalDate,
    val anchorDay: Int?,
    val endDate: LocalDate?,
    val expectedAmountCents: Long?,
    /** Optional expense category inherited by generated occurrences (contract 1.2). */
    val category: String? = null,
)

data class ContractSimulationParams(
    val runs: Int = 500,
    val seed: Long = 42,
    val incomeVariationMin: Int = -8,
    val incomeVariationMax: Int = 8,
    val expenseVariationMin: Int = 0,
    val expenseVariationMax: Int = 0,
    val surpriseProbabilityPpm: Int = 150_000,
    val surpriseCheckIntervalDays: Int = 14,
    val surpriseAmountMin: Int = 2000,
    val surpriseAmountMax: Int = 15_000,
    val quantileNum: Int = 1,
    val quantileDen: Int = 10,
    val reserveCents: Long = 0,
    /** Per-category expense variation (contract 1.2, MCD-0025): category -> inclusive percent range. */
    val expenseCategoryVariation: Map<String, IntRange> = emptyMap(),
)

/** A single `(recurrence_id, date)` occurrence to suppress from expansion (contract 1.1, MCD-0024). */
data class ContractRecurrenceExclusion(
    val recurrenceId: String,
    val date: LocalDate,
)

data class ContractScenario(
    val scenarioId: String,
    val asOf: LocalDate,
    val startingBalanceCents: Long,
    val horizonDays: Int,
    val events: List<ContractEvent>,
    val recurrences: List<ContractRecurrence>,
    val simulation: ContractSimulationParams?,
    /** Declared contract version, echoed into the result (contract 1.1). */
    val contractVersion: String = "1.0",
    /** Generated occurrences to omit, matched on `(recurrence_id, date)` (contract 1.1, MCD-0024). */
    val occurrenceExclusions: List<ContractRecurrenceExclusion> = emptyList(),
    /**
     * Declared liabilities (contract 2.0, MCD-0026). `null` means the key was absent (no `debt`
     * block is emitted); an empty list means `liabilities: []` was declared (a zeroed `debt` block).
     */
    val liabilities: List<ContractLiability>? = null,
    /** Payoff strategy, only meaningful when [liabilities] is present (contract 2.0). */
    val debtStrategy: String = "snowball",
    /** Extra monthly payment distributed by the strategy, only meaningful with [liabilities]. */
    val extraMonthlyPaymentCents: Long = 0,
) {
    /** Inclusive lower bound, exclusive upper bound of the contract forecast window. */
    val windowEndExclusive: LocalDate get() = asOf.plusDays(horizonDays.toLong())
}

object ContractScenarioParser {

    private val EVENT_KEYS = setOf("id", "name", "category", "date", "amount_cents", "type", "sequence")
    private val RECURRENCE_KEYS = setOf(
        "id", "name", "category", "type", "amount_cents", "frequency", "start_date",
        "end_date", "anchor_day", "expected_amount_cents",
    )
    private val SIMULATION_KEYS = setOf(
        "runs", "seed", "income_variation_min", "income_variation_max",
        "expense_variation_min", "expense_variation_max", "expense_category_variation",
        "surprise_probability_ppm", "surprise_check_interval_days", "surprise_amount_min",
        "surprise_amount_max", "quantile_num", "quantile_den", "reserve_cents",
    )
    private val CATEGORY_RANGE_KEYS = setOf("category", "min", "max")
    private val LIABILITY_KEYS = setOf(
        "id", "name", "balance_cents", "apr_basis_points", "min_payment_cents",
        "kind", "min_payment_percent_bps", "min_payment_floor_cents", "due_day_of_month",
    )
    private val SCENARIO_KEYS = setOf(
        "contract_version", "scenario_id", "as_of", "starting_balance_cents",
        "horizon_days", "events", "recurrences", "occurrence_exclusions", "simulation",
        "debt_strategy", "extra_monthly_payment_cents", "liabilities",
    )
    private val EXCLUSION_KEYS = setOf("recurrence_id", "date")
    private val SUPPORTED_CONTRACT_VERSIONS = setOf("1.0", "1.1", "1.2", "2.0")
    private val FREQUENCIES = setOf(
        "weekly", "biweekly", "semimonthly", "monthly", "bimonthly",
        "quarterly", "semiannually", "annually", "onetime",
    )

    fun parse(json: String): ContractScenario = parse(Json.parseToJsonElement(json))

    fun parse(element: JsonElement): ContractScenario {
        val root = element as? JsonObject ?: schema("scenario must be an object")
        rejectUnknown(root, SCENARIO_KEYS, "scenario")
        val contractVersion = requireString(root, "contract_version", "scenario")
        if (contractVersion !in SUPPORTED_CONTRACT_VERSIONS) {
            schema("contract_version must be one of ${SUPPORTED_CONTRACT_VERSIONS.sorted()}")
        }
        // MC-09: occurrence_exclusions is a 1.1 addition; a 1.0 document that carries it is
        // rejected rather than silently reinterpreted under 1.1 semantics.
        if (root.containsKey("occurrence_exclusions") && contractVersion == "1.0") {
            schema("occurrence_exclusions requires contract_version 1.1")
        }
        val scenarioId = requireString(root, "scenario_id", "scenario")
        if (scenarioId.isBlank()) schema("scenario_id must not be blank")

        if (!root.containsKey("as_of")) schema("scenario is missing required property: as_of")
        val asOfElement = root["as_of"]
        if (asOfElement is JsonNull || asOfElement !is JsonPrimitive) {
            throw ContractException(ContractErrorCode.MISSING_AS_OF, "as_of must be a date string")
        }
        val asOf = parseDate(asOfElement.content, "as_of")

        val startingBalance = requireLong(root, "starting_balance_cents", "scenario")
        val horizon = requireInt(root, "horizon_days", "scenario")
        if (horizon < 0) throw ContractException(ContractErrorCode.INVALID_HORIZON, "horizon_days = $horizon")

        val eventArray = root["events"] as? JsonArray ?: schema("scenario.events must be an array")
        val events = eventArray.mapIndexed { index, el ->
            parseEvent(el, index)
        }

        val recurrences = (root["recurrences"] as? JsonArray)?.map { parseRecurrence(it) } ?: emptyList()

        val exclusions = (root["occurrence_exclusions"] as? JsonArray)?.map { parseExclusion(it) } ?: emptyList()

        val simulation = root["simulation"]?.let { parseSimulation(it) }

        // Contract 2.0 (MCD-0026): liabilities + the debt strategy. Every optional; `liabilities`
        // stays null when the key is absent so the emitter knows to omit the `debt` block.
        val debtStrategy = root.getStringOrNull("debt_strategy") ?: "snowball"
        ContractDebt.validateStrategy(debtStrategy)
        val extraMonthlyPayment = root.longOrNull("extra_monthly_payment_cents") ?: 0L
        if (extraMonthlyPayment < 0) schema("extra_monthly_payment_cents must be >= 0")
        val liabilitiesElement = root["liabilities"]
        val liabilities: List<ContractLiability>? = when (liabilitiesElement) {
            null -> null
            is JsonArray -> liabilitiesElement.mapIndexed { index, el -> parseLiability(el, index) }
            else -> schema("scenario.liabilities must be an array")
        }
        if (liabilities != null) ContractDebt.validateLiabilities(liabilities)

        // Sign invariants (contracts/ledger.md): evaluated after structure so the code is precise.
        events.forEach { validateSign(it.type, it.amountCents, "event '${it.name ?: it.id ?: it.date}'") }
        recurrences.forEach { validateSign(it.type, it.amountCents, "recurrence '${it.id}'") }
        ContractMoney.requireValid(startingBalance, "starting_balance_cents")

        return ContractScenario(
            scenarioId = scenarioId,
            asOf = asOf,
            startingBalanceCents = startingBalance,
            horizonDays = horizon,
            events = events,
            recurrences = recurrences,
            simulation = simulation,
            contractVersion = contractVersion,
            occurrenceExclusions = exclusions,
            liabilities = liabilities,
            debtStrategy = debtStrategy,
            extraMonthlyPaymentCents = extraMonthlyPayment,
        )
    }

    private fun parseExclusion(element: JsonElement): ContractRecurrenceExclusion {
        val obj = element as? JsonObject ?: schema("occurrence_exclusion must be an object")
        rejectUnknown(obj, EXCLUSION_KEYS, "occurrence_exclusion")
        val recurrenceId = requireString(obj, "recurrence_id", "occurrence_exclusion")
        val date = parseDate(requireString(obj, "date", "occurrence_exclusion"), "occurrence_exclusion.date")
        return ContractRecurrenceExclusion(recurrenceId = recurrenceId, date = date)
    }

    private fun parseLiability(element: JsonElement, index: Int): ContractLiability {
        val obj = element as? JsonObject ?: schema("liabilities[$index] must be an object")
        rejectUnknown(obj, LIABILITY_KEYS, "liabilities[$index]")
        val id = requireString(obj, "id", "liabilities[$index]")
        val balance = requireLong(obj, "balance_cents", "liabilities[$index]")
        val apr = requireInt(obj, "apr_basis_points", "liabilities[$index]")
        val minPayment = requireLong(obj, "min_payment_cents", "liabilities[$index]")
        return ContractLiability(
            id = id,
            name = obj.getStringOrNull("name"),
            balanceCents = balance,
            aprBasisPoints = apr,
            minPaymentCents = minPayment,
            kind = obj.getStringOrNull("kind") ?: "installment",
            minPaymentPercentBps = obj.intOrNull("min_payment_percent_bps") ?: 0,
            minPaymentFloorCents = obj.longOrNull("min_payment_floor_cents") ?: 0L,
            dueDayOfMonth = obj.intOrNull("due_day_of_month") ?: 1,
        )
    }

    private fun parseEvent(element: JsonElement, index: Int): ContractEvent {
        val obj = element as? JsonObject ?: schema("event[$index] must be an object")
        rejectUnknown(obj, EVENT_KEYS, "event[$index]")
        val type = requireString(obj, "type", "event[$index]")
        if (type !in setOf("income", "expense", "adjustment")) {
            schema("event[$index].type must be income|expense|adjustment")
        }
        val date = parseDate(requireString(obj, "date", "event[$index]"), "event[$index].date")
        val amount = requireLong(obj, "amount_cents", "event[$index]")
        val sequence = obj["sequence"]?.let { requireInt(obj, "sequence", "event[$index]") }
        return ContractEvent(
            id = obj.getStringOrNull("id"),
            name = obj.getStringOrNull("name"),
            date = date,
            amountCents = amount,
            type = type,
            sequence = sequence,
            inputIndex = index,
            category = obj.getStringOrNull("category"),
        )
    }

    private fun parseRecurrence(element: JsonElement): ContractRecurrence {
        val obj = element as? JsonObject ?: schema("recurrence must be an object")
        val id = requireString(obj, "id", "recurrence")
        rejectUnknown(obj, RECURRENCE_KEYS, "recurrence '$id'")
        val type = requireString(obj, "type", "recurrence '$id'")
        if (type !in setOf("income", "expense")) schema("recurrence '$id'.type must be income|expense")
        val amount = requireLong(obj, "amount_cents", "recurrence '$id'")
        val frequency = requireString(obj, "frequency", "recurrence '$id'")
        if (frequency !in FREQUENCIES) schema("recurrence '$id'.frequency '$frequency' is not supported")
        val startDate = parseDate(requireString(obj, "start_date", "recurrence '$id'"), "recurrence '$id'.start_date")
        val endDate = obj.getStringOrNull("end_date")?.let { parseDate(it, "recurrence '$id'.end_date") }
        val anchor = obj["anchor_day"]?.let { requireInt(obj, "anchor_day", "recurrence '$id'") }
        if (anchor != null && anchor !in 1..31) schema("recurrence '$id'.anchor_day must be 1..31")
        val expected = obj["expected_amount_cents"]?.let { requireLong(obj, "expected_amount_cents", "recurrence '$id'") }
        return ContractRecurrence(
            id = id,
            name = obj.getStringOrNull("name"),
            type = type,
            amountCents = amount,
            frequency = frequency,
            startDate = startDate,
            anchorDay = anchor,
            endDate = endDate,
            expectedAmountCents = expected,
            category = obj.getStringOrNull("category"),
        )
    }

    private fun parseCategoryRanges(element: JsonElement?): Map<String, IntRange> {
        if (element == null || element is JsonNull) return emptyMap()
        val array = element as? JsonArray ?: schema("expense_category_variation must be an array")
        val result = LinkedHashMap<String, IntRange>()
        array.forEachIndexed { index, el ->
            val obj = el as? JsonObject ?: schema("expense_category_variation[$index] must be an object")
            rejectUnknown(obj, CATEGORY_RANGE_KEYS, "expense_category_variation[$index]")
            val category = requireString(obj, "category", "expense_category_variation[$index]")
            val min = requireInt(obj, "min", "expense_category_variation[$index]")
            val max = requireInt(obj, "max", "expense_category_variation[$index]")
            if (min > max) schema("expense_category_variation '$category': min > max")
            if (result.containsKey(category)) {
                schema("duplicate expense_category_variation category '$category'")
            }
            result[category] = min..max
        }
        return result
    }

    private fun parseSimulation(element: JsonElement): ContractSimulationParams {
        val obj = element as? JsonObject ?: schema("simulation must be an object")
        rejectUnknown(obj, SIMULATION_KEYS, "simulation")
        val defaults = ContractSimulationParams()
        val runs = obj.intOrNull("runs") ?: defaults.runs
        if (runs < 1) throw ContractException(ContractErrorCode.INVALID_RUNS, "runs = $runs")
        val seed = obj.longOrNull("seed") ?: defaults.seed
        if (seed < 0) schema("simulation.seed must be >= 0")
        val incomeMin = obj.intOrNull("income_variation_min") ?: defaults.incomeVariationMin
        val incomeMax = obj.intOrNull("income_variation_max") ?: defaults.incomeVariationMax
        val expenseMin = obj.intOrNull("expense_variation_min") ?: defaults.expenseVariationMin
        val expenseMax = obj.intOrNull("expense_variation_max") ?: defaults.expenseVariationMax
        if (incomeMax < incomeMin) schema("income_variation_max must be >= income_variation_min")
        if (expenseMax < expenseMin) schema("expense_variation_max must be >= expense_variation_min")
        val surprisePpm = obj.intOrNull("surprise_probability_ppm") ?: defaults.surpriseProbabilityPpm
        if (surprisePpm !in 0..1_000_000) schema("surprise_probability_ppm must be 0..1_000_000")
        val interval = obj.intOrNull("surprise_check_interval_days") ?: defaults.surpriseCheckIntervalDays
        if (interval < 1) schema("surprise_check_interval_days must be >= 1")
        val amountMin = obj.intOrNull("surprise_amount_min") ?: defaults.surpriseAmountMin
        val amountMax = obj.intOrNull("surprise_amount_max") ?: defaults.surpriseAmountMax
        if (amountMin < 0 || amountMax < 0) schema("surprise amount bounds must be >= 0")
        if (amountMax < amountMin) schema("surprise_amount_max must be >= surprise_amount_min")
        val quantileNum = obj.intOrNull("quantile_num") ?: defaults.quantileNum
        val quantileDen = obj.intOrNull("quantile_den") ?: defaults.quantileDen
        if (quantileNum < 1 || quantileDen < 1) schema("quantile must be positive")
        if (quantileNum > quantileDen) schema("quantile must be <= 1")
        val reserve = obj.longOrNull("reserve_cents") ?: defaults.reserveCents
        if (reserve < 0) schema("reserve_cents must be >= 0")
        val categoryVariation = parseCategoryRanges(obj["expense_category_variation"])

        return ContractSimulationParams(
            runs = runs,
            seed = seed,
            incomeVariationMin = incomeMin,
            incomeVariationMax = incomeMax,
            expenseVariationMin = expenseMin,
            expenseVariationMax = expenseMax,
            surpriseProbabilityPpm = surprisePpm,
            surpriseCheckIntervalDays = interval,
            surpriseAmountMin = amountMin,
            surpriseAmountMax = amountMax,
            quantileNum = quantileNum,
            quantileDen = quantileDen,
            reserveCents = reserve,
            expenseCategoryVariation = categoryVariation,
        )
    }

    private fun validateSign(type: String, amount: Long, what: String) {
        when (type) {
            "income" -> if (amount <= 0) invalidAmount("$what: income amount must be > 0 (was $amount)")
            "expense" -> if (amount >= 0) invalidAmount("$what: expense amount must be < 0 (was $amount)")
            "adjustment" -> if (amount == 0L) invalidAmount("$what: adjustment amount must be non-zero")
            else -> schema("$what: unknown type '$type'")
        }
    }

    private fun invalidAmount(message: String): Nothing =
        throw ContractException(ContractErrorCode.INVALID_AMOUNT, message)

    private fun parseDate(raw: String, what: String): LocalDate =
        try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            schema("$what is not an ISO-8601 date: '$raw'")
        }

    private fun requireString(obj: JsonObject, key: String, context: String): String {
        val p = obj[key] as? JsonPrimitive ?: schema("$context.$key is required and must be a string")
        if (!p.isString) schema("$context.$key must be a string")
        return p.content
    }

    private fun requireLong(obj: JsonObject, key: String, context: String): Long {
        val v = obj.longOrNull(key) ?: schema("$context.$key is required and must be an integer")
        return v
    }

    private fun requireInt(obj: JsonObject, key: String, context: String): Int {
        val v = obj.longOrNull(key) ?: schema("$context.$key is required and must be an integer")
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) schema("$context.$key is out of integer range")
        return v.toInt()
    }

    private fun rejectUnknown(obj: JsonObject, allowed: Set<String>, context: String) {
        val unknown = obj.keys - allowed
        if (unknown.isNotEmpty()) schema("$context has unknown properties: ${unknown.sorted()}")
    }

    private fun schema(message: String): Nothing =
        throw ContractException(ContractErrorCode.SCHEMA_INVALID, message)

    internal fun JsonObject.getString(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    internal fun JsonObject.getStringOrNull(key: String): String? = getString(key)

    internal fun JsonObject.longOrNull(key: String): Long? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p.isString) return null
        return p.longOrNull
    }

    internal fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()
}
