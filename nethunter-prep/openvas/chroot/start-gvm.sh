#!/usr/bin/env bash
# Start the GVM stack inside the NetHunter chroot WITHOUT systemd.
# Order matters: postgres -> redis-openvas -> ospd-openvas -> gvmd -> notus-scanner -> gsad

set -euo pipefail

log()  { printf '\033[1;36m[start]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "must run as root inside the chroot" >&2; exit 1; }

ensure_dir() { mkdir -p "$1"; chown -R "$2" "$1" 2>/dev/null || true; }

# ---- postgres ----
if pgrep -x postgres >/dev/null; then
  log "postgres already running"
else
  log "starting postgres..."
  ensure_dir /var/run/postgresql postgres:postgres
  pg_cluster=$(ls /etc/postgresql/ 2>/dev/null | head -n1 || true)
  if [ -n "$pg_cluster" ]; then
    su - postgres -c "pg_ctlcluster $pg_cluster main start" || warn "pg_ctlcluster failed; trying pg_ctl"
  fi
  sleep 2
  pgrep -x postgres >/dev/null || warn "postgres did not start"
fi

# ---- redis for openvas ----
if pgrep -f redis-openvas.conf >/dev/null; then
  log "redis-openvas already running"
else
  log "starting redis-openvas..."
  ensure_dir /var/run/redis redis:redis
  if [ -f /etc/redis/redis-openvas.conf ]; then
    redis-server /etc/redis/redis-openvas.conf --daemonize yes || warn "redis-openvas failed to start"
  else
    warn "/etc/redis/redis-openvas.conf not found"
  fi
  sleep 1
fi

# ---- ospd-openvas ----
if pgrep -f ospd-openvas >/dev/null; then
  log "ospd-openvas already running"
else
  log "starting ospd-openvas..."
  ensure_dir /var/run/ospd _gvm:_gvm
  ensure_dir /var/log/gvm _gvm:_gvm
  sudo -u _gvm -g _gvm ospd-openvas \
    --unix-socket /var/run/ospd/ospd-openvas.sock \
    --pid-file /var/run/ospd/ospd-openvas.pid \
    --log-file /var/log/gvm/ospd-openvas.log \
    --lock-file-dir /var/run/ospd \
    --socket-mode 0o770 \
    -f || warn "ospd-openvas failed to start"
  sleep 2
fi

# ---- notus-scanner (uses mqtt via mosquitto when available) ----
if command -v notus-scanner >/dev/null; then
  if pgrep -f notus-scanner >/dev/null; then
    log "notus-scanner already running"
  else
    log "starting notus-scanner..."
    sudo -u _gvm -g _gvm notus-scanner \
      --products-directory /var/lib/notus/products \
      --log-file /var/log/gvm/notus-scanner.log \
      -f >/dev/null 2>&1 &
    sleep 1
  fi
fi

# ---- gvmd ----
if pgrep -x gvmd >/dev/null; then
  log "gvmd already running"
else
  log "starting gvmd..."
  ensure_dir /var/run/gvmd _gvm:_gvm
  sudo -u _gvm -g _gvm gvmd \
    --osp-vt-update=/var/run/ospd/ospd-openvas.sock \
    --listen-group=_gvm \
    --unix-socket=/var/run/gvmd/gvmd.sock || warn "gvmd failed to start"
  # gvmd daemonizes itself; give it a moment.
  sleep 3
fi

# ---- gsad (web UI) ----
if pgrep -x gsad >/dev/null; then
  log "gsad already running"
else
  log "starting gsad on :9392..."
  gsad \
    --listen=0.0.0.0 \
    --port=9392 \
    --mlisten=127.0.0.1 \
    --mport=9390 \
    --drop-privileges=_gvm \
    --gnutls-priorities=SECURE128 || warn "gsad failed to start"
  sleep 2
fi

log "done. quick check:"
ss -ltn 2>/dev/null | grep -E '9392|9390' || warn "no listener on 9390/9392 yet; see /var/log/gvm/*.log"
