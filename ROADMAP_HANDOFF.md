# MonteCarloLedger — Next-Session Roadmap Handoff

> **Repository Location:** `C:\Workspace\Project_Android\MonteCarloLedger`  
> **Target SDK:** Android (AGP 8.x / Kotlin 2.x / Jetpack Compose / Room DB)  
> **Build Commands:** `.\gradlew testDebugUnitTest` | `.\gradlew assembleDebug`

---

## 1. Executive Summary & Current App State

`MonteCarloLedger` is a deterministic & stochastic personal finance management app that combines a 90-day cash flow engine with Monte Carlo simulations to detect overdraft risk, project safe-to-spend reserves, and automate budget management.

### Completed Work (Phases 1, 2, P1, P2, P3, P4, P5)

| Feature / System | Status | Highlights | Key Files |
|------------------|--------|------------|-----------|
| **Phase 1: Engine Math Fixes** | ✅ VERIFIED | Percentile mid-horizon insolvency detection, 64-bit `Long` arithmetic, unfloored income contribution math, semi-monthly date calculations. | [MonteCarloEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/MonteCarloEngine.kt), [ForecastEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/ForecastEngine.kt), [RecurrenceMath.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/RecurrenceMath.kt) |
| **Phase 2: Security & Domain Audit** | ✅ VERIFIED | Robust AES-GCM backup envelope HMAC-SHA256 verification, domain sign normalization, stochastic expense variance. | [SecurityUtils.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/security/SecurityUtils.kt), [DomainRules.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/domain/DomainRules.kt) |
| **P1: Merchant Auto-Categorization & Rules Engine** | ✅ VERIFIED | 4-tier precedence matching (`USER_EXACT` → `USER_KEYWORD` → `PRESET_DEFAULT` → `FALLBACK`), CSV ingest integration, retroactive rule application, `TransactionRulesScreen.kt`. | [CategoryRuleEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/CategoryRuleEngine.kt), [TransactionRulesScreen.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/ui/TransactionRulesScreen.kt) |
| **P2: Proactive Overdraft Action Center** | ✅ VERIFIED | Action Engine generating 1-click bill date shift recommendations, daily spend pace capping, and emergency asset transfer advice when Monte Carlo risk $>0\%$. | [OverdraftActionEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/OverdraftActionEngine.kt), [DashboardProductLayer.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/ui/DashboardProductLayer.kt) |
| **P3: Budget Pacing & Safe Spend Velocity Alerts** | ✅ VERIFIED | Spending velocity tracking (`ON_TRACK`, `WARNING`, `CRITICAL`), target vs actual daily pace gauge UI card. | [BudgetPacingEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/BudgetPacingEngine.kt), [DashboardProductLayer.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/ui/DashboardProductLayer.kt) |
| **P4: Debt Payoff & Cash Flow Impact Simulator** | ✅ VERIFIED | Snowball vs. Avalanche strategy simulation with cash flow safety guard preventing extra payments that risk overdraft. | [DebtPayoffEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/DebtPayoffEngine.kt), [DebtPayoffScreen.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/ui/DebtPayoffScreen.kt) |
| **P5: Android Glance Home Screen Widget** | ✅ VERIFIED | Native Glance widget showing safe-to-spend balance, next upcoming bill, and forecast risk status. | [MonteCarloLedgerGlanceWidget.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/widget/MonteCarloLedgerGlanceWidget.kt), [GlanceWidgetReceiver.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/widget/GlanceWidgetReceiver.kt) |

---

## 2. Next Roadmap Targets

1. **`Int` → `Long` Cents Migration (Room v10 → v11)**: Promote all monetary fields across Room entities to 64-bit `Long`.
2. **Package Namespace Rename**: Rename package from `com.example.app` to `com.montecarlo.ledger`.
3. **Release Build Hardening**: Fix `assembleRelease` Compose mapping compilation artifact.

---

### 🟢 P3: Budget Pacing & Safe Spend Velocity Alerts

#### Objective
Provide real-time variable spending velocity tracking. Warn the user if their current daily spending rate will exhaust their safe-to-spend reserve before their next paycheck.

#### Specifications
1. **Engine Layer (`BudgetPacingEngine.kt`)**:
   * **Input**: `safeToSpendCents`, `daysToPayday`, `currentMonthTransactions`, `categoryBudgets`.
   * **Calculations**:
     * `targetDailyVelocityCents = safeToSpendCents / daysToPayday`
     * `actualDailyVelocityCents = (spendingLast7Days / 7)`
     * `pacingStatus`: `ON_TRACK` (actual $\le$ target), `WARNING` (actual $> 1.15 \times$ target), `CRITICAL` (actual $> 1.4 \times$ target or safe-to-spend depleted in $< 7$ days).
2. **ViewModel & State (`MainViewModel.kt`, `AppUiState.kt`)**:
   * Expose `pacingState` flow combining transactions, budgets, and cash flow window.
3. **UI Component (`DashboardProductLayer.kt` & `ForecastScreen.kt`)**:
   * Render a **Spend Pacing Bar / Gauge** displaying `Actual: $X/day vs Target: $Y/day` with color-coded status pills (`Green`, `Amber`, `Red`).

#### Files to Create/Modify
- `[NEW]` [BudgetPacingEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/BudgetPacingEngine.kt)
- `[NEW]` [BudgetPacingEngineTest.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/test/java/com/example/app/processing/BudgetPacingEngineTest.kt)
- `[MODIFY]` [AppUiState.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/AppUiState.kt)
- `[MODIFY]` [MainViewModel.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/MainViewModel.kt)
- `[MODIFY]` [DashboardProductLayer.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/ui/DashboardProductLayer.kt)

---

### 🟡 P4: Debt Payoff & Cash Flow Impact Simulator

#### Objective
Allow users to simulate accelerating debt repayment (Snowball vs. Avalanche strategies + custom extra monthly payment amount) directly against their Monte Carlo 90-day cash flow forecast.

#### Specifications
1. **Engine Layer (`DebtPayoffEngine.kt`)**:
   * Calculate payoff date, total interest paid, and monthly payment schedules for debt payments (`type == "debt"` or category `"debt"`).
   * **Cash Flow Safety Guard**: Run Monte Carlo simulation with proposed extra monthly payment. If extra payment causes overdraft risk ($>0\%$), flag warning: *"Extra payment of $X exceeds safe reserve on [Date]"*.
2. **UI Component (`DebtPayoffScreen.kt`)**:
   * Slider for `Extra Monthly Payment ($0 - $1,000)`.
   * Switch between **Snowball** (lowest balance first) and **Avalanche** (highest interest first).
   * Visual comparison of original payoff date vs. accelerated payoff date & total interest saved.

#### Files to Create/Modify
- `[NEW]` [DebtPayoffEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/processing/DebtPayoffEngine.kt)
- `[NEW]` [DebtPayoffEngineTest.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/test/java/com/example/app/processing/DebtPayoffEngineTest.kt)
- `[NEW]` [DebtPayoffScreen.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/ui/DebtPayoffScreen.kt)
- `[MODIFY]` [MainViewModel.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/MainViewModel.kt)
- `[MODIFY]` [AppView.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/ui/AppView.kt)

---

### 🔵 P5: Android Glance Home Screen Widget

#### Objective
Provide a clean Android Glance home screen widget displaying the user's current Safe-to-Spend balance, next upcoming bill due, and forecast risk status at a glance.

#### Specifications
1. **Glance Provider (`MonteCarloLedgerGlanceWidget.kt`)**:
   * Uses `androidx.glance` Compose layout API for widgets.
   * Displays:
     * **Safe-to-Spend Balance**: Large green/cyan text (or red if deficit).
     * **Next Bill**: Name, amount, and due date.
     * **Forecast Status Pill**: "Stable", "Thin Runway", or "High Risk".
2. **Widget Receiver & Broadcast (`GlanceWidgetReceiver.kt`)**:
   * Trigger widget update whenever Room DB transactions, payments, or balance reconciliation state changes.

#### Files to Create/Modify
- `[NEW]` [MonteCarloLedgerGlanceWidget.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/widget/MonteCarloLedgerGlanceWidget.kt)
- `[NEW]` [GlanceWidgetReceiver.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/example/app/widget/GlanceWidgetReceiver.kt)
- `[MODIFY]` [AndroidManifest.xml](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/AndroidManifest.xml) (Register receiver & provider)

---

## 3. Verification Protocol for Next Session

When executing each phase in a new session:

1. **Unit Test Suite**: Run `.\gradlew testDebugUnitTest` after implementing engine logic. Verify all tests pass.
2. **Debug Assembly**: Run `.\gradlew assembleDebug` after UI integration to ensure clean compilation.
3. **Evidence Requirement**: Include exact test and build output in final turn report.
