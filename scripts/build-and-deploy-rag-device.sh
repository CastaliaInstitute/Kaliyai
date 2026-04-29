#!/usr/bin/env bash
# Build offline SQLite RAG (sample + optional NIST PDFs), push kaliyai_rag.db, install debug APK.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAG="${REPO_ROOT}/kaliyai-rag"
MCP_ENV="${REPO_ROOT}/nethunter-gemini-mcp/.env"
CASTALIA_ENV="${REPO_ROOT}/../castalia.institute/.env"

# Max PDF pages per NIST file (text layer via --mode auto); reduce if embedding API is slow.
: "${KALIYAI_RAG_MAX_PDF_PAGES:=35}"

if [[ -f "${MCP_ENV}" ]] && grep -qE '^[[:space:]]*GEMINI_API_KEY[[:space:]]*=' "${MCP_ENV}" 2>/dev/null; then
  set -a
  # shellcheck disable=SC1090
  source "${MCP_ENV}"
  set +a
elif [[ -f "${CASTALIA_ENV}" ]] && grep -qE '^[[:space:]]*GEMINI_API_KEY[[:space:]]*=' "${CASTALIA_ENV}" 2>/dev/null; then
  set -a
  # shellcheck disable=SC1090
  source "${CASTALIA_ENV}"
  set +a
else
  echo "No GEMINI_API_KEY in ${MCP_ENV} or ${CASTALIA_ENV}"
  exit 1
fi

cd "${RAG}"
if [[ ! -d .venv ]]; then
  python3 -m venv .venv
fi
# shellcheck disable=SC1091
source .venv/bin/activate
pip install -q -r requirements.txt

export PYTHONPATH=.
OUT="${RAG}/data/out"
mkdir -p "${OUT}"

META="${OUT}/bundle_meta.yaml"
cat > "${META}" << 'YAML'
source: Kaliyai offline RAG bundle
version: seed-deploy
domain: governance
task_type: [interpretation]
framework: NIST
maps_to: []
offensive_risk: low
allowed_use: defensive
evidence_type: [documentation]
audience: [analyst, engineer]
uri: https://kaliyai.castalia.institute/
YAML

BUNDLE="${OUT}/corpus_bundle.md"
cp "${RAG}/sample_corpus/readme_excerpt.md" "${BUNDLE}"

if compgen -G "${RAG}/sources/nist/*.pdf" > /dev/null; then
  echo "==> Extracting NIST PDFs (auto text layer, max ${KALIYAI_RAG_MAX_PDF_PAGES} pages each)"
  for pdf in "${RAG}/sources/nist"/*.pdf; do
    [[ -f "${pdf}" ]] || continue
    base=$(basename "${pdf}" .pdf)
    echo ""
    echo ""
    echo "## ${base}"
    echo ""
    python -m kaliyai_rag.cli pdf-to-text "${pdf}" \
      -o "${OUT}/${base}.txt" \
      --mode auto \
      --max-pages "${KALIYAI_RAG_MAX_PDF_PAGES}"
    cat "${OUT}/${base}.txt" >> "${BUNDLE}"
  done
else
  echo "==> No PDFs under kaliyai-rag/sources/nist/ — using sample markdown only (run kaliyai-rag/scripts/bootstrap_seed_pack.sh --nist-only)"
fi

python -m kaliyai_rag.cli chunk-file "${BUNDLE}" \
  --meta-yaml "${META}" \
  -o "${OUT}/chunks.jsonl"

python -m kaliyai_rag.cli embed-jsonl \
  -i "${OUT}/chunks.jsonl" \
  -o "${OUT}/chunks.embedded.jsonl" \
  --batch-size 8

python -m kaliyai_rag.cli export-sqlite \
  -i "${OUT}/chunks.embedded.jsonl" \
  -o "${OUT}/kaliyai_rag.db"

DEVICE_PATH="/sdcard/Download/kaliyai_rag.db"
adb push "${OUT}/kaliyai_rag.db" "${DEVICE_PATH}"
echo "Pushed DB to device: ${DEVICE_PATH}"

cd "${REPO_ROOT}/nethunter-gemini-mcp"
./gradlew installDebug -q

echo ""
echo "--- Phone ---------------------------------------------------------------"
echo "Default DB path is Downloads/kaliyai_rag.db (no Settings change needed)."
echo "  /storage/emulated/0/Download/kaliyai_rag.db"
echo "(Grant storage read if needed on your Android version.)"
