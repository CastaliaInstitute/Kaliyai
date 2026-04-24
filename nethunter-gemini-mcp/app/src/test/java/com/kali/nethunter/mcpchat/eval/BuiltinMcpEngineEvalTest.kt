package com.kali.nethunter.mcpchat.eval

import androidx.test.core.app.ApplicationProvider
import com.kali.nethunter.mcpchat.data.BuiltinMcpEngine
import com.kali.nethunter.mcpchat.data.KaliNethunterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Data-driven built-in MCP evals: [evals/eval_cases.json] &rarr; [BuiltinEvalCase] rows.
 * No network; runs on JVM + Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.DEFAULT_MANIFEST_NAME)
class BuiltinMcpEngineEvalTest {

    private val eval: EvalFile by lazy {
        val s = javaClass.classLoader?.getResourceAsStream("evals/eval_cases.json")
            ?: error("Missing test resource evals/eval_cases.json")
        EvalFile.load(s)
    }

    @Test
    fun allBuiltinEvalCases() {
        val engine = BuiltinMcpEngine(ApplicationProvider.getApplicationContext())
        for (c in eval.builtin) {
            c.kaliExecEnabled?.let { on ->
                engine.kaliConfig = KaliNethunterConfig(execEnabled = on, commandWrapper = null)
            } ?: run {
                engine.kaliConfig = KaliNethunterConfig()
            }
            val out = engine.callTool(c.tool, c.args)
            val text = McpResultParsers.text(out)
            val isErr = McpResultParsers.isError(out)
            c.expectIsError?.let { want ->
                assertEquals("case ${c.id} isError", want, isErr)
            }
            for (frag in c.textContains) {
                assertTrue("case ${c.id} should contain '$frag' in: $text", text.contains(frag, ignoreCase = true))
            }
            for (frag in c.textNotContains) {
                assertTrue("case ${c.id} should NOT contain '$frag' in: $text", !text.contains(frag, ignoreCase = true))
            }
        }
    }

    @Test
    fun evalFileVersion() {
        assertTrue("eval version", eval.version >= 1)
    }
}
