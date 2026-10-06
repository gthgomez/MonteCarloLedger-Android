package com.montecarlo.ledger

/**
 * Product-level feature flags for the MonteCarlo Contract 1.0 adoption (MC-06b).
 *
 * [contractForecastEnabled] routes the dashboard's forecast, Monte Carlo and safe-to-spend
 * outputs through the canonical Kotlin contract engine
 * (`com.montecarlo.ledger.contract`), which clears native bugs B-05 (opening-negative
 * ignored), B-06 (three percentile conventions) and B-07 (monthly recurrence anchor lost)
 * for those outputs.
 *
 * It is a mutable, volatile flag so it can be toggled at runtime (e.g. from a debug menu
 * or a settings-backed override) without rebuilding. It defaults to `true` because the
 * adoption is test-covered; flip it to `false` to fall back to the legacy native engines.
 *
 * Documented in `docs/contract-adoption.md`.
 */
object FeatureFlags {
    @Volatile
    var contractForecastEnabled: Boolean = true
}
