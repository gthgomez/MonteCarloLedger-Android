package com.montecarlo.ledger.contract

/**
 * Canonical error codes defined by MonteCarlo Contract 1.0 (the scenario schema and the contract
 * documents).
 *
 * These codes cross the contract boundary unchanged; they are what the fixtures' `expected.error`
 * blocks assert and what the dumb comparer (`tools/conformance/compare.py`) matches.
 */
enum class ContractErrorCode {
    SCHEMA_INVALID,
    INVALID_AMOUNT,
    MONEY_OVERFLOW,
    MISSING_AS_OF,
    INVALID_HORIZON,
    INVALID_RUNS,
}

/**
 * Thrown by the contract path for any violation of Contract 1.0. The [code] is the only
 * contract-observable part; the message is diagnostic.
 */
class ContractException(
    val code: ContractErrorCode,
    message: String,
) : RuntimeException("$code: $message")
