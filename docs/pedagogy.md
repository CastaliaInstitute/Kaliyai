---
layout: castalia
title: Teaching — Cybersecurity specialization (AINS-63xx)
---

# Kali AI in the MSAI Cybersecurity track

**Kali AI** (this site and [CastaliaInstitute/anubis](https://github.com/CastaliaInstitute/anubis)) is the **open, hands-on capstone** for the **Aurnova MSAI Cybersecurity AI** concentration: a **real** NetHunter lab, a **generative** planning layer, and a **governed** bridge between “what the model says” and what actually runs in Kali. The specialization’s **AINS-63xx** course sequence turns that into structured reading, exercises, and assessments published as **Jupyter Books** on GitHub Pages.

<p><a class="btn" href="{{ '/' | relative_url }}">← Back to home</a></p>

---

## Pedagogical value (why it belongs in a Cybersecurity MSAI program)

1. **Grounds abstract AI in operational security** — Students do not only prompt a chatbot. They see **MCP (Model Context Protocol)**-style **tool use** bound to a **Kali** userland: intent routing, allow/deny, and the difference between a **plan** (Gemini) and **execution** (chroot / device).

2. **Teaches the full vulnerability-management arc** — The [GVM / Greenbone]({{ '/openvas.html' | relative_url }}) material covers the **same components** enterprises use (feeds, manager, scanner, management API) and then forces students to reason about **kernel, memory, and process** limits on <em>real</em> hardware: when a full RDBMS-backed stack cannot live on the phone, what is a **defensible trust boundary** for moving the engine (e.g. to a workstation) while keeping the **operator** on the device? That maps cleanly to **risk** and **continuous monitoring** conversations.

3. **Supports reproducible, ethical lab practice** — A [declarative dev environment]({{ '/codespaces.html' | relative_url }}) (devcontainer) plus **gitignored credentials** make it possible to teach the <strong>same technical stack</strong> in CI, in the cloud, or on a personal laptop <em>without</em> normalizing “paste your API key in the chat.” The technology lesson is: **where keys live in the filesystem and in process memory** matters as much as which model you call.

4. **Connects three layers students must eventually integrate** — (i) **Mobile / edge** and rooting implications, (ii) **classical** offensive toolchains (Kali, scanners, Metasploit, etc.), (iii) **modern** LLM APIs and evaluation ([eval harness](https://github.com/CastaliaInstitute/anubis/tree/main/nethunter-gemini-mcp/app/src/test) in the repo). That is the right shape of **Cybersecurity + AI** work as a field, not a slide deck.

---

## AINS-63xx — course repositories (Jupyter Books)

| Course | Topic | Book (GitHub Pages) | Source |
|--------|--------|--------------------|--------|
| **AINS 6300** | AI in threat detection | [Read the book](https://castaliainstitute.github.io/ains-6300-ai-in-threat-detection/) | [ains-6300-ai-in-threat-detection](https://github.com/CastaliaInstitute/ains-6300-ai-in-threat-detection) |
| **AINS 6301** | Automated response systems | [Read the book](https://castaliainstitute.github.io/ains-6301-automated-response-systems/) | [ains-6301-automated-response-systems](https://github.com/CastaliaInstitute/ains-6301-automated-response-systems) |
| **AINS 6302** | AI for risk assessment | [Read the book](https://castaliainstitute.github.io/ains-6302-ai-for-risk-assessment/) | [ains-6302-ai-for-risk-assessment](https://github.com/CastaliaInstitute/ains-6302-ai-for-risk-assessment) |

Kali AI / Anubis is the **shared lab referent** across this sequence (see each repo’s description and cross-links to **anubis.castalia.institute**). Faculty can point students at [CastaliaInstitute/anubis](https://github.com/CastaliaInstitute/anubis) for **source**, this site for **architecture and technology context**, the [GVM / NetHunter]({{ '/openvas.html' | relative_url }}) material for the **vulnerability-management layer**, and the [dev environment]({{ '/codespaces.html' | relative_url }}) page for **how the tooling is packaged** for coursework.

**Related (foundations, broader AI program):** [AINS 6001 — Foundations of Artificial Intelligence](https://github.com/CastaliaInstitute/ains-6001-foundations-of-artificial-intelligence) (when students need Prerequisite depth before the 63xx block).

---

## How faculty typically use this stack

- **6300 (threat detection)** — Map **signal vs. noise**, NVT/feed semantics, and “what an AI is allowed to assert” to concrete scanner and log workflows; Anubis + GVM are the object lesson.  
- **6301 (response / orchestration)** — Treat **MCP and tool-calling** as a **policy-controlled automation** problem (same class of issues as SOAR, but in a small, auditable lab).  
- **6302 (risk)** — Use **GVM** placement (where the RDBMS and daemons run vs. where the operator stands, what crosses `adb` or TLS) to discuss **risk to the organization** vs. **risk to the lab** and defensible architectures.

For policies, org charts, and accreditation text, work with the **Castalia Institute** and program leadership; this page describes **technical and pedagogical alignment** only.
