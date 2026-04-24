#!/usr/bin/env bash
# Run the Anubis / NetHunter MCP eval suite.
#
# Default: offline tests only (built-in tools, intent routing) — no Gemini network calls.
# Live Gemini prompt evals: export ANUBIS_LIVE_GEMINI_EVAL=1 and GEMINI_API_KEY (e.g. from project .env).
# Example:
#   set -a && [ -f .env ] && . ./.env && set +a
#   export ANUBIS_LIVE_GEMINI_EVAL=1
#   ./scripts/run-evals.sh
set -euo pipefail
cd "$(dirname "$0")/.."
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
fi
exec ./gradlew :app:testDebugUnitTest --no-daemon "$@"
