#!/usr/bin/env bash
# OpenVAS / Greenbone Vulnerability Manager deployment orchestrator
# Runs on the Mac; drives install inside the Kali NetHunter chroot on an adb-connected Android device.
#
# Target device: rooted NetHunter with Kali chroot at /data/local/nhsystem/kalifs
# Tested on:    OnePlus One (bacon), Android 11 / LOS 18.1, armhf, 3 GB RAM
#
# Usage:
#   ./deploy.sh swap       # create 4 GB swapfile on /data (REQUIRED before install on low-RAM devices)
#   ./deploy.sh install    # apt install gvm + run gvm-setup inside the chroot (long: 1+ hour)
#   ./deploy.sh start      # start postgresql, redis, gvmd, ospd-openvas, gsad
#   ./deploy.sh stop       # stop all GVM services
#   ./deploy.sh forward    # adb forward tcp:9392 -> device tcp:9392 (access https://localhost:9392 on Mac)
#   ./deploy.sh status     # show service status and process list
#   ./deploy.sh logs [svc] # tail logs (gvmd|ospd|gsad|feed); default: gvmd
#   ./deploy.sh shell      # drop into an interactive shell inside the chroot
#   ./deploy.sh feed-sync  # resync NVT + SCAP + CERT feeds (slow)
#   ./deploy.sh uninstall  # apt purge gvm and related packages
#   ./deploy.sh kill-apt   # kill in-progress apt in chroot + clean dpkg lock
#   ./deploy.sh install-risky  # install without requiring swap (OOM-prone)
#   ./deploy.sh all        # swap -> install -> start -> forward

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHROOT_DIR_ON_DEVICE=/data/local/nhsystem/kalifs
BUSYBOX_ON_DEVICE=/data/data/com.offsec.nethunter/scripts/bin/busybox_nh
SWAPFILE_ON_DEVICE=/data/local/openvas-swap
SWAP_SIZE_MB=4096
CHROOT_STAGE=/sdcard/openvas-stage
CHROOT_SCRIPT_DIR_IN_CHROOT=/root/openvas

# ---------- helpers ----------

require_adb() {
  command -v adb >/dev/null || { echo "adb not found on PATH" >&2; exit 1; }
  local count
  count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  [ "$count" -ge 1 ] || { echo "no adb device in 'device' state" >&2; adb devices; exit 1; }
  [ "$count" -eq 1 ] || echo "warning: $count devices attached, using default (use ANDROID_SERIAL to pick)"
}

# Run a command as root on the Android host (outside the chroot).
asu() {
  adb shell "su -c $(printf '%q' "$*")"
}

# Run a command inside the Kali chroot with a clean env and proper PATH.
# Args: any number of shell tokens (will be joined).
inchroot() {
  local cmd="$*"
  # env -i keeps the chroot from inheriting Android's /system/bin pollution.
  asu "env -i HOME=/root TERM=xterm LANG=C.UTF-8 DEBIAN_FRONTEND=noninteractive \
       PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
       $BUSYBOX_ON_DEVICE chroot $CHROOT_DIR_ON_DEVICE /bin/bash -lc $(printf '%q' "$cmd")"
}

# Copy a local file into the chroot at a target path.
push_to_chroot() {
  local src="$1" dst="$2"
  adb push "$src" "$CHROOT_STAGE" >/dev/null
  local base; base=$(basename "$src")
  asu "mkdir -p $CHROOT_DIR_ON_DEVICE$(dirname "$dst") && cp $CHROOT_STAGE/$base $CHROOT_DIR_ON_DEVICE$dst && chmod +x $CHROOT_DIR_ON_DEVICE$dst"
}

stage_chroot_scripts() {
  adb shell "mkdir -p $CHROOT_STAGE"
  for f in install-gvm.sh start-gvm.sh stop-gvm.sh feed-sync.sh; do
    push_to_chroot "$SCRIPT_DIR/chroot/$f" "$CHROOT_SCRIPT_DIR_IN_CHROOT/$f"
  done
  echo "[+] Staged chroot scripts into $CHROOT_SCRIPT_DIR_IN_CHROOT"
}

# ---------- subcommands ----------

cmd_swap() {
  require_adb
  echo "[*] Probing kernel swap support..."
  local kernel_has_swap
  kernel_has_swap=$(asu "[ -e /proc/swaps ] && echo yes || echo no" | tr -d '\r\n')
  if [ "$kernel_has_swap" != "yes" ]; then
    echo "[!] /proc/swaps is missing — this kernel was built with CONFIG_SWAP=n."
    echo "    swapon will return ENOSYS. No file-backed or zram swap is possible."
    echo "    Known affected devices: OnePlus One (bacon) + LOS 18.1 stock kernel."
    echo "    Options:"
    echo "      1) Flash a custom kernel with CONFIG_SWAP=y (hard)."
    echo "      2) Proceed without swap — GVM install is very likely to OOM. (see './deploy.sh install-risky')"
    echo "      3) Run GVM on the Mac/server and drive it from the phone with gvm-tools (recommended)."
    return 1
  fi
  echo "[*] Checking current swap on device..."
  asu "free -h | awk '/^Swap/'"
  local have
  have=$(asu "[ -f $SWAPFILE_ON_DEVICE ] && echo yes || echo no" | tr -d '\r\n')
  if [ "$have" = "yes" ]; then
    echo "[*] Existing swapfile at $SWAPFILE_ON_DEVICE"
  else
    echo "[*] Creating ${SWAP_SIZE_MB} MB swapfile at $SWAPFILE_ON_DEVICE (this takes a minute)..."
    asu "dd if=/dev/zero of=$SWAPFILE_ON_DEVICE bs=1M count=$SWAP_SIZE_MB status=none && chmod 600 $SWAPFILE_ON_DEVICE && mkswap $SWAPFILE_ON_DEVICE"
  fi
  echo "[*] Enabling swap..."
  if ! asu "swapon $SWAPFILE_ON_DEVICE"; then
    echo "[!] swapon failed. Check 'dmesg | tail' on the device."
    return 1
  fi
  asu "free -h"
  echo "[*] Lowering swappiness so Android UI stays responsive..."
  asu "sysctl -w vm.swappiness=60 >/dev/null || true"
  echo "[+] Swap enabled. (Note: not persistent across reboots; re-run './deploy.sh swap'.)"
}

cmd_install() {
  require_adb
  # Refuse to start if any apt/dpkg process is running in the chroot.
  # (Can't rely on lockfile existence because gvm-setup removes them between runs.)
  # pgrep -x matches exact process name (comm field), avoiding self-match from our own cmdline.
  # Trailing 'echo END:N' lets us pick the real count out of noisy shell chatter.
  local raw
  raw=$(inchroot "N=\$(pgrep -x 'apt|apt-get|dpkg|aptitude' 2>/dev/null | wc -l); echo END:\$N")
  local apt_procs
  apt_procs=$(echo "$raw" | awk -F: '/^END:/ {print $2+0; exit}')
  apt_procs=${apt_procs:-0}
  if [ "$apt_procs" -gt 0 ]; then
    echo "[!] $apt_procs apt/dpkg process(es) running in the chroot."
    echo "    This is typically NetHunter's first-boot auto-install of kali-linux-large."
    echo "    To kill and reclaim the lock: ./deploy.sh kill-apt"
    inchroot "pgrep -lax 'apt|apt-get|dpkg|aptitude'"
    return 1
  fi
  stage_chroot_scripts
  echo "[*] Running installer inside chroot. This will take 1+ hour on this device."
  echo "    Logs will stream; you can Ctrl-C and resume by re-running 'install'."
  inchroot "bash $CHROOT_SCRIPT_DIR_IN_CHROOT/install-gvm.sh"
}

cmd_install_risky() {
  echo "[!] install-risky: proceeds without swap. OOM is likely during gvm-setup/feed load."
  echo "    Press Ctrl-C now to abort, or wait 5 seconds..."
  sleep 5
  cmd_install "$@"
}

cmd_kill_apt() {
  require_adb
  echo "[*] Looking for apt/dpkg processes in chroot..."
  inchroot "
    pids=\$(pgrep -f 'apt(-get)? |dpkg ' || true)
    if [ -z \"\$pids\" ]; then echo '  none found'; exit 0; fi
    echo \"  killing: \$pids\"
    kill \$pids 2>/dev/null; sleep 2
    kill -9 \$pids 2>/dev/null || true
    echo '[*] Removing stale lock files and running dpkg --configure -a...'
    rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock /var/lib/apt/lists/lock
    dpkg --configure -a || true
    echo '[+] dpkg is clean. You can now run: ./deploy.sh install'
  "
}

cmd_start() {
  require_adb
  stage_chroot_scripts
  inchroot "bash $CHROOT_SCRIPT_DIR_IN_CHROOT/start-gvm.sh"
}

cmd_stop() {
  require_adb
  stage_chroot_scripts
  inchroot "bash $CHROOT_SCRIPT_DIR_IN_CHROOT/stop-gvm.sh"
}

cmd_forward() {
  require_adb
  adb forward --remove tcp:9392 2>/dev/null || true
  adb forward tcp:9392 tcp:9392
  echo "[+] Port forward active."
  echo "    Open on your Mac: https://localhost:9392"
  echo "    (self-signed cert: accept the browser warning)"
}

cmd_status() {
  require_adb
  echo "=== Android host memory/swap ==="
  asu "free -h; echo; ls -lh $SWAPFILE_ON_DEVICE 2>/dev/null || echo '(no swapfile)'"
  echo
  echo "=== Chroot services ==="
  inchroot "
    for svc in postgresql redis-server gvmd ospd-openvas gsad notus-scanner; do
      pid=\$(pgrep -f \"\$svc\" | head -1)
      if [ -n \"\$pid\" ]; then printf '  %-18s up (pid %s)\n' \"\$svc\" \"\$pid\"
      else printf '  %-18s DOWN\n' \"\$svc\"; fi
    done
    echo
    echo '--- listening ports ---'
    ss -ltnp 2>/dev/null | grep -E '9390|9391|9392|5432|6379' || echo '(none)'
  "
}

cmd_logs() {
  require_adb
  local svc="${1:-gvmd}"
  case "$svc" in
    gvmd)   inchroot "tail -n 120 -F /var/log/gvm/gvmd.log" ;;
    ospd)   inchroot "tail -n 120 -F /var/log/gvm/ospd-openvas.log" ;;
    gsad)   inchroot "tail -n 120 -F /var/log/gvm/gsad.log" ;;
    feed)   inchroot "tail -n 120 -F /var/log/gvm/greenbone-feed-sync.log 2>/dev/null || journalctl -u greenbone-feed-sync --no-pager -n 120" ;;
    *) echo "unknown svc '$svc'; choose: gvmd|ospd|gsad|feed"; exit 2 ;;
  esac
}

cmd_shell() {
  require_adb
  echo "[*] Entering chroot. Type 'exit' to return."
  adb shell "su -c '/data/data/com.offsec.nethunter/scripts/bootkali_bash'"
}

cmd_feed_sync() {
  require_adb
  stage_chroot_scripts
  inchroot "bash $CHROOT_SCRIPT_DIR_IN_CHROOT/feed-sync.sh"
}

cmd_uninstall() {
  require_adb
  echo "[!] This will remove GVM and its data. Feed data under /var/lib/gvm and /var/lib/openvas will be deleted."
  read -r -p "Type 'yes' to continue: " ans
  [ "$ans" = "yes" ] || { echo "aborted"; exit 1; }
  inchroot "apt-get -y purge 'gvm*' 'gsad*' 'ospd-openvas*' 'openvas-scanner*' 'notus-scanner*' 'greenbone-feed-sync*' 'python3-gvm*' 'gvm-tools*' || true
            apt-get -y autoremove --purge || true
            rm -rf /var/lib/gvm /var/lib/openvas /var/log/gvm /etc/gvm"
  echo "[+] Uninstalled."
}

cmd_all() {
  cmd_swap
  cmd_install
  cmd_start
  cmd_forward
  echo
  echo "========================================================================"
  echo " Done. GVM should be reachable at https://localhost:9392 on your Mac."
  echo " Admin credentials are printed at the end of the install output above."
  echo "========================================================================"
}

usage() {
  sed -n '3,20p' "$0"
}

main() {
  local sub="${1:-}"
  shift || true
  case "$sub" in
    swap)           cmd_swap "$@" ;;
    install)        cmd_install "$@" ;;
    install-risky)  cmd_install_risky "$@" ;;
    kill-apt)       cmd_kill_apt "$@" ;;
    start)          cmd_start "$@" ;;
    stop)           cmd_stop "$@" ;;
    forward)        cmd_forward "$@" ;;
    status)         cmd_status "$@" ;;
    logs)           cmd_logs "$@" ;;
    shell)          cmd_shell "$@" ;;
    feed-sync)      cmd_feed_sync "$@" ;;
    uninstall)      cmd_uninstall "$@" ;;
    all)            cmd_all "$@" ;;
    ""|-h|--help|help) usage ;;
    *) echo "unknown subcommand: $sub" >&2; usage; exit 2 ;;
  esac
}

main "$@"
