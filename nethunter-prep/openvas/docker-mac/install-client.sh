#!/usr/bin/env bash
# Install the GVM client tools (gvm-tools / gvm-cli) inside the NetHunter chroot
# on the adb-connected device, and configure them to talk to the Docker GVM
# running on this Mac over GMP on port 9390 (exposed via adb reverse).
#
# After this you can, from NetHunter Terminal on the phone, run:
#   gvm-cli tls --hostname 127.0.0.1 --port 9390 \
#     --gmp-username admin --gmp-password "$(cat ~/.gvm-admin)" \
#     --xml "<get_version/>"

set -euo pipefail

cd "$(dirname "$0")"
CHROOT=/data/local/nhsystem/kalifs
BUSYBOX=/data/data/com.offsec.nethunter/scripts/bin/busybox_nh

command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }
adb devices | awk 'NR>1 && $2=="device"' | read _ _ _ _ || { echo "no adb device"; exit 1; }

if ! docker compose ps --services 2>/dev/null | grep -q '^gvmd$'; then
  echo "[!] GVM containers aren't up. Run ./up.sh first."; exit 1
fi

echo "[*] Ensuring gmp-bridge sidecar is running..."
docker compose up -d gmp-bridge >/dev/null

echo "[*] adb reverse tcp:9390 (phone) -> Mac:9390 (gmp-bridge)..."
adb reverse --remove tcp:9390 2>/dev/null || true
adb reverse tcp:9390 tcp:9390

echo "[*] Installing gvm-tools in the NetHunter chroot..."
cat > /tmp/install-gvm-client.sh <<'EOF'
#!/bin/bash
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export DEBIAN_FRONTEND=noninteractive
set -e
apt-get update -qq
apt-get install -y --no-install-recommends gvm-tools python3-gvm
# On Android, network syscalls require membership in GID 3003 (sockets) / 3004 (inet).
# The _gvm user (created by the python3-gvm dependency chain, or here as fallback)
# needs those groups to open TCP sockets inside the chroot.
id _gvm >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin _gvm
for gid in 3003 3004; do
  g=$(getent group "$gid" | cut -d: -f1)
  [ -n "$g" ] && usermod -aG "$g" _gvm 2>/dev/null || true
done
echo "[+] _gvm groups: $(id _gvm)"
mkdir -p /root/.config/gvm
cat > /root/.config/gvm/gvm-tools.conf <<'CFG'
[auth]
gmp_username = admin

[main]
timeout = 60

[gmp]
username = admin
CFG
echo '[+] gvm-tools installed.'
gvm-cli --version || true
echo
echo 'Try from this chroot / NetHunter Terminal:'
echo "  gvm-cli --gmp-username admin --gmp-password 'PASTE' \\"
echo "     tls --hostname 127.0.0.1 --port 9390 --no-credentials \\"
echo '     --xml "<get_version/>"'
EOF

adb push /tmp/install-gvm-client.sh /sdcard/openvas-stage/install-gvm-client.sh >/dev/null
adb shell "su -c 'cp /sdcard/openvas-stage/install-gvm-client.sh ${CHROOT}/root/install-gvm-client.sh && chmod +x ${CHROOT}/root/install-gvm-client.sh'"
adb shell "su -c 'env -i HOME=/root TERM=xterm LANG=C.UTF-8 PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ${BUSYBOX} chroot ${CHROOT} /bin/bash -lc /root/install-gvm-client.sh'"

echo
echo "========================================================================"
echo " gvm-tools installed on the phone. Test from NetHunter Terminal:"
echo
echo "   gvm-cli tls --hostname 127.0.0.1 --port 9390 \\"
echo "     --gmp-username admin --gmp-password '<PW>' \\"
echo "     --xml '<get_version/>'"
echo
echo " The 'adb reverse' from this script persists until the USB cable is"
echo " disconnected or adb restarted. Re-run this script to re-establish."
echo "========================================================================"
