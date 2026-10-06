package com.montecarlo.ledger.contract

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Canonical forecast block (`schemas/result.schema.json#/$defs/forecast`). */
data class ContractForecastResult(
    val minimumBalanceCents: Long,
    val minimumBalanceDate: LocalDate,
    val endingBalanceCents: Long,
    val firstNegativeDate: LocalDate?,
)

/** Canonical risk block (`schemas/result.schema.json#/$defs/risk`). */
data class ContractRiskResult(
    val negativeBalanceProbabilityPpm: Long,
    val minimumBalanceP10Cents: Long,
    val minimumBalanceP50Cents: Long,
    val minimumBalanceP90Cents: Long,
    val endingBalanceP10Cents: Long,
    val endingBalanceP50Cents: Long,
    val endingBalanceP90Cents: Long,
    val projectedLowPointCents: Long,
    val safeToSpendCents: Long,
)

/** Canonical result (`schemas/result.schema.json`). */
data class ContractResult(
    val scenarioId: String,
    val forecast: ContractForecastResult,
    val risk: ContractRiskResult? = null,
    val contractVersion: String = "1.0",
)

/**
 * Emits exactly the fields of `result.schema.json`. Only contract fields are written; JSON object
 * key order is not observable to the comparer.
 */
object ContractResultEmitter {

    fun toJsonElement(result: ContractResult): JsonElement = buildJsonObject {
        put("contract_version", result.contractVersion)
        put("scenario_id", result.scenarioId)
        put("forecast", forecastElement(result.forecast))
        result.risk?.let { put("risk", riskElement(it)) }
    }

    fun toJsonString(result: ContractResult): String =
        toJsonElement(result).toString()

    private fun forecastElement(f: ContractForecastResult): JsonObject = buildJsonObject {
        put("minimum_balance_cents", f.minimumBalanceCents)
        put("minimum_balance_date", f.minimumBalanceDate.toString())
        put("ending_balance_cents", f.endingBalanceCents)
        if (f.firstNegativeDate == null) {
            put("first_negative_date", JsonNull)
        } else {
            put("first_negative_date", f.firstNegativeDate.toString())
        }
    }

    private fun riskElement(r: ContractRiskResult): JsonObject = buildJsonObject {
        put("negative_balance_probability_ppm", r.negativeBalanceProbabilityPpm)
        put("minimum_balance_p10_cents", r.minimumBalanceP10Cents)
        put("minimum_balance_p50_cents", r.minimumBalanceP50Cents)
        put("minimum_balance_p90_cents", r.minimumBalanceP90Cents)
        put("ending_balance_p10_cents", r.endingBalanceP10Cents)
        put("ending_balance_p50_cents", r.endingBalanceP50Cents)
        put("ending_balance_p90_cents", r.endingBalanceP90Cents)
        put("projected_low_point_cents", r.projectedLowPointCents)
        put("safe_to_spend_cents", r.safeToSpendCents)
    }

    /** Renders a result as a plain `json`-parseable string (used by the emitter test). */
    fun toCompactJsonString(result: ContractResult): String =
        toJsonElement(result).toString()
}
