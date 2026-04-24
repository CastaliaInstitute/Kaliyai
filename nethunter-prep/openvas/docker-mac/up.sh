#!/usr/bin/env bash
# Bring up Greenbone Community Containers on this Mac.
# First run is slow (~2-4 GB of images + feed sync inside containers).

set -euo pipefail
cd "$(dirname "$0")"

command -v docker >/dev/null || { echo "docker not installed" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker Desktop isn't running; start it and retry." >&2; exit 1; }

echo "[*] Pulling Greenbone Community images (first run only, ~3 GB)..."
docker compose pull

echo "[*] Starting stack..."
docker compose up -d

echo "[*] Waiting for GSA web UI on localhost:9392..."
for i in {1..90}; do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:9392/ 2>/dev/null | grep -qE '^(200|301|302|401|403)$'; then
    echo "[+] GSA is up."
    break
  fi
  sleep 5
done

echo
echo "========================================================================"
echo " Open: http://localhost:9392   (plain HTTP; only bound to 127.0.0.1)"
echo " First-time admin password:  ./admin-password.sh"
echo " Logs:                       docker compose logs -f gvmd"
echo " Down:                       ./down.sh"
echo "========================================================================"
