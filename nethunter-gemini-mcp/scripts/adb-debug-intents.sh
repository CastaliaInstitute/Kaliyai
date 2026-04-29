#!/usr/bin/env bash
# Fire debug-only intents at a device/emulator. Install a **debug** build; see app/src/debug/AndroidManifest.xml
set -euo pipefail
PKG="com.kali.nethunter.mcpchat"
ACT="${PKG}/.MainActivity"

view() {
  adb shell am start -a android.intent.action.VIEW -d "$1" -n "$ACT"
}

command() {
  # --es cmd <name> and optional --es message <text>
  adb shell am start -a com.kali.nethunter.mcpchat.debug.COMMAND -n "$ACT" "$@"
}

case "${1:-}" in
  settings) view "mcpchat://debug/settings" ;;
  clear) view "mcpchat://debug/clear" ;;
  refresh|refresh_mcp) view "mcpchat://debug/refresh_mcp" ;;
  ping) view "mcpchat://debug/ping" ;;
  send)
    shift
    msg="${*:?usage: $0 send <message>}"
    # URL encode spaces and special characters
    enc=$(printf '%s' "$msg" | sed 's/ /%20/g; s/?/%3F/g; s/&/%26/g')
    view "mcpchat://debug/send?q=${enc}"
    ;;
  s|cmd_settings) command --es cmd settings ;;
  c|cmd_clear) command --es cmd clear ;;
  r|cmd_refresh) command --es cmd refresh_mcp ;;
  m|cmd_send)
    shift
    msg="${*:?usage: $0 cmd_send <message>}"
    # Use explicit message param to avoid shell word splitting issues
    adb shell am start \
      -a "com.kali.nethunter.mcpchat.debug.COMMAND" \
      -n "${ACT}" \
      --es "cmd" "send" \
      --es "message" "$msg"
    ;;
  *)
    echo "Usage: $0 {settings|clear|refresh|ping|send <msg>|s|c|r|m <msg>}" >&2
    echo "  settings / clear / refresh / ping  — deep links (mcpchat://debug/...)" >&2
    echo "  send <msg>                          — same via ?q= (limited encoding)" >&2
    echo "  s / c / r / m <msg>                — explicit COMMAND (use m for reliable send)" >&2
    exit 1
    ;;
esac
