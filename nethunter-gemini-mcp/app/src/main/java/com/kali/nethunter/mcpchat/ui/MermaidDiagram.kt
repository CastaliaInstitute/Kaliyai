package com.kali.nethunter.mcpchat.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidDiagram(
    mermaidCode: String,
    modifier: Modifier = Modifier,
) {
    val htmlContent = buildMermaidHtml(mermaidCode)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadDataWithBaseURL(
                    null,
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                null,
                htmlContent,
                "text/html",
                "UTF-8",
                null,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
    )
}

private fun buildMermaidHtml(mermaidCode: String): String {
    val escapedCode = mermaidCode
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
    <style>
        body {
            margin: 0;
            padding: 0;
            background: #1a1a1a;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
        }
        #diagram {
            width: 100%;
            padding: 10px;
        }
        .mermaid {
            display: flex;
            justify-content: center;
        }
        /* Dark theme for mermaid */
        .node rect, .node circle, .node polygon {
            fill: #2d4a3e !important;
            stroke: #00e5ff !important;
            stroke-width: 2px !important;
        }
        .node .label {
            color: #00e5ff !important;
            font-family: monospace !important;
        }
        .edgePath .path {
            stroke: #00b8d4 !important;
            stroke-width: 2px !important;
        }
        .edgeLabel {
            background-color: #1a1a1a !important;
            color: #00e5ff !important;
            font-family: monospace !important;
        }
        .cluster rect {
            fill: #111d35 !important;
            stroke: #00e5ff !important;
            stroke-width: 1px !important;
        }
        .cluster .label {
            color: #00e5ff !important;
            font-family: monospace !important;
        }
    </style>
</head>
<body>
    <div id="diagram">
        <div class="mermaid">
            $escapedCode
        </div>
    </div>
    <script>
        mermaid.initialize({
            startOnLoad: true,
            theme: 'dark',
            themeVariables: {
                primaryColor: '#2d4a3e',
                primaryTextColor: '#00e5ff',
                primaryBorderColor: '#00e5ff',
                lineColor: '#00b8d4',
                secondaryColor: '#111d35',
                tertiaryColor: '#0a1628',
                fontFamily: 'monospace'
            },
            flowchart: {
                useMaxWidth: true,
                htmlLabels: true,
                curve: 'basis'
            },
            securityLevel: 'loose'
        });
    </script>
</body>
</html>
    """.trimIndent()
}

fun extractMermaidBlocks(text: String): List<Pair<String, String>> {
    val results = mutableListOf<Pair<String, String>>()
    val mermaidPattern = Regex("```mermaid\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)

    val matches = mermaidPattern.findAll(text)
    for (match in matches) {
        val code = match.groupValues[1].trim()
        results.add("mermaid" to code)
    }

    return results
}

fun hasMermaidBlock(text: String): Boolean {
    return text.contains("```mermaid")
}

fun removeMermaidBlocks(text: String): String {
    return text.replace(Regex("```mermaid\\n.*?\\n```", RegexOption.DOT_MATCHES_ALL), "[Mermaid diagram shown below]")
}
