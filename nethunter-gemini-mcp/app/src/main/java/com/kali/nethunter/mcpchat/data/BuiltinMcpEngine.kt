package com.kali.nethunter.mcpchat.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * In-process MCP-style tools shipped inside the APK (no loopback server required).
 * [callTool] returns the same shape as a remote [McpClient.tools/call] result: `content` array, optional `isError`.
 */
class BuiltinMcpEngine(
    private val appContext: Context,
) {
    @Volatile
    var kaliConfig: KaliNethunterConfig = KaliNethunterConfig()

    private val coreToolDefs: List<McpTool> = listOf(
        McpTool(
            name = "echo",
            description = "Echo a string (tests tool wiring in-app).",
            inputSchema = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "message",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Text to echo back")
                            },
                        )
                    },
                )
                put("required", buildJsonArray { add(JsonPrimitive("message")) })
            },
        ),
        McpTool(
            name = "device_info",
            description = "Basic device and app info.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            },
        ),
        McpTool(
            name = "wifi_scan",
            description = "List nearby Wi-Fi APs (SSID, signal dBm, frequency). Requires app Location permission and system Location on; Android throttles scans.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            },
        ),
    )

    fun isKaliMcpTool(name: String): Boolean = name.startsWith("kali_nethunter_")

    fun listCoreToolsOnly(): List<McpTool> = coreToolDefs

    fun listKaliToolsOnly(): List<McpTool> = buildKaliMcpToolDefs()

    /**
     * Full in-app list when no remote MCP URL (core + Kali/NetHunter).
     */
    fun listTools(): List<McpTool> = listCoreToolsOnly() + listKaliToolsOnly()

    fun callTool(name: String, arguments: JsonObject): JsonObject = when (name) {
        "echo" -> {
            val msg = (arguments["message"] as? JsonPrimitive)?.content ?: "empty"
            mcpTextResult("echo: $msg")
        }
        "device_info" -> {
            val p = appContext.packageName
            val v = runCatching {
                val pi = if (Build.VERSION.SDK_INT >= 33) {
                    appContext.packageManager.getPackageInfo(
                        p,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appContext.packageManager.getPackageInfo(p, 0)
                }
                pi?.versionName ?: "?"
            }.getOrDefault("?")
            val s = buildString {
                appendLine("manufacturer=${Build.MANUFACTURER}")
                appendLine("model=${Build.MODEL}")
                appendLine("androidSdk=${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                appendLine("app=$p $v")
            }
            mcpTextResult(s.trim())
        }
        "wifi_scan" -> mcpTextResult(wifiScanText())
        "kali_nethunter_info" -> mcpTextResult(kaliNethunterInfoText())
        "kali_nethunter_list_tools" -> mcpTextResult(kaliNethunterListToolsText())
        "kali_nethunter_exec" -> kaliNethunterExec(arguments)
        else -> mcpErrorResult("unknown tool: $name")
    }

    private fun kaliNethunterInfoText(): String = buildString {
        val su = KaliNethunterCommandRunner.hasSuOnSystem()
        appendLine("kali_nethunter_info")
        appendLine("suAvailable=$su (best-effort scan of common su paths)")
        appendLine("execEnabledInApp=${kaliConfig.execEnabled}")
        appendLine("chrootWrapper=${kaliConfig.commandWrapperOrDefault().ifEmpty { "(auto: bootkali, kali, nethunter)" }}")
        appendLine("toolCatalogSize=${KaliNethunterToolCatalog.load(appContext).size} names in embedded catalog")
    }.trimEnd()

    private fun kaliNethunterListToolsText(): String {
        val all = KaliNethunterToolCatalog.load(appContext)
        if (all.isEmpty()) {
            return "kali_nethunter_list_tools: catalog empty"
        }
        val show = 400
        val head = all.take(show)
        val more = (all.size - show).coerceAtLeast(0)
        return buildString {
            append("kali_nethunter_list_tools: ${all.size} names (Kali/NetHunter; not all are installed in every chroot).")
            appendLine()
            appendLine()
            appendLine(head.joinToString("\n"))
            if (more > 0) {
                appendLine()
                appendLine("… and $more more; use a filter in your workflow or kali_nethunter_exec: `apt list` / `compgen -c`.")
            }
        }.trimEnd()
    }

    private fun kaliNethunterExec(arguments: JsonObject): JsonObject {
        if (!kaliConfig.execEnabled) {
            return mcpErrorResult(
                "kali_nethunter_exec: disabled in Settings. Enable “NetHunter / Kali exec” to run chroot commands.",
            )
        }
        val line = (arguments["command"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (line.isEmpty()) {
            return mcpErrorResult("kali_nethunter_exec: required argument 'command' (string) is missing or empty")
        }
        val timeout = parseTimeoutSec(arguments)
        if (isSemiInteractiveTtyRequest(line)) {
            return mcpErrorResult(
                "kali_nethunter_exec: use non-interactive command lines only (e.g. `nmap -h`, not shells or curses TUI).",
            )
        }
        val o = KaliNethunterCommandRunner.run(
            line = line,
            timeoutSec = timeout,
            config = kaliConfig,
        )
        return if (o.ok) mcpTextResult(o.text) else mcpErrorResult(o.text)
    }

    private fun parseTimeoutSec(arguments: JsonObject): Long {
        val raw = arguments["timeout_sec"] ?: return 120L
        if (raw !is JsonPrimitive) return 120L
        return (raw.content.toLongOrNull() ?: 120L).coerceIn(5L, 600L)
    }

    /**
     * Reject common interactive patterns that will hang a non-TTY chroot.
     */
    private fun isSemiInteractiveTtyRequest(line: String): Boolean {
        val t = line.lowercase()
        val disallowed = listOf(
            "msfconsole", "vim ", " vi ", "nano", "htop", "less ", "more ", "man ",
            "ssh ", "top", "wifite", "wireshark",
        )
        if (t.startsWith("msfconsole") || t == "sh" || t == "bash" || t == "zsh") return true
        if (t.contains("<<")) return true
        for (d in disallowed) {
            if (t.contains(d, ignoreCase = true)) return true
        }
        return false
    }

    private fun buildKaliMcpToolDefs(): List<McpTool> {
        val listSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }
        val base = listOf(
            McpTool(
                name = "kali_nethunter_info",
                description = "Diagnostics: whether su is detected, in-app Kali settings, and catalog size.",
                inputSchema = listSchema,
            ),
            McpTool(
                name = "kali_nethunter_list_tools",
                description = "List hundreds of Kali/NetHunter tool names (embedded catalog; actual install set depends on your chroot).",
                inputSchema = listSchema,
            ),
        )
        val exec = McpTool(
            name = "kali_nethunter_exec",
            description = "Run one non-interactive line inside the NetHunter / Kali chroot (e.g. `nmap -sn 192.168.1.0/24`). Requires root and a working chroot (bootkali or Settings wrapper). " +
                "Use only on systems and networks you are allowed to test.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "command",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Single shell line to run inside the chroot (no interactive TUI).")
                            },
                        )
                        put(
                            "timeout_sec",
                            buildJsonObject {
                                put("type", "integer")
                                put("description", "Seconds to wait (5–600, default 120).")
                            },
                        )
                    },
                )
                put("required", buildJsonArray { add(JsonPrimitive("command")) })
            },
        )
        return if (kaliConfig.execEnabled) {
            base + exec
        } else {
            base
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiScanText(): String {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "wifi_scan: not allowed — grant Location in the app (use “Location (Wi‑Fi scan)” in the chat toolbar), then try again."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!lm.isLocationEnabled) {
                return "wifi_scan: system Location is off. Turn on Location in quick settings, then try again."
            }
        }
        val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) {
            return "wifi_scan: Wi-Fi radio is off. Enable Wi-Fi, then try again."
        }
        return runCatching {
            @Suppress("MissingPermission", "DEPRECATION")
            if (Build.VERSION.SDK_INT < 29) {
                wm.startScan()
                Thread.sleep(800L)
            } else {
                Thread.sleep(200L)
            }
            @Suppress("MissingPermission")
            val raw = wm.scanResults ?: emptyList()
            if (raw.isEmpty()) {
                "wifi_scan: no access points in last cache. Wait a few seconds and try again (the OS throttles Wi-Fi scans)."
            } else {
                val lines = raw
                    .distinctBy { it.BSSID + it.SSID }
                    .sortedByDescending { it.level }
                    .take(50)
                    .mapIndexed { i, sr ->
                        val label = if (sr.SSID.isNotEmpty()) sr.SSID else "<hidden>"
                        "${i + 1}. $label  ${sr.level} dBm  ${sr.frequency} MHz  ${sr.BSSID}"
                    }
                "wifi_scan (${lines.size} networks)\n" + lines.joinToString("\n")
            }
        }.getOrElse { e ->
            "wifi_scan failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun mcpTextResult(text: String): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
            },
        )
    }

    private fun mcpErrorResult(message: String): JsonObject = buildJsonObject {
        put("isError", "true")
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", message)
                    },
                )
            },
        )
    }
}
