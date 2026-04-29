package com.kali.nethunter.mcpchat.eval

import androidx.test.core.app.ApplicationProvider
import com.kali.nethunter.mcpchat.data.BuiltinMcpEngine
import com.kali.nethunter.mcpchat.data.McpResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates `kaliyai_rag_retrieve` wiring and response shape using a **stub** suspend handler
 * (no Gemini embed, no on-device SQLite). Loads [evals/rag_eval_cases.json].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.DEFAULT_MANIFEST_NAME)
class RagToolStubEvalTest {

    private val eval: RagEvalFile by lazy {
        val s = javaClass.classLoader?.getResourceAsStream("evals/rag_eval_cases.json")
            ?: error("Missing test resource evals/rag_eval_cases.json")
        RagEvalFile.load(s)
    }

    /** Mirrors minimal validation from ChatViewModel.retrieveRagForMcp for offline testing. */
    private suspend fun stubRagRetrieve(args: JsonObject): JsonObject {
        val q = (args["query"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (q.isEmpty()) {
            return McpResponse.error("kaliyai_rag_retrieve: missing non-empty 'query'")
        }
        val top = (args["top_k"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 6
        val dom = (args["domain"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val body = buildString {
            appendLine("kaliyai_rag_retrieve (top_k=$top, domain=${dom.ifEmpty { "—" }}):")
            appendLine()
            appendLine("**[1] NIST Cybersecurity Framework** (sim=0.912)")
            appendLine()
            appendLine(
                "The NIST Cybersecurity Framework (CSF) 2.0 provides governance and risk-based " +
                    "guidance across Govern, Identify, Protect, Detect, Respond, and Recover.",
            )
        }
        return McpResponse.text(body.trim())
    }

    @Test
    fun allRagToolStubCases() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = BuiltinMcpEngine(ctx, ragRetrieve = { args -> stubRagRetrieve(args) })
        for (c in eval.ragToolStubCases) {
            val out = engine.callTool("kaliyai_rag_retrieve", c.args)
            val text = McpResultParsers.text(out)
            val isErr = McpResultParsers.isError(out)
            c.expectIsError?.let { assertEquals("case ${c.id} isError", it, isErr) }
            for (frag in c.textContains) {
                assertTrue(
                    "case ${c.id} should contain '$frag' in: ${text.take(800)}",
                    text.contains(frag, ignoreCase = true),
                )
            }
            for (frag in c.textNotContains) {
                assertTrue(
                    "case ${c.id} should NOT contain '$frag'",
                    !text.contains(frag, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun ragRetrieve_without_handler_returns_error() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = BuiltinMcpEngine(ctx)
        val out = engine.callTool(
            "kaliyai_rag_retrieve",
            buildJsonObject { put("query", JsonPrimitive("test")) },
        )
        assertTrue(McpResultParsers.isError(out))
        assertTrue(McpResultParsers.text(out).contains("handler", ignoreCase = true))
    }
}
