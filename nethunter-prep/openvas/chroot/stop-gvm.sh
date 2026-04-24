#!/usr/bin/env bash
# Stop the GVM stack inside the NetHunter chroot. Reverse order of start.

set -u

log() { printf '\033[1;36m[stop]\033[0m %s\n' "$*"; }

stop_by_pat() {
  local pat="$1"
  local pids
  pids=$(pgrep -f "$pat" || true)
  if [ -n "$pids" ]; then
    log "stopping $pat (pids: $pids)"
    kill $pids 2>/dev/null || true
    sleep 1
    pids=$(pgrep -f "$pat" || true)
    [ -n "$pids" ] && { log "force-killing $pat"; kill -9 $pids 2>/dev/null || true; }
  else
    log "$pat not running"
  fi
}

stop_by_pat gsad
stop_by_pat gvmd
stop_by_pat notus-scanner
stop_by_pat ospd-openvas
stop_by_pat redis-openvas.conf

pg_cluster=$(ls /etc/postgresql/ 2>/dev/null | head -n1 || true)
if [ -n "$pg_cluster" ] && pgrep -x postgres >/dev/null; then
  log "stopping postgres cluster $pg_cluster"
  su - postgres -c "pg_ctlcluster $pg_cluster main stop -m fast" 2>/dev/null || true
fi

log "done."
