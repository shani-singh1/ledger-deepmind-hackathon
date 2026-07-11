package com.khataagent.core.agent

/**
 * Builds the compact (~300-token, hard-capped) state block injected into every prompt.
 * The model is stateless; SQLite is the brain. Implementation lives in :data (it needs repo access).
 */
interface StateBlockBuilder {
    /** Returns a plain-text block: today's totals, open credits, last 3 txns, known customer names. */
    suspend fun build(): String
}
