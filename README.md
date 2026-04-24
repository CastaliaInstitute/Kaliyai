# Anubis

**Anubis** is the on-device AI companion for **Kali NetHunter** — an Android app that drives Kali's pen-testing toolchain through Google Gemini over an MCP (Model Context Protocol) bridge.

Part of the [Kali AI](https://anubis.castalia.institute/) project by the [Castalia Institute](https://github.com/CastaliaInstitute). The public site is served on **anubis.castalia.institute** (Cloudflare in front of GitHub Pages; DNS details in [docs/DOMAIN.md](./docs/DOMAIN.md)).

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
Never commit a Gemini key. For **local** work, use a gitignored `nethunter-gemini-mcp/.env` (see `.env.example`). For **CI**, store the key only in GitHub:

```bash
gh secret set GEMINI_API_KEY --repo CastaliaInstitute/anubis
# paste the key at the prompt, or: gh secret set GEMINI_API_KEY -b"$KEY" --repo CastaliaInstitute/anubis
```

**Codespaces:** add the same name under **Settings → Secrets and variables → Codespaces** for this repo. `postCreate` writes it into a local, untracked `nethunter-gemini-mcp/.env` for Gradle; it is not exported to the shell and not on GitHub Pages.

```bash
cd nethunter-gemini-mcp
cp .env.example .env   # local only; fill for device builds
./gradlew :app:assembleDebug
```

### Stage a NetHunter device (OnePlus One `bacon`)
```bash
cd nethunter-prep
./install-magisk-bacon.sh
./install-kali-linux-large.sh
```

### Open in Codespaces
Click the "Open in GitHub Codespaces" button on the [project page](https://anubis.castalia.institute/) or:
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/CastaliaInstitute/anubis?quickstart=1)

## Licensing

Source code in this repository is released under the MIT License (see `LICENSE`). Kali NetHunter, Magisk, LineageOS, TWRP, and Gemini are trademarks / properties of their respective owners.
