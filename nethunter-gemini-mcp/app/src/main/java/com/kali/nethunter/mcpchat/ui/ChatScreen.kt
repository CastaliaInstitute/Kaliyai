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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.kali.nethunter.mcpchat.R
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kali.nethunter.mcpchat.BuildConfig
import com.kali.nethunter.mcpchat.ui.theme.KaliyaiTheme
import com.kali.nethunter.mcpchat.ui.MermaidDiagram
import com.kali.nethunter.mcpchat.ui.extractMermaidBlocks
import com.kali.nethunter.mcpchat.ui.hasMermaidBlock
import com.kali.nethunter.mcpchat.ui.removeMermaidBlocks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp(viewModel: ChatViewModel) {
    val s by viewModel.settings.collectAsState()
    KaliyaiTheme {
        val snack = remember { SnackbarHostState() }
        val ctx = LocalContext.current
        val focusManager = LocalFocusManager.current
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "Kaliyai Dragon Rook",
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                        )
                        Text(
                            "Kaliyai",
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showSettings = !viewModel.showSettings }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                },
            )
            if (viewModel.isSending) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val hint = viewModel.processingHint
            if (hint != null && viewModel.isSending) {
                Text(
                    text = hint,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    fontFamily = FontFamily.Monospace,
                )
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
                val canSend = hasGeminiKey && !viewModel.isSending && input.isNotBlank()
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Ask Kaliyai...",
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                viewModel.send(input)
                                input = ""
                                focusManager.clearFocus()
                            }
                        },
                    ),
                    minLines = 1,
                    maxLines = 6,
                )
                IconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                        focusManager.clearFocus()
                    },
                    enabled = canSend,
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
                var offlineRagDb by remember { mutableStateOf(s.offlineRagDbPath) }
                var offlineRagDom by remember { mutableStateOf(s.offlineRagDomain) }
                var offlineRagTopK by remember { mutableStateOf(s.offlineRagTopK.toString()) }
                LaunchedEffect(s) {
                    key = s.geminiKey
                    mcpUrl = s.mcpBaseUrl
                    model = s.model
                    kaliExec = s.kaliNethunterExec
                    kaliWrap = s.kaliNethunterWrapper
                    offlineRagDb = s.offlineRagDbPath
                    offlineRagDom = s.offlineRagDomain
                    offlineRagTopK = s.offlineRagTopK.toString()
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
                    Text(
                        "Offline RAG",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "By default the app opens Downloads/kaliyai_rag.db (e.g. after adb push). " +
                            "Set a path below only to use another location.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = offlineRagDb,
                        onValueChange = { offlineRagDb = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional: override SQLite path (.db)") },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                    )
                    OutlinedTextField(
                        value = offlineRagDom,
                        onValueChange = { offlineRagDom = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional: meta.domain filter (empty = all)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = offlineRagTopK,
                        onValueChange = { v ->
                            offlineRagTopK = v.filter { ch -> ch.isDigit() }.take(2)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Chunks to inject (1–32)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        offlineRagDbPath = offlineRagDb.trim(),
                                        offlineRagDomain = offlineRagDom.trim(),
                                        offlineRagTopK = offlineRagTopK.toIntOrNull()?.coerceIn(1, 32) ?: 6,
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
private fun ToolTraceBubble(line: ChatLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (line.toolResultIsError) {
                    androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                },
            ),
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth(0.96f),
        ) {
            Column(Modifier.padding(12.dp)) {
                val scheme = androidx.compose.material3.MaterialTheme.colorScheme
                val onTrace = if (line.toolResultIsError) {
                    scheme.onErrorContainer
                } else {
                    scheme.onTertiaryContainer
                }
                Text(
                    "Tool output",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = onTrace.copy(alpha = 0.88f),
                )
                Text(
                    line.traceLabel.orEmpty(),
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        softWrap = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine) {
    if (line.role == ChatLine.Role.ToolTrace) {
        ToolTraceBubble(line)
        return
    }
    val isUser = line.role == ChatLine.Role.User
    val isSys = line.role == ChatLine.Role.System
    val text = if (isSys) "[${line.text}]" else line.text
    val hasTable = text.contains("|") && text.contains("\n")
    val hasMermaid = hasMermaidBlock(text)

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
            if (hasMermaid) {
                // Render mermaid diagrams
                Column(
                    modifier = Modifier.padding(12.dp),
                ) {
                    // Render text content without mermaid blocks
                    val textWithoutMermaid = removeMermaidBlocks(text)
                    if (textWithoutMermaid.isNotBlank() && textWithoutMermaid != "[Mermaid diagram shown below]") {
                        MarkdownContent(
                            text = textWithoutMermaid,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    // Render each mermaid diagram
                    val mermaidBlocks = extractMermaidBlocks(text)
                    mermaidBlocks.forEach { (_, code) ->
                        MermaidDiagram(
                            mermaidCode = code,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else if (hasTable) {
                // Horizontal scroll for table content
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    MarkdownTableText(text)
                }
            } else {
                // Use custom markdown renderer
                MarkdownContent(
                    text = text,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val lines = text.lines()
    var inCodeBlock = false
    var codeBlockContent = StringBuilder()

    Column(modifier = modifier) {
        lines.forEach { line ->
            when {
                // Code block start/end
                line.trim().startsWith("```") -> {
                    if (inCodeBlock) {
                        // End code block
                        Surface(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = androidx.compose.material3.MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = codeBlockContent.toString().trimEnd(),
                                fontFamily = FontFamily.Monospace,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                softWrap = false,
                            )
                        }
                        codeBlockContent = StringBuilder()
                        inCodeBlock = false
                    } else {
                        inCodeBlock = true
                    }
                }
                inCodeBlock -> {
                    codeBlockContent.appendLine(line)
                }
                // Headers
                line.trimStart().startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(line.trimStart().substring(2)),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                line.trimStart().startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(line.trimStart().substring(3)),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                line.trimStart().startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(line.trimStart().substring(4)),
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                // Horizontal rule
                line.trim().matches(Regex("^[-*_]{3,}$")) -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                // Bullet lists
                line.trimStart().matches(Regex("^[-*]\\s+.+")) -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", fontWeight = FontWeight.Bold)
                        Text(parseInlineMarkdown(line.trimStart().substring(2).trimStart()))
                    }
                }
                // Numbered lists
                line.trimStart().matches(Regex("^\\d+\\.\\s+.+")) -> {
                    val match = Regex("^(\\d+)\\.\\s+(.+)").find(line.trimStart())
                    if (match != null) {
                        val (num, content) = match.destructured
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("$num. ", fontWeight = FontWeight.Bold)
                            Text(parseInlineMarkdown(content))
                        }
                    }
                }
                // Regular paragraph
                line.isNotBlank() -> {
                    // Check if this looks like nmap or command output (columnar/fixed-width)
                    val isFixedWidthOutput = line.contains(Regex("^\\s*\\d+\\s+\\w+\\s+\\w+")) || // nmap port lines
                        line.contains(Regex("[|\\-]\\s*\\d+\\s*\\w")) || // table borders with content
                        (line.contains(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) && line.contains(Regex("\\d+/"))) // IP:port patterns
                    
                    if (isFixedWidthOutput) {
                        // Use monospace with horizontal scroll for fixed-width output
                        Surface(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            shape = androidx.compose.material3.MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        ) {
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                                softWrap = false,
                            )
                        }
                    } else {
                        Text(
                            text = parseInlineMarkdown(line),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            when {
                // Bold and italic (***text***)
                remaining.startsWith("***") -> {
                    val endIndex = remaining.indexOf("***", 3)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(remaining.substring(3, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 3)
                    } else {
                        append("***")
                        remaining = remaining.substring(3)
                    }
                }
                // Bold (**text**)
                remaining.startsWith("**") -> {
                    val endIndex = remaining.indexOf("**", 2)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(remaining.substring(2, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 2)
                    } else {
                        append("**")
                        remaining = remaining.substring(2)
                    }
                }
                // Italic (*text* or _text_)
                remaining.startsWith("*") || remaining.startsWith("_") -> {
                    val marker = remaining[0]
                    val endIndex = remaining.indexOf(marker, 1)
                    if (endIndex != -1 && remaining.substring(1, endIndex).isNotBlank()) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(remaining.substring(1, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 1)
                    } else {
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    }
                }
                // Inline code (`code`)
                remaining.startsWith("`") -> {
                    val endIndex = remaining.indexOf("`", 1)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2D2D2D))) {
                            append(remaining.substring(1, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 1)
                    } else {
                        append("`")
                        remaining = remaining.substring(1)
                    }
                }
                // Regular text
                else -> {
                    val nextSpecial = remaining.indexOfFirst { it == '*' || it == '_' || it == '`' }
                    if (nextSpecial == -1) {
                        append(remaining)
                        remaining = ""
                    } else {
                        append(remaining.substring(0, nextSpecial))
                        remaining = remaining.substring(nextSpecial)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableText(text: String) {
    val lines = text.lines()
    val tableLines = lines.filter { it.trim().startsWith("|") || it.trim().contains("|") }

    if (tableLines.isEmpty()) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            softWrap = false,
        )
        return
    }

    Column {
        tableLines.forEachIndexed { index, line ->
            val isHeaderSeparator = line.trim().replace("|", "").trim().all { it == '-' || it == ' ' || it == ':' }
            val cells = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }

            if (cells.isNotEmpty() && !isHeaderSeparator) {
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = cell,
                            fontFamily = FontFamily.Monospace,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            softWrap = false,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .widthIn(min = 20.dp),
                        )
                    }
                }
                if (index == 0 || (index > 0 && lines.getOrNull(index - 1)?.let { prev ->
                    prev.trim().replace("|", "").trim().all { it == '-' || it == ' ' || it == ':' }
                } == true)) {
                    // Header row - add visual separator
                    androidx.compose.material3.HorizontalDivider(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
