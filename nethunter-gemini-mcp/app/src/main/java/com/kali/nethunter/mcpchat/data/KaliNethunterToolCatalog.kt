package com.kali.nethunter.mcpchat.data

import android.content.Context

/**
 * Catalog of Kali/NetHunter tool names: loaded from assets ([ASSET_NAME]) and deduplicated, sorted.
 * Lines starting with `#` and blanks are ignored. If the asset is missing, a small embedded list is used.
 */
object KaliNethunterToolCatalog {

    const val ASSET_NAME = "kali_nethunter_tool_catalog.txt"
    private const val ignorePrefix = '#'
    private val defaultFallback: List<String> = listOf(
        "nmap", "msfvenom", "msfconsole", "sqlmap", "aircrack-ng", "hashcat", "hydra", "responder",
        "wireshark", "tcpdump", "nikto", "gobuster", "burpsuite", "zaproxy", "recon-ng", "wifite",
    )

    @Volatile
    private var cached: List<String>? = null

    fun load(context: Context): List<String> {
        val hit = cached
        if (hit != null) return hit
        synchronized(this) {
            if (cached != null) return cached!!
            val fromAsset = runCatching {
                context.applicationContext.assets.open(ASSET_NAME).use { s ->
                    s.bufferedReader(Charsets.UTF_8)
                        .lineSequence()
                        .map { it.substringBefore(ignorePrefix).trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                }
            }.getOrNull().orEmpty()
            val all = (fromAsset + defaultFallback)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSortedSet(String.CASE_INSENSITIVE_ORDER)
                .toList()
            cached = all
            return all
        }
    }
}
