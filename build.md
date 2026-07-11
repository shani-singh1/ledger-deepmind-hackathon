# BUILD.md — KhataAgent (working name)

## Goal

A fully offline, voice-driven ledger + inventory agent for kirana stores, running Gemma 4 E2B on-device via LiteRT-LM. The agent holds state across the day in SQLite, executes validated tool calls (credits, payments, stock), recovers from its own errors, and defers to a human when uncertain — with an optional online escalation path where a cloud Gemini agent handles work above the local model's pay grade (weekly reconciliation, anomaly review, reorder suggestions).

**One-line pitch:** an agent that works where the internet doesn't, and knows exactly when it needs help.

**Tracks:** Special Prize (Best Use of Gemma 4 — Local-First Agents) + Problem Statement 2 (Autonomous Orchestration) via the cloud escalation layer.

**Demo arc (3 min):** airplane mode ON → speak 3-4 transactions in Hindi/Kannada mixed with English → show one deliberate ambiguity triggering a human-confirm card → show the deferral log → flip wifi ON → trigger escalation → Gemini's weekly summary/anomaly report renders. Both deferral targets (human + cloud) shown live.

---

## Priority Ladder

Everything below is ordered so that stopping at ANY line still leaves a submittable project. Never start a lower priority before the one above it is demo-stable.

### P0 — Survive (submittable, wins nothing yet)
1. LiteRT-LM inference running in-app on device, GPU backend, CPU fallback (already de-risked ✅)
2. SQLite schema + DAO layer (customers, transactions, inventory, deferral_log)
3. Single-turn tool-call loop: text prompt → Gemma JSON tool call → validator → SQLite write → confirmation string
4. Minimal UI: transaction list + mic button + confirm cards

### P1 — Win the Gemma track
5. Native audio input wired (mic PCM → Gemma audio tower), replacing text entry
6. Offline TTS confirmations (Android TextToSpeech, offline voice pack pre-downloaded)
7. Validator rules + human-deferral flow (confirm cards with accept/reject)
8. Error recovery loop (malformed JSON → 1 retry with parse error injected → defer)
9. Deferral log screen (the "clear boundaries" evidence for judges)
10. State injection: compact ~300-token state block per turn (today's totals, open credits, last 3 txns)

### P2 — Win eligibility + polish
11. Cloud escalation: serialize day/week SQLite state → Gemini 3.5 Flash (or Managed Agent) → render summary/anomaly/reorder report. Connectivity-gated with graceful "offline — queued" behavior.
12. Frontend polish pass (see Frontend section — this is a scored criterion, 25% live demo)
13. Multilingual demo hardening: test exact phrases you'll say on stage, in the languages you'll say them

### P3 — Optional (only if everything above is stable)
14. KV cache q4_0 + prefill tuning benchmarks (show tok/s numbers in the demo — judges love numbers)
15. Photo → stock entry (Gemma vision tower, loaded on demand)
16. End-of-day voice reconciliation ritual ("close the khata")
17. Streaming token UI for the escalation report
18. GIF/screen-record for the 1-min submission video with captions

**Cut order if behind:** 17 → 16 → 15 → 14 → 13 → 11 (submission still qualifies for Gemma track alone).

---

## Architecture

```
┌─────────────────────────── ANDROID APP (Kotlin) ───────────────────────────┐
│                                                                             │
│  ┌──────────┐   PCM audio    ┌─────────────────────┐                       │
│  │ Mic /    │ ─────────────► │  AgentTurn          │                       │
│  │ Text UI  │                │  Orchestrator       │                       │
│  └──────────┘                │  (stateless/turn)   │                       │
│                              └──────┬──────────────┘                       │
│                                     │ prompt = system + tools +            │
│                                     │ state block (~300 tok) + audio       │
│                                     ▼                                      │
│                              ┌─────────────────────┐                       │
│                              │ LiteRT-LM Runtime   │  Gemma 4 E2B          │
│                              │ GPU (OpenCL) → CPU  │  .litertlm, mmap'd    │
│                              └──────┬──────────────┘                       │
│                                     │ JSON tool call                       │
│                                     ▼                                      │
│  ┌──────────┐   defer      ┌─────────────────────┐    valid    ┌────────┐ │
│  │ Confirm  │ ◄─────────── │  Validator          │ ──────────► │ SQLite │ │
│  │ Card UI  │              │  (schema + business │             │ (truth)│ │
│  └────┬─────┘              │   rules, pure fns)  │             └───┬────┘ │
│       │ accept/reject      └──────┬──────────────┘                 │      │
│       └────────────────────────►  │ retry (max 1)                  │      │
│                                   ▼                                ▼      │
│                            ┌──────────────┐              ┌──────────────┐ │
│                            │ Deferral Log │              │ Offline TTS  │ │
│                            └──────────────┘              │ confirmation │ │
│                                                          └──────────────┘ │
│  ── connectivity-gated ──────────────────────────────────────────────────  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ EscalationService: serialize state → Gemini 3.5 Flash / Managed     │  │
│  │ Agent → weekly summary, anomaly review, reorder suggestions.        │  │
│  │ Offline ⇒ enqueue, badge "will sync". Never blocks local loop.      │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Core principles

- **Model is stateless; SQLite is the brain.** No chat history in context. Each turn rebuilds a compact state block. Keeps prefill <2K tokens ⇒ low latency, small KV cache.
- **Check step lives in code, not the model.** Validator is pure Kotlin functions — deterministic, testable, demoable.
- **Local loop never depends on network.** Escalation is fire-and-forget, queued when offline. The app must be indistinguishable in airplane mode except for the escalation card.
- **Every failure has a defined next state.** No dead ends: retry → defer → log. The deferral log is a feature, not a debug artifact.

### Data model (SQLite)

```sql
customers(id, name, phone_hint, name_phonetic, created_at)
transactions(id, customer_id, type TEXT CHECK(type IN ('credit','payment','sale')),
             amount, item, note, status TEXT CHECK(status IN ('confirmed','pending','rejected')),
             source TEXT, created_at)
inventory(id, item, qty, unit, low_watermark)
deferral_log(id, turn_id, raw_model_output, reason, resolution, created_at)
daily_state(date, opening_cash, total_credit, total_payments, txn_count)  -- materialized per turn
```

### Tool schemas (Gemma function calls)

```
add_credit(customer: string, amount: number, item?: string, note?: string)
record_payment(customer: string, amount: number)
record_sale(item: string, qty: number, amount: number)
update_stock(item: string, qty_delta: number)
query_balance(customer: string)
query_today()          -- totals, txn count
close_day()            -- P3: reconciliation ritual
ask_clarification(question: string)   -- model's legal escape hatch — better than hallucinating a call
```

`ask_clarification` matters: give the model a sanctioned way to be unsure and it fabricates less. Render it as a confirm card.

### Validator rules (defer if any fire)

- amount > configurable daily-max (default ₹5,000 single txn)
- customer not found AND no phonetic match above threshold → "new customer?" card
- customer name phonetically ambiguous (2+ matches) → disambiguation card
- payment > outstanding balance
- duplicate suspect: same customer+amount within 2 minutes
- schema violation / missing required field (after the one retry)

### Turn lifecycle (state machine — implement as a sealed class)

```
IDLE → LISTENING → INFERRING → VALIDATING
  VALIDATING → COMMITTED  (write + TTS confirm)
  VALIDATING → RETRYING   (inject parse/validation error, max 1) → VALIDATING
  VALIDATING → DEFERRED   (confirm card + log)
  DEFERRED   → COMMITTED | REJECTED  (human decision)
any state → ERRORED → IDLE  (toast + log, never crash)
```

---

## Latency & Performance

- **GPU (OpenCL) primary, CPU fallback, NPU never** (driver crashes on some devices — not worth it at a hackathon).
- **Prefill budget:** system + tools + state ≤ ~1,200 tokens; hard-cap state block at 300. Prefill dominates perceived latency on mobile.
- **Session warm-up:** initialize the LiteRT-LM session at app start (splash), not on first mic press. First inference on a cold session is the slowest thing in the app.
- **Cap decode length:** tool calls are ≤~80 tokens. Set max output tokens accordingly; don't let it ramble.
- **Perceived latency > actual latency:** mic release → instant waveform-freeze animation → "thinking" state within 100ms → stream/flash the parsed intent as soon as JSON closes. Never a frozen screen.
- **KV cache q4_0** (P3): measure before/after, put the number in the demo.
- **Audio:** 16kHz mono PCM, chunk and cap utterance length (~15s) — long audio inflates prefill.
- **All inference off main thread** (coroutines + Dispatchers.Default), UI updates via StateFlow. Frame drops during inference read as jank to judges.

## Error Recovery & Fallbacks (explicit matrix)

| Failure | Detection | Recovery | Last resort |
|---|---|---|---|
| Malformed JSON from model | parse fail | 1 retry, error injected into prompt | defer card w/ raw transcript |
| Valid JSON, fails validation | validator | defer card | human rejects, logged |
| GPU backend init fails | exception at startup | silent CPU fallback + latency-mode UI hint | text input still works |
| Model file missing/corrupt | checksum at startup | re-download prompt (bundled token) | text-only stub mode for UI demo |
| Mic permission denied / audio fail | callback | fall back to text input (keep it a first-class path!) | — |
| TTS voice pack missing | engine callback | visual-only confirmations | — |
| Gemini API fail/offline | HTTP/connectivity | queue + "will sync" badge | escalation is optional by design |
| Inference > 20s hang | timeout watchdog | cancel session, reset to IDLE | log, toast |
| App killed mid-turn | pending txn status | on restart, surface pending as defer cards | SQLite is durable — nothing lost |

**Rule: text input is not a debug tool, it's the demo insurance.** If stage acoustics kill the mic, you type the same commands and everything downstream is identical.

---

## Frontend (scored — treat as a feature)

Stack: Jetpack Compose, single activity, Material 3 with a custom palette (do NOT ship default purple — instant "template" read).

Direction: **ledger-book aesthetic, not chatbot aesthetic.** This is a khata, so make it look like one — ruled-line texture, tabular numerals for amounts, ₹ prominent, red/green for credit/payment. Warm paper tones, one accent color. Big touch targets (shopkeeper with wet hands, not a developer).

Screens (only 4):
1. **Today** — running ledger feed (newest top), day totals header, giant mic FAB. Each txn a card: customer, amount, type-colored, time.
2. **Confirm card** (modal sheet) — what the agent understood, why it deferred (plain language: "₹12,000 is above your usual — confirm?"), accept/reject. This screen IS the thesis; make it the best one.
3. **Agent log** — deferral log rendered honestly: raw model output, reason, resolution. Judges' catnip.
4. **Insights** — escalation reports from Gemini; offline shows queued state elegantly, not an error.

Micro-interactions worth the time: mic press-and-hold with live amplitude ring; parsed intent "types itself" into a card before commit; committed card slides into the ledger with the day-total counting up. These three animations alone will carry the live-demo score.

Status honesty in the top bar: `● on-device` / `○ offline` / `↑ syncing` — quietly reinforces the pitch the entire demo.

---

## Claude Code — Parallel Workstreams

Structure the repo so subagents don't collide. Contracts first, then fan out.

**Phase 0 (sequential, 30 min, you + one agent):** freeze the contracts — `ToolCall` sealed types, DB schema, `TurnState` machine, module boundaries. Everything below codes against these.

```
:app          — UI (Compose), navigation, theming
:agent        — orchestrator, prompt builder, LiteRT-LM wrapper, turn state machine
:data         — Room/SQLite, DAOs, state-block serializer
:validate     — pure Kotlin validator (zero Android deps ⇒ unit-testable fast)
:escalate     — Gemini client, queue, report models
```

**Phase 1 fan-out (4 parallel subagents):**
- **A (agent):** LiteRT-LM session mgmt, prompt assembly, JSON parsing + retry loop
- **B (data):** schema, DAOs, state-block builder, seed script with realistic demo data (20 customers, 60 txns — never demo an empty app)
- **C (app):** all 4 screens against fake in-memory repos + the frozen contracts
- **D (validate + escalate):** validator rules w/ unit tests; Gemini client behind an interface

**Phase 2 (sequential, you):** integration — wire real repos into UI, real model into orchestrator. Keep this yourself; it's where the bugs live and you need to understand every seam for Q&A.

**Phase 3 fan-out again:** audio pipeline (A) ∥ polish/animations (C) ∥ demo seed-data + script rehearsal (you).

Subagent rules of engagement:
- contracts in `:agent/api` are read-only for everyone after phase 0; changes go through you
- every subagent writes unit tests for its own module before handing back
- no subagent touches `MainActivity` / DI wiring — integration is human-only

---

## Submission checklist

- [ ] repo PUBLIC before submitting
- [ ] README: what was built during the event (explicitly — disqualification risk), architecture diagram, tok/s numbers
- [ ] 1-min video: airplane-mode shot first, deferral card, log screen, escalation flip
- [ ] all teammates on the submission page
- [ ] submission form: Problem Statement 2 + Gemma special prize called out loudly in README/video
- [ ] rehearse the 3-min live demo twice, once on venue wifi OFF