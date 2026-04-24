#!/usr/bin/env bash
# Resync the Greenbone Community feed (NVT + SCAP + CERT + GVMD data).
# Slow on low-end devices; feed is ~2 GB total.

set -u

log() { printf '\033[1;36m[feed]\033[0m %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "run as root" >&2; exit 1; }

mkdir -p /var/log/gvm
chown -R _gvm:_gvm /var/lib/gvm /var/lib/openvas /var/lib/notus 2>/dev/null || true

if command -v greenbone-feed-sync >/dev/null; then
  log "running greenbone-feed-sync (all feeds)..."
  sudo -u _gvm greenbone-feed-sync 2>&1 | tee -a /var/log/gvm/greenbone-feed-sync.log
else
  # Fallback to the older per-feed tools if they exist.
  log "greenbone-feed-sync not found; trying legacy scripts"
  for tool in greenbone-nvt-sync greenbone-scapdata-sync greenbone-certdata-sync greenbone-feed-sync; do
    if command -v "$tool" >/dev/null; then
      log "running $tool"
      sudo -u _gvm "$tool" 2>&1 | tee -a /var/log/gvm/$tool.log || true
    fi
  done
fi

log "feed sync complete. Restart gvmd for new VTs to register:"
log "  bash /root/openvas/stop-gvm.sh && bash /root/openvas/start-gvm.sh"
