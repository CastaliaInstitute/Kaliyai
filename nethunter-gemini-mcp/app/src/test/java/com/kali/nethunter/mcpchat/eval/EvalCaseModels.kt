package com.kali.nethunter.mcpchat.eval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream

@Serializable
data class EvalFile(
    val version: Int = 1,
    val description: String? = null,
    val builtin: List<BuiltinEvalCase> = emptyList(),
    @SerialName("promptE2E")
    val promptE2E: List<PromptE2eCase> = emptyList(),
    val routing: List<RoutingCase> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun load(stream: InputStream): EvalFile =
            json.decodeFromString<EvalFile>(stream.bufferedReader().readText())
    }
}

@Serializable
data class BuiltinEvalCase(
    val id: String,
    val tool: String,
    val args: JsonObject = JsonObject(emptyMap()),
    @SerialName("textContains")
    val textContains: List<String> = emptyList(),
    @SerialName("textNotContains")
    val textNotContains: List<String> = emptyList(),
    @SerialName("expectIsError")
    val expectIsError: Boolean? = null,
    @SerialName("kaliExecEnabled")
    val kaliExecEnabled: Boolean? = null,
)

@Serializable
data class PromptE2eCase(
    val id: String,
    val message: String,
    @SerialName("anyToolIn")
    val anyToolIn: List<String> = emptyList(),
    @SerialName("summaryContainsAny")
    val summaryContainsAny: List<String> = emptyList(),
)

@Serializable
data class RoutingCase(
    val id: String,
    @SerialName("userMessage")
    val userMessage: String,
    @SerialName("suggestedTool")
    val suggestedTool: String,
)

/** [`evals/rag_eval_cases.json`] — offline stubbed `kaliyai_rag_retrieve` expectations. */
@Serializable
data class RagEvalFile(
    val version: Int = 1,
    val description: String? = null,
    @SerialName("ragToolStubCases")
    val ragToolStubCases: List<RagToolStubCase> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun load(stream: InputStream): RagEvalFile =
            json.decodeFromString<RagEvalFile>(stream.bufferedReader().readText())
    }
}

@Serializable
data class RagToolStubCase(
    val id: String,
    val args: JsonObject = JsonObject(emptyMap()),
    @SerialName("textContains")
    val textContains: List<String> = emptyList(),
    @SerialName("textNotContains")
    val textNotContains: List<String> = emptyList(),
    @SerialName("expectIsError")
    val expectIsError: Boolean? = null,
)

object McpResultParsers {
    fun text(mcp: JsonObject): String {
        val arr = mcp["content"]?.jsonArray ?: return ""
        return buildString {
            for (el in arr) {
                val t = el.jsonObject
                val s = t["text"]?.jsonPrimitive?.content ?: continue
                if (isNotEmpty()) append('\n')
                append(s)
            }
        }
    }

    fun isError(mcp: JsonObject): Boolean =
        mcp["isError"]?.jsonPrimitive?.content == "true"
}
