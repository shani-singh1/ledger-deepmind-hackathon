package com.khataagent.core

/** Top-bar status pill — quietly reinforces the offline-first pitch the whole demo. */
enum class AgentStatus {
    /** ● on-device — local model answering, no network needed. */
    ON_DEVICE,

    /** ○ offline — escalation queued, local loop unaffected. */
    OFFLINE,

    /** ↑ syncing — a queued escalation is being sent. */
    SYNCING,
}
