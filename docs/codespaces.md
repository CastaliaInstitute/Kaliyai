---
layout: default
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
cp .env.example .env
nano .env                      # paste your GEMINI_API_KEY
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

Set `GEMINI_API_KEY` as a [Codespaces secret](https://docs.github.com/en/codespaces/managing-your-codespaces/managing-secrets-for-your-codespaces) scoped to `CastaliaInstitute/anubis`. The devcontainer exports it into the shell and the Gradle build.

## Customising

Edit `.devcontainer/devcontainer.json` and `.devcontainer/postCreate.sh` to add tools. Rebuild the container with **Command Palette → Codespaces: Rebuild Container**.
