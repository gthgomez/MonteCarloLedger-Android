# Anti-drift and conformance

Target behavior is04 plus an approved fixture release, not whichever engine currently passes more tests. Keep legacy characterization separate from target certification.

## Equality levels

| Level | Required comparison |
|---|---|
| Validation | Exact accepted value/error: grammar, type, sign, range, date, reference, recurrence |
| Deterministic financial behavior | Exact cents, identity, dates, ordering, transfers, observations, pending transitions, forecasts, debt |
| Stochastic reducer with supplied draws | Exact paths/minimum/end/daily quantiles and negative counts |
| Native seeded replay | Exact within the same implementation/RNG/model version |
| Sampling laws | Predeclared invariant/statistical equivalence, not native sequence identity |
| Persistence | Semantic round trip, idempotency, atomic rollback and crash recovery |

Do not automatically regenerate expected outputs from Python or Kotlin. Financial-semantic changes require an ADR, fixture/model version and explicit old/new comparison. A green UI snapshot does not prove monetary correctness.

## Fixture release and runners

After D00 grants appropriate rights, publish one `conformance/v1/` bundle with contract, schemas, fixtures, provenance/license and SHA-256 manifest. Both repositories vendor the same pinned digest; CI does not fetch a moving branch. Each result/report records contract/model/engine versions, source revision, fixture hash, supported profile, failures and unsupported cases. Missing mandatory cases fail an advertised profile; unsupported debt cannot be silently claimed complete.

Proposed Python runner: `tests/conformance/test_contract.py`, adapter `monte_carlo_ledger/conformance/adapter.py`. Kotlin: `app/src/test/java/com/montecarlo/ledger/conformance/ContractFixtureTest.kt`, pure contract DTO/adapter package, test resources. Production engines do not depend on test runners. Preserve local SQL IDs behind portable mappings.

The synthetic examples are operation-level fixtures: `{id, operation, mode, input, expected}`. Compare expected fields recursively and validate complete result schema/invariants separately. They are proposed requirements, not evidence that either current engine passes. Diagnostics/timing/generated nonfinancial IDs may be excluded explicitly, not arbitrary numerical fields.

Coverage must include money bounds/signs/overflow; leap/month-end/weekly/biweekly/semimonthly/monthly/two-month/quarterly/yearly/once; overdue/moved/paid/skipped bills; same-name multiple incomes and one-occurrence overrides; same-day phases; pending replacement and linked bill suppression; separate cash/credit accounts; budgets/refunds; installment/revolving minima/nonconvergence; opening-negative windows/headroom; fixed-seed zero-noise and shared-draw Monte Carlo; extreme percentiles; coverage/calibration/category variance; and backup round-trip/rejection.

## Metamorphic properties

Input permutation cannot change canonical output. A purchase cannot improve deterministic headroom with all other assumptions fixed. Transfers conserve combined net value but can alter account-specific risk. Same operation key/input posts once; changed input conflicts. Moving a bill preserves identity and produces one event. Paid/skipped/pending-covered obligations do not generate duplicate debits. Pending→posted releases hold and posts once.

Zero-noise simulation equals deterministic output. Opening negativity counts without events. Explicit shocks can occur without base events. Chart enablement/batching cannot change results. Every money operation either stays in range or fails before mutation. Backup identity/link/money round trips are exact, and invalid restore leaves target unchanged.

## RNG policy

Python Random and Kotlin Random are not assumed sequence-compatible. Native replay is version-scoped. Exact cross-language stochastic fixtures inject keyed outcomes `(run, driver, stable target, draw kind)`; common scenario drivers avoid unrelated random shifts when new events are inserted. Standardizing a PRNG alone would not standardize bounded sampling, distribution transforms or rounding; a later portable algorithm needs full versioned vectors.

Sampling tests compare CDFs against known finite laws/Bernoulli probabilities with fixed sample/seed panels and a predeclared family error budget. One conservative per-sample DKW bound is sqrt(log(2/alpha)/(2N)); use the sum of independent sample bounds for two empirical CDFs, splitting alpha across the planned family. Record statistic/N/alpha/bound. Do not retry until green or use a universal dollar epsilon for discontinuous quantiles. Performance limits are a separate test class.

## Commands and release evidence

After runners exist:

```sh
python -m pytest tests/conformance -q
./gradlew :app:testDebugUnitTest --tests 'com.montecarlo.ledger.conformance.*'
```

These are planned commands, not runnable current-head claims. Add ordinary unit/property tests, SQLite/Room migration suites and emulator tests separately. Cross-repo qualification must name both exact tested revisions and common fixture digest. An independent reviewer checks expected financial meaning and failure handling, not only count of passing tests.
