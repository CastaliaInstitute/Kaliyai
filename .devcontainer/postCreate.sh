#!/usr/bin/env bash
# Post-create setup for the Kali AI / Anubis Codespace.
# Runs once after the devcontainer image is built.
#
# Gemini: never log or export the key. Prefer GitHub Codespace secrets
# (GEMINI_API_KEY) or a local, gitignored nethunter-gemini-mcp/.env. Gradle
# reads that file; we do not source it into .bashrc (keeps the key out of
# generic shell environments).

set -euo pipefail

MCP_ENV="nethunter-gemini-mcp/.env"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

write_env_from_env_var() {
  umask 077
  if [[ -n "${GEMINI_API_KEY:-}" ]]; then
    printf 'GEMINI_API_KEY=%s\n' "${GEMINI_API_KEY}" > "${REPO_ROOT}/${MCP_ENV}"
    echo "    wrote ${MCP_ENV} (from environment; key not shown)"
  fi
}

echo "==> [1/5] nethunter-gemini-mcp/.env (Gradle; gitignored, never committed)"
# Priority: 1) keep existing  2) repo-root .env copy  3) $GEMINI_API_KEY (e.g. Codespace secret)
if [[ -f "${REPO_ROOT}/${MCP_ENV}" ]]; then
  if grep -qE '^[[:space:]]*GEMINI_API_KEY[[:space:]]*=' "${REPO_ROOT}/${MCP_ENV}" 2>/dev/null; then
    echo "    using existing ${MCP_ENV}"
  else
    if [[ -n "${GEMINI_API_KEY:-}" ]]; then
      write_env_from_env_var
    else
      echo "    ${MCP_ENV} exists but has no GEMINI line; set Codespace secret GEMINI_API_KEY and re-run this script"
    fi
  fi
elif [[ -f "${REPO_ROOT}/.env" ]] && grep -qE '^[[:space:]]*GEMINI_API_KEY[[:space:]]*=' "${REPO_ROOT}/.env" 2>/dev/null; then
  umask 077
  cp "${REPO_ROOT}/.env" "${REPO_ROOT}/${MCP_ENV}"
  echo "    copied repo-root .env -> ${MCP_ENV} (untracked)"
elif [[ -n "${GEMINI_API_KEY:-}" ]]; then
  write_env_from_env_var
else
  echo "    no key yet. Add a [Codespaces secret] GEMINI_API_KEY for this repo, or create a local, gitignored ${MCP_ENV}. See docs/codespaces.md and README."
fi

echo "==> [2/5] Pulling Kali Linux rolling container"
docker pull kalilinux/kali-rolling:latest || {
  echo "    (docker pull failed — re-run: docker pull kalilinux/kali-rolling:latest)"
}

echo "==> [3/5] Creating persistent 'kali' container"
if ! docker ps -a --format '{{.Names}}' | grep -q '^kali$'; then
  docker run -d --name kali \
    --restart unless-stopped \
    -v "${REPO_ROOT}":/workspaces/anubis \
    -w /workspaces/anubis \
    kalilinux/kali-rolling:latest \
    sleep infinity || true
fi

echo "==> [4/5] Provisioning Kali with a baseline toolkit"
docker exec kali bash -lc '
  set -e
  apt-get update -y
  DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    nmap \
    netcat-traditional \
    curl \
    wget \
    git \
    python3 \
    python3-pip \
    jq \
    dnsutils \
    iproute2 \
    iputils-ping \
    ca-certificates
' || echo "    (apt provisioning failed; re-run when docker is ready)"

echo "==> [5/5] Shell helpers in ~/.bashrc (no .env source — avoids exporting GEMINI to every shell)"
BASHRC="${HOME}/.bashrc"
if ! grep -q "# kali-ai-helpers" "$BASHRC" 2>/dev/null; then
  cat >> "$BASHRC" <<EOF

# kali-ai-helpers ---------------------------------------------------------
alias kali='docker exec -it kali bash'
alias kali-root='docker exec -it -u 0 kali bash'
alias anubis-build='(cd ${REPO_ROOT}/nethunter-gemini-mcp && ./gradlew :app:assembleDebug)'
alias anubis-test='(cd ${REPO_ROOT}/nethunter-gemini-mcp && ./gradlew :app:testDebugUnitTest)'
alias anubis-evals='(cd ${REPO_ROOT}/nethunter-gemini-mcp && bash scripts/run-evals.sh)'

echo ""
echo "  Kali AI / Anubis Codespace ready. Gemini key: gitignored ${MCP_ENV} or Codespaces secret; never commit."
echo "    anubis-build   build the Android APK"
echo "    anubis-test   unit tests (use GEMINI in CI via gh secret, not in Pages)"
echo "    kali           drop into the Kali container"
echo ""
# ------------------------------------------------------------------------
EOF
fi

echo "Done. Open a new shell for aliases."
