---
layout: castalia
title: Development environment (Codespaces & devcontainer)
---

# What this dev environment is

The repository includes a <a href="https://containers.dev/">Development Container (devcontainer)</a> — a <strong>declarative, reproducible</strong> description of the OS and packages needed to (1) <strong>compile the Android app</strong>, and (2) <strong>run a Kali userland in parallel</strong> for command-line work. It is the same <em>technologies</em> you would install by hand; they are precomposed so a student or CI job gets a <strong>known-good toolchain</strong> without baking huge images into git.

[GitHub Codespaces](https://github.com/features/codespaces) is one host for that spec: a managed container with VS Code in the browser, backed by Microsoft’s build grid. The **anubis** spec can also be opened locally in **Dev Containers** with Docker on your own machine. Nothing here replaces the on-phone stack; it is a <strong>developer mirror</strong> of it.

## How the spec is built (devcontainer + images)

| Technology role | In the OCI / feature stack | What it is for |
|-----------------|----------------------------|----------------|
| LTS userland for tooling | <code>mcr.microsoft.com/devcontainers/base:jammy</code> | A predictable Ubuntu with security updates for all native packages. |
| Android compile toolchain | <code>ghcr.io/devcontainers/features/java:1</code> + Gradle wrapper in repo | JVM 17, matching the app’s <code>compileSdk</code> / <code>target</code> choices. |
| <code>sdkmanager</code>, ADB, build-tools | <code>ghcr.io/devcontainers/features/android-sdk:1</code> | Headless <code>assembleDebug</code> / <code>assembleRelease</code> and logcat. |
| Nested OCI (sidecar Kali) | <code>ghcr.io/devcontainers/features/docker-in-docker:2</code> | Lets the same spec run a Kali container whose packages align with the NetHunter chroot. |
| Rolling Kali CLI | <code>kalilinux/kali-rolling</code> (via <code>postCreate.sh</code>) | Quick <code>apt</code> installs for lab exercises, not a replacement for a rooted chroot. |
| Local secret wiring | <code>.devcontainer/postCreate.sh</code> (gitignored <code>.env</code> only) | Binds the Gemini key into Gradle; never in Pages or committed trees. |

<p>
  <a class="btn btn-primary" href="https://codespaces.new/CastaliaInstitute/anubis?quickstart=1">Open the repo in GitHub Codespaces</a>
</p>

## One way to work with it

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
