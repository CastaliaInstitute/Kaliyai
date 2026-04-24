#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ "${1:-}" = "--wipe" ]; then
  echo "[!] --wipe: removing all GVM data volumes. This deletes feeds, DB, scan history."
  read -r -p "Type 'yes' to proceed: " ans; [ "$ans" = "yes" ] || { echo aborted; exit 1; }
  docker compose down -v
  echo "[+] Stack down; volumes wiped."
else
  docker compose down
  echo "[+] Stack stopped. Volumes preserved. Run './up.sh' to resume."
  echo "    To fully wipe data:  ./down.sh --wipe"
fi
