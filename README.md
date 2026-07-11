# KhataAgent — an offline, voice-driven ledger agent for kirana stores

**Runs entirely on-device on Gemma 4 E2B via LiteRT-LM.** Speak transactions in Hindi/Kannada/English;
the agent parses them into validated tool calls, writes them to SQLite, recovers from its own errors,
and defers to a human when uncertain. When the internet is available, a cloud Gemini agent handles the
heavier weekly reconciliation / anomaly / reorder work the local model shouldn't attempt.

> **Built during the Google DeepMind Bangalore Hackathon.** All application code in this repo was
> written at the event. On-device model: `litert-community/gemma-4-E2B-it-litert-lm`.

## Tracks
- **Special Prize — Best Use of Gemma 4 (Local-First Agents)**
- **Problem Statement 2 — Autonomous Orchestration** (via the cloud escalation layer)

## Why it's different
An agent that **works where the internet doesn't, and knows exactly when it needs help.** The whole
demo runs in airplane mode; the only thing that needs a network is the optional cloud escalation.

## Architecture
Multi-module Kotlin app; the model is stateless and SQLite is the brain. Each turn rebuilds a compact
~300-token state block, so prefill stays small and latency stays low.

```
:app       Compose UI (4 screens), navigation, theming, DI
:agent     LiteRT-LM session, prompt assembly, JSON tool-call parsing + retry loop, turn state machine
:data      Room/SQLite, DAOs, state-block builder, demo seed data
:validate  pure-Kotlin validator (the "check step in code, not the model") + unit tests
:escalate  Gemini-Flash client behind an interface, offline queue
:core      frozen contracts shared by all of the above
```

See [CONTRACTS.md](CONTRACTS.md) for the module API surface and [build.md](build.md) for the full design.

## The agent loop (sense → decide → act → check → defer)
`IDLE → LISTENING → INFERRING → VALIDATING → {COMMITTED | RETRYING | DEFERRED}`.
Malformed JSON → one retry with the parse error injected → then defer. Every deferral is logged; the
deferral log is a feature, not a debug artifact.

## Performance
- GPU (OpenCL) primary, CPU fallback, **NPU never** (driver-crash risk).
- Prefill budget ≤ ~1,200 tokens; state block hard-capped at 300; decode capped (~96 tokens).
- Session warmed up at splash, not on first mic press.
- _tok/s numbers: measured on-device, filled in after benchmarking (P3)._

## Build & run
```bash
export JAVA_HOME="/path/to/Android Studio/jbr"
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# push the model once (offline fallback path):
adb push gemma-4-E2B-it.litertlm /sdcard/Download/
```

## Demo arc (3 min)
Airplane mode ON → speak 3–4 transactions → one deliberate ambiguity triggers a human-confirm card →
show the deferral log → flip wifi ON → escalation → Gemini's weekly summary/anomaly report renders.
