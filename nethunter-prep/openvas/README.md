# OpenVAS / GVM on Kali NetHunter (OnePlus One)

> **Important outcome of our first deploy on OnePlus One + LOS 18.1 (kernel 3.4.113):**
>
> **On-device GVM is not viable on this kernel.** The stock LineageOS 18.1 `bacon` kernel was built without `CONFIG_SYSVIPC` (no SysV shared memory / semaphores). PostgreSQL's `initdb` bootstrap path calls `shmget()` unconditionally for a small anchor segment — even with `shared_memory_type = mmap` set — so the database can't be initialized. Since `gvmd` requires PostgreSQL (no SQLite/MySQL fallback), GVM cannot start.
>
> Additionally this kernel has `CONFIG_SWAP=n` (no swapon) and no zram, so even if we patched postgres, `gvmd` + feed-loaded Redis on 3 GB RAM would OOM.
>
> **Two realistic deployments are therefore documented here:**
>
> 1. **`docker-mac/`** — run GVM in Docker Desktop on the Mac, use the phone (NetHunter Terminal) as an operator console over `gvm-cli`. This is the path that actually works today. *(Recommended.)*
> 2. **`chroot/`** — original on-device install scripts. Kept for devices with kernels that have `CONFIG_SYSVIPC=y` + `CONFIG_SWAP=y` (most arm64 OnePlus 7/8/9, most Pixels running AOSP/GrapheneOS, or a custom `bacon` kernel).
>
> See **Kernel requirements** below to check before trying path 2 on another device.

Deploy the Greenbone Vulnerability Manager (OpenVAS) stack to **a Kali NetHunter Android device connected over adb**, driven from your Mac.

## What this gives you

- GVM Community Edition running on the phone's Kali chroot.
- Web UI (GSA) reachable from the Mac at `https://localhost:9392` via `adb forward`.
- Re-runnable subcommands for swap, install, start/stop, feed sync, logs, status, uninstall.

## Why not "an APK"?

OpenVAS isn't an Android app. It's a multi-daemon server stack (`gvmd`, `ospd-openvas`, `gsad`, `redis`, `postgresql`, `notus-scanner`). NetHunter's APK ships a Kali chroot, and we install GVM *inside* that chroot with `apt`. You access the UI through a browser on any device that can reach port 9392 on the phone — typically your Mac, via `adb forward`.

## Target hardware reality check

This project assumes you're deploying to the OnePlus One (`bacon`) rig described in `../nethunter-prep/`:

| Thing | Value |
|---|---|
| Device | OnePlus One A0001, Android 11 / LOS 18.1 |
| Arch | `armeabi-v7a` (armhf, 32-bit) |
| RAM | 3 GB (~140 MB free after Android) |
| Storage | 55 GB, ~46 GB free |
| Chroot | `/data/local/nhsystem/kali-armhf`, Kali 2026.1 rolling |

**Important constraints:**
- With only 3 GB RAM and no swap, GVM will OOM during the NVT feed load. A **4 GB swapfile is mandatory** — `./deploy.sh swap` handles it.
- Install + first feed sync typically takes **over an hour** on this hardware.
- Scan throughput will be modest. This is fine for a lab, not for scanning a /16.
- Greenbone officially supports amd64/arm64; armhf is community packaging in Kali. If something is broken, it's usually `notus-scanner` or `ospd-openvas` — check `./deploy.sh logs ospd`.

## One-time prerequisites

On your Mac:
- Android Platform Tools (`adb`) — `brew install android-platform-tools`
- A USB cable, device unlocked and in `adb devices` as `device`.

On the device:
- NetHunter installed with Kali chroot provisioned (your `nethunter-prep` already did this).
- Rooted (Magisk); `su` works from `adb shell`.

## Quick start

```bash
cd nethunter-prep/openvas
chmod +x deploy.sh chroot/*.sh

./deploy.sh swap        # create/enable 4 GB swapfile on /data
./deploy.sh install     # apt install gvm + gvm-setup (long-running)
./deploy.sh start       # start all daemons (no systemd in chroot)
./deploy.sh forward     # adb forward tcp:9392 -> device

# open https://localhost:9392 in your Mac browser
# accept the self-signed cert
# login with the admin/password printed at end of install
```

Or in one shot:

```bash
./deploy.sh all
```

## Commands

| Command | What it does |
|---|---|
| `./deploy.sh swap` | Create/enable 4 GB swap at `/data/local/openvas-swap`. Not persistent across reboots — rerun after reboot. |
| `./deploy.sh install` | `apt install gvm` + `gvm-setup` inside the chroot. Idempotent. Set `FORCE_SETUP=1` to re-run setup. |
| `./deploy.sh start` | Bring up postgres → redis-openvas → ospd-openvas → notus-scanner → gvmd → gsad. |
| `./deploy.sh stop` | Reverse order shutdown. |
| `./deploy.sh forward` | `adb forward tcp:9392 tcp:9392`. |
| `./deploy.sh status` | Host memory/swap + chroot service check + listening ports. |
| `./deploy.sh logs [gvmd\|ospd\|gsad\|feed]` | Tail the chosen log; defaults to `gvmd`. |
| `./deploy.sh shell` | Drop into the chroot interactively via `bootkali_bash`. |
| `./deploy.sh feed-sync` | Re-run `greenbone-feed-sync` (NVT + SCAP + CERT). Slow. |
| `./deploy.sh uninstall` | Purge GVM packages + wipe `/var/lib/gvm`, `/var/lib/openvas`. |

## Where stuff lives

On the Mac:

```
nethunter-prep/openvas/
├── deploy.sh                 # run from Mac; orchestrates everything over adb
├── README.md
└── chroot/
    ├── install-gvm.sh        # staged into the chroot, run by deploy.sh install
    ├── start-gvm.sh
    ├── stop-gvm.sh
    └── feed-sync.sh
```

On the device:

```
/data/local/openvas-swap               # 4 GB swapfile (Android host, not chroot)
/sdcard/openvas-stage/                 # temporary staging for script push
/data/local/nhsystem/kali-armhf/
  └── root/openvas/                    # installed helper scripts inside chroot
      ├── install-gvm.sh
      ├── start-gvm.sh
      ├── stop-gvm.sh
      └── feed-sync.sh
/data/local/nhsystem/kali-armhf/
  ├── var/lib/gvm/                     # GVMD state, reports
  ├── var/lib/openvas/                 # scanner data
  ├── var/log/gvm/                     # all GVM daemon logs
  └── root/gvm-admin-credentials.txt   # admin password captured from gvm-setup
```

## Getting the admin password after install

```bash
./deploy.sh shell
# inside chroot:
cat /root/gvm-admin-credentials.txt
# or rotate it:
gvmd --user=admin --new-password='my-new-password'
```

## After a reboot

Swapfile + chroot mounts do not survive a reboot. Do:

```bash
# 1. launch NetHunter app on the device so it re-mounts the chroot
# 2. from the Mac:
./deploy.sh swap
./deploy.sh start
./deploy.sh forward
```

## Troubleshooting

- **Install killed with `Killed` or `SIGKILL` mid-install** → you skipped `./deploy.sh swap`. Run it and re-run `install`.
- **`gsad` not listening on 9392** → check `./deploy.sh logs gsad`. Often a cert issue; regenerate with `gvm-manage-certs -af`.
- **Scans run but find nothing** → NVT feed didn't finish syncing. Run `./deploy.sh feed-sync` and restart (`stop` then `start`).
- **`ospd-openvas` crashes on startup** → commonly a redis-openvas socket path mismatch. Check `/etc/openvas/openvas.conf` `db_address` points to `/var/run/redis-openvas/redis.sock` and that file exists.
- **Browser says connection refused on `localhost:9392`** → `adb forward` was not re-applied after reconnecting USB. Run `./deploy.sh forward`.
- **Everything crawls** → expected; the OnePlus One's SoC is from 2014. Consider running GVM on a Linux box and using the phone only as a thin client.

## Path 1: GVM in Docker on the Mac (works today)

See `docker-mac/README.md`. Short version:

```bash
cd nethunter-prep/openvas/docker-mac
./up.sh                   # brings up Greenbone Community Containers
./admin-password.sh       # print/reset admin password
open https://localhost:9392

# On the NetHunter device:
./install-client.sh       # apt-get install gvm-tools inside the chroot + writes ~/.config/gvm/gvm-tools.conf
```

From then on, on the phone in NetHunter Terminal you can drive scans:

```bash
gvm-cli --gmp-username admin --gmp-password "$PW" socket --xml "<get_tasks/>"
gvm-script --gmp-username admin --gmp-password "$PW" socket scripts/start-scan.gmp.py 192.168.1.0/24
```

## Path 2: On-device install (only for SysV-capable kernels)

See `chroot/` scripts and the `./deploy.sh` orchestrator above. Will fail on any kernel that lacks `CONFIG_SYSVIPC` or `CONFIG_SWAP`.

### Kernel requirements to check before trying Path 2

On the target device:

```bash
adb shell 'su -c "
  echo SysV:  $(ls /proc/sys/kernel/shmmax 2>/dev/null >/dev/null && echo yes || echo NO)
  echo Swap:  $([ -e /proc/swaps ] && echo yes || echo NO)
  echo zram:  $(ls /sys/block/ | grep -q zram && echo yes || echo NO)
  echo PID1:  $(readlink /proc/1/exe 2>/dev/null || echo unknown)
  zcat /proc/config.gz 2>/dev/null | grep -E \"SYSVIPC|CONFIG_SWAP|SHMEM\" || echo \"  (config.gz unreadable)\"
"'
```

You need `SysV: yes` and `Swap: yes`. If both are NO (like the bacon kernel we tested), stop and use Path 1.

### What failed on bacon specifically

- Kernel: `Linux 3.4.113-lineageos-g6fa629709f1 armv7l`
- LOS build: `18.1-20240206-NIGHTLY-bacon`
- `swapon` → `ENOSYS` (CONFIG_SWAP=n)
- `shmget` → `ENOSYS` (CONFIG_SYSVIPC=n) → initdb fails with: `FATAL: could not create shared memory segment: Function not implemented`

The apt install of GVM itself succeeded (424 packages, ~7 GB on disk). Those are dormant but harmless; they'll work if a SysV-capable kernel is later flashed.
