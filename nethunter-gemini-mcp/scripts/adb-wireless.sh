#!/usr/bin/env bash
# Wireless ADB helper (same Wi‑Fi as your computer; USB only for initial tcpip or cable debugging).
set -euo pipefail

usage() {
  sed -n '1,120p' <<'EOF'
Usage:
  ./scripts/adb-wireless.sh help
  ./scripts/adb-wireless.sh devices
  ./scripts/adb-wireless.sh pair <ip:pairing_port>   # Android 11+; you enter the 6-digit code when prompted
  ./scripts/adb-wireless.sh connect <ip:port>        # use the “IP address & port” from Wireless debugging
  ./scripts/adb-wireless.sh tcpip [port]            # USB required; default port 5555, then connect to <phone-lan-ip>:port
  ./scripts/adb-wireless.sh disconnect [ip:port|all]
  ./scripts/adb-wireless.sh install-debug    # assembleDebug + adb install -r (needs one device in adb devices)

Phone (Android 11+), recommended:
  Settings → Developer options → Wireless debugging → ON
  - “Pair device with pairing code” → note Pairing code, IP address, and Port (use with: pair)
  - After paired, “IP address & port” (use with: connect) — may differ from the pairing port

Legacy (any Android, needs USB once):
  1) USB cable, USB debugging ON
  2) Run:  ./scripts/adb-wireless.sh tcpip
  3) Unplug; find phone Wi‑Fi IP (status bar or About phone)
  4) Run:  ./scripts/adb-wireless.sh connect <that-ip>:5555

Troubleshooting:
  - Machine and phone on the same subnet; disable VPN on phone if adb disconnects
  - macOS firewall may block inbound adb; allow adb or temporarily disable for pairing
  - Unplug USB after tcpip so the device listens on Wi‑Fi
EOF
}

cmd="${1:-help}"
case "$cmd" in
  help|-h|--help)
    usage
    ;;
  devices)
    adb devices -l
    ;;
  pair)
    shift
    [[ "${1:-}" =~ : ]] || { echo "usage: $0 pair <ip:pairing_port>"; exit 1; }
    exec adb pair "$@"
    ;;
  connect)
    shift
    [[ -n "${1:-}" ]] || { echo "usage: $0 connect <ip:port>"; exit 1; }
    adb connect "$@"
    adb devices -l
    ;;
  tcpip)
    port="${2:-5555}"
    echo "Enabling TCP/IP on port ${port} (keep USB connected until this succeeds)..."
    adb tcpip "$port"
    echo "Unplug USB. On the phone: Settings → Wi‑Fi → current network → note IPv4 (or Status)."
    echo "Then run: $0 connect <that-ip>:${port}"
    ;;
  disconnect)
    shift
    if [[ $# -eq 0 ]]; then
      adb disconnect
    else
      adb disconnect "$@"
    fi
    adb devices -l
    ;;
  install-debug)
    root="$(cd "$(dirname "$0")/.." && pwd)"
    if [[ "$(adb get-state 2>/dev/null || echo "")" != "device" ]]; then
      echo "No device in adb (wireless or USB). Connect first, e.g.:"
      echo "  $0 connect <ip:port>"
      exit 1
    fi
    cd "$root"
    ./gradlew :app:assembleDebug --no-daemon -q
    adb install -r "$root/app/build/outputs/apk/debug/app-debug.apk"
    echo "Installed: com.kali.nethunter.mcpchat (debug)"
    ;;
  *)
    echo "Unknown command: $cmd"
    usage
    exit 1
    ;;
esac
