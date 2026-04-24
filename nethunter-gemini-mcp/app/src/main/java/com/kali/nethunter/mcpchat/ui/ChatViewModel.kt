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
import com.kali.nethunter.mcpchat.data.PrefsRepository
import com.kali.nethunter.mcpchat.data.newGeminiHttpClient
import com.kali.nethunter.mcpchat.data.jsonPartText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ChatLine(val id: String, val role: Role, val text: String) {
    enum class Role { User, Model, System }
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
    private val builtin = BuiltinMcpEngine(app)
    private var mcp: McpClient? = null
    private var cachedTools: List<McpTool> = emptyList()
    var toolsStatus by mutableStateOf<String?>(null)
        private set

    private val _settings = MutableStateFlow(SettingsUi())
    val settings: StateFlow<SettingsUi> = _settings.asStateFlow()
    var showSettings by mutableStateOf(false)

    val lines = mutableStateListOf<ChatLine>()
    var isSending by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /** Content blocks in Gemini order: user, model, user (function), … */
    private val contentBlocks = mutableListOf<kotlinx.serialization.json.JsonObject>()

    private val systemPrompt = """
        You are Anubis, a concise assistant. The app includes built-in MCP tools (no server required)
        and may optionally use an external JSON-RPC MCP URL for more tools. When the user asks to
        scan Wi-Fi, list networks, or see nearby access points, call the wifi_scan tool (after
        they have granted Location if needed). On a rooted device with Kali / NetHunter, use
        kali_nethunter_list_tools to see a large catalog, kali_nethunter_info for chroot / su checks,
        and kali_nethunter_exec for one-off non-interactive chroot shell lines (use only on authorized
        systems; avoid interactive TUI or shells). Use tools with correct arguments when helpful;
        summarize results briefly.
    """.trimIndent()

    init {
        viewModelScope.launch {
            _settings.value = SettingsUi(
                geminiKey = prefs.geminiKey.first(),
                mcpBaseUrl = prefs.mcpUrl.first(),
                model = prefs.model.first(),
                kaliNethunterExec = prefs.kaliNethunterExec.first(),
                kaliNethunterWrapper = prefs.kaliNethunterWrapper.first(),
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
            _settings.value = s
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
    }

    private fun isBuiltinMcp() = _settings.value.mcpBaseUrl.isBlank()

    /**
     * Remote MCP + in-app kali_nethunter_* (same names as the built-in kali set; server wins on collision).
     */
    private fun mergeKaliMcp(tools: List<McpTool>): List<McpTool> {
        val fromBuiltin = builtin.listKaliToolsOnly()
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
        lines.add(ChatLine(id = newId(), role = ChatLine.Role.User, text = t))
        contentBlocks.add(jsonPartText(t))
        val modelName = _settings.value.model.trim().ifEmpty { "gemini-2.5-flash" }
        var tools = cachedTools
        if (tools.isEmpty() && isBuiltinMcp()) {
            tools = builtin.listTools()
            cachedTools = tools
        }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    var out = StringBuilder()
                    var step = 0
                    while (step < 12) {
                        step++
                        val contents = toContentsArray()
                        val outcome = gemini.generate(
                            apiKey = key,
                            model = modelName,
                            contents = contents,
                            systemInstruction = systemPrompt,
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
            }
            res
                .onSuccess { text ->
                    lines.add(ChatLine(id = newId(), role = ChatLine.Role.Model, text = text))
                }
                .onFailure { e ->
                    val msg = e.message ?: e.toString()
                    lastError = msg
                    lines.add(ChatLine(id = newId(), role = ChatLine.Role.System, text = msg))
                }
            isSending = false
        }
    }

    private fun newId() = System.nanoTime().toString()

    /**
     * **Debug only:** handle `mcpchat://debug/...` and [Intent] with action
     * [com.kali.nethunter.mcpchat.debug.COMMAND] (see [scripts/adb-debug-intents.sh]).
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
        mcp?.close()
        mcp = null
        http.close()
    }
}
