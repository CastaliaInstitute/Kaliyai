#!/usr/bin/env bash
# Runs INSIDE the Kali chroot on the NetHunter device.
# Installs GVM (OpenVAS) from kali-rolling and runs gvm-setup with low-RAM tuning.
#
# Safe to re-run: apt-get install is idempotent; gvm-setup is skipped if already set up
# unless FORCE_SETUP=1 is exported.

set -euo pipefail

log() { printf '\033[1;36m[install]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "must run as root inside the chroot"
[ -f /etc/os-release ] && . /etc/os-release
[ "${ID:-}" = "kali" ] || warn "not a Kali chroot? (ID=${ID:-unknown})"

export DEBIAN_FRONTEND=noninteractive
export APT_LISTCHANGES_FRONTEND=none

# --- sanity: memory + swap ---
log "memory snapshot:"
free -h || true
total_swap_kb=$(awk '/^SwapTotal:/ {print $2}' /proc/meminfo)
if [ "${total_swap_kb:-0}" -lt 2000000 ]; then
  warn "swap is <2 GB inside the namespace. If install OOMs, run './deploy.sh swap' on the Mac first."
fi

# --- apt update ---
log "apt-get update..."
apt-get update

# --- core prerequisites (small, cache-friendly) ---
log "installing prerequisites..."
apt-get -y install --no-install-recommends \
  ca-certificates curl gnupg lsb-release \
  postgresql postgresql-contrib \
  redis-server \
  xsltproc rsync

# --- tune postgres BEFORE it spins up large shared_buffers on a 3 GB device ---
PG_CONF_DIR=$(ls -d /etc/postgresql/*/main 2>/dev/null | head -n1 || true)
if [ -n "${PG_CONF_DIR}" ] && [ -f "$PG_CONF_DIR/postgresql.conf" ]; then
  log "tuning postgres in $PG_CONF_DIR for low memory"
  sed -i \
    -e "s/^#*shared_buffers.*/shared_buffers = 128MB/" \
    -e "s/^#*work_mem.*/work_mem = 8MB/" \
    -e "s/^#*maintenance_work_mem.*/maintenance_work_mem = 64MB/" \
    -e "s/^#*effective_cache_size.*/effective_cache_size = 512MB/" \
    -e "s/^#*max_connections.*/max_connections = 40/" \
    "$PG_CONF_DIR/postgresql.conf"
fi

# --- tune redis for low memory: cap, disable persistence (feed is re-downloadable) ---
if [ -f /etc/redis/redis.conf ]; then
  log "tuning redis for low memory"
  sed -i \
    -e "s/^#* *maxmemory .*/maxmemory 512mb/" \
    -e "s/^#* *maxmemory-policy .*/maxmemory-policy allkeys-lru/" \
    -e "s/^save .*/# save disabled for OpenVAS feed cache/" \
    -e "s/^appendonly .*/appendonly no/" \
    /etc/redis/redis.conf
fi

# --- the main GVM install ---
log "installing gvm meta-package (this is the big download)..."
apt-get -y install gvm

# --- run gvm-setup (first time only) ---
SETUP_STAMP=/var/lib/gvm/.nh-gvm-setup.done
if [ -f "$SETUP_STAMP" ] && [ "${FORCE_SETUP:-0}" != "1" ]; then
  log "gvm-setup already ran previously (stamp: $SETUP_STAMP). Skipping."
  log "To re-run: FORCE_SETUP=1 bash $0"
else
  log "running gvm-setup — this downloads ~2 GB of NVT/SCAP/CERT feed data and can take HOURS on this device."
  log "It's safe to interrupt and re-run; feed-sync is resumable."

  # gvm-setup starts postgres and redis-openvas systemd units. In a chroot without
  # systemd we need to run them directly first.
  log "bringing up postgres manually (no systemd in chroot)..."
  mkdir -p /var/run/postgresql
  chown postgres:postgres /var/run/postgresql || true
  if ! pgrep -x postgres >/dev/null; then
    su - postgres -c "pg_ctlcluster $(ls /etc/postgresql/ | head -n1) main start" || true
    sleep 3
  fi

  log "bringing up redis-openvas manually..."
  if [ -f /etc/redis/redis-openvas.conf ]; then
    mkdir -p /var/run/redis
    chown redis:redis /var/run/redis || true
    if ! pgrep -f redis-openvas.conf >/dev/null; then
      redis-server /etc/redis/redis-openvas.conf --daemonize yes || true
      sleep 2
    fi
  fi

  # Capture admin password to a known location regardless of gvm-setup's verbosity.
  log "invoking gvm-setup..."
  if gvm-setup 2>&1 | tee /var/log/gvm-setup.log; then
    log "gvm-setup finished."
  else
    warn "gvm-setup returned non-zero; review /var/log/gvm-setup.log. Continuing."
  fi

  # Extract admin password from the setup log if present.
  admin_line=$(grep -E "password for 'admin'|admin password|admin user created" /var/log/gvm-setup.log | tail -n1 || true)
  if [ -n "$admin_line" ]; then
    echo "$admin_line" > /root/gvm-admin-credentials.txt
    log "admin credentials saved to /root/gvm-admin-credentials.txt (inside chroot)"
  fi

  mkdir -p "$(dirname "$SETUP_STAMP")"
  date -u +%FT%TZ > "$SETUP_STAMP"
fi

# --- verify ---
log "verifying install..."
gvm-check-setup 2>&1 | tail -n 40 || warn "gvm-check-setup reported issues (often expected on first run until feeds finish)"

cat <<EOF

========================================================================
GVM installed.

Next steps (run from the Mac):
  ./deploy.sh start          # bring up services
  ./deploy.sh forward        # expose :9392 on localhost
  open https://localhost:9392

Admin credentials: see /root/gvm-admin-credentials.txt inside the chroot,
or: ./deploy.sh shell  then  cat /root/gvm-admin-credentials.txt

If the feed isn't ready yet, scans will be limited. Run:
  ./deploy.sh feed-sync
========================================================================
EOF
