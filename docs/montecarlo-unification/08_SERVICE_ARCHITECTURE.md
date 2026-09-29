# Application services and adapters

P-API's current unauthenticated local GET is not a hosted service. Its lifespan initializes/migrates SQLite; separate consistency/timeline reads do not prove one snapshot. Do not expose it publicly unchanged.

## Dependency direction

CLI/local HTTP/hosted API/MCP/Android UI → application queries/commands/snapshot builder → immutable canonical domain → recurrence/occurrences/deterministic reducer. Stochastic/scenario/debt orchestration reuses that reducer. Repository/UOW ports connect application state to SQLite/Room. Pure financial code imports neither databases, UI, clocks, network nor model SDKs.

Proposed Python modules: domain/{money,dates,models,recurrence}; application/{commands,queries,snapshot,ports}; engine/{forecast,simulation,scenario,debt,calibration}; storage/{sqlite_repository,migrations,paths}; adapters/{cli,http,mcp}. Existing db_manager, forecasting, timeline_service, risk, cli and api remain compatibility facades until migrated. Update package discovery. Android uses existing domain/processing plus new application services; LedgerRepository implements persistence/UOW and DashboardDeriver presents results, not another ledger engine.

`build_snapshot(scope,as_of,expected_revision)` reads relevant state in one consistent transaction and returns immutable DTOs/coverage/reconciliation. Long computation runs outside the write lock on that revision. A later ledger change makes the UI result stale, not numerically relabeled current. Commands validate first, then receipt+posting+links+cursor/cache+revision commit atomically.

## Stateless calculations

POST /v1/calculations/forecast: minimal normalized snapshot, scope, as_of/end, reserve/policy → account-aware baseline/windows/headroom/qualification.

POST /v1/calculations/risk: same plus explicit model/drivers/seed → baseline, minimum/end/daily distributions/counts.

POST /v1/calculations/compare: one snapshot and bounded overlays → comparable results, computed deltas and changed assumptions.

POST /v1/calculations/debt: named debt terms/model plus cash snapshot → payoff and cash guard; disabled until debt conformance.

POST here transmits calculation input; it is not a ledger mutation. No permanent financial tables are required. Process minimal requests in memory, disable body logs/caches, return results. Infrastructure retention remains a separate disclosure; no application persistence is not guaranteed zero retention.

Strict schemas reject SQL, file paths, arbitrary URLs/code/import names. Initial proposed hosted limits:366 days,5000 runs,10000 events,4 scenarios plus byte/concurrency/time limits, adjustable downward after benchmarks. These are design limits, not performance claims. Local batch mode may allow larger bounded work. Return limit errors/cancellation honestly.

Envelope includes input digest, revision, versions, currency/scope/date/horizon, result, assumptions, warnings and qualification. 400/422 validation;409 revision/idempotency conflict;413 size;429 quota;503 unavailable. Failure never yields invented financial values.

## Local versus hosted security

Local HTTP is off by default, explicit loopback only with a protected session bearer secret, Host/Origin allowlist and browser pairing where applicable. CORS is not authentication; loopback does not exclude other local processes. Defend DNS rebinding and CSRF. No startup migration in a read-only server. Local mutation routes are separate and require authorization, operation identity, target revision and confirmation.

Hosted deployment cannot import the local mutation router or obtain a user's local DB path. No public tunnel to the current API. TLS, request isolation, authorization for user-associated data and resource control remain necessary for stateless computation. Keep a local compatibility GET temporarily, but redact financial values from generic errors.

## Optional AI layer

Intent → schema-constrained arguments → semantic validation → engine → structured output → explanation. JSON validity does not prove finance semantics or authority. Missing dates/accounts must be requested or shown as missing, never invented. The Site may use Responses for this optional application-owned layer; current OpenAI documentation recommends Responses for new projects. GPT-6 model guidance is current, but model choice/reasoning effort should be evaluated on extraction/explanation accuracy and cost. Straightforward instructions and explicit constraints are preferable to demanding hidden chain-of-thought as proof.

Agents SDK is an application-owned orchestration option; current Agents API is a managed durable agent harness. Neither is needed for a calculator. ChatGPT MCP can use ChatGPT's model without another model call. Avoid durable sessions, vector stores, hosted files and autonomous tool scopes merely to do arithmetic.

Minimize model input, explicitly review store=false and caching/abuse-monitoring/feature retention, and do not claim zero retention from one flag. No model has permission to post merely because it selected a tool. See official sources16 and privacy11.
