package com.kali.nethunter.mcpchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("mcp_chat")

class PrefsRepository(private val context: Context) {
    private val ds = context.dataStore
    private val kGeminiKey = stringPreferencesKey("gemini_key")
    private val kMcpUrl = stringPreferencesKey("mcp_url")
    private val kModel = stringPreferencesKey("model")
    private val kKaliExec = booleanPreferencesKey("kali_nethunter_exec")
    private val kKaliWrapper = stringPreferencesKey("kali_nethunter_wrapper")

    val geminiKey: Flow<String> = ds.data.map { it[kGeminiKey].orEmpty() }
    /** Empty = use in-app tools only (no extra MCP server on device or host). */
    val mcpUrl: Flow<String> = ds.data.map { it[kMcpUrl].orEmpty() }
    val model: Flow<String> = ds.data.map { it[kModel] ?: "gemini-2.5-flash" }
    /** In-app chroot `kali_nethunter_exec`; default on for NetHunter builds, user can turn off. */
    val kaliNethunterExec: Flow<Boolean> = ds.data.map { it[kKaliExec] ?: true }
    val kaliNethunterWrapper: Flow<String> = ds.data.map { it[kKaliWrapper].orEmpty() }

    suspend fun setGeminiKey(v: String) = ds.edit { it[kGeminiKey] = v }
    suspend fun setMcpUrl(v: String) = ds.edit { it[kMcpUrl] = v }
    suspend fun setModel(v: String) = ds.edit { it[kModel] = v }
    suspend fun setKaliNethunterExec(v: Boolean) = ds.edit { it[kKaliExec] = v }
    suspend fun setKaliNethunterWrapper(v: String) = ds.edit { it[kKaliWrapper] = v }
}
