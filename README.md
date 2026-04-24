# Anubis

**Anubis** is the on-device AI companion for **Kali NetHunter** — an Android app that drives Kali's pen-testing toolchain through Google Gemini over an MCP (Model Context Protocol) bridge.

Part of the [Kali AI](https://castaliainstitute.github.io/anubis/) project by the [Castalia Institute](https://github.com/CastaliaInstitute).

```
┌───────────────────┐     Gemini     ┌──────────────────┐   adb / exec   ┌──────────────────┐
│  Anubis (Android) │ ─────────────► │  Gemini function │ ─────────────► │  Kali NetHunter  │
│  Compose + Kotlin │ ◄───────────── │  calls  (tools)  │ ◄───────────── │   chroot shell   │
└───────────────────┘   tool-use     └──────────────────┘    stdout      └──────────────────┘
```

## Repository layout

| Path | Purpose |
|------|---------|
| [`nethunter-gemini-mcp/`](./nethunter-gemini-mcp) | The Anubis Android app (Kotlin + Jetpack Compose). Gradle `rootProject.name = "anubis"`. |
| [`nethunter-prep/`](./nethunter-prep) | Host-side scripts that stage a OnePlus One (`bacon`) with LineageOS 18.1 + TWRP + Magisk + Kali NetHunter. Large binary artifacts are git-ignored. |
| [`docs/`](./docs) | GitHub Pages site describing the Kali AI project. |
| [`.devcontainer/`](./.devcontainer) | GitHub Codespaces definition — spins up a Kali userland plus the Android SDK to build/run Anubis in the cloud. |

## Quick start

### Build the Android app
```bash
cd nethunter-gemini-mcp
cp .env.example .env   # add your GEMINI_API_KEY
./gradlew :app:assembleDebug
```

### Stage a NetHunter device (OnePlus One `bacon`)
```bash
cd nethunter-prep
./install-magisk-bacon.sh
./install-kali-linux-large.sh
```

### Open in Codespaces
Click the "Open in GitHub Codespaces" button on the [project page](https://castaliainstitute.github.io/anubis/) or:
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/CastaliaInstitute/anubis?quickstart=1)

## Licensing

Source code in this repository is released under the MIT License (see `LICENSE`). Kali NetHunter, Magisk, LineageOS, TWRP, and Gemini are trademarks / properties of their respective owners.
