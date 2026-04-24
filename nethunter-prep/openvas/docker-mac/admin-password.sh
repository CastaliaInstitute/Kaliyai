#!/usr/bin/env bash
# Reset / print the GVM admin password.
# On a fresh first run the Greenbone containers create 'admin' automatically with a random password
# that's only printed once in gvmd logs. Easiest: just set our own.

set -euo pipefail
cd "$(dirname "$0")"

NEW_PW="${1:-}"
if [ -z "$NEW_PW" ]; then
  # Temporarily disable pipefail so SIGPIPE from tr->head doesn't abort us.
  set +o pipefail
  NEW_PW=$(LC_ALL=C tr -dc 'A-Za-z0-9!@#%^*_=+-' </dev/urandom | head -c 24)
  set -o pipefail
fi

echo "[*] Stopping gvmd daemon so we can run gvmd CLI against the same DB..."
docker compose stop gvmd >/dev/null

# Greenbone's gvmd entrypoint does 'exec gosu gvmd "$@"', so keeping the entrypoint
# and overriding only the command ensures we run as the right unix user with proper
# DB role (peer auth). Any non-zero exit is tolerated (user may not exist yet).
echo "[*] Ensuring admin user exists and setting password..."
out=$(docker compose run --rm --no-deps gvmd \
      gvmd --user=admin --new-password="$NEW_PW" 2>&1 || true)
echo "$out" | grep -vE '^$|postgres container|retry psql|SELECT|connection|^\(' | head -20

if echo "$out" | grep -qiE 'Failed to find user|User not found'; then
  echo "[*] 'admin' does not yet exist; creating..."
  docker compose run --rm --no-deps gvmd \
      gvmd --create-user=admin --password="$NEW_PW" 2>&1 | tail -5
fi

echo "[*] Restarting gvmd..."
docker compose up -d gvmd >/dev/null

mkdir -p .secrets
chmod 700 .secrets
printf 'admin\n%s\n' "$NEW_PW" > .secrets/admin.txt
chmod 600 .secrets/admin.txt

echo "[+] admin credentials:"
echo "    user:      admin"
echo "    password:  $NEW_PW"
echo
echo "Saved to:  $(pwd)/.secrets/admin.txt"
echo "Login at:  http://localhost:9392"
