# Scenario operations: "afford this?" and "what if my paycheck is late?" (WS6)

General-purpose purchase / delayed-paycheck comparisons for Android, mirroring the Python
engine's `decisions.simulate_purchase` and `decisions.simulate_paycheck_delay`.

## API

`com.montecarlo.ledger.scenario.ScenarioOps` (contract semantics only — no forecast math):

| Function | Meaning |
|---|---|
| `simulatePurchase(amountCents, date, inputs)` | baseline scenario vs. the same scenario plus one dated expense |
| `simulatePaycheckDelay(days, inputs)` | baseline scenario vs. the earliest in-window income moved `days` later |
| `applyPurchase(scenario, amountCents, date)` | the purchase scenario mutation alone |
| `applyPaycheckDelay(scenario, days)` | the delay scenario mutation alone |

Each `simulate*` call (a) builds the current scenario with
`adoption.ContractScenarioBridge.build`, (b) applies the mutation, (c) runs **both** scenarios
through `contract.ContractRunner`, and (d) returns `baseline` / `withPurchase` (or `delayed`) /
`change`, where each headline carries `safeToSpendCents`, `projectedLowPointCents`,
`projectedLowPointDate`, `probabilityNegativePpm` and `firstNegativeDate`, and `change` carries the
same fields as modified-minus-baseline (`firstNegativeDateShiftDays` is null unless both scenarios
have a first-negative date, so "appeared"/"disappeared" is never encoded as a zero).

`scenario.ScenarioComparisonText` formats those values for the UI (`ui/ScenarioWhatIfCard.kt`, shown
at the top of the Forecast tab). `MainViewModel` holds the canonical scenario inputs produced by the
latest `DashboardDeriver` derivation, so a hypothetical is always measured against the scenario on
screen.

## Scenario mutations and Python parity

**Purchase** — Python appends
`{"date": d, "amount_cents": -abs(amount), "type": "expense", "name": "Planned purchase"}` to a copy
of the materialized scenario and forces the scalar expense variation to zero
(`setdefault("expense_variation_min", 0)` / `expense_variation_max`) *precisely so the injected
purchase consumes no RNG draw* — otherwise the extra event would shift every later draw and the
delta would not be attributable to the purchase. `applyPurchase` mirrors both halves: the event is
appended with `sequence = null` (contract default `1` for an expense) and `inputIndex = events.size`
(the same lower tie-break the appended Python event gets), and the scalar expense range is zeroed
for **both** runs.

*Documented deviation:* contract 1.2 per-category variation is not cleared. The injected purchase
carries no category, so it still consumes no draw; the category draws taken by other events are
identical and identically ordered in both runs, so they cancel in the delta. Clearing them would
change the baseline away from the scenario the dashboard shows. Keeping a non-zero *scalar* range
would instead break the draw-alignment guarantee: on the WS6 fixture, Python with
`expense_variation_min/max = -10/10` kept in place reports a purchase delta of `-23661` cents
instead of the true `-20000`.

**Paycheck delay** — Python's `_shift_first_income` moves the date of the earliest materialized
*income event* by N days and touches nothing else. `applyPaycheckDelay` mirrors that at the contract
level: the occurrence the income recurrence would still generate on the original date is suppressed
with a contract 1.1 `occurrence_exclusions` entry (MCD-0024) and one explicit income event carries
the shifted date, so exactly one paycheck moves and the rest of the schedule keeps its month anchor
(a 1/20 paycheck delayed 25 days becomes 2/14, while 2/20 and 3/20 stay put). A shift that leaves
the window removes the paycheck in both implementations. Edge case: with two incomes starting on the
same day Python's stable sort keeps list order while Kotlin breaks the tie on `input_index`; both
pick "the first one".

## Python parity harness

Run against the vendored reference engine (`monte_carlo_ledger/contract/engine.py`, contract 1.2)
in the Python worktree:

```python
"""WS6 parity harness: run the Python reference contract engine on the shared WS6 fixture.

The fixture is the materialized form of the Android bridge fixture used by ScenarioOpsTest:
  as_of 2026-01-01, horizon 90, starting balance $2,000.00
  rent   -150000 on 1/10, 2/10, 3/10
  income +100000 on 1/20, 2/20, 3/20

The second scenario is exactly what decisions.simulate_purchase builds: a copy of the first with
one explicit expense event appended and the scalar expense variation forced to zero.
"""
import json
import sys
import types
from datetime import date, timedelta

BASE = "/home/linuxuser/MonteCarlo-Ledger/.factory-wt/base-master/monte_carlo_ledger/contract"
pkg = types.ModuleType("mccontract")
pkg.__path__ = [BASE]
sys.modules["mccontract"] = pkg
import mccontract.engine as engine  # noqa: E402

SIM = {
    "runs": 500, "seed": 42,
    "income_variation_min": -8, "income_variation_max": 8,
    "expense_variation_min": 0, "expense_variation_max": 0,
    "surprise_probability_ppm": 150000, "surprise_check_interval_days": 14,
    "surprise_amount_min": 2000, "surprise_amount_max": 15000,
    "quantile_num": 1, "quantile_den": 10, "reserve_cents": 0,
}

EVENTS = [
    {"date": "2026-01-10", "amount_cents": -150000, "type": "expense", "name": "Rent"},
    {"date": "2026-01-20", "amount_cents": 100000, "type": "income", "name": "Paycheck"},
    {"date": "2026-02-10", "amount_cents": -150000, "type": "expense", "name": "Rent"},
    {"date": "2026-02-20", "amount_cents": 100000, "type": "income", "name": "Paycheck"},
    {"date": "2026-03-10", "amount_cents": -150000, "type": "expense", "name": "Rent"},
    {"date": "2026-03-20", "amount_cents": 100000, "type": "income", "name": "Paycheck"},
]

BASELINE = {
    "contract_version": "1.2", "scenario_id": "ws6-parity", "as_of": "2026-01-01",
    "starting_balance_cents": 200000, "horizon_days": 90,
    "events": EVENTS, "simulation": SIM,
}

WITH_PURCHASE = json.loads(json.dumps(BASELINE))
WITH_PURCHASE["events"].append(
    {"date": "2026-01-15", "amount_cents": -20000, "type": "expense", "name": "Planned purchase"}
)

DELAYED = json.loads(json.dumps(BASELINE))
incomes = sorted((e for e in DELAYED["events"] if e["type"] == "income"), key=lambda e: e["date"])
incomes[0]["date"] = (date.fromisoformat(incomes[0]["date"]) + timedelta(days=25)).isoformat()

for label, scenario in (("baseline", BASELINE), ("with_purchase", WITH_PURCHASE), ("delayed", DELAYED)):
    result = engine.run_scenario(scenario)
    print(label, json.dumps({"forecast": result["forecast"], "risk": result["risk"]}, sort_keys=True))
```

Observed output of the harness above (wrapped for width; the run prints the complete forecast and
risk blocks):

```
baseline {"forecast": {"ending_balance_cents": 50000, "first_negative_date": "2026-03-10",
    "minimum_balance_cents": -50000, "minimum_balance_date": "2026-03-10"},
  "risk": {"ending_balance_p10_cents": 25439, "ending_balance_p50_cents": 42179,
    "ending_balance_p90_cents": 57212, "minimum_balance_p10_cents": -70343,
    "minimum_balance_p50_cents": -56677, "minimum_balance_p90_cents": -43000,
    "negative_balance_probability_ppm": 1000000, "projected_low_point_cents": -50000,
    "safe_to_spend_cents": -70343}}
with_purchase {"forecast": {"ending_balance_cents": 30000, "first_negative_date": "2026-02-10",
    "minimum_balance_cents": -70000, "minimum_balance_date": "2026-03-10"},
  "risk": {"ending_balance_p10_cents": 5439, "ending_balance_p50_cents": 22179,
    "ending_balance_p90_cents": 37212, "minimum_balance_p10_cents": -90343,
    "minimum_balance_p50_cents": -76677, "minimum_balance_p90_cents": -63000,
    "negative_balance_probability_ppm": 1000000, "projected_low_point_cents": -70000,
    "safe_to_spend_cents": -90343}}
delayed {"forecast": {"ending_balance_cents": 50000, "first_negative_date": "2026-02-10",
    "minimum_balance_cents": -100000, "minimum_balance_date": "2026-02-10"},
  "risk": {"ending_balance_p10_cents": 25439, "ending_balance_p50_cents": 42179,
    "ending_balance_p90_cents": 57212, "minimum_balance_p10_cents": -113696,
    "minimum_balance_p50_cents": -100000, "minimum_balance_p90_cents": -100000,
    "negative_balance_probability_ppm": 1000000, "projected_low_point_cents": -100000,
    "safe_to_spend_cents": -113696}}
```

The purchase deltas implied by those runs (`-20000` for both `minimum_balance_p10_cents` and
`safe_to_spend_cents`, `0` for the probability) and the delay delta (`-43353` safe-to-spend) match
the earlier, more detailed harness run to the cent.

`ScenarioOpsTest.purchaseComparisonMatchesThePythonReferenceNumbers` and
`ScenarioOpsTest.paycheckDelayComparisonMatchesThePythonReferenceNumbers` assert exactly those
numbers against the **Android** pipeline — `ContractScenarioBridge` recurrences → `ContractRunner` →
`ScenarioOps`. The Android fixture reaches the same in-window events (rent 1/10, 2/10, 3/10;
paycheck 1/20, 2/20, 3/20) with no ordering ties, so the canonical ordering and the RNG draw stream
are identical to Python's materialized event list, and the simulation parameters are the ones
Python's `decisions.DEFAULT_SIM` resolves to (`runs 500`, `seed 42`, income ±8%, expense 0%,
surprises on).

`ScenarioOpsTest.purchaseDatedOnTheAsOfDayShiftsEverySimulatedTroughByExactlyTheAmount` pins the
draw-alignment rule on a calibrated-style fixture with a non-zero scalar expense range: with the
purchase dated on `as_of`, every simulated trough moves by exactly the purchase amount, which cannot
happen if the injected event consumed a draw.

## Residual risk

- The comparison reuses the `simulation` block of the current scenario (calibrated ranges in the
  app), not Python's uncalibrated defaults, so absolute safe-to-spend figures differ from the Python
  CLI. The parity claim is about *semantics and deltas*, not about identical headline numbers on a
  calibrated ledger.
- `scenario_inputs` is in-memory: a what-if run before the first successful derivation (or in
  `Native` mode, or in the `Failed` state with nothing retained) reports "no canonical forecast
  scenario is available right now".
- The purchase comparison deliberately ignores contract 2.0 liabilities; it answers a cash-ledger
  question and does not model debt-schedule effects.
