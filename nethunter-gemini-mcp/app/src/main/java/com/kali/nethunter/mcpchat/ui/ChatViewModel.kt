package com.kali.nethunter.mcpchat.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kali.nethunter.mcpchat.data.BuiltinMcpEngine
import com.kali.nethunter.mcpchat.data.KaliNethunterConfig
import com.kali.nethunter.mcpchat.data.GeminiResult
import com.kali.nethunter.mcpchat.data.GeminiRestClient
import com.kali.nethunter.mcpchat.data.McpClient
import com.kali.nethunter.mcpchat.data.McpException
import com.kali.nethunter.mcpchat.data.McpTool
import com.kali.nethunter.mcpchat.BuildConfig
import com.kali.nethunter.mcpchat.data.McpResponse
import com.kali.nethunter.mcpchat.data.plainTextFromMcpToolResponse
import com.kali.nethunter.mcpchat.data.summarizeToolArgs
import com.kali.nethunter.mcpchat.data.OfflineRagPaths
import com.kali.nethunter.mcpchat.data.OfflineRagStore
import com.kali.nethunter.mcpchat.data.PrefsRepository
import com.kali.nethunter.mcpchat.data.newGeminiHttpClient
import com.kali.nethunter.mcpchat.data.jsonPartText
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject

/**
 * @param traceLabel Tool trace rows only: tool name + argument summary.
 * @param toolResultIsError Tool trace: MCP returned isError (stderr-style styling).
 */
data class ChatLine(
    val id: String,
    val role: Role,
    val text: String,
    val traceLabel: String? = null,
    val toolResultIsError: Boolean = false,
) {
    enum class Role { User, Model, System, ToolTrace }
}

data class SettingsUi(
    val geminiKey: String = "",
    /** Non-empty: JSON-RPC MCP server URL. Empty: tools run in the app (in APK). */
    val mcpBaseUrl: String = "",
    val model: String = "gemini-2.5-flash",
    /** If true, the model may call kali_nethunter_exec (requires root / NetHunter chroot). */
    val kaliNethunterExec: Boolean = true,
    /**
     * Optional: first path/token for the chroot entry (e.g. `bootkali` or a full path). Empty: try bootkali, kali, nethunter.
     */
    val kaliNethunterWrapper: String = "",
    /**
     * Optional override path to `kaliyai_rag.db`. Empty uses [OfflineRagPaths.defaultKaliyaiRagDbAbsolutePath].
     */
    val offlineRagDbPath: String = "",
    /** Optional: require chunk meta.domain to match (empty = no filter). */
    val offlineRagDomain: String = "",
    /** Max chunks to inject into the system prompt (1–32). */
    val offlineRagTopK: Int = 6,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(
                    extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Application,
                )
                return ChatViewModel(app) as T
            }
        }
    }

    private val http = newGeminiHttpClient()
    private val gemini = GeminiRestClient(http)
    private val prefs = PrefsRepository(app)
    private val builtin = BuiltinMcpEngine(
        app,
        ragRetrieve = { args -> retrieveRagForMcp(args) },
    )
    private var mcp: McpClient? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var cachedTools: List<McpTool> = emptyList()
    private var offlineRagStore: OfflineRagStore? = null
    private var offlineRagStorePath: String? = null
    var toolsStatus by mutableStateOf<String?>(null)
        private set

    private val _settings = MutableStateFlow(SettingsUi())
    val settings: StateFlow<SettingsUi> = _settings.asStateFlow()
    var showSettings by mutableStateOf(false)

    val lines = mutableStateListOf<ChatLine>()
    var isSending by mutableStateOf(false)
        private set
    /** Short status while the model runs or tools execute (shown under the app bar). */
    var processingHint by mutableStateOf<String?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /** Content blocks in Gemini order: user, model, user (function), … */
    private val contentBlocks = mutableListOf<kotlinx.serialization.json.JsonObject>()

    private val systemPrompt = """
        You are Kaliyai, the Kali NetHunter AI companion. You have direct access to a Kali Linux
        chroot environment on this Android device via built-in MCP tools.

        OFFLINE RAG:
        An offline corpus SQLite at Downloads/kaliyai_rag.db is used when present (override path optional in Settings).
        Relevant excerpts may be injected automatically. You may also call kaliyai_rag_retrieve for on-demand passages.

        SECURITY FRAMEWORK EXPERTISE:
        You synthesize industry standards to provide authoritative cybersecurity guidance:
        - NIST NICE Framework: Workforce roles, knowledge, skills, tasks (what cyber professionals should know)
        - NIST Cybersecurity Framework: Governance and risk lifecycle (Identify, Protect, Detect, Respond, Recover)
        - MITRE ATT&CK: Canonical adversary tactics and techniques based on real-world observations
        - CIS Controls v8.1: Prioritized defensive safeguards against prevalent attacks
        - OWASP Top 10/WSTG: Web and API application security testing standards
        - PTES + NIST 800-115: Penetration testing execution standard and technical security testing guide

        REASONING APPROACH:
        Frame all security assessments as: Objective → Scope → Evidence → Hypothesis → Test → Interpretation → Report
        You are a cybersecurity staff officer, not just a tool wrapper. Provide synthesis, prioritization, restraint, and reporting.

        BE PROACTIVE - ALWAYS USE TOOLS:
        When the user asks about network state, WiFi, or scanning, ALWAYS call the appropriate tool first.
        Do NOT answer from your training data - actually execute the tool and report real results.
        
        REQUIRED TOOL CALLS:
        - "What am I connected to?" -> MUST call wifi_info tool immediately
        - "check wifi" -> MUST call wifi_scan AND wifi_info tools
        - "scan network" -> MUST call wifi_info first to get subnet, then kali_nethunter_exec with nmap
        - "scan wifi" -> MUST call wifi_scan tool
        - "run nmap" -> MUST call kali_nethunter_exec with the nmap command
        - "what tools do you have" -> MUST call kali_nethunter_list_tools
        
        NEVER say "I cannot scan" or "I'm not connected" without first trying the tool.
        Let the tool fail and report that error, don't assume the answer.

        AVAILABLE CAPABILITIES:
        - Kali NetHunter chroot with 400+ pentesting tools (nmap, metasploit, aircrack-ng, etc.)
        - WiFi scanning (requires Location permission)
        - Root shell access for authorized security testing

        OUTPUT FORMATS (use these for better visualization):
        1. MARKDOWN TABLES: For scan results, tool output, or structured data, use | Column | format
           Example: | PORT | STATE | SERVICE |\n|------|-------|---------|\n| 22 | open | ssh |

        2. MERMAID DIAGRAMS: For network topology, attack paths, or workflows, use ```mermaid blocks
           Supported: flowchart, sequenceDiagram, classDiagram, stateDiagram, erDiagram
           Example:
           ```mermaid
           flowchart TD
               A[Recon] --> B[Scan]
               B --> C[Exploit]
           ```

        TOOLS:
        - wifi_info: Show current WiFi connection (SSID, IP, signal) - USE THIS FIRST to get network
        - wifi_scan: List nearby WiFi APs (needs Location permission)
        - kali_nethunter_list_tools: Show available Kali tools
        - kali_nethunter_info: Check chroot/su status
        - kali_nethunter_exec: Run any Kali command in chroot (use for nmap, msfconsole, gvm-cli, etc.)
        
        GVM/OpenVAS VULNERABILITY SCANNING:
        - gvm-cli: XML command line tool for Greenbone Vulnerability Manager (GMP protocol)
          
          Common GVM Workflows:
          1. CREATE TARGET: gvm-cli socket --xml "<create_target><name>Web-Servers</name><hosts>192.168.1.10-20</hosts></create_target>"
          2. CREATE TASK: gvm-cli socket --xml "<create_task><name>Weekly-Scan</name><target id='TARGET_ID'/><config id='daba56c8-73ec-11df-a475-0022647640b8'/></create_task>"
             - Config IDs: Full and fast=daba56c8-73ec-11df-a475-0022647640b8, Discovery=8715c877-47a0-438d-9e44-c7d0e8e3a67e
          3. START SCAN: gvm-cli socket --xml "<start_task task_id='TASK_ID'/>"
          4. GET RESULTS: gvm-cli socket --xml "<get_results severity>'7.0'</severity>" for Critical/High only
          5. EXPORT REPORT: gvm-cli socket --xml "<get_reports report_id='REPORT_ID' format_id='a994b278-1f62-11e1-96ac-406186ea6fc5'/>"
             - Format IDs: XML=a994b278-1f62-11e1-96ac-406186ea6fc5, CSV=9087b18c-97c3-40ba-82e3-9a4c8b248635
          6. REMOTE TLS: gvm-cli tls --hostname 192.168.1.100 --port 9390 --xml "<get_configs/>"
          
          IMPORTANT: ALWAYS TRY THE COMMAND FIRST! Some gvm-cli versions work fine as root.
          If the command fails with a root error, report the actual error message and suggest:
          - Using sudo -u gvm gvm-cli ... if a gvm user exists
          - Running gsad web interface instead
          - Using openvas directly for standalone scans
          
        - gvm-script: Execute GMP Python scripts for automation
        - openvas: Legacy scanner command for standalone scans (openvas -t target)
        - gsad: Greenbone Security Assistant daemon for web UI

        HANDLING MISSING TOOLS:
        If a tool like nmap, masscan, or metasploit is not found in the chroot, the Kali NetHunter
        installation may be minimal or incomplete. Check these possibilities:
        
        1. CHROOT NOT INITIALIZED: The Kali chroot may need to be set up first via the NetHunter app
           - Open the NetHunter app and complete chroot installation
           - May require downloading the full chroot (not just minimal)
        
        2. MINIMAL CHROOT: The chroot may be a minimal install without pentesting tools
           - Install full Kali: bootkali full-upgrade or reinstall chroot with full image
        
        3. TOOLS NOT INSTALLED: Even in full chroot, some tools need installation:
           - Try: apt update && apt install -y nmap metasploit-framework
        
        4. CHROOT PATH ISSUES: Check which chroot wrapper is being used:
           - bootkali, kali, or nethunter commands may mount different chroot paths
        
        5. ALTERNATIVE: If chroot is unavailable, use Android-native tools or standalone binaries

        SECURITY: Only run commands on systems you own or have explicit permission to test.
        Keep responses concise. Prefer tables/diagrams over walls of text.
    """.trimIndent()

    init {
        viewModelScope.launch {
            _settings.value = SettingsUi(
                geminiKey = prefs.geminiKey.first(),
                mcpBaseUrl = prefs.mcpUrl.first(),
                model = prefs.model.first(),
                kaliNethunterExec = prefs.kaliNethunterExec.first(),
                kaliNethunterWrapper = prefs.kaliNethunterWrapper.first(),
                offlineRagDbPath = prefs.offlineRagDbPath.first(),
                offlineRagDomain = prefs.offlineRagDomain.first(),
                offlineRagTopK = prefs.offlineRagTopK.first(),
            )
            applyKaliConfigFrom(_settings.value)
            refreshMcp()
        }
    }

    fun updateSettings(s: SettingsUi) {
        _settings.value = s
    }

    fun saveSettings(s: SettingsUi) {
        viewModelScope.launch {
            prefs.setGeminiKey(s.geminiKey.trim())
            prefs.setMcpUrl(s.mcpBaseUrl.trim())
            prefs.setModel(s.model.trim())
            prefs.setKaliNethunterExec(s.kaliNethunterExec)
            prefs.setKaliNethunterWrapper(s.kaliNethunterWrapper.trim())
            prefs.setOfflineRagDbPath(s.offlineRagDbPath.trim())
            prefs.setOfflineRagDomain(s.offlineRagDomain.trim())
            prefs.setOfflineRagTopK(s.offlineRagTopK.coerceIn(1, 32))
            invalidateOfflineRagStore()
            _settings.value = s.copy(offlineRagTopK = s.offlineRagTopK.coerceIn(1, 32))
            applyKaliConfigFrom(s)
            mcp?.close()
            mcp = null
            refreshMcp()
        }
    }

    private fun applyKaliConfigFrom(s: SettingsUi) {
        val wrap = s.kaliNethunterWrapper.trim()
        builtin.kaliConfig = KaliNethunterConfig(
            execEnabled = s.kaliNethunterExec,
            commandWrapper = if (wrap.isEmpty()) null else wrap,
        )
    }

    fun clearConversation() {
        contentBlocks.clear()
        lines.clear()
        processingHint = null
    }

    private fun isBuiltinMcp() = _settings.value.mcpBaseUrl.isBlank()

    /**
     * Remote MCP + in-app kali_nethunter_* (same names as the built-in kali set; server wins on collision).
     */
    private fun mergeKaliMcp(tools: List<McpTool>): List<McpTool> {
        val fromBuiltin = builtin.listKaliToolsOnly() + builtin.listRagToolsOnly()
        if (fromBuiltin.isEmpty()) return tools
        val byName = tools.associateBy { it.name }
        return tools + fromBuiltin.filter { it.name !in byName }
    }

    fun refreshMcp() {
        val url = _settings.value.mcpBaseUrl.trim()
        if (url.isEmpty()) {
            mcp?.close()
            mcp = null
            applyKaliConfigFrom(_settings.value)
            cachedTools = builtin.listTools()
            toolsStatus = "Built-in: ${cachedTools.size} tools (in APK, no server; includes Kali/NetHunter catalog + exec if enabled)"
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val c = McpClient(url)
                    c.initialize()
                    val tools = c.listTools()
                    c to tools
                }
            }
                .onSuccess { (client, tools) ->
                    mcp?.close()
                    mcp = client
                    applyKaliConfigFrom(_settings.value)
                    cachedTools = mergeKaliMcp(tools)
                    val k = cachedTools.size - tools.size
                    val extra = if (k > 0) " +$k in-app kali" else ""
                    toolsStatus = "Remote MCP: ${tools.size} tools at $url$extra"
                }
                .onFailure { e ->
                    mcp = null
                    applyKaliConfigFrom(_settings.value)
                    val fallback = builtin.listTools()
                    cachedTools = fallback
                    toolsStatus = "Remote MCP failed (${e.message ?: e.javaClass.simpleName}) — using built-in: ${fallback.size} tools"
                }
        }
    }

    private fun toContentsArray(): JsonArray = buildJsonArray { contentBlocks.forEach { add(it) } }

    /** Prefs key if set, otherwise key baked in at build from root `.env` (GEMINI_API_KEY). */
    fun effectiveGeminiKey(): String {
        val p = _settings.value.geminiKey.trim()
        if (p.isNotEmpty()) return p
        return BuildConfig.BAKED_GEMINI_API_KEY.trim()
    }

    /** User override from Settings, or [OfflineRagPaths.defaultKaliyaiRagDbAbsolutePath] when blank. */
    fun effectiveOfflineRagDbPath(): String {
        val o = _settings.value.offlineRagDbPath.trim()
        if (o.isNotEmpty()) return o
        return OfflineRagPaths.defaultKaliyaiRagDbAbsolutePath()
    }

    private fun invalidateOfflineRagStore() {
        synchronized(this) {
            offlineRagStore?.close()
            offlineRagStore = null
            offlineRagStorePath = null
        }
    }

    /** Opens read-only DB once per path; caller must hold coroutine context for SQLite. */
    private fun openOfflineRagStore(path: String): OfflineRagStore? {
        val p = path.trim()
        if (p.isEmpty()) return null
        synchronized(this) {
            if (offlineRagStorePath == p && offlineRagStore != null) {
                return offlineRagStore
            }
            offlineRagStore?.close()
            val s = OfflineRagStore.openReadOnly(p)
            offlineRagStore = s
            offlineRagStorePath = if (s != null) p else null
            return s
        }
    }

    /** Formats offline [OfflineRagStore.Hit] lines for system augmentation or MCP tool output. */
    private fun formatRagHitsContent(hits: List<OfflineRagStore.Hit>): String = buildString {
        hits.forEachIndexed { i, h ->
            val src = runCatching {
                JSONObject(h.metaJson).optString("source", "unknown")
            }.getOrDefault("unknown")
            append("\n**[")
            append(i + 1)
            append("] ")
            append(src)
            append("** (sim=")
            append(String.format("%.3f", h.similarity.toDouble()))
            append(")\n")
            append(h.body.take(1800))
            append("\n")
        }
    }.trim()

    private suspend fun retrieveRagForMcp(arguments: JsonObject): JsonObject {
        val q = (arguments["query"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (q.isEmpty()) return McpResponse.error("kaliyai_rag_retrieve: missing non-empty 'query'")
        val key = effectiveGeminiKey()
        if (key.isEmpty()) return McpResponse.error("kaliyai_rag_retrieve: set Gemini API key in Settings or build .env")
        val path = effectiveOfflineRagDbPath()
        val store = openOfflineRagStore(path)
            ?: return McpResponse.error(
                "kaliyai_rag_retrieve: cannot open database at $path (export kaliyai_rag.db to Downloads or set path in Settings)",
            )
        val domainArg = (arguments["domain"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val domain = domainArg.takeIf { it.isNotEmpty() }
            ?: _settings.value.offlineRagDomain.trim().takeIf { it.isNotEmpty() }
        val topFromArg = (arguments["top_k"] as? JsonPrimitive)?.content?.toIntOrNull()?.coerceIn(1, 32)
        val topK = topFromArg ?: _settings.value.offlineRagTopK.coerceIn(1, 32)
        val qEmb = try {
            gemini.embedContent(apiKey = key, text = q, taskType = "RETRIEVAL_QUERY")
        } catch (e: Exception) {
            return McpResponse.error("kaliyai_rag_retrieve: embed failed: ${e.message}")
        }
        val hits = store.search(qEmb, topK = topK, domain = domain)
        if (hits.isEmpty()) {
            return McpResponse.text(
                "kaliyai_rag_retrieve: no matching chunks (try another query or clear domain filter).",
            )
        }
        val body = buildString {
            appendLine("kaliyai_rag_retrieve (top_k=$topK, domain=${domain ?: "—"}):")
            appendLine(formatRagHitsContent(hits))
        }
        return McpResponse.text(body.trim())
    }

    private suspend fun augmentSystemWithOfflineRag(userText: String, apiKey: String): String {
        val path = effectiveOfflineRagDbPath()
        val store = openOfflineRagStore(path) ?: return systemPrompt
        val qEmb = try {
            gemini.embedContent(apiKey = apiKey, text = userText, taskType = "RETRIEVAL_QUERY")
        } catch (e: Exception) {
            android.util.Log.w("Kaliyai", "offline RAG embed failed: ${e.message}")
            return systemPrompt
        }
        val domain = _settings.value.offlineRagDomain.trim().takeIf { it.isNotEmpty() }
        val topK = _settings.value.offlineRagTopK.coerceIn(1, 32)
        val hits = store.search(qEmb, topK = topK, domain = domain)
        if (hits.isEmpty()) return systemPrompt
        val block = "\n\n---\nOffline corpus excerpts (device storage):\n${formatRagHitsContent(hits)}"
        return systemPrompt + block
    }

    fun send(userText: String) {
        val t = userText.trim()
        if (t.isEmpty() || isSending) return
        val key = effectiveGeminiKey()
        if (key.isEmpty()) {
            lastError = "Set GEMINI_API_KEY in project .env and rebuild, or add a key in settings."
            showSettings = true
            return
        }
        isSending = true
        lastError = null
        // Log for eval capture
        android.util.Log.d("KaliyaiEval", "USER_INPUT: $t")
        lines.add(ChatLine(id = newId(), role = ChatLine.Role.User, text = t))
        contentBlocks.add(jsonPartText(t))
        val modelName = _settings.value.model.trim().ifEmpty { "gemini-2.5-flash" }
        var tools = cachedTools
        if (tools.isEmpty() && isBuiltinMcp()) {
            tools = builtin.listTools()
            cachedTools = tools
        }
        viewModelScope.launch {
            processingHint = "Preparing…"
            try {
                val text = withContext(Dispatchers.IO) {
                    val augmentedSystem = augmentSystemWithOfflineRag(t, key)
                    var out = StringBuilder()
                    var step = 0
                    while (step < 12) {
                        step++
                        withContext(Dispatchers.Main) {
                            processingHint = "Thinking… asking Gemini (step $step)"
                        }
                        val contents = toContentsArray()
                        val outcome = gemini.generate(
                            apiKey = key,
                            model = modelName,
                            contents = contents,
                            systemInstruction = augmentedSystem,
                            tools = tools,
                        )
                        when (val r = outcome.result) {
                            is GeminiResult.Error,
                            is GeminiResult.Blocked,
                            -> {
                                if (out.isNotEmpty()) {
                                    out.append("\n\n")
                                }
                                out.append(
                                    when (r) {
                                        is GeminiResult.Error -> r.message
                                        is GeminiResult.Blocked -> r.reason
                                        else -> ""
                                    },
                                )
                                break
                            }
                            is GeminiResult.Text -> {
                                contentBlocks.add(outcome.model)
                                if (r.text.isNotBlank()) {
                                    if (out.isNotEmpty()) {
                                        out.append("\n\n")
                                    }
                                    out.append(r.text)
                                }
                                break
                            }
                            is GeminiResult.FunctionCalls -> {
                                contentBlocks.add(outcome.model)
                                for (c in r.calls) {
                                    android.util.Log.d("KaliyaiEval", "TOOL_CALL: ${c.name} args=${c.args}")
                                }
                                withContext(Dispatchers.Main) {
                                    processingHint = "Running ${r.calls.size} tool(s)…"
                                }
                                val parts = buildJsonArray {
                                    for (c in r.calls) {
                                        val raw = when {
                                            builtin.isKaliMcpTool(c.name) ->
                                                builtin.callTool(c.name, c.args)
                                            isBuiltinMcp() -> builtin.callTool(c.name, c.args)
                                            else -> {
                                                val client = mcp
                                                    ?: throw McpException("Remote MCP not connected. Set URL and tap “Refresh MCP”.")
                                                client.callTool(c.name, c.args)
                                            }
                                        }
                                        val (plain, isErr) = plainTextFromMcpToolResponse(raw)
                                        val label = "${c.name} · ${summarizeToolArgs(c.args)}"
                                        withContext(Dispatchers.Main) {
                                            lines.add(
                                                ChatLine(
                                                    id = newId(),
                                                    role = ChatLine.Role.ToolTrace,
                                                    text = plain,
                                                    traceLabel = label,
                                                    toolResultIsError = isErr,
                                                ),
                                            )
                                        }
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "functionResponse",
                                                    buildJsonObject {
                                                        put("name", c.name)
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
                                withContext(Dispatchers.Main) {
                                    processingHint = "Sending tool results to Gemini…"
                                }
                            }
                        }
                    }
                    if (step >= 12) {
                        if (out.isNotEmpty()) {
                            out.append("\n\n")
                        }
                        out.append("Stopped: maximum tool/response steps reached.")
                    }
                    out.toString().ifBlank { "…" }
                }
                val logText = if (text.length > 500) text.take(500) + "..." else text
                android.util.Log.d("KaliyaiEval", "MODEL_RESPONSE: $logText")
                lines.add(ChatLine(id = newId(), role = ChatLine.Role.Model, text = text))
            } catch (e: Throwable) {
                val msg = e.message ?: e.toString()
                android.util.Log.e("KaliyaiEval", "ERROR: $msg")
                lastError = msg
                lines.add(ChatLine(id = newId(), role = ChatLine.Role.System, text = msg))
            } finally {
                processingHint = null
                isSending = false
            }
        }
    }

    private fun newId() = System.nanoTime().toString()

    /**
     * Request an AI review of a previous response for security, accuracy, and quality.
     */
    fun reviewResponse(responseText: String, onReviewComplete: (String) -> Unit) {
        viewModelScope.launch {
            val key = effectiveGeminiKey()
            if (key.isEmpty()) {
                onReviewComplete("Review: No Gemini API key available")
                return@launch
            }

            val reviewPrompt = """
                Review this Kaliyai security assistant response for a field operator:

                RESPONSE TO REVIEW:
                ---
                $responseText
                ---

                Provide a brief critique covering:
                1. SECURITY: Are there any dangerous or unauthorized suggestions? (scope violations, unsafe commands)
                2. ACCURACY: Are technical details correct? (port numbers, protocols, tool usage)
                3. COMPLETENESS: Is anything missing that an operator would need?
                4. FORMATTING: Is the markdown/mermaid/table output well-formed?

                Format as a short bulleted list. Be concise.
            """.trimIndent()

            val client = http
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val body = buildJsonObject {
                        put("contents", buildJsonArray {
                            addJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    addJsonObject { put("text", reviewPrompt) }
                                })
                            }
                        })
                    }
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key"
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(body.toString())
                    }
                    val responseBody = response.bodyAsText()
                    if (response.status.value in 200..299) {
                        parseReviewResponse(responseBody)
                    } else {
                        "Review failed: HTTP ${response.status.value}"
                    }
                }.getOrElse { e ->
                    "Review error: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            onReviewComplete(result)
        }
    }

    private fun parseReviewResponse(jsonText: String): String {
        return runCatching {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val first = candidates?.firstOrNull()?.jsonObject
            val content = first?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "No review text received"
        }.getOrDefault("Failed to parse review")
    }

    /**
     * **Debug only:** handle mcpchat://debug/... and Intent with action
     * com.kali.nethunter.mcpchat.debug.COMMAND (see scripts/adb-debug-intents.sh).
     */
    fun applyDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        when (intent.action) {
            "com.kali.nethunter.mcpchat.debug.COMMAND" -> {
                when (intent.getStringExtra("cmd")) {
                    "settings" -> showSettings = true
                    "clear" -> clearConversation()
                    "refresh_mcp", "refresh" -> refreshMcp()
                    "send" -> intent.getStringExtra("message")?.trim()?.takeIf { it.isNotEmpty() }?.let { send(it) }
                }
            }
            Intent.ACTION_VIEW -> {
                val d = intent.data ?: return
                if (d.scheme == "mcpchat" && d.host == "debug") {
                    applyDebugUri(d)
                }
            }
            else -> { }
        }
    }

    private fun applyDebugUri(uri: Uri) {
        if (uri.scheme != "mcpchat" || uri.host != "debug") return
        when (uri.pathSegments.firstOrNull()) {
            "settings" -> showSettings = true
            "clear" -> clearConversation()
            "refresh_mcp" -> refreshMcp()
            "ping" -> {
                val base = toolsStatus?.trim()?.removeSuffix(" [debug ping ok]")?.trim().orEmpty()
                toolsStatus = if (base.isEmpty()) "debug: ping ok" else "$base [debug ping ok]"
            }
            "send" -> uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }?.let { send(it) }
            else -> { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        invalidateOfflineRagStore()
        mcp?.close()
        mcp = null
        http.close()
    }
}
