#!/usr/bin/env bash
# Run all RAG-related checks: kaliyai-rag pytest, golden retrieval (Gemini + SQLite), Android *Rag* unit tests.
# Loads GEMINI_API_KEY from nethunter-gemini-mcp/.env or kaliyai-rag/.env when present.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for envfile in "${ROOT}/nethunter-gemini-mcp/.env" "${ROOT}/kaliyai-rag/.env"; do
  if [[ -f "$envfile" ]] && grep -qE '^[[:space:]]*(GEMINI_API_KEY|GOOGLE_API_KEY)[[:space:]]*=' "$envfile" 2>/dev/null; then
    set -a
    # shellcheck disable=SC1090
    source "$envfile"
    set +a
    break
  fi
done

echo "======== kaliyai-rag: pytest (offline) ========"
cd "${ROOT}/kaliyai-rag"
if [[ -f .venv/bin/activate ]]; then
  # shellcheck disable=SC1091
  source .venv/bin/activate
fi
python -m pytest tests/ -q --tb=short

echo "======== kaliyai-rag: multi-chunk ranking eval (offline, no Gemini) ========"
python -m kaliyai_rag.cli eval-ranking

echo "======== kaliyai-rag: golden retrieval (network: Gemini embed) ========"
./scripts/run_retrieval_evals.sh

echo "======== nethunter-gemini-mcp: unit tests *Rag* ========"
cd "${ROOT}/nethunter-gemini-mcp"
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
fi
./gradlew :app:testDebugUnitTest --tests '*Rag*' --no-daemon

echo "======== All extensive RAG eval stages completed ========"
