package com.montecarlo.ledger.adoption

/**
 * The single Monte Carlo configuration shared by every product surface (dashboard, widget and
 * scenario operations).
 *
 * The dashboard and the home-screen widget answer the same financial question for the same
 * ledger. If they run a different number of scenarios (the widget historically used 100 while
 * the dashboard used 500), their probabilities and quantiles are drawn from different samples
 * and can disagree even though both are "canonical". Sharing this configuration — not merely a
 * calibration class — is what makes their conclusions comparable.
 *
 * A surface that intentionally needs a different workload must expose that difference explicitly;
 * it must not present its output as interchangeable with this configuration's.
 */
object CanonicalSimulationConfig {
    const val RUNS: Int = 500
    const val SEED: Long = 42L
}
