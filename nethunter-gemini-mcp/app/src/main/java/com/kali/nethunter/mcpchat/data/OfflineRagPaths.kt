package com.kali.nethunter.mcpchat.data

import android.os.Environment
import java.io.File

/** Canonical locations for the exported offline RAG SQLite DB on device. */
object OfflineRagPaths {
    /**
     * Default corpus path: **Downloads/kaliyai_rag.db** (same as typical `adb push … /sdcard/Download/`).
     * Used when Settings leave the path blank so users do not need to configure storage.
     */
    @Suppress("DEPRECATION")
    fun defaultKaliyaiRagDbAbsolutePath(): String {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "kaliyai_rag.db").absolutePath
    }
}
