# MonteCarloLedger — Next-Session Roadmap Handoff

> **Repository Location:** `C:\Workspace\Project_Android\MonteCarloLedger`  
> **Target SDK:** Android (AGP 9.2 / Kotlin 2.3 / Jetpack Compose / Room DB 2.8)  
> **Package Namespace:** `com.montecarlo.ledger`  
> **Build Commands:** `.\gradlew.bat :app:testDebugUnitTest` | `.\gradlew.bat :app:assembleRelease`

---

## 1. Executive Summary & Current App State

`MonteCarloLedger` is a deterministic & stochastic personal finance management app that combines a 90-day cash flow engine with Monte Carlo simulations to detect overdraft risk, project safe-to-spend reserves, and automate budget management.

### Completed Work (Phases 1–4, P1–P5)

| Feature / System | Status | Highlights | Key Files |
|------------------|--------|------------|-----------|
| **Phase 1: Engine Math Fixes** | ✅ VERIFIED | Percentile mid-horizon insolvency detection, 64-bit `Long` arithmetic, unfloored income contribution math, semi-monthly date calculations. | [MonteCarloEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/processing/MonteCarloEngine.kt), [ForecastEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/processing/ForecastEngine.kt), [RecurrenceMath.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/processing/RecurrenceMath.kt) |
| **Phase 2: Security & Domain Audit** | ✅ VERIFIED | Robust AES-GCM backup envelope HMAC-SHA256 verification, domain sign normalization, stochastic expense variance. | [SecurityUtils.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/security/SecurityUtils.kt), [DomainRules.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/domain/DomainRules.kt) |
| **P1: Merchant Auto-Categorization & Rules Engine** | ✅ VERIFIED | 4-tier precedence matching (`USER_EXACT` → `USER_KEYWORD` → `PRESET_DEFAULT` → `FALLBACK`), CSV ingest integration, retroactive rule application, `TransactionRulesScreen.kt`. | [CategoryRuleEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/processing/CategoryRuleEngine.kt), [TransactionRulesScreen.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/ui/TransactionRulesScreen.kt) |
| **P2: Proactive Overdraft Action Center** | ✅ VERIFIED | Action Engine generating 1-click bill date shift recommendations, daily spend pace capping, and emergency asset transfer advice when Monte Carlo risk $>0\%$. | [OverdraftActionEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/processing/OverdraftActionEngine.kt), [DashboardProductLayer.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/ui/DashboardProductLayer.kt) |
| **P3: Budget Pacing & Safe Spend Velocity Alerts** | ✅ VERIFIED | Spending velocity tracking (`ON_TRACK`, `WARNING`, `CRITICAL`), target vs actual daily pace gauge UI card. | [BudgetPacingEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/processing/BudgetPacingEngine.kt), [DashboardProductLayer.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/ui/DashboardProductLayer.kt) |
| **P4: Debt Payoff & Cash Flow Impact Simulator** | ✅ VERIFIED | Snowball vs. Avalanche strategy simulation with cash flow safety guard preventing extra payments that risk overdraft. | [DebtPayoffEngine.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/processing/DebtPayoffEngine.kt), [DebtPayoffScreen.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/ui/DebtPayoffScreen.kt) |
| **P5: Android Glance Home Screen Widget** | ✅ VERIFIED | Native Glance widget showing safe-to-spend balance, next upcoming bill, and forecast risk status. | [MonteCarloLedgerGlanceWidget.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/widget/MonteCarloLedgerGlanceWidget.kt), [GlanceWidgetReceiver.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/widget/GlanceWidgetReceiver.kt) |
| **Phase 2 (Core): Long Cents Precision Migration** | ✅ VERIFIED | Room DB v10->v11 (`MIGRATION_10_11`), all entities, DAOs, backup snapshots, engines, and view models promoted to 64-bit `Long` cents. | [AppDatabase.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/data/AppDatabase.kt), [LedgerBackupSnapshot.kt](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/src/main/java/com/montecarlo/ledger/data/BackupSnapshot.kt) |
| **Phase 3 (Core): Production Package Namespace Rename** | ✅ VERIFIED | Moved all main, unit test, and androidTest packages from `com.example.app` to `com.montecarlo.ledger`, updated ProGuard rules and schema paths. | [build.gradle.kts](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/build.gradle.kts), [proguard-rules.pro](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/proguard-rules.pro) |
| **Phase 4 (Core): Release Build Hardening** | ✅ VERIFIED | Resolved `compose-group-mapping` artifact resolution, enabled R8 minification, verified `assembleRelease`. | [build.gradle.kts](file:///C:/Workspace/Project_Android/MonteCarloLedger/app/build.gradle.kts) |

---

## 2. Verification Protocol

When validating modifications:

1. **Unit Test Suite**: Run `.\gradlew.bat :app:testDebugUnitTest --no-daemon`. All 182 unit tests must pass cleanly.
2. **Release Build Assembly**: Run `.\gradlew.bat :app:assembleRelease --no-daemon`. Must produce signed or unsigned release APK with 0 compilation or R8 errors.
