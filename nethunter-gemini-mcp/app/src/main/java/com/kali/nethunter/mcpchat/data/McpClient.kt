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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class JsonRpcError(val code: Int, val message: String)

@Serializable
private data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonObject = buildJsonObject { },
)

@Serializable
private data class JsonRpcResponse(
    val id: Int? = null,
    val result: JsonObject? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    @SerialName("inputSchema")
    val inputSchema: JsonObject? = null,
)

class McpException(message: String) : Exception(message)

/**
 * JSON-RPC 2.0 over HTTP for MCP servers that answer with JSON (Streamable HTTP–compatible).
 * Point [baseUrl] at your local bridge (Kali/NetHunter) listening on a loopback port, e.g.
 * `http://127.0.0.1:3000/mcp`.
 */
class McpClient(
    private val baseUrl: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
) {
    var sessionId: String? = null
        private set
    private var nextId: Int = 1

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val http: HttpClient = HttpClient(Android) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
        install(ContentNegotiation) { json(json) }
    }

    fun close() {
        http.close()
    }

    suspend fun initialize() {
        val id = nextId++
        val params = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject { })
            put(
                "clientInfo",
                buildJsonObject {
                    put("name", "kalyai-android")
                    put("version", "1.0.0")
                },
            )
        }
        val result = postJsonRpc(id, "initialize", params, captureSession = true)
        if (result.containsKey("error")) {
            val msg = (result["error"] as? JsonObject)?.get("message")?.toString() ?: result.toString()
            throw McpException("initialize: $msg")
        }
    }

    suspend fun listTools(): List<McpTool> {
        val id = nextId++
        val result = postJsonRpc(id, "tools/list", buildJsonObject { }, captureSession = true)
        val arr = result["tools"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            McpTool(
                name = (o["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null,
                description = (o["description"] as? JsonPrimitive)?.content,
                inputSchema = o["inputSchema"] as? JsonObject,
            )
        }
    }

    /**
     * [arguments] is the `arguments` object for `tools/call` (often JSON for tool params).
     */
    suspend fun callTool(name: String, arguments: JsonObject = buildJsonObject { }): JsonObject {
        val id = nextId++
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        val result = postJsonRpc(id, "tools/call", params, captureSession = true)
        if (result.containsKey("isError") && (result["isError"] as? JsonPrimitive)?.content == "true") {
            return buildJsonObject {
                put("isError", "true")
                put("error", result.toString())
            }
        }
        return result
    }

    private suspend fun postJsonRpc(
        id: Int,
        method: String,
        params: JsonObject,
        captureSession: Boolean,
    ): JsonObject {
        val body = JsonRpcRequest(id = id, method = method, params = params)
        val url = baseUrl.trimEnd('/')
        val response = http.post(url) {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json, text/event-stream")
            header("Mcp-Protocol-Version", "2024-11-05")
            sessionId?.let { header("Mcp-Session-Id", it) }
            extraHeaders.forEach { (k, v) -> header(k, v) }
            setBody(json.encodeToString(JsonRpcRequest.serializer(), body))
        }
        val newSession = response.headers["Mcp-Session-Id"] ?: response.headers["mcp-session-id"]
        if (captureSession && newSession != null) {
            sessionId = newSession
        }
        val text = response.bodyAsText()
        if (text.startsWith("event:") || (text.isNotEmpty() && text.contains("data: ") && text.length < 4096 && !text.trimStart().startsWith("{"))) {
            throw McpException(
                "MCP server returned event-stream text. This client expects a JSON JSON-RPC body; use a JSON HTTP MCP bridge or a proxy that returns JSON for each request.",
            )
        }
        val envelope = runCatching { json.decodeFromString(JsonRpcResponse.serializer(), text) }
            .getOrNull()
        if (envelope != null) {
            envelope.error?.let { e -> throw McpException("MCP $method: ${e.message} (${e.code})") }
            if (envelope.result != null) {
                return envelope.result
            }
        }
        val asObj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (asObj != null) {
            if (asObj["error"] != null) {
                val e = asObj["error"] as? JsonObject
                val m = (e?.get("message") as? JsonPrimitive)?.content ?: asObj.toString()
                throw McpException("MCP $method: $m")
            }
            asObj["result"]?.let { r ->
                if (r is JsonObject) {
                    return r
                }
            }
            if (asObj["tools"] != null || asObj["content"] != null) {
                return asObj
            }
        }
        throw McpException("MCP $method: unparseable response: ${text.take(500)}")
    }
}
