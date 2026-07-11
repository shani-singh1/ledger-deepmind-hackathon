package com.khataagent.agent

/**
 * Pure function that assembles the full per-turn prompt: system instructions + few-shot
 * examples + the fresh ~300-token state block + the user's utterance (or an audio placeholder
 * for a voice turn) + an optional injected parse/validation error for the one-retry loop.
 *
 * The model is stateless (BUILD.md) — no chat history is ever included, so every call rebuilds
 * the whole prompt from scratch. [UTTERANCE_MARKER] is a stable, greppable marker line so both
 * this builder and [StubInferenceEngine] agree on where the user's words live in the prompt.
 */
object PromptBuilder {

    /** Shared with [StubInferenceEngine] so it can pull the utterance back out of a full prompt. */
    const val UTTERANCE_MARKER = "User said: "

    private const val AUDIO_PLACEHOLDER = "(spoken aloud - audio attached, no transcript)"

    fun build(stateBlock: String, utterance: String?, retryError: String? = null): String = buildString {
        append(SystemPrompt.INSTRUCTIONS)
        append("\n\n")
        append(SystemPrompt.FEW_SHOT_EXAMPLES)
        append("\n\n### Current state\n")
        append(stateBlock.trim())
        append('\n')
        if (retryError != null) {
            append("\n### Previous attempt failed\n")
            append("Your last response could not be parsed: ").append(retryError).append('\n')
            append("Respond again with ONLY one valid JSON tool call, nothing else.\n")
        }
        append("\n### Turn\n")
        append(UTTERANCE_MARKER)
        if (utterance != null) {
            append('"').append(utterance).append('"')
        } else {
            append(AUDIO_PLACEHOLDER)
        }
        append('\n')
        append("Respond with exactly one JSON tool call and nothing else.")
    }
}
