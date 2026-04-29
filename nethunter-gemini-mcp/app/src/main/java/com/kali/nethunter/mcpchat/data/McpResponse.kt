package com.kali.nethunter.mcpchat.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Shape matches Gemini MCP tool result: `content` array, optional `isError`. */
object McpResponse {
    fun text(text: String) = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
            },
        )
    }

    fun error(message: String) = buildJsonObject {
        put("isError", "true")
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", message)
                    },
                )
            },
        )
    }
}

/** Human-readable tool output for UI / logs (pairs text with MCP error flag). */
fun plainTextFromMcpToolResponse(response: JsonObject): Pair<String, Boolean> {
    val err = response["isError"]?.jsonPrimitive?.content == "true"
    val arr = response["content"] as? JsonArray ?: return Pair(response.toString(), err)
    val sb = StringBuilder()
    for (part in arr) {
        val o = part.jsonObject
        val type = o["type"]?.jsonPrimitive?.content
        val tx = o["text"]?.jsonPrimitive?.content
        if (type == "text" && tx != null) {
            sb.append(tx)
            sb.append('\n')
        }
    }
    val text = sb.toString().trimEnd()
    return Pair(if (text.isNotEmpty()) text else response.toString(), err)
}

fun summarizeToolArgs(args: JsonObject, maxLen: Int = 280): String {
    if (args.isEmpty()) return "{}"
    val raw = args.entries.joinToString(", ") { (k, v) ->
        val vs = when (v) {
            is JsonPrimitive -> v.content.take(160)
            else -> v.toString().take(160)
        }
        "$k=$vs"
    }
    return if (raw.length > maxLen) raw.take(maxLen) + "…" else raw
}
