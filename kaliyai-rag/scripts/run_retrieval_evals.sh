#!/usr/bin/env bash
# Build sample_corpus → embedded JSONL → SQLite, then run golden retrieval evals (needs GEMINI_API_KEY).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ -f "${ROOT}/.venv/bin/activate" ]]; then
  # shellcheck disable=SC1091
  source "${ROOT}/.venv/bin/activate"
fi
TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

OUT_JSONL="$TMP/chunks.jsonl"
OUT_EMB="$TMP/chunks.embedded.jsonl"
OUT_DB="$TMP/kaliyai_rag.db"

python -m kaliyai_rag.cli chunk-file sample_corpus/readme_excerpt.md \
  --meta-yaml sample_corpus/meta.yaml \
  -o "$OUT_JSONL"
python -m kaliyai_rag.cli embed-jsonl -i "$OUT_JSONL" -o "$OUT_EMB"
python -m kaliyai_rag.cli export-sqlite -i "$OUT_EMB" -o "$OUT_DB"
python -m kaliyai_rag.cli eval-retrieval --db "$OUT_DB"
