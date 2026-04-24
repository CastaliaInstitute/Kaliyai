package com.kali.nethunter.mcpchat.eval

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Documentation-style **intent** checks: each [RoutingCase] must map to a tool we would expect
 * a good model to use (heuristic must agree with the suggested tool for the example question).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.DEFAULT_MANIFEST_NAME)
class IntentRoutingEvalTest {

    private val eval: EvalFile by lazy {
        val s = javaClass.classLoader?.getResourceAsStream("evals/eval_cases.json")
            ?: error("Missing test resource evals/eval_cases.json")
        EvalFile.load(s)
    }

    private fun expectedToolsForExampleQuestion(m: String): Set<String> {
        val t = m.lowercase()
        val s = mutableSetOf<String>()
        if (t.contains("wifi") || t.contains("wi-fi") || t.contains("ssid") ||
            t.contains("access point") || t.contains("access points")
        ) {
            s.add("wifi_scan")
        }
        if (t.contains("nethunter") || t.contains("bootkali") ||
            (t.contains("chroot") && (t.contains("available") || t.contains("device") || t.contains(" su")))
        ) {
            s.add("kali_nethunter_info")
        }
        if ((t.contains("nmap") && t.contains("chroot")) ||
            (t.contains("nmap") && t.contains("help")) ||
            (t.contains("nmap") && t.contains("one line"))
        ) {
            s.add("kali_nethunter_exec")
        }
        return s
    }

    @Test
    fun routingExamplesMatchHeuristic() {
        for (r in eval.routing) {
            val set = expectedToolsForExampleQuestion(r.userMessage)
            assertTrue(
                "case ${r.id}: suggested ${r.suggestedTool} not in heuristic $set for: ${r.userMessage}",
                r.suggestedTool in set,
            )
        }
    }
}
