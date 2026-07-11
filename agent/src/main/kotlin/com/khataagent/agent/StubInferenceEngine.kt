package com.khataagent.agent

import com.khataagent.core.agent.InferenceBackend
import com.khataagent.core.agent.InferenceEngine

/**
 * Deterministic, model-free stand-in for [LiteRtInferenceEngine]. Demo insurance (BUILD.md):
 * the whole app — UI, orchestrator, validator, repository — runs and produces plausible tool
 * calls without the 2.6 GB Gemma model ever being loaded. Heuristics are tuned for the
 * Hindi/English kirana phrasing used in [SystemPrompt]'s few-shot examples, not for general
 * language understanding — when nothing matches confidently it asks for clarification rather
 * than guessing, same contract the real model is instructed to follow.
 */
class StubInferenceEngine : InferenceEngine {

    override val backend: InferenceBackend = InferenceBackend.STUB

    override suspend fun warmUp() {
        // No model to load — instant.
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String {
        val utterance = extractUtterance(prompt)
        return heuristicToolCall(utterance)
    }

    override suspend fun generateWithAudio(
        prompt: String,
        audioPcm: ShortArray,
        sampleRate: Int,
        maxTokens: Int,
    ): String {
        // The stub has no ASR / audio tower. A pure-audio turn's prompt has no real utterance
        // text, so the heuristic below naturally falls through to ask_clarification - an honest
        // "I can't hear you, please type it" rather than fabricating a guess from silence.
        return generate(prompt, maxTokens)
    }

    override fun close() = Unit

    companion object {
        private val CREDIT_WORDS = listOf("udhaar", "udhar", "credit", "likho", "likh")
        private val PAYMENT_WORDS = listOf("diya", "diye", "payment", "jama", "paid", "chuka")
        private val SALE_WORDS = listOf("becha", "bech", "sale", "sold", "bik")
        private val STOCK_WORDS = listOf("stock", "restock", "mangwaya", "godown")
        private val BALANCE_WORDS = listOf("balance", "kitna baaki", "kitna dena", "kitna lena", "owe")
        private val TODAY_WORDS = listOf("aaj ka", "today", "total batao", "din ka hisaab")

        private val AMOUNT_REGEX = Regex("""\d+(?:\.\d+)?""")

        // "<name> ko/ne/se/ka/ki/ke <...>" - common Hindi grammatical markers around a customer name.
        private val NAME_BEFORE_MARKER = Regex("""\b([a-z]{2,})\s+(?:ko|ne|se|ka|ki|ke)\b""")

        /** Keywords, grammatical particles and filler words - never a customer name or item. */
        private val STOPWORDS = (
            CREDIT_WORDS + PAYMENT_WORDS + SALE_WORDS + STOCK_WORDS + BALANCE_WORDS + TODAY_WORDS +
                listOf(
                    "rupee", "rupaye", "rs", "kg", "gram", "liye", "ke", "ka", "ki", "ko", "ne", "se",
                    "hai", "the", "for", "mein", "wala", "wale", "kal", "customer", "kitna", "dena",
                    "lena", "baaki", "user", "said",
                )
            ).toSet()

        fun extractUtterance(prompt: String): String {
            val idx = prompt.lastIndexOf(PromptBuilder.UTTERANCE_MARKER)
            if (idx == -1) return ""
            val tail = prompt.substring(idx + PromptBuilder.UTTERANCE_MARKER.length)
            return tail.lineSequence().firstOrNull()?.trim('"', ' ', '\r', '\n') ?: ""
        }

        fun heuristicToolCall(utteranceRaw: String): String {
            val utterance = utteranceRaw.lowercase()
            if (utterance.isBlank() || utterance.contains("spoken aloud")) {
                return clarification("I couldn't hear a clear command - please type it instead.")
            }

            val numbers = AMOUNT_REGEX.findAll(utterance).map { it.value.toDouble() }.toList()
            val customer = extractCustomer(utterance)

            return when {
                CREDIT_WORDS.any { utterance.contains(it) } && numbers.isNotEmpty() && customer != null -> {
                    val amount = numbers.first()
                    val item = extractItem(utterance, exclude = customer)
                    if (item != null) {
                        """{"tool":"add_credit","customer":"$customer","amount":${fmt(amount)},"item":"$item"}"""
                    } else {
                        """{"tool":"add_credit","customer":"$customer","amount":${fmt(amount)}}"""
                    }
                }

                PAYMENT_WORDS.any { utterance.contains(it) } && numbers.isNotEmpty() && customer != null ->
                    """{"tool":"record_payment","customer":"$customer","amount":${fmt(numbers.first())}}"""

                SALE_WORDS.any { utterance.contains(it) } && numbers.isNotEmpty() -> {
                    val qty = if (numbers.size >= 2) numbers.first() else 1.0
                    val amount = numbers.last()
                    val item = extractItem(utterance, exclude = null) ?: "item"
                    """{"tool":"record_sale","item":"$item","qty":${fmt(qty)},"amount":${fmt(amount)}}"""
                }

                STOCK_WORDS.any { utterance.contains(it) } -> {
                    val item = extractItem(utterance, exclude = null) ?: "item"
                    val delta = numbers.firstOrNull() ?: 0.0
                    val signed = if (utterance.contains("kam") || utterance.contains("ghatao") || utterance.contains("nikal")) -delta else delta
                    """{"tool":"update_stock","item":"$item","qty_delta":${fmt(signed)}}"""
                }

                BALANCE_WORDS.any { utterance.contains(it) } && customer != null ->
                    """{"tool":"query_balance","customer":"$customer"}"""

                TODAY_WORDS.any { utterance.contains(it) } ->
                    """{"tool":"query_today"}"""

                else -> clarification("Sorry, I didn't catch the customer and amount - could you repeat that?")
            }
        }

        private fun clarification(question: String): String =
            """{"tool":"ask_clarification","question":"$question"}"""

        private fun tokens(utterance: String): List<String> =
            utterance.split(Regex("\\s+")).map { it.trim(',', '.', '"') }.filter { it.isNotBlank() }

        private fun extractCustomer(utterance: String): String? {
            NAME_BEFORE_MARKER.find(utterance)?.groupValues?.get(1)?.let { candidate ->
                if (candidate !in STOPWORDS) return candidate
            }
            return tokens(utterance).firstOrNull { token ->
                token.length >= 3 && token.all { it.isLetter() } && token !in STOPWORDS
            }
        }

        private fun extractItem(utterance: String, exclude: String?): String? =
            tokens(utterance).firstOrNull { token ->
                token.length >= 3 && token.all { it.isLetter() } && token !in STOPWORDS && token != exclude
            }

        private fun fmt(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    }
}
