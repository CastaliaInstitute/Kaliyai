# Kaliyai

**Kaliyai** (Kali Y AI — "Kali and AI") is the on-device AI companion for **Kali NetHunter** — an Android app that drives Kali's pen-testing toolchain through Google Gemini over an MCP (Model Context Protocol) bridge.

Part of the [Kali AI](https://kaliyai.castalia.institute/) project by the [Castalia Institute](https://github.com/CastaliaInstitute). The public site is served on **kaliyai.castalia.institute** (Cloudflare in front of GitHub Pages; DNS details in [docs/DOMAIN.md](./docs/DOMAIN.md)).

```
┌───────────────────┐     Gemini     ┌──────────────────┐   adb / exec   ┌──────────────────┐
│ Kaliyai (Android) │ ─────────────► │  Gemini function │ ─────────────► │  Kali NetHunter  │
│  Compose + Kotlin │ ◄───────────── │  calls  (tools)  │ ◄───────────── │   chroot shell   │
└───────────────────┘   tool-use     └──────────────────┘    stdout      └──────────────────┘
```

## Repository layout

| Path | Purpose |
|------|---------|
| [`nethunter-gemini-mcp/`](./nethunter-gemini-mcp) | The Kaliyai Android app (Kotlin + Jetpack Compose). Gradle `rootProject.name = "kaliyai"`. |
| [`kaliyai-rag/`](./kaliyai-rag) | **RAG tooling** for the Kali&AI corpus: chunk metadata schema, Gemini embeddings, Postgres/pgvector SQL, ingestion CLI (`kaliyai-rag/README.md`). |
| [`nethunter-prep/`](./nethunter-prep) | Host-side scripts that stage a OnePlus One (`bacon`) with LineageOS 18.1 + TWRP + Magisk + Kali NetHunter. Large binary artifacts are git-ignored. |
| [`nethunter-prep/openvas/`](./nethunter-prep/openvas) | **OpenVAS / GVM:** `deploy.sh` (Mac) installs Greenbone in the Kali chroot; [`docker-mac/`](./nethunter-prep/openvas/docker-mac) runs GVM in Docker and uses the phone as `gvm-cli` client over `adb reverse` (recommended on stock `bacon` kernel). See the [site](https://kaliyai.castalia.institute/openvas.html). |

## Cybersecurity MSAI (AINS-63xx)

Kaliyai is the **open lab referent** for the **Aurnova MSAI — Cybersecurity AI** concentration. Companion **Jupyter Books** in [CastaliaInstitute](https://github.com/CastaliaInstitute):

| Code | Book (Pages) | Repository |
|------|--------------|------------|
| AINS 6300 | [AI in threat detection](https://castaliainstitute.github.io/ains-6300-ai-in-threat-detection/) | [ains-6300-ai-in-threat-detection](https://github.com/CastaliaInstitute/ains-6300-ai-in-threat-detection) |
| AINS 6301 | [Automated response systems](https://castaliainstitute.github.io/ains-6301-automated-response-systems/) | [ains-6301-automated-response-systems](https://github.com/CastaliaInstitute/ains-6301-automated-response-systems) |
| AINS 6302 | [AI for risk assessment](https://castaliainstitute.github.io/ains-6302-ai-for-risk-assessment/) | [ains-6302-ai-for-risk-assessment](https://github.com/CastaliaInstitute/ains-6302-ai-for-risk-assessment) |

Rationale, learning outcomes, and how faculty can use the stack: **[Pedagogy (GitHub Pages)](https://kaliyai.castalia.institute/pedagogy.html)** and [`docs/pedagogy.md`](./docs/pedagogy.md).
| [`docs/`](./docs) | GitHub Pages site describing the Kaliyai project. |
| [`.devcontainer/`](./.devcontainer) | GitHub Codespaces definition — spins up a Kali userland plus the Android SDK to build/run Kaliyai in the cloud. |

## Quick start

### Build the Android app
Never commit a Gemini key. For **local** work, use a gitignored `nethunter-gemini-mcp/.env` (see `.env.example`), or place `GEMINI_API_KEY` in a sibling checkout **`../castalia.institute/.env`** — Gradle uses that when the local `.env` has no key. For **CI**, store the key only in GitHub:

```bash
gh secret set GEMINI_API_KEY --repo CastaliaInstitute/Kaliyai
# paste the key at the prompt, or: gh secret set GEMINI_API_KEY -b"$KEY" --repo CastaliaInstitute/Kaliyai
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
Click the "Open in GitHub Codespaces" button on the [project page](https://kaliyai.castalia.institute/) or:
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/CastaliaInstitute/Kaliyai?quickstart=1)

## Licensing

Source code in this repository is released under the MIT License (see `LICENSE`). Kali NetHunter, Magisk, LineageOS, TWRP, and Gemini are trademarks / properties of their respective owners.
