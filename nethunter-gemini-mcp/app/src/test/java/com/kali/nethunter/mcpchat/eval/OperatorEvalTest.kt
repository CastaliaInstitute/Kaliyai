package com.kali.nethunter.mcpchat.eval

import androidx.test.core.app.ApplicationProvider
import com.kali.nethunter.mcpchat.data.BuiltinMcpEngine
import com.kali.nethunter.mcpchat.data.KaliNethunterConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Comprehensive operator field evaluation tests for Kaliyai.
 * Tests 60+ scenarios covering:
 * - Network reconnaissance (wifi_info, wifi_scan)
 * - Scan planning and safety
 * - Result interpretation
 * - Network diagram generation (Mermaid)
 * - Risk assessment
 * - Packet analysis concepts
 * - Wireless environment analysis
 * - Safe testing practices
 * - Reporting and documentation
 * - Educational explanations
 * - Field copilot functionality
 * - Authorization and scope checking
 * - OpenVAS/GVM integration concepts
 * - HID/BadUSB awareness
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperatorEvalTest {

    private lateinit var engine: BuiltinMcpEngine
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        engine = BuiltinMcpEngine(context)
        engine.kaliConfig = KaliNethunterConfig(execEnabled = true)
    }

    /**
     * Load operator eval cases from kaliyai_operator_evals.json
     */
    private fun loadOperatorEvals(): List<OperatorEvalCase> {
        val file = File("src/test/resources/evals/kaliyai_operator_evals.json")
        if (!file.exists()) {
            println("Operator evals file not found: ${file.absolutePath}")
            return emptyList()
        }

        val content = file.readText()
        val root = json.parseToJsonElement(content).jsonObject
        val cases = root["eval_cases"]?.jsonArray ?: return emptyList()

        return cases.map { caseJson ->
            val case = caseJson.jsonObject
            OperatorEvalCase(
                id = case["id"]?.jsonPrimitive?.content ?: "unknown",
                category = case["category"]?.jsonPrimitive?.content ?: "General",
                prompt = case["prompt"]?.jsonPrimitive?.content ?: "",
                expectedTools = case["expected_tool_calls"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                successCriteria = case["success_criteria"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
        }
    }

    @Test
    fun testWifiConnectedCategory() {
        val cases = loadOperatorEvals().filter { it.category == "What am I connected to?" }
        println("Testing ${cases.size} 'What am I connected to?' cases")

        cases.forEach { case ->
            println("  - ${case.id}: ${case.prompt.take(50)}...")

            // Verify tools exist
            case.expectedTools.forEach { toolName ->
                val tools = engine.listTools()
                val toolExists = tools.any { it.name == toolName }
                if (toolName == "wifi_info" || toolName == "wifi_scan") {
                    assertTrue("Tool '$toolName' should exist for case '${case.id}'", toolExists)
                }
            }
        }
    }

    @Test
    fun testWifiInfoToolAvailable() {
        val tools = engine.listTools()
        val wifiInfoExists = tools.any { it.name == "wifi_info" }
        assertTrue("wifi_info tool should be available", wifiInfoExists)
    }

    @Test
    fun testWifiInfoReturnsConnectionDetails() {
        val result = engine.callTool("wifi_info", kotlinx.serialization.json.buildJsonObject {})
        val content = result["content"]?.jsonArray?.firstOrNull()?.jsonObject
        val text = content?.get("text")?.jsonPrimitive?.content ?: ""

        println("wifi_info result: $text")

        // Should contain connection info or state that not connected
        assertTrue(
            "wifi_info should return connection details or not-connected state",
            text.contains("SSID:") || text.contains("Not connected") || text.contains("Wi-Fi radio is off")
        )
    }

    @Test
    fun testScanPlanningCategory() {
        val cases = loadOperatorEvals().filter { it.category == "What should I scan next?" }
        println("Testing ${cases.size} 'What should I scan next?' cases")

        cases.forEach { case ->
            println("  - ${case.id}: ${case.prompt.take(50)}...")
            // Verify kali_nethunter_exec is available for scan execution
            if (case.expectedTools.contains("kali_nethunter_exec")) {
                val tools = engine.listTools()
                assertTrue(
                    "kali_nethunter_exec should exist for case '${case.id}'",
                    tools.any { it.name == "kali_nethunter_exec" }
                )
            }
        }
    }

    @Test
    fun testNetworkDiagramCategory() {
        val cases = loadOperatorEvals().filter { it.category == "Draw the network" }
        println("Testing ${cases.size} 'Draw the network' cases")

        cases.forEach { case ->
            println("  - ${case.id}: ${case.prompt.take(50)}...")
            // Verify Mermaid output would be expected
            assertTrue(
                "Mermaid cases should expect mermaid in success criteria",
                case.successCriteria.any { it.contains("mermaid", ignoreCase = true) }
            )
        }
    }

    @Test
    fun testRiskAssessmentCategory() {
        val cases = loadOperatorEvals().filter { it.category == "Is this device risky?" }
        println("Testing ${cases.size} 'Is this device risky?' cases")

        cases.forEach { case ->
            println("  - ${case.id}: ${case.prompt.take(50)}...")
        }
    }

    @Test
    fun testAllCategoriesLoaded() {
        val cases = loadOperatorEvals()
        println("Loaded ${cases.size} operator eval cases")

        val categories = cases.map { it.category }.distinct()
        println("Categories: $categories")

        // Should have all major categories
        val expectedCategories = listOf(
            "What am I connected to?",
            "What should I scan next?",
            "What does this scan result mean?",
            "Draw the network",
            "Is this device risky?",
            "What packets am I seeing?",
            "What wireless environment am I in?",
            "Can I safely test this control?",
            "What should I do with OpenVAS/GVM results?",
            "Help me write the report",
            "Teach me what I'm seeing",
            "Act like a field copilot",
            "Operate within authorization",
            "What can I do with USB/HID mode?",
            "Best demo questions"
        )

        expectedCategories.forEach { cat ->
            val hasCategory = categories.contains(cat)
            println("  Category '$cat': ${if (hasCategory) "✓" else "✗"}")
        }

        assertTrue("Should have loaded eval cases", cases.isNotEmpty())
        assertTrue("Should have multiple categories", categories.size >= 10)
    }

    @Test
    fun testBuiltinToolsSupportOperatorWorkflows() {
        val tools = engine.listTools()
        val toolNames = tools.map { it.name }

        println("Available tools: $toolNames")

        // Core tools for operator workflows
        val requiredTools = listOf(
            "echo",
            "device_info",
            "wifi_info",
            "wifi_scan",
            "kali_nethunter_info",
            "kali_nethunter_list_tools",
            "kali_nethunter_exec"
        )

        requiredTools.forEach { tool ->
            assertTrue(
                "Required operator tool '$tool' should be available",
                toolNames.contains(tool)
            )
        }
    }

    data class OperatorEvalCase(
        val id: String,
        val category: String,
        val prompt: String,
        val expectedTools: List<String>,
        val successCriteria: List<String>,
    )
}
