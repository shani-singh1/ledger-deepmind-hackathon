package com.khataagent.agent

import com.khataagent.core.tool.ToolCall
import com.khataagent.core.tool.ToolNames
import com.khataagent.core.tool.ToolParseResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Turns raw Gemma output into a [ToolCall]. Must be robust to everything a small on-device model
 * can do wrong: markdown code fences around the JSON, leading/trailing prose ("Sure! {...}"),
 * numbers quoted as strings, missing optional fields. Anything it can't make sense of becomes a
 * [ToolParseResult.Failure] carrying the raw text + a human-readable reason, which the
 * orchestrator injects back into the retry prompt.
 */
object ToolCallParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawOutput: String): ToolParseResult {
        val extracted = extractJsonObject(rawOutput)
            ?: return ToolParseResult.Failure(rawOutput, "No JSON object found in model output")

        val obj = try {
            val element = json.parseToJsonElement(extracted)
            element as? JsonObject
                ?: return ToolParseResult.Failure(rawOutput, "Extracted text is not a JSON object")
        } catch (e: Exception) {
            return ToolParseResult.Failure(rawOutput, "JSON parse error: ${e.message}")
        }

        val tool = obj["tool"]?.let { stringOf(it) }
            ?: return ToolParseResult.Failure(rawOutput, "Missing required field 'tool'")

        return try {
            val call = when (tool) {
                ToolNames.ADD_CREDIT -> ToolCall.AddCredit(
                    customer = requireString(obj, "customer"),
                    amount = requireDouble(obj, "amount"),
                    item = optionalString(obj, "item"),
                    note = optionalString(obj, "note"),
                )

                ToolNames.RECORD_PAYMENT -> ToolCall.RecordPayment(
                    customer = requireString(obj, "customer"),
                    amount = requireDouble(obj, "amount"),
                )

                ToolNames.RECORD_SALE -> ToolCall.RecordSale(
                    item = requireString(obj, "item"),
                    qty = requireDouble(obj, "qty"),
                    amount = requireDouble(obj, "amount"),
                )

                ToolNames.UPDATE_STOCK -> ToolCall.UpdateStock(
                    item = requireString(obj, "item"),
                    qtyDelta = requireDouble(obj, "qty_delta"),
                )

                ToolNames.QUERY_BALANCE -> ToolCall.QueryBalance(
                    customer = requireString(obj, "customer"),
                )

                ToolNames.QUERY_TODAY -> ToolCall.QueryToday

                ToolNames.CLOSE_DAY -> ToolCall.CloseDay

                ToolNames.ASK_CLARIFICATION -> ToolCall.AskClarification(
                    question = optionalString(obj, "question") ?: "Could you clarify?",
                )

                else -> return ToolParseResult.Failure(rawOutput, "Unknown tool name: '$tool'")
            }
            ToolParseResult.Success(call)
        } catch (e: MissingFieldException) {
            ToolParseResult.Failure(rawOutput, e.message ?: "Missing required field")
        } catch (e: Exception) {
            ToolParseResult.Failure(rawOutput, "Failed to build tool call: ${e.message}")
        }
    }

    private class MissingFieldException(message: String) : Exception(message)

    private fun requireString(obj: JsonObject, key: String): String =
        optionalString(obj, key)?.takeIf { it.isNotBlank() }
            ?: throw MissingFieldException("Missing or blank required field '$key'")

    private fun optionalString(obj: JsonObject, key: String): String? {
        val el = obj[key] ?: return null
        return stringOf(el)
    }

    private fun stringOf(el: JsonElement): String? {
        if (el is JsonNull) return null
        return (el as? JsonPrimitive)?.contentOrNull
    }

    private fun requireDouble(obj: JsonObject, key: String): Double {
        val el = obj[key] ?: throw MissingFieldException("Missing required field '$key'")
        return doubleOf(el) ?: throw MissingFieldException("Field '$key' is not a number")
    }

    private fun doubleOf(el: JsonElement): Double? {
        if (el is JsonNull) return null
        val prim = el as? JsonPrimitive ?: return null
        return prim.doubleOrNull ?: prim.contentOrNull?.trim()?.toDoubleOrNull()
    }

    /**
     * Strips ```json ... ``` (or bare ``` ... ```) fences, then extracts the first balanced
     * `{...}` block, tolerating leading/trailing prose the model might have added despite
     * instructions. Returns null if no balanced object can be found.
     */
    private fun extractJsonObject(raw: String): String? {
        var text = raw.trim()

        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        fenceRegex.find(text)?.let { text = it.groupValues[1].trim() }

        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null // unbalanced braces
    }
}
