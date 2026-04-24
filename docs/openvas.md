---
layout: default
title: OpenVAS / GVM on NetHunter
---

# OpenVAS / GVM implementation

[Greenbone Vulnerability Manager](https://www.greenbone.net/) (GVM) is the maintained stack behind what people still call **OpenVAS**: network and host scanning with a large NVT feed, **GSA** (web UI on port **9392**), and **GMP** (API on **9390**) for automation.

In this project, GVM is **not** shipped as an Android app. It is a **multi-daemon Linux service** we install with `apt` **inside the Kali NetHunter chroot** on a rooted device, with a **Mac-side orchestrator** that talks over `adb` so you can script install, start/stop, and port forwards from your laptop.

**Source in the repo:** [`nethunter-prep/openvas/`](https://github.com/CastaliaInstitute/anubis/tree/main/nethunter-prep/openvas) (see the README there for the full command reference and file layout).

---

## Two deployment paths

### Path 1 — GVM in Docker on the Mac (works on the OnePlus One + LOS 18.1)

The stock LineageOS 18.1 `bacon` kernel is built **without** `CONFIG_SYSVIPC` and **without** `CONFIG_SWAP`. PostgreSQL’s `initdb` path uses System V shared memory; without it, the database cannot bootstrap, and **GVM cannot run** on the phone. There is also no swapon / zram for a 3 GB device, so a full on-device GVM would OOM during feed load anyway.

**The supported lab setup:** run the **Greenbone community containers** on the Mac under [`nethunter-prep/openvas/docker-mac/`](https://github.com/CastaliaInstitute/anubis/tree/main/nethunter-prep/openvas/docker-mac) (`up.sh`, `down.sh`, `docker-compose.yml`), use GSA at `https://localhost:9392`, then use **`install-client.sh`** to put **`gvm-tools`** in the NetHunter chroot and wire **`adb reverse`** so the phone can call **`gvm-cli` / `gvm-script`** against the Mac over TLS (through a small **gmp-bridge** sidecar). The phone becomes a **field console**; the engine runs where the kernel and RAM allow.

Admin passwords and similar files live under **`.secrets/`** on the Mac — that directory is **gitignored**; never commit credentials.

### Path 2 — On-device GVM in the chroot (other hardware)

`deploy.sh` and the scripts under `chroot/` implement a full **on-device** install: optional **4 GB swapfile** on `/data`, staged install and `gvm-setup`, manual service order (no `systemd` in the chroot), `adb forward` to reach GSA from the host browser.

This path is **only realistic** on devices whose kernels expose **SysV IPC** and **swap** (or you accept the documented failure modes). We document **kernel triage** (`/proc/config.gz`, `swapon`, `shmget`) in the tree README. On `bacon` with the stock LOS 18.1 kernel, use Path 1.

---

## What the automation does

| Piece | Role |
|-------|------|
| `deploy.sh` | Mac entry point: `swap`, `install`, `start`, `stop`, `forward`, `status`, `logs`, `feed-sync`, `shell`, `uninstall`, `all`. Pushes helper scripts into the chroot and runs them via `busybox chroot`. |
| `chroot/install-gvm.sh` | `apt` install, low-RAM **PostgreSQL** and **Redis** tuning, `gvm` meta-package, `gvm-setup`, credential capture to a root-only file in the chroot. |
| `chroot/start-gvm.sh` / `stop-gvm.sh` | Ordered bring-up/tear-down: postgres → redis-openvas → ospd → notus → gvmd → gsad. |
| `chroot/feed-sync.sh` | Re-runs `greenbone-feed-sync` (slow; resumable). |
| `docker-mac/` | Production path for this phone: Compose stack, TLS bridge for GMP, client install on device. |

---

## Relation to Kali AI / Anubis

- **Anubis**’s tool catalog already lists OpenVAS-related CLI entry points (`openvas`, `gvm-cli`, etc.) in [`kali_nethunter_tool_catalog.txt`](https://github.com/CastaliaInstitute/anubis/blob/main/nethunter-gemini-mcp/app/src/main/assets/kali_nethunter_tool_catalog.txt) so the model can plan scans when those binaries exist in the chroot.
- GVM in this repo is about **operational lab deployment** — how you get a real scanner and UI, not a separate “AI product.”
- GVM **admin credentials** and **GMP passwords** are host/chroot only; they do not appear on this site or in the Android app by default.

[← Back to Kali AI / Anubis](index.html)
