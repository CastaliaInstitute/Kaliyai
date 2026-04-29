#!/usr/bin/env bash
# Run the Kaliyai / NetHunter MCP eval suite.
#
# Default: offline tests only (built-in tools, intent routing) — no Gemini network calls.
# Live Gemini prompt evals: export KALIYAI_LIVE_GEMINI_EVAL=1 and GEMINI_API_KEY (e.g. from project .env).
# Example:
#   set -a && [ -f .env ] && . ./.env && set +a
#   export KALIYAI_LIVE_GEMINI_EVAL=1
#   ./scripts/run-evals.sh
#
# Test categories:
#   - BuiltinMcpEngineEvalTest: Core MCP tools (echo, wifi_scan, kali_nethunter_*)
#   - IntentRoutingEvalTest: Intent routing and parsing
#   - GeminiPromptE2eEvalTest: Live Gemini API tests (requires GEMINI_API_KEY)
#   - OperatorEvalTest: 60+ SecOps field scenarios (see kaliyai_operator_evals.json)
#   - RagToolStubEvalTest: kaliyai_rag_retrieve stub (evals/rag_eval_cases.json)
#   - RagOfflineStoreEvalTest: SQLite cosine search + domain filter (no network)
#
# Full RAG stack (pytest + eval-ranking + golden retrieval + *Rag* tests): ../scripts/run-extensive-rag-evals.sh from repo root.
# Retrieval-only (Python): kaliyai-rag/scripts/run_retrieval_evals.sh (needs GEMINI_API_KEY).
#
# Run specific test class:
#   ./scripts/run-evals.sh --tests '*OperatorEvalTest*'
#
set -euo pipefail
cd "$(dirname "$0")/.."
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
fi
exec ./gradlew :app:testDebugUnitTest --no-daemon "$@"
