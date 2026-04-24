---
layout: castalia
title: Running Kali AI in GitHub Codespaces
---

# Running Kali AI in Codespaces

The repo is configured so a single click launches a full build environment in the cloud: Kali Linux userland, Android SDK + command-line tools, JDK 17, and the Anubis source tree.

<p>
  <a class="btn btn-primary" href="https://codespaces.new/CastaliaInstitute/anubis?quickstart=1">
    🚀 Open in Codespaces
  </a>
</p>

## What you get

| Layer | Provided by |
|-------|-------------|
| Ubuntu 22.04 base | `mcr.microsoft.com/devcontainers/base:jammy` |
| JDK 17 + Gradle | `ghcr.io/devcontainers/features/java:1` |
| Android SDK 35 + cmdline-tools + platform-tools | `ghcr.io/devcontainers/features/android-sdk:1` |
| Docker-in-Docker | `ghcr.io/devcontainers/features/docker-in-docker:2` |
| Kali Linux rolling (as a container) | `kalilinux/kali-rolling` + `postCreate.sh` |

## Typical workflow

```bash
# 1. Build the Android APK
cd nethunter-gemini-mcp
# Prefer a [Codespace secret] GEMINI_API_KEY so the key is never in git.
# postCreate writes a gitignored .env for Gradle; do not commit it.
cp .env.example .env
./gradlew :app:assembleDebug

# 2. Enter Kali
kali                            # alias defined in postCreate.sh
# inside Kali:
apt update && apt install -y nmap
nmap -sV scanme.nmap.org

# 3. Run the eval suite
cd /workspaces/anubis/nethunter-gemini-mcp
./scripts/run-evals.sh
```

## Secrets

- **Repository / Actions** — [GitHub Actions secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets): set `GEMINI_API_KEY` with `gh secret set` (or the repo **Settings → Secrets and variables → Actions**). The static site in `docs/` never uses this; only workflows or manual automation you add should reference `secrets.GEMINI_API_KEY`.
- **Codespaces** — [Codespaces settings](https://docs.github.com/en/codespaces/managing-your-codespaces/managing-secrets-for-your-codespaces) for `CastaliaInstitute/anubis`: add `GEMINI_API_KEY`. It is only written into a local, **gitignored** `nethunter-gemini-mcp/.env` for Gradle. It is **not** `source`d into the shell, so the key is not in every process environment and never appears on Pages.

## Customising

Edit `.devcontainer/devcontainer.json` and `.devcontainer/postCreate.sh` to add tools. Rebuild the container with **Command Palette → Codespaces: Rebuild Container**.
