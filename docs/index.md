---
layout: default
title: Kali AI — Anubis
---

# Kali AI

**Kali AI** pairs Google Gemini with [Kali NetHunter](https://www.kali.org/docs/nethunter/) so a phone running Kali can plan, execute, and narrate security work in natural language. The project has two parts:

1. **Anubis** — an Android app (Kotlin + Jetpack Compose) that talks to Gemini and forwards tool-calls to a local MCP server on the device.
2. **NetHunter prep** — host-side scripts that flash a OnePlus One (`bacon`) with LineageOS 18.1 + TWRP + Magisk + Kali NetHunter so Anubis has a real Kali userland to drive.

<p align="center">
  <a class="btn btn-primary" href="https://codespaces.new/CastaliaInstitute/anubis?quickstart=1">
    🚀 Open in GitHub Codespaces
  </a>
  &nbsp;
  <a class="btn" href="https://github.com/CastaliaInstitute/anubis">View on GitHub</a>
</p>

---

## Architecture

```
 ┌──────────────────────────┐
 │  Anubis   (Android app)  │   Jetpack Compose UI
 │  com.kali.nethunter.     │   ─ ChatScreen / ViewModel
 │  mcpchat                 │   ─ GeminiRestClient
 └────────────┬─────────────┘
              │ Gemini function-calling
              ▼
 ┌──────────────────────────┐
 │  BuiltinMcpEngine        │   Kotlin MCP server, in-process
 │  + Tool catalog          │   ─ kali_nethunter_tool_catalog.txt
 └────────────┬─────────────┘
              │ exec / adb / chroot
              ▼
 ┌──────────────────────────┐
 │  Kali NetHunter chroot   │   nmap, aircrack-ng, metasploit, …
 └──────────────────────────┘
```

## Components

### Anubis (Android)
- Package: `com.kali.nethunter.mcpchat`
- Min SDK 26, target SDK 35, Kotlin 2.0, Compose BoM 2024.10
- Gradle: `./gradlew :app:assembleDebug`
- API key: set `GEMINI_API_KEY` in the repo-root `.env` (baked into `BuildConfig.BAKED_GEMINI_API_KEY`)
- Source: [`nethunter-gemini-mcp/`](https://github.com/CastaliaInstitute/anubis/tree/main/nethunter-gemini-mcp)

### NetHunter prep (host-side)
Scripts for a OnePlus One running LineageOS 18.1 + Kali NetHunter:

| Script | What it does |
|--------|--------------|
| `install-magisk-bacon.sh` | Patches the stock `boot.img` with Magisk and flashes it via fastboot. |
| `install-kali-linux-large.sh` | Installs the full Kali rootfs in the NetHunter chroot. |
| `adb-debug-intents.sh` | Pokes Anubis over `adb shell am` for debug flows. |

Large artifacts (ROMs, Magisk, Kali rootfs, Gapps) are **not** checked in — the scripts fetch or point at them locally.

Source: [`nethunter-prep/`](https://github.com/CastaliaInstitute/anubis/tree/main/nethunter-prep)

---

## Run it in the cloud

The repo ships a [devcontainer](https://github.com/CastaliaInstitute/anubis/tree/main/.devcontainer) that boots a Kali Linux userland *inside* an Ubuntu Codespace and installs the Android SDK so you can build Anubis and exercise a Kali shell from the same browser tab.

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/CastaliaInstitute/anubis?quickstart=1)

Inside the Codespace:

```bash
# Build the Android APK
cd nethunter-gemini-mcp
./gradlew :app:assembleDebug

# Drop into Kali
kali          # alias: runs `docker exec -it kali bash`
```

See the [Codespaces guide](./codespaces.html) for details.

---

## Project status

| Area | State |
|------|-------|
| Anubis Compose UI | ✅ functional chat + tool-call visualisation |
| Gemini REST integration | ✅ function-calling loop |
| Built-in MCP engine | ✅ tool catalog, in-process dispatch |
| Eval harness | ✅ Robolectric unit tests + JSON eval cases |
| OnePlus One bring-up | ✅ Magisk + LOS 18.1 + NetHunter 2026.1 |
| Cloud Codespaces | ✅ Kali-in-Docker + Android SDK |

## Links

- GitHub: <https://github.com/CastaliaInstitute/anubis>
- Castalia Institute: <https://github.com/CastaliaInstitute>
- Kali NetHunter: <https://www.kali.org/docs/nethunter/>
- Google Gemini: <https://ai.google.dev/>
- Model Context Protocol: <https://modelcontextprotocol.io/>
