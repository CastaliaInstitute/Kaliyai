# Kaliyai RAG

Retrieval layer for **Kali&AI**: multi-step analyst workflows (scope → enumerate → interpret → map to frameworks → assess risk → safe next step → report), backed by a **canonical + operational** corpus—not generic blog spam.

This folder is a **Python 3.11+** toolkit to:

1. Define a **chunk metadata schema** (filters, safety, audience, framework crosswalks).
2. **Chunk** plain text / markdown, **embed** with **Gemini** (`gemini-embedding-001` by default), and store vectors in **Postgres + pgvector** (tested layout: **Supabase**).
3. Track **MVP sources** and **analyst questions** as YAML for routing and evaluation (`config/`).

The Android app (`nethunter-gemini-mcp/`) does not bundle the corpus; wire retrieval through a **host-side MCP tool** or backend that calls the same DB/RPC with your org’s keys.

## Corpus philosophy

- **Tier 1–10** coverage matches the roadmap you maintain in product docs: standards (NIST CSF, CIS, MITRE), vuln intel (CVE/NVD, KEV, CWE), methodologies (PTES, OWASP WSTG), tool manuals (Nmap, Burp, ZAP, …), detection content (Sigma, Elastic, …), cloud/K8s, compliance summaries, report templates, lab routing.
- **License reality**: many standards are **not** redistributable from this repo. Keep **downloadable artifacts** under `kaliyai-rag/data/` (gitignored) and ingest only what your license allows.
- **Offensive/exploit content**: index **metadata and defensive framing** in production; avoid storing raw exploit code unless the deployment is **lab-only** and policy allows it.

### Bulk seed pack (repos + NIST PDFs + feeds)

Official mirrors and public feeds are scripted so you can populate `sources/` without committing binaries:

- **`docs/CORPUS_SEED_PACK.md`** — three collections (`cyber_standards`, `cyber_intel_structured`, `cyber_tools_and_detection`) and ingestion notes.
- **`config/corpus_collections.yaml`** — same mapping for pipelines.
- **`scripts/bootstrap_seed_pack.sh`** — `git clone --depth 1`, `curl` NIST PDFs, CISA KEV JSON/CSV.

```bash
cd kaliyai-rag && chmod +x scripts/bootstrap_seed_pack.sh
./scripts/bootstrap_seed_pack.sh          # everything
./scripts/bootstrap_seed_pack.sh --git-only
```

## Quick start

```bash
cd kaliyai-rag
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
# Optional PDF OCR (pdf-to-text): install system `tesseract` + `poppler` — see docs/CORPUS_SEED_PACK.md
export GEMINI_API_KEY="..."   # or copy .env.example -> .env
python -m kaliyai_rag.cli env-check
```

### Download corpus from the web

Fetch pages with a clear **User-Agent**, pause between batch requests, and **only** index content you are allowed to use (copyright, license, robots).

```bash
# Single page → Markdown on disk
python -m kaliyai_rag.cli fetch-url "https://example.com/" -o ./data/raw/example.md

# Many URLs (see config/urls_example.txt)
python -m kaliyai_rag.cli fetch-urls -i ./config/urls_example.txt -o ./data/raw --delay 2
```

Then run **`chunk-file`** on each `.md` with an appropriate `--meta-yaml` (set `uri` to the canonical source URL).

### 1) Chunk a local text file

Write a **meta YAML** matching `ChunkMetadata` (see `kaliyai_rag/schema.py`). Example `sample-meta.yaml`:

```yaml
source: OWASP Web Security Testing Guide
version: stable
domain: web-security
task_type: [test-plan, interpretation]
framework: OWASP
maps_to: []
offensive_risk: low
allowed_use: defensive
evidence_type: [http-request, header]
audience: [analyst, engineer]
uri: https://owasp.org/www-project-web-security-testing-guide/
```

```bash
python -m kaliyai_rag.cli chunk-file ./data/wstg_fragment.md \
  --meta-yaml ./sample-meta.yaml \
  -o ./data/out/chunks.jsonl
```

### 2) Embed JSONL

```bash
python -m kaliyai_rag.cli embed-jsonl \
  -i ./data/out/chunks.jsonl \
  -o ./data/out/chunks.embedded.jsonl \
  --batch-size 8
```

### 3) Database (Supabase / Postgres)

Apply `sql/001_pgvector_kaliyai_rag.sql` in the SQL editor (or your migration runner).

Set:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY` (ingestion jobs only; never ship to mobile clients)

Upsert:

```bash
python -m kaliyai_rag.cli upsert-jsonl -i ./data/out/chunks.embedded.jsonl
```

Query (dev):

```bash
python -m kaliyai_rag.cli query "How do I test for SSRF safely in an authorized assessment?" --top-k 8 --domain web-security
```

### 4) Offline SQLite for Kaliyai Android (SD card)

The Android app can load a **read-only SQLite** file produced here—no Supabase required on-device.

1. Build `chunks.embedded.jsonl` (steps 1–2 above).
2. Export:

```bash
python -m kaliyai_rag.cli export-sqlite \
  -i ./data/out/chunks.embedded.jsonl \
  -o ./data/out/kaliyai_rag.db
```

3. Copy `kaliyai_rag.db` to the phone (USB, `adb push` to **Downloads**, or SD sync). The app defaults to **`Download/kaliyai_rag.db`**; use Settings only to override the path.

The schema matches `OfflineRagStore.kt` (`chunks` table). Retrieval runs **full-scan cosine similarity** on-device (good for modest corpora). Query embeddings still use **Gemini** over the network unless you add an on-device embedder later.

In the Kaliyai Android app, retrieval happens in two ways:

1. **Automatic:** each user message can augment the system prompt with top‑K chunks when the corpus file is present (default path or optional override in Settings).
2. **Tool:** the model may call **`kaliyai_rag_retrieve`** with `{ "query": "...", "domain": "...", "top_k": 8 }` to pull corpus excerpts into the tool-result channel on demand.

### Golden retrieval evals (sample corpus)

Regression checks that **natural questions** retrieve chunks containing expected phrases from `sample_corpus/readme_excerpt.md`. This calls Gemini for **query** embeddings only (same model/settings as your corpus build).

```bash
python -m kaliyai_rag.cli chunk-file sample_corpus/readme_excerpt.md \
  --meta-yaml sample_corpus/meta.yaml -o /tmp/chunks.jsonl
python -m kaliyai_rag.cli embed-jsonl -i /tmp/chunks.jsonl -o /tmp/emb.jsonl
python -m kaliyai_rag.cli export-sqlite -i /tmp/emb.jsonl -o /tmp/kaliyai_rag.db
python -m kaliyai_rag.cli eval-retrieval --db /tmp/kaliyai_rag.db
```

Or: `./scripts/run_retrieval_evals.sh`. From the **kaliyai repo root**, `./scripts/run-extensive-rag-evals.sh` runs pytest, **`eval-ranking`** (offline multi-chunk distractor DB — see below), this Gemini retrieval suite, then Android `*Rag*` tests.

**Multi-chunk ranking (fast, no API):** `config/evals/retrieval_ranking.json` exercises cosine **ranking among competing chunks** (`tests/fixtures/ranking/kaliyai_ranking.db`). Regenerate after changing `kaliyai_rag/ranking_fixture.py`:

`python -m kaliyai_rag.cli refresh-ranking-fixture`

Golden semantic cases live in **`config/evals/retrieval_golden.json`** (`body_contains_any`, optional `domain` / `meta_source_contains`). Extend that file as you grow the corpus; larger deployments usually snapshot a **fixture DB** or subset for CI.

## Metadata schema

Chunks carry structured JSON (`meta`) alongside `body` text. The canonical shape is `ChunkMetadata` in `kaliyai_rag/schema.py` — fields include `source`, `version`, `domain`, `task_type`, `framework`, `maps_to`, `offensive_risk`, `allowed_use`, `evidence_type`, `audience`, `uri`.

Use **`domain`** and **`task_type`** for hybrid retrieval with `match_kaliyai_rag_chunks` (SQL RPC).

## Config files

| File | Purpose |
|------|---------|
| `config/sources_mvp.yaml` | MVP corpus pointers + tiers + canonical URIs |
| `config/question_taxonomy.yaml` | Twelve analyst questions → suggested filters |

## Environment variables

| Variable | Purpose |
|----------|---------|
| `GEMINI_API_KEY` / `GOOGLE_API_KEY` | Embeddings (`embedContent`) |
| `KALIYAI_EMBEDDING_MODEL` | Optional; default `models/gemini-embedding-001` (matches Kaliyai Android offline RAG). |
| `KALIYAI_EMBEDDING_DIMENSION` | Optional; must match `vector(N)` if you change Gemini `outputDimensionality` |
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | REST upsert + RPC search |

## Tests

```bash
cd kaliyai-rag
pytest -q
```

## Safety

- Treat **`allowed_use`** and **`offensive_risk`** as first-class retrieval filters for generation policy.
- Prefer **`RETRIEVAL_QUERY`** embeddings at query time and **`RETRIEVAL_DOCUMENT`** for corpus chunks (handled in `embeddings.py`).
- For production mobile users, expose retrieval only via **authenticated backend or MCP** with rate limits and audit logging—not raw Supabase keys in the APK.
