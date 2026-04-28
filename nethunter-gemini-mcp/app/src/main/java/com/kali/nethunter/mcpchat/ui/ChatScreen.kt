package com.kali.nethunter.mcpchat.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kali.nethunter.mcpchat.BuildConfig
import com.kali.nethunter.mcpchat.ui.theme.KaliyaiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp(viewModel: ChatViewModel) {
    val s by viewModel.settings.collectAsState()
    KaliyaiTheme {
        val snack = remember { SnackbarHostState() }
        val ctx = LocalContext.current
        LaunchedEffect(viewModel.lastError) {
            val e = viewModel.lastError ?: return@LaunchedEffect
            snack.showSnackbar(e)
        }
        var input by remember { mutableStateOf("") }
        var permRefresh by remember { mutableStateOf(0) }
        val requestLocation = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permRefresh++ }
        val locGranted = remember(permRefresh, ctx) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }
        val hasGeminiKey =
            s.geminiKey.isNotBlank() || BuildConfig.BAKED_GEMINI_API_KEY.isNotBlank()
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopAppBar(
                title = { Text("Kaliyai") },
                actions = {
                    IconButton(onClick = { viewModel.showSettings = !viewModel.showSettings }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                },
            )
            if (viewModel.isSending) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (hasGeminiKey) {
                val keyHint = when {
                    s.geminiKey.isNotBlank() -> "Gemini: key from Settings (overrides build)"
                    BuildConfig.BAKED_GEMINI_API_KEY.isNotBlank() ->
                        "Gemini: key from build (.env at compile time — rebuild after changing .env)"
                    else -> null
                }
                if (keyHint != null) {
                    Text(
                        keyHint,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
            if (!hasGeminiKey) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Google Gemini API key required",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Set GEMINI_API_KEY in the project .env and rebuild, or add a key in Settings.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Button(onClick = { viewModel.showSettings = true }) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Add key")
                        }
                    }
                }
            }
            Text(
                text = viewModel.toolsStatus.orEmpty().ifEmpty { "MCP: …" },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.clearConversation() },
                ) { Text("Clear chat") }
                TextButton(
                    onClick = { viewModel.refreshMcp() },
                ) { Text("Refresh MCP") }
                if (ctx is ComponentActivity) {
                    TextButton(
                        onClick = {
                            requestLocation.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                    ) {
                        Text(if (locGranted) "Location ✓" else "Location (Wi‑Fi scan)")
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            ) {
                items(
                    viewModel.lines,
                    key = { it.id },
                ) { line ->
                    ChatBubble(line)
                }
            }
            Row(
                modifier = Modifier
                    .imePadding()
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message (Gemini + in‑app or remote tools)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    minLines = 1,
                    maxLines = 6,
                )
                IconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = hasGeminiKey && !viewModel.isSending && input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
            SnackbarHost(
                hostState = snack,
                modifier = Modifier
                    .padding(8.dp),
            )
        }
        if (viewModel.showSettings) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.showSettings = false },
                sheetState = sheetState,
            ) {
                var key by remember { mutableStateOf(s.geminiKey) }
                var mcpUrl by remember { mutableStateOf(s.mcpBaseUrl) }
                var model by remember { mutableStateOf(s.model) }
                var kaliExec by remember { mutableStateOf(s.kaliNethunterExec) }
                var kaliWrap by remember { mutableStateOf(s.kaliNethunterWrapper) }
                LaunchedEffect(s) {
                    key = s.geminiKey
                    mcpUrl = s.mcpBaseUrl
                    model = s.model
                    kaliExec = s.kaliNethunterExec
                    kaliWrap = s.kaliNethunterWrapper
                }
                var keyVisible by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .padding(16.dp)
                        .imePadding()
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Google Gemini", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(
                        "Optional override: if empty, the app uses the key from project .env (GEMINI_API_KEY) at build time. " +
                            "If you enter a key here, it is saved on this device (DataStore) and overrides the baked-in key.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (BuildConfig.BAKED_GEMINI_API_KEY.isNotBlank()) {
                        Text(
                            "This build includes a baked-in key from .env.",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        label = { Text("Gemini API key") },
                        placeholder = { Text("Paste your key from aistudio.google.com/apikey") },
                        visualTransformation = if (keyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        ),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        if (keyVisible) "Hide key" else "Show key",
                                    )
                                }
                            }
                        },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cm?.primaryClip
                                val t = if (clip != null && clip.itemCount > 0) {
                                    clip.getItemAt(0).text?.toString()?.trim()
                                } else {
                                    null
                                }
                                if (!t.isNullOrEmpty()) {
                                    key = t
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text("Paste from clipboard")
                        }
                        TextButton(onClick = { key = "" }) { Text("Clear key") }
                    }
                    TextButton(
                        onClick = {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                            ctx.startActivity(i)
                        },
                    ) { Text("Open Google AI Studio (get a key)") }
                    OutlinedTextField(
                        value = mcpUrl,
                        onValueChange = { mcpUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional: remote MCP URL (empty = in‑app tools in APK only)") },
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model (e.g. gemini-2.5-flash, gemini-2.5-pro)") },
                    )
                    Text(
                        "Kali / NetHunter (in-app MCP)",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Tools: kali_nethunter_list_tools (large catalog), kali_nethunter_info, and kali_nethunter_exec " +
                            "for a single non-interactive chroot line (root + NetHunter chroot). Use only on systems you are allowed to test.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Enable kali_nethunter_exec",
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = kaliExec,
                            onCheckedChange = { kaliExec = it },
                        )
                    }
                    OutlinedTextField(
                        value = kaliWrap,
                        onValueChange = { kaliWrap = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional: chroot wrapper (empty = try bootkali, kali, nethunter)") },
                        singleLine = true,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = { viewModel.showSettings = false }) { Text("Close") }
                        Button(
                            onClick = {
                                viewModel.saveSettings(
                                    SettingsUi(
                                        geminiKey = key.trim(),
                                        mcpBaseUrl = mcpUrl.trim(),
                                        model = model.trim().ifEmpty { "gemini-2.5-flash" },
                                        kaliNethunterExec = kaliExec,
                                        kaliNethunterWrapper = kaliWrap,
                                    ),
                                )
                                viewModel.showSettings = false
                            },
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine) {
    val isUser = line.role == ChatLine.Role.User
    val isSys = line.role == ChatLine.Role.System
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSys -> androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                    isUser -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                },
            ),
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth(0.92f),
        ) {
            Text(
                text = if (isSys) "[${line.text}]" else line.text,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
