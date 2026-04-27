package com.kali.nethunter.mcpchat.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed class GeminiResult {
    data class Text(val text: String) : GeminiResult()
    data class FunctionCalls(
        val calls: List<FnCall>,
    ) : GeminiResult() {
        data class FnCall(val name: String, val args: JsonObject)
    }

    data class Blocked(val reason: String) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
}

/** Always carry the [model] block from the API so callers can append it to `contents`. */
data class GenerateOutcome(val result: GeminiResult, val model: JsonObject)

class GeminiRestClient(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun generate(
        apiKey: String,
        model: String,
        contents: JsonArray,
        systemInstruction: String?,
        tools: List<McpTool>,
    ): GenerateOutcome = withContext(Dispatchers.IO) {
        val functionDeclarations = buildJsonArray {
            tools.forEach { t ->
                addJsonObject {
                    put("name", t.name)
                    put("description", t.description ?: t.name)
                    if (t.inputSchema != null) {
                        put("parameters", t.inputSchema)
                    } else {
                        put(
                            "parameters",
                            buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject { })
                            },
                        )
                    }
                }
            }
        }
        val body = buildJsonObject {
            put("contents", contents)
            if (!systemInstruction.isNullOrBlank()) {
                val s = systemInstruction
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put("parts", buildJsonArray { addJsonObject { put("text", s) } })
                    },
                )
            }
            if (functionDeclarations.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        addJsonObject {
                            put("function_declarations", functionDeclarations)
                        }
                    },
                )
                put(
                    "toolConfig",
                    buildJsonObject {
                        put(
                            "functionCallingConfig",
                            buildJsonObject { put("mode", "AUTO") },
                        )
                    },
                )
            }
        }
        val safeModel = if (model.startsWith("models/")) model else "models/$model"
        val url =
            "https://generativelanguage.googleapis.com/v1beta/${safeModel}:generateContent" +
                "?key=${apiKey}"
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-client", "kalyai-android/1.0.0")
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.value.let { it in 200..299 }) {
            return@withContext GenerateOutcome(
                GeminiResult.Error("HTTP ${response.status.value}: ${text.take(2000)}"),
                buildJsonObject { put("role", "model") },
            )
        }
        parseResponse(text)
    }

    private fun parseResponse(responseBody: String): GenerateOutcome {
        val root = runCatching { json.parseToJsonElement(responseBody) as? JsonObject }
            .getOrNull()
            ?: return GenerateOutcome(
                GeminiResult.Error("Invalid JSON from Gemini"),
                buildJsonObject { put("role", "model") },
            )
        val promptFeedback = root["promptFeedback"]?.jsonObject
        if (promptFeedback != null) {
            val b = promptFeedback["blockReason"]?.jsonPrimitive?.contentOrNull
            if (b != null) {
                return GenerateOutcome(
                    GeminiResult.Blocked("blocked: $b"),
                    buildJsonObject { put("role", "model") },
                )
            }
        }
        val cands = root["candidates"] as? JsonArray
        if (cands == null || cands.isEmpty()) {
            return GenerateOutcome(
                GeminiResult.Error("No candidates: $responseBody".take(4000)),
                buildJsonObject { put("role", "model") },
            )
        }
        val first = cands[0].jsonObject
        if (first["finishReason"]?.jsonPrimitive?.content == "SAFETY") {
            return GenerateOutcome(
                GeminiResult.Blocked("safety"),
                buildJsonObject { put("role", "model") },
            )
        }
        val content = first["content"] as? JsonObject
        if (content == null) {
            return GenerateOutcome(
                GeminiResult.Error("No content in first candidate: $responseBody"),
                buildJsonObject { put("role", "model") },
            )
        }
        val parts = content["parts"] as? JsonArray
        if (parts == null) {
            return GenerateOutcome(
                GeminiResult.Text(""),
                content,
            )
        }

        val fns = mutableListOf<GeminiResult.FunctionCalls.FnCall>()
        val textParts = StringBuilder()
        for (p in parts) {
            val obj = p as? JsonObject ?: continue
            val fn = obj["functionCall"] as? JsonObject
            if (fn != null) {
                val name = fn["name"]?.jsonPrimitive?.contentOrNull
                    ?: continue
                val args = (fn["args"] as? JsonObject)
                    ?: (fn["arguments"] as? JsonObject)
                    ?: buildJsonObject { }
                fns.add(GeminiResult.FunctionCalls.FnCall(name, args))
            }
            val tx = (obj["text"] as? JsonPrimitive)?.contentOrNull
            if (tx != null) {
                if (textParts.isNotEmpty()) {
                    textParts.append('\n')
                }
                textParts.append(tx)
            }
        }
        if (fns.isNotEmpty()) {
            return GenerateOutcome(GeminiResult.FunctionCalls(fns), content)
        }
        return GenerateOutcome(
            GeminiResult.Text(textParts.toString().ifBlank { "" }),
            content,
        )
    }
}

fun newGeminiHttpClient(): HttpClient = HttpClient(Android) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 300_000
        connectTimeoutMillis = 30_000
    }
    install(ContentNegotiation) { json(mcpJson) }
}

private val mcpJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun jsonPartText(text: String) = buildJsonObject {
    put("role", "user")
    put("parts", buildJsonArray { addJsonObject { put("text", text) } })
}

