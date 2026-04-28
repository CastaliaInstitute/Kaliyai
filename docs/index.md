---
layout: home
title: Kali AI — Kaliyai
---

<div class="kali-landing">
<!--
  Logo concept: Kaliyai (Kali & AI)
  
  Text representation:
  ╭─────────────────────────────╮
  │  🐍 KALI  ⟁  AI  🐍        │
  │       ╲   │   ╱            │
  │        ╲  │  ╱             │
  │         ╲ │ ╱              │
  │      KALIYAI               │
  │   (kali y ai)              │
  ╰─────────────────────────────╯
  
  Meaning:
  - ⟁ (triple-yoga symbol) = "y" (and) in Spanish
  - 🐍 = Kaliya, the serpent/dragon from Hindu mythology
  - KALI = Kali Linux (security distribution)
  - AI = Artificial Intelligence / Gemini
  - The serpent wraps around the conjunction of human security expertise and AI
-->
<section class="kali-hero" aria-label="Kali AI introduction">
  <div class="kali-hero-inner">
    <p class="kali-eyebrow">Castalia Institute · NetHunter · Gemini · MCP</p>
    <h1><span class="kali-logo-text">KALI</span><span class="kali-logo-ampersand">⟁</span><span class="kali-logo-ai">AI</span></h1>
    <p class="kali-logo-subtitle">Kaliyai — kali y ai</p>
    <p class="kali-hero-subline">A technical stack, not a hosted service</p>
    <p class="kali-deck"><strong>Kaliyai</strong> (kali y ai — “Kali and AI”) is a native <strong>Android</strong> app: <strong>Kotlin</strong> and <strong>Jetpack Compose</strong> for UI, a client for <strong>Google Gemini</strong> (function-calling), and a built-in <strong>Model Context Protocol (MCP)</strong> runtime that maps those calls to real tools. Under the hood, that ties into <strong>Kali NetHunter</strong>’s <strong>chroot</strong> so the same stack can drive <strong>nmap</strong>, <strong>Metasploit</strong>, RF tools, and ordinary shell workflows — the LLM is the planner, not the process that owns the network stack.</p>
    <p class="kali-deck-note">This project also explores <strong>Greenbone / GVM</strong> (the software behind “OpenVAS”): managed vulnerability scanning, <strong>NVT</strong> feeds, <strong>GMP</strong>, and <strong>GSA</strong>, in tension with <strong>mobile</strong> CPU and <strong>kernel</strong> constraints. <a href="#tech-stack">How that fits the stack</a> · <a href="{{ '/openvas.html' | relative_url }}">GVM in depth</a></p>
    <div class="kali-hero-stats" role="list">
      <div class="kali-hero-stat" role="listitem"><span class="kali-hero-stat-label">LLM</span> Gemini + tool / function APIs</div>
      <div class="kali-hero-stat" role="listitem"><span class="kali-hero-stat-label">MCP</span> Tool schema &amp; in-process host</div>
      <div class="kali-hero-stat" role="listitem"><span class="kali-hero-stat-label">Userland</span> Kali in NetHunter chroot</div>
    </div>
    <p class="kali-hero-motto">The important boundary is <em>cognitive</em> (the model) versus <em>mechanical</em> (Kali, radios, scanners, shells). The former never needs root on your lab hardware; the latter is where the security story lives.</p>
    <div class="kali-hero-cta">
      <a class="btn btn-primary" href="https://github.com/CastaliaInstitute/Kaliyai">Browse the repository</a>
      <a class="btn" href="https://codespaces.new/CastaliaInstitute/Kaliyai?quickstart=1">Optional cloud dev (Codespaces)</a>
    </div>
  </div>
</section>

<section class="kali-pillars" id="three-pillars" aria-label="What makes up Kali AI">
  <p class="kali-section-kicker">What each layer is</p>
  <h2>Three pillars</h2>
  <div class="kali-pillar-grid">
    <article class="kali-card">
      <span class="kali-card-icon" aria-hidden="true">
        <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" focusable="false"><rect x="10" y="3" width="20" height="34" rx="3" stroke="currentColor" stroke-width="1.5"/><rect x="14" y="7" width="12" height="18" rx="1" fill="currentColor" fill-opacity="0.15"/><circle cx="20" cy="30" r="1.5" fill="currentColor" fill-opacity="0.4"/><path d="M8 10 L5 7 L5 14 Z" fill="currentColor" fill-opacity="0.4"/><path d="M8 20 L3 20 L6 16 Z" fill="currentColor" fill-opacity="0.3"/><path d="M32 20 L35 20 L32 16 Z" fill="currentColor" fill-opacity="0.3"/></svg>
      </span>
      <h3>Kaliyai (app + MCP host)</h3>
      <p>Runs on the phone as a normal Android process. <strong>Compose</strong> drives the chat UI; <strong>Coroutines / JVM</strong> run networking to Gemini; the <strong>MCP</strong> layer is the place where you declare and enforce which tools exist and how they are invoked — a structured bridge between an LLM’s <em>intent</em> and concrete actions.</p>
    </article>
    <article class="kali-card">
      <span class="kali-card-icon" aria-hidden="true">
        <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" focusable="false"><rect x="4" y="14" width="32" height="14" rx="2" stroke="currentColor" stroke-width="1.2"/><rect x="8" y="18" width="8" height="3" fill="currentColor" fill-opacity="0.3"/><rect x="8" y="24" width="5" height="2" fill="currentColor" fill-opacity="0.2"/><path d="M20 2 L20 12 M20 2 L16 6 M20 2 L24 6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/><rect x="17" y="28" width="6" height="10" rx="1" fill="currentColor" fill-opacity="0.2"/></svg>
      </span>
      <h3>Kali in a NetHunter chroot</h3>
      <p><strong>Kali Linux</strong> is a Debian-based distribution aimed at security testing. <strong>NetHunter</strong> is Offensive Security’s way of carrying that userland on a rooted phone via a <strong>chroot</strong> (an isolated full Linux root filesystem). You get the same tool ecosystem as a laptop lab — package manager, <code>msfconsole</code>, SDR, Wi-Fi tooling — on hardware you can put in a pocket. This repo was developed against a <strong>OnePlus One (bacon)</strong> with current Kali, but the ideas generalize to other chroot-capable devices.</p>
    </article>
    <article class="kali-card">
      <span class="kali-card-icon" aria-hidden="true">
        <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" focusable="false"><path d="M20 3 L6 20 L12 20 L8 35 L20 25 L20 3 Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" fill="currentColor" fill-opacity="0.12"/><circle cx="28" cy="24" r="6" stroke="currentColor" stroke-width="1" fill="none" stroke-dasharray="3 2"/><path d="M20 3 L20 8" stroke="currentColor" stroke-width="0.8" stroke-opacity="0.5"/></svg>
      </span>
      <h3>Greenbone (GVM / “OpenVAS”)</h3>
      <p><strong>GVM</strong> is Greenbone’s vulnerability-management stack: <strong>PostgreSQL</strong>-backed state, a manager daemon, scanner components, a web front-end (<strong>GSA</strong>), and the <strong>Greenbone Management Protocol (GMP)</strong> for automation. People still say “OpenVAS” for the scanner lineage; the feed of <strong>Network Vulnerability Tests (NVTs)</strong> is what makes it comparable to enterprise exposure programs. <em>How</em> you host that on a small Android kernel (vs. a workstation) is a systems constraint, not a statement about the protocol or data model — see the <a href="{{ '/openvas.html' | relative_url }}">GVM write-up</a>.</p>
    </article>
  </div>
</section>

<section class="kali-teach" id="teaching" aria-label="MSAI Cybersecurity teaching">
  <div class="kali-teach-inner">
    <p class="kali-teach-kicker">Aurnova MSAI · Cybersecurity AI concentration</p>
    <h2>Built for the <strong>AINS-63xx</strong> sequence</h2>
    <p class="kali-teach-deck">Kali AI is a <strong>concrete</strong> stack to teach with: a <strong>classical</strong> offensive Linux userland (Kali, GVM) paired with a <strong>modern</strong> generative control plane (Gemini) and a <strong>governed</strong> tool protocol (MCP) — the same <em>kinds of technologies</em> that appear in threat detection, SOAR, and risk programs, but in a self-contained org. The <strong>AINS 6300–6302</strong> Jupyter Books in Castalia’s GitHub pick up those three themes in structured form.</p>
    <ul class="kali-teach-list">
      <li><a href="https://castaliainstitute.github.io/ains-6300-ai-in-threat-detection/"><strong>AINS 6300</strong> — AI in threat detection</a> · <a href="https://github.com/CastaliaInstitute/ains-6300-ai-in-threat-detection">repo</a></li>
      <li><a href="https://castaliainstitute.github.io/ains-6301-automated-response-systems/"><strong>AINS 6301</strong> — Automated response systems</a> · <a href="https://github.com/CastaliaInstitute/ains-6301-automated-response-systems">repo</a></li>
      <li><a href="https://castaliainstitute.github.io/ains-6302-ai-for-risk-assessment/"><strong>AINS 6302</strong> — AI for risk assessment</a> · <a href="https://github.com/CastaliaInstitute/ains-6302-ai-for-risk-assessment">repo</a></li>
    </ul>
    <p class="kali-teach-cta"><a class="btn btn-primary" href="{{ '/pedagogy.html' | relative_url }}">Why this matters in the program →</a></p>
  </div>
</section>

<section class="kali-arch" id="flow" aria-label="Architecture diagram">
  <p class="kali-section-kicker">Data flow</p>
  <h2>How it fits together</h2>
  <p class="kali-sub">Gemini is a <strong>remote</strong> API: it scores tokens and proposes structured <strong>function calls</strong>, but it does <em>not</em> have a TUN device into your chroot. The phone runs Kaliyai and the tool engine; the chroot runs Kali. Trust boundaries and logging attach to the latter, not to the generative service.</p>
  <svg class="kali-flow-svg" viewBox="0 0 800 200" width="100%" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Data flow: Kaliyai to Gemini, MCP, then Kali chroot">
    <defs>
      <linearGradient id="gbox" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#163040"/><stop offset="100%" stop-color="#0a1520"/></linearGradient>
      <filter id="gs"><feDropShadow dx="0" dy="2" stdDeviation="3" flood-color="#000" flood-opacity="0.45"/></filter>
      <marker id="marrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L8,3 L0,6 z" fill="#22d3ee"/></marker>
    </defs>
    <rect x="0" y="0" width="800" height="200" fill="#050c12" rx="12"/>
    <!-- Kaliyai -->
    <g filter="url(#gs)">
    <rect x="28" y="44" width="150" height="100" rx="10" fill="url(#gbox)" stroke="rgba(34,211,238,0.35)"/>
    <text x="103" y="78" text-anchor="middle" fill="#ecfeff" font-size="16" font-family="DM Sans, system-ui, sans-serif" font-weight="700">Kaliyai</text>
    <text x="103" y="100" text-anchor="middle" fill="#67e8f9" font-size="12" font-family="JetBrains Mono, monospace">Compose UI</text>
    <text x="103" y="120" text-anchor="middle" fill="#67e8f9" font-size="11" font-family="JetBrains Mono, monospace">Gemini client</text>
    </g>
    <!-- arrow 1 -->
    <line x1="188" y1="94" x2="270" y2="94" stroke="#0891b2" stroke-width="2" marker-end="url(#marrow)"/>
    <text x="230" y="80" text-anchor="middle" fill="#22d3ee" font-size="9" font-family="DM Sans, sans-serif">REST · prompt</text>
    <!-- Gemini -->
    <g filter="url(#gs)">
    <rect x="282" y="32" width="150" height="120" rx="10" fill="url(#gbox)" stroke="rgba(120, 180, 255,0.4)"/>
    <text x="357" y="80" text-anchor="middle" fill="#b0d0ff" font-size="16" font-family="DM Sans, system-ui, sans-serif" font-weight="700">Gemini</text>
    <text x="357" y="104" text-anchor="middle" fill="#67e8f9" font-size="11" font-family="DM Sans, sans-serif">function calling</text>
    <text x="357" y="124" text-anchor="middle" fill="#22d3ee" font-size="10" font-family="JetBrains Mono, monospace">REST</text>
    </g>
    <!-- arrow 2 -->
    <line x1="440" y1="94" x2="512" y2="94" stroke="#0891b2" stroke-width="2" marker-end="url(#marrow)"/>
    <text x="478" y="80" text-anchor="middle" fill="#22d3ee" font-size="9" font-family="DM Sans, sans-serif">tool calls</text>
    <!-- MCP -->
    <g filter="url(#gs)">
    <rect x="524" y="44" width="120" height="100" rx="10" fill="url(#gbox)" stroke="rgba(34,211,238,0.35)"/>
    <text x="584" y="85" text-anchor="middle" fill="#ecfeff" font-size="14" font-family="DM Sans, system-ui, sans-serif" font-weight="700">MCP engine</text>
    <text x="584" y="110" text-anchor="middle" fill="#67e8f9" font-size="10" font-family="JetBrains Mono, monospace">in-process</text>
    <text x="584" y="128" text-anchor="middle" fill="#67e8f9" font-size="10" font-family="DM Sans, sans-serif">tool catalog</text>
    </g>
    <!-- arrow: MCP (center x=584) to chroot bar -->
    <line x1="584" y1="144" x2="584" y2="150" stroke="#0891b2" stroke-width="2" marker-end="url(#marrow)"/>
    <text x="600" y="140" text-anchor="middle" fill="#22d3ee" font-size="9" font-family="DM Sans, sans-serif">exec / intent</text>
    <!-- Chroot (wide) -->
    <g filter="url(#gs)">
    <rect x="420" y="150" width="360" height="40" rx="8" fill="#0a1820" stroke="rgba(34,211,238,0.28)"/>
    <text x="600" y="168" text-anchor="middle" fill="#22d3ee" font-size="13" font-family="DM Sans, system-ui, sans-serif" font-weight="600">Kali NetHunter chroot</text>
    <text x="600" y="184" text-anchor="middle" fill="#0891b2" font-size="10" font-family="JetBrains Mono, monospace">nmap · msf · OpenVAS / GVM* · aircrack · …</text>
    </g>
    <text x="8" y="16" fill="#0e7490" font-size="9" font-family="JetBrains Mono, monospace">* GVM: scanner+manager+UI; may split across hosts — see /openvas.html</text>
  </svg>
</section>

<div class="kali-components" id="tech-stack">
  <p class="kali-kicker">Names you can look up</p>
  <h2 id="at-a-glance">How the pieces map</h2>
  <p class="kali-landing-p">The repo implements a <strong>research and teaching</strong> stack, not a hosted product. The table is the mental model: what <em>thing</em> in the world each part corresponds to. Build scripts, flash instructions, and secrets handling live in <a href="https://github.com/CastaliaInstitute/Kaliyai">GitHub</a>, not on this static site.</p>
  <div class="kali-table-wrap">
  <table>
    <thead><tr><th>Concern</th><th>Primary technologies</th></tr></thead>
    <tbody>
      <tr><td>On-device app &amp; control plane</td><td><a href="https://kotlinlang.org/">Kotlin</a> · <a href="https://developer.android.com/jetpack/compose">Jetpack Compose</a> · <a href="https://github.com/CastaliaInstitute/Kaliyai/tree/main/nethunter-gemini-mcp">Kaliyai package</a> <code>com.kali.nethunter.mcpchat</code></td></tr>
      <tr><td>Generative layer</td><td><a href="https://ai.google.dev/gemini-api">Google Gemini API</a> (text + function / tool calls)</td></tr>
      <tr><td>Tool abstraction</td><td><a href="https://modelcontextprotocol.io/">Model Context Protocol</a> — in-process “host” in the app, mapping calls to Kali/ADB/GVM as appropriate</td></tr>
      <tr><td>Linux tool ecosystem</td><td><a href="https://www.kali.org/">Kali</a> userland in a <a href="https://www.kali.org/docs/nethunter/">NetHunter</a> <strong>chroot</strong></td></tr>
      <tr><td>Managed vulnerability assessment</td><td><a href="https://www.greenbone.net/en/blog/openvas-and-gvm-terminology-openvas-gvm-gmp-greenbone-terminology-de-mystified-for-technical-users-01/">GVM / Greenbone</a> (GMP, GSA, NVT feed) — <a href="{{ '/openvas.html' | relative_url }}">lab integration</a></td></tr>
      <tr><td>Optional dev &amp; CI environment</td><td><a href="https://containers.dev/">Dev Containers</a> (Ubuntu, JDK, Android SDK, <a href="https://docs.docker.com/get-started/introduction/#docker-in-docker">Docker</a> sidecar) — <a href="{{ '/codespaces.html' | relative_url }}">how we use it</a></td></tr>
    </tbody>
  </table>
  </div>
  <p class="kali-landing-p">Credentials: <code>GEMINI_API_KEY</code> and any lab passwords stay in <code>.env</code> and <a href="https://docs.github.com/en/codespaces/managing-your-codespaces/managing-secrets-for-your-codespaces">Codespaces / repo secrets</a> — not in <code>docs/</code> or in git history.</p>
  </div>

<section class="kali-cloud" aria-label="Development environment">
  <h2>Same stack, a workstation-class shell</h2>
  <p>Because building an <strong>APK</strong> and hosting a <strong>full Linux</strong> at the same time is resource-heavy, the repository ships a <a href="https://containers.dev/">devcontainer</a> definition: a standard <strong>Ubuntu</strong> base, <strong>Java / Gradle</strong> for the Android app, the <strong>Android SDK</strong> toolchains, and a <strong>Kali</strong> process namespace via <strong>Docker</strong> so you can exercise CLI workflows beside the app without carrying a second laptop image in git. That is <em>an engineering convenience</em>, not a requirement to use Kaliyai on a phone in the field — it is the same components, in a controllable form factor for CI and coursework.</p>
  <a href="https://codespaces.new/CastaliaInstitute/Kaliyai?quickstart=1"><img src="https://github.com/codespaces/badge.svg" width="200" height="32" alt="Open in GitHub Codespaces" /></a>
  <p class="kali-cloud-mono">For install steps, <code>postCreate</code> behavior, and secret wiring, read <a href="{{ '/codespaces.html' | relative_url }}">Development environment (Codespaces / devcontainer)</a> — a companion page to this overview.</p>
  <div class="kali-cloud-terminal" aria-label="Illustrative commands only">
  <div class="kali-cloud-terminal-bar">Example — not a deployment checklist</div>
  <pre><code>cd nethunter-gemini-mcp
./gradlew :app:assembleDebug
# in devcontainer, Kali is available as a sidecar / alias</code></pre>
  </div>
</section>
</div>

<div class="kali-footer-block kali-landing">
  <p class="kali-lede">The <a href="https://github.com/CastaliaInstitute/Kaliyai#readme">README on GitHub</a> records repo layout, build steps, and contribution workflow. <strong>This site is the architecture narrative</strong>: which technologies are in play, how responsibilities are split, and how the work connects to the <a href="{{ '/pedagogy.html' | relative_url }}">AINS-63xx</a> program.</p>
  <div class="kali-footer-grid">
  <div class="kali-footer-col" markdown="1">

## Status {#status}

- Kaliyai — Compose + chat
- Gemini — function-calling
- Builtin MCP + eval harness
- OnePlus One + NetHunter
- **Greenbone GVM** — NVT / GMP / GSA
- **Devcontainer** (optional) — build + Kali sidecar
- **AINS 6300–6302** Jupyter Books
{: .kali-chips}

  </div>
  <div class="kali-footer-col" markdown="1">

## Links {#links}

- [kaliyai.castalia.institute](https://kaliyai.castalia.institute/) (this site) · <a href="{{ '/DOMAIN.html' | relative_url }}">DNS &amp; Cloudflare</a>
- [Pedagogy &amp; AINS-63xx]({{ '/pedagogy.html' | relative_url }})
- <a href="https://github.com/CastaliaInstitute/Kaliyai">CastaliaInstitute/Kaliyai</a> on GitHub
- <a href="https://www.kali.org/docs/nethunter/">Kali NetHunter</a> · <a href="https://ai.google.dev/">Gemini</a> · <a href="https://modelcontextprotocol.io/">MCP</a>

  </div>
  </div>
</div>
