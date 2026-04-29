# Kaliyai

**Kaliyai** (Kali Y AI — "Kali and AI") is the on-device AI companion for **Kali NetHunter** — an Android app that drives Kali's pen-testing toolchain through Google Gemini over an MCP (Model Context Protocol) bridge.

Part of the Kali AI project by the Castalia Institute. The public site is served on kaliyai.castalia.institute.

Kaliyai connects the Android app to Gemini function calls, then to Kali NetHunter chroot shell execution on device.

## Repository layout

The nethunter-gemini-mcp folder contains the Kaliyai Android app (Kotlin + Jetpack Compose). Gradle rootProject.name is "kaliyai".

The kaliyai-rag folder holds RAG tooling: chunk metadata schema, Gemini embeddings, SQLite export for offline retrieval on SD card.

NetHunter preparation scripts live under nethunter-prep for devices such as OnePlus One bacon with LineageOS and Magisk.

## Cybersecurity MSAI

Kaliyai is the open lab referent for the Aurnova MSAI Cybersecurity AI concentration. Companion Jupyter Books cover AI in threat detection, automated response systems, and AI for risk assessment.

Faculty use kaliyai.castalia.institute pedagogy pages for learning outcomes and lab workflows.

## Quick start

Build the Android app with a gitignored GEMINI_API_KEY in nethunter-gemini-mcp/.env. Never commit API keys.

Offline RAG uses SQLite chunks exported from embedded JSONL; place `kaliyai_rag.db` in Downloads (or set an override path in Kaliyai settings).
