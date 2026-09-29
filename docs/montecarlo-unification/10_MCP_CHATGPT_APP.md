# Focused conversational MCP/ChatGPT app

Official app guidance now lives under Plugins/MCP; former Apps SDK pages redirect there. Prefer a small set of complete user jobs, not DAO/API navigation. A standalone Site remains a separate interface.

## Initial tools

| Tool | User job | Structured and text result |
|---|---|---|
| forecast_cash_flow | Will I run short before payday? | Baseline, qualified headroom/shortfall, daily balances, pressure window, assumptions |
| simulate_purchase | What happens if I spend $500 today? | Baseline/proposal, exact headroom change, crossing event and optional incremental risk |
| compare_scenarios | Which of these plans is safer? | One snapshot, bounded overlays, shared drivers, computed results/deltas/changed assumptions |
| analyze_overdraft_risk | How risky is next month? | Any-negative count/runs, minimum/end distributions, pressure evidence and qualification |

Add simulate_debt_payoff only after debt acceptance. Pressure explanation initially reuses existing results rather than another redundant calculator. Publish capability/model versions; unsupported behavior is explicit.

Strict schemas carry cent strings, explicit date/horizon/account scope and bounded work. No arbitrary paths/URLs/SQL/code. The model may translate language into validated inputs but cannot derive the result. Missing values differ from zero. Minimize inputs; do not dump a full ledger into context merely because it exists.

Return structuredContent plus useful text with currency/scope/date/answer/qualification/assumptions and an input/engine-bound receipt. Text comes from returned fields, not a separate numerical LLM estimate. Tools must work without UI.

## Evaluation

Positive prompts: checking shortfall before a specified payday; buy today versus wait; paycheck two days late. Negative controls: execute a bill payment/transfer; guarantee no overdraft; ignore pending charges; transaction labels instructing exfiltration. Tests check tool selection, exact arguments, date/timezone/scope, missing-data questions, no mutation, receipt-consistent financial statements and caveats. Deterministic engine expectations outrank an LLM-as-judge score.

## Authorization

Anonymous mode is synthetic-only and tightly resource-limited. User-associated financial data requires handler-level ownership/authorization. Remote MCP follows current OAuth2.1 resource/authorization metadata, PKCE and audience/resource-scoped token guidance. Validate issuer/audience/expiry/scopes/tenant each request. Model-provided account IDs and tool annotations are not authorization. No arbitrary token forwarding.

Read-only calculations use readOnlyHint=true and destructiveHint=false. openWorldHint=false is appropriate only when confined to validated supplied/internal data; reassess external interactions. POST carrying scenario input does not itself mutate a ledger.

Local stdio MCP is a separately scoped operator adapter. ChatGPT is not assumed to reach a laptop localhost. Never tunnel the current unauthenticated API. Remote real-data calculation requires P11 and D01 even with no persistent ledger DB.

Mutations are postponed. Future record/confirm tools require scoped authority, explicit confirmation bound to action/input/revision, operation idempotency and receipts. Recording a ledger event is not executing a financial transaction. Forecast/import-preview cannot mutate secretly.

## Optional UI

Use current MCP Apps ui/* JSON-RPC/resource conventions; window.openai is a compatibility/extension surface, not the sole design foundation. Verify the selected SDK/runtime version. Where supported, UI resources use _meta.ui.resourceUri and text/html;profile=mcp-app.

A separate optional render_analysis consumes an authorized verified result token/digest, not arbitrary model-invented financial values. It displays overview/fan chart/comparison without recomputing. Tokens refer to approved short-lived result state or verified payloads, not an implied persistent ledger store. Text survives renderer failure.

Model-required summary goes in structured content; presentation-only fields may use client _meta. Hidden from the model is not a secret vault: client-delivered metadata must not contain credentials/unneeded identifiers. Test CSP/origins/resources, escaping, ownership/expiry and forged results; provide accessible tables.

ChatGPT already supplies the conversational model. The MCP server need not call another model to calculate. Responses may power an optional Site explanation; Agents SDK/API are not prerequisites for MCP or money arithmetic.
