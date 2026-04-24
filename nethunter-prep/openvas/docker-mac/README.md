# GVM (OpenVAS) on the Mac + NetHunter phone as operator console

This is the deployment path that actually works on the OnePlus One + LOS 18.1 rig. GVM runs on your Mac in Docker; the NetHunter chroot only gets `gvm-tools` installed so you can drive scans from the phone over `gvm-cli`.

## Why this path

The LOS 18.1 `bacon` kernel has `CONFIG_SYSVIPC=n` and `CONFIG_SWAP=n`. PostgreSQL can't `initdb` without SysV shared memory, and GVM can't run without PostgreSQL. See the parent `../README.md` for the full forensic. This Docker path sidesteps both kernel limitations.

## Requirements

- Docker Desktop for Mac, running.
- `adb`, with the device in `device` state (same as the on-device path).
- ~5 GB free disk on the Mac for Greenbone images and feed data.
- First-run time: 20–40 min (image pulls + in-container feed sync). Subsequent starts are seconds.

## Quick start

```bash
cd nethunter-prep/openvas/docker-mac

./up.sh                      # pull + start stack (first run: 15-30 min for pulls + feed unpack)
./admin-password.sh          # sets admin password; saves to .secrets/admin.txt
open http://localhost:9392   # login with admin / <password from above>

./install-client.sh          # installs gvm-tools in NetHunter chroot + adb reverse
```

## Using it from the phone (verified working)

The phone reaches GVM over `adb reverse tcp:9390 → Mac:9390 → gmp-bridge (socat+TLS) → gvmd.sock`. Run these inside the NetHunter chroot (e.g. from NetHunter Terminal on the phone, or via `su -c` from your Mac):

```bash
# Must run as _gvm user (gvm-tools refuses to run as root, and _gvm has the
# Android `inet`/`sockets` GIDs that install-client.sh already added).
PW="<paste-from-Mac:.secrets/admin.txt>"

runuser -u _gvm -- gvm-cli --gmp-username admin --gmp-password "$PW" \
  tls --hostname 127.0.0.1 --port 9390 --no-credentials \
  --xml '<get_version/>'

runuser -u _gvm -- gvm-cli --gmp-username admin --gmp-password "$PW" \
  tls --hostname 127.0.0.1 --port 9390 --no-credentials \
  --xml '<get_tasks filter="rows=3"/>'

# Scripted scan with gvm-script (uses gvm-tools' scripts dir)
runuser -u _gvm -- gvm-script --gmp-username admin --gmp-password "$PW" \
  tls --hostname 127.0.0.1 --port 9390 --no-credentials \
  /usr/share/doc/python3-gvm/examples/gmp/start-scan.py 192.168.1.0/24
```

A successful `<get_version/>` call returns `<get_version_response status="200">...<version>22.7</version>` ; a successful `<authenticate/>` returns `<role>Admin</role>`.

The `adb reverse tcp:9390` tunnel persists until the USB cable is disconnected, `adb` is restarted, or the phone reboots. Re-run `./install-client.sh` to re-establish.

## Files

```
docker-mac/
├── docker-compose.yml     Greenbone Community Containers (full stack)
├── up.sh                  docker compose pull + up -d + readiness wait
├── down.sh                docker compose down  (add --wipe to delete volumes)
├── admin-password.sh      set/rotate admin password, saves to .secrets/admin.txt
├── install-client.sh      installs gvm-tools in NetHunter chroot + adb reverse
├── gmp-bridge/            socat+TLS sidecar (unix sock -> localhost:9390 TLS)
│   ├── Dockerfile
│   └── entrypoint.sh
└── README.md              this file
```

## Ports summary

| Port | Transport | Purpose | Exposed on |
| --- | --- | --- | --- |
| `9392` | HTTP | GSA web UI | `127.0.0.1` only |
| `9390` | TLS (self-signed) | GMP (for gvm-tools / adb reverse) | `127.0.0.1` only |
| `5432` | — | PostgreSQL (internal) | not exposed |
| `6379` | — | Redis (internal) | not exposed |

## Operational notes

- **Feed freshness**: Greenbone's data-only images (`vulnerability-tests`, `scap-data`, `cert-bund-data`, `dfn-cert-data`, `notus-data`, `data-objects`, `report-formats`) each ship a snapshot of their feed. `docker compose pull` refreshes them; after a pull, `docker compose up -d` will re-run the one-shot data containers and gvmd will pick up the new feed on next scan.
- **Resource usage on Mac**: ~2–3 GB RAM while idle, ~4–6 GB peaking during scans with a loaded NVT DB.
- **Persistence**: volumes `gvmd_data_vol`, `psql_data_vol`, etc. keep your targets/tasks/reports across restarts. Use `./down.sh --wipe` to reset everything.
- **Reboots**: `docker compose up -d` includes `restart: on-failure`, but to survive Mac restarts enable "Start Docker Desktop when you log in" in Docker Desktop settings.
