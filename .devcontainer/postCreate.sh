#!/usr/bin/env bash
# Post-create setup for the Kali AI / Anubis Codespace.
# Runs once after the devcontainer image is built.

set -euo pipefail

echo "==> [1/5] Seeding .env from GEMINI_API_KEY secret (if set)"
if [[ -n "${GEMINI_API_KEY:-}" ]]; then
  cat > nethunter-gemini-mcp/.env <<EOF
GEMINI_API_KEY=${GEMINI_API_KEY}
EOF
  echo "    wrote nethunter-gemini-mcp/.env"
else
  echo "    GEMINI_API_KEY not set. Add it as a Codespaces secret, then re-run this script."
fi

echo "==> [2/5] Pulling Kali Linux rolling container"
# Use Docker-in-Docker so we can sandbox Kali tooling away from the Codespace host.
docker pull kalilinux/kali-rolling:latest || {
  echo "    (docker pull failed — you may need to wait for dockerd to come up; re-run 'docker pull kalilinux/kali-rolling:latest' later)"
}

echo "==> [3/5] Creating persistent 'kali' container"
if ! docker ps -a --format '{{.Names}}' | grep -q '^kali$'; then
  docker run -d --name kali \
    --restart unless-stopped \
    -v "${PWD}":/workspaces/anubis \
    -w /workspaces/anubis \
    kalilinux/kali-rolling:latest \
    sleep infinity || true
fi

echo "==> [4/5] Provisioning Kali with a baseline toolkit"
docker exec kali bash -lc '
  set -e
  apt-get update -y
  # kali-linux-headless is huge; default to a lighter baseline the user can expand.
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
' || echo "    (apt provisioning failed; re-run manually once docker is ready)"

echo "==> [5/5] Installing shell helpers"
BASHRC="${HOME}/.bashrc"
if ! grep -q "# kali-ai-helpers" "$BASHRC" 2>/dev/null; then
  cat >> "$BASHRC" <<'EOF'

# kali-ai-helpers ---------------------------------------------------------
alias kali='docker exec -it kali bash'
alias kali-root='docker exec -it -u 0 kali bash'
alias anubis-build='(cd /workspaces/anubis/nethunter-gemini-mcp && ./gradlew :app:assembleDebug)'
alias anubis-test='(cd /workspaces/anubis/nethunter-gemini-mcp && ./gradlew :app:testDebugUnitTest)'
alias anubis-evals='(cd /workspaces/anubis/nethunter-gemini-mcp && bash scripts/run-evals.sh)'

echo ""
echo "  Kali AI / Anubis Codespace ready."
echo "    anubis-build   build the Android APK"
echo "    anubis-test    run unit tests"
echo "    anubis-evals   run the Gemini eval harness"
echo "    kali           drop into the Kali Linux container"
echo ""
# ------------------------------------------------------------------------
EOF
fi

echo "Done. Open a new terminal to pick up aliases."
