package com.kali.nethunter.mcpchat.eval

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kali.nethunter.mcpchat.data.BuiltinMcpEngine
import com.kali.nethunter.mcpchat.data.GeminiResult
import com.kali.nethunter.mcpchat.data.GeminiRestClient
import com.kali.nethunter.mcpchat.data.McpException
import com.kali.nethunter.mcpchat.data.jsonPartText
import com.kali.nethunter.mcpchat.data.newGeminiHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Optional** live evals: calls Gemini (needs `GEMINI_API_KEY` in the environment) and runs the
 * multi-step tool loop like [com.kali.nethunter.mcpchat.ui.ChatViewModel]. If the key is missing,
 * tests are **skipped** (not failed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.DEFAULT_MANIFEST_NAME)
class GeminiPromptE2eEvalTest {

    private var http: HttpClient? = null
    private val key: String?
        get() = (System.getenv("GEMINI_API_KEY")
            ?: System.getProperty("GEMINI_API_KEY"))?.trim()?.takeIf { it.isNotEmpty() }

    @After
    fun close() {
        http?.close()
        http = null
    }

    @Test
    fun promptE2E_cases() = runBlocking {
        assumeTrue(
            "Set ANUBIS_LIVE_GEMINI_EVAL=1 to opt in to live Gemini prompt evals (not run by default on CI).",
            System.getenv("ANUBIS_LIVE_GEMINI_EVAL") == "1",
        )
        val apiKey = key
        assumeTrue("Set GEMINI_API_KEY (or source .env before Gradle) to run e2e prompt evals.", !apiKey.isNullOrEmpty())
        val k = requireNotNull(apiKey)
        val client = newGeminiHttpClient().also { http = it }
        val gemini = GeminiRestClient(client)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val builtin = BuiltinMcpEngine(ctx)
        val tools = builtin.listTools()
        val s = requireNotNull(javaClass.classLoader).getResourceAsStream("evals/eval_cases.json")
        val eval: EvalFile = s.use { EvalFile.load(it) }
        for (c in eval.promptE2E) {
            val contentBlocks = mutableListOf<kotlinx.serialization.json.JsonObject>()
            contentBlocks.add(jsonPartText(c.message))
            val called = mutableListOf<String>()
            var allText = StringBuilder()
            var step = 0
            var done = false
            while (step < 6 && !done) {
                step++
                val contents: JsonArray = buildJsonArray { contentBlocks.forEach { add(it) } }
                val out = gemini.generate(
                    apiKey = k,
                    model = "gemini-2.5-flash",
                    contents = contents,
                    systemInstruction = evalSystemPrompt,
                    tools = tools,
                )
                when (val r = out.result) {
                    is GeminiResult.Error, is GeminiResult.Blocked -> {
                        throw AssertionError("e2e ${c.id} API: $r")
                    }
                    is GeminiResult.Text -> {
                        contentBlocks.add(out.model)
                        if (r.text.isNotBlank()) {
                            if (allText.isNotEmpty()) {
                                allText.append('\n')
                            }
                            allText.append(r.text)
                        }
                        done = true
                    }
                    is GeminiResult.FunctionCalls -> {
                        contentBlocks.add(out.model)
                        val parts = buildJsonArray {
                            for (fc in r.calls) {
                                called.add(fc.name)
                                val raw = runCatching { builtin.callTool(fc.name, fc.args) }
                                    .getOrElse { t ->
                                        throw t as? McpException ?: t
                                    }
                                add(
                                    buildJsonObject {
                                        put(
                                            "functionResponse",
                                            buildJsonObject {
                                                put("name", fc.name)
                                                put("response", raw)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        contentBlocks.add(
                            buildJsonObject {
                                put("role", "user")
                                put("parts", parts)
                            },
                        )
                    }
                }
            }
            if (c.anyToolIn.isNotEmpty()) {
                val hit = c.anyToolIn.any { t -> t in called }
                org.junit.Assert.assertTrue(
                    "e2e ${c.id} expected at least one tool in ${c.anyToolIn} but had $called",
                    hit,
                )
            }
            if (c.summaryContainsAny.isNotEmpty() && allText.isNotEmpty()) {
                val lower = allText.toString().lowercase()
                val anyWord = c.summaryContainsAny.any { w -> lower.contains(w.lowercase()) }
                org.junit.Assert.assertTrue(
                    "e2e ${c.id} final text should match one of ${c.summaryContainsAny} got: " +
                        allText.toString().take(1000),
                    anyWord,
                )
            }
        }
    }

    private companion object {
        val evalSystemPrompt = """
            You are Anubis, a concise assistant. The app includes built-in MCP tools (no server required)
            and may optionally use an external JSON-RPC MCP URL for more tools. When the user asks to
            scan Wi-Fi, list networks, or see nearby access points, call the wifi_scan tool (after
            they have granted Location if needed). On a rooted device with Kali / NetHunter, use
            kali_nethunter_list_tools to see a large catalog, kali_nethunter_info for chroot / su checks,
            and kali_nethunter_exec for one-off non-interactive chroot shell lines (use only on authorized
            systems; avoid interactive TUI or shells). Use tools with correct arguments when helpful;
            summarize results briefly. When the user clearly asks to test echo, call the echo tool.
        """.trimIndent()
    }
}