# CONTRACTS.md — frozen after Phase 0

Everything below is **read-only** for subagents. Code against these interfaces; do not edit `:core`.
If a contract feels wrong, STOP and flag it to the integrator (human) — do not change it unilaterally.

## Modules & ownership

| Module | Type | Owner | Depends on |
|---|---|---|---|
| `:core` | pure Kotlin | (frozen) | serialization, coroutines-core |
| `:validate` | pure Kotlin | Agent D | `:core` |
| `:escalate` | android-lib | Agent D | `:core`, okhttp |
| `:data` | android-lib | Agent B | `:core`, Room+KSP |
| `:agent` | android-lib | Agent A | `:core`, mediapipe tasks-genai |
| `:app` | android-app | Agent C | `:core` (impls wired in Phase 2) |

**Rules of engagement**
- Touch ONLY your own module's `src/`. Never edit `:core`, another module, `settings.gradle.kts`, `libs.versions.toml`, `MainActivity`, or DI wiring.
- Every module ships **unit tests** for its own logic before handing back.
- Build must pass: `./gradlew :<yourmodule>:assembleDebug` (android) or `:<yourmodule>:compileKotlin` + `:<yourmodule>:test` (pure kotlin).
- JVM target is 11, compileSdk 36, minSdk 29. JAVA_HOME = Android Studio JBR.

## `:core` API surface (the whole contract)

- `com.khataagent.core.model` — `Customer`, `Transaction`, `InventoryItem`, `DeferralEntry`, `DailyState`, enums `TxnType`, `TxnStatus`, `TxnSource`.
- `com.khataagent.core.tool` — `ToolCall` (sealed: `AddCredit`, `RecordPayment`, `RecordSale`, `UpdateStock`, `QueryBalance`, `QueryToday`, `CloseDay`, `AskClarification`), `ToolNames`, `ToolParseResult`.
- `com.khataagent.core.agent` — `TurnState` (sealed), `ConfirmCard`, `DeferKind`, `InferenceEngine` + `InferenceBackend`, `StateBlockBuilder`.
- `com.khataagent.core.validate` — `Validator`, `ValidationContext`, `ValidationResult` (`Valid`/`Defer`), `DeferReason`.
- `com.khataagent.core.data` — `LedgerRepository` (suspend CRUD + `Flow` observers).
- `com.khataagent.core.escalate` — `EscalationClient`, `ReportKind`, `LedgerSnapshot`, `CreditSummary`, `EscalationResult`, `EscalationReport`, `ConnectivityMonitor`.
- `com.khataagent.core` — `AgentStatus`.

## Tool-call wire format (Gemma output → `ToolCall`)

Single JSON object per turn, e.g.:
```json
{"tool":"add_credit","customer":"ramesh","amount":250,"item":"rice","note":null}
{"tool":"record_payment","customer":"sita","amount":500}
{"tool":"ask_clarification","question":"Which Ramesh — the one on MG Road?"}
```
Tool names are the constants in `ToolNames`. The parser (Agent A) maps these to `ToolCall`.

## Validator rules (Agent D implements; defer if ANY fire)

- `amount > dailyMaxSingleTxn` (default ₹5,000) → `OVER_DAILY_MAX`
- customer not found AND no phonetic match → `CUSTOMER_NOT_FOUND`
- 2+ phonetic matches → `CUSTOMER_AMBIGUOUS`
- payment > outstanding balance → `OVERPAYMENT`
- same customer+amount within `duplicateWindowMillis` → `DUPLICATE_SUSPECT`
- `AskClarification` from the model → `MODEL_CLARIFICATION`
- (schema violations are caught by the parser in `:agent`, not here)

Every `Defer` must build a `ConfirmCard` with plain-language `humanMessage`.

## Seed data (Agent B) — never demo an empty app
20 realistic kirana customers (Indian names), 60 transactions across a few days, ~12 inventory items
with a couple below low-watermark. Mix credit/payment/sale. Include one pair that would trip the
duplicate-suspect rule so the demo can show a deferral.
