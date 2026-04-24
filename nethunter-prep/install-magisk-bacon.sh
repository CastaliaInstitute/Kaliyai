#!/usr/bin/env bash
# OnePlus One (bacon) + same Lineage 18.1 build as in this folder:
#   patch boot in Magisk app, then fastboot flash boot (recommended; see Magisk install.md).
# Usage:
#   ./install-magisk-bacon.sh            — Lineage must be running; USB debugging on (installs app, pushes boot, opens Magisk)
#   ./install-magisk-bacon.sh fastboot   — after you patched in Magisk: pull magisk_patched*.img and fastboot flash boot
#   ./install-magisk-bacon.sh twrp       — device in TWRP: push Magisk zip, optional twrp install (deprecated; may need in-app follow-up)
set -euo pipefail
cd "$(dirname "$0")"
BOOT="boot.lineage-18.1-20240206-bacon.img"
MAGISK_APK="Magisk-v30.7.apk"
MAGISK_TWRP="Magisk-v30.7-twrp-flashable.zip"
MODE="${1:-app}"
SCRIPT="$0"

if [[ ! -f "$BOOT" ]]; then
  echo "Missing $BOOT — re-run: unzip -o lineage-18.1-20240206-nightly-bacon-signed.zip boot.img && mv boot.img $BOOT"
  exit 1
fi
if [[ ! -f "$MAGISK_APK" ]]; then
  echo "Missing $MAGISK_APK. Download: https://github.com/topjohnwu/Magisk/releases"
  exit 1
fi

echo "== Waiting for authorized ADB device =="
n=0
while true; do
  if adb get-state 2>/dev/null | grep -q device$; then
    break
  fi
  n=$((n+1))
  if (( n >= 30 )); then
    echo "No device (60s). Set USB to File Transfer, enable USB debugging, authorize the RSA prompt, then re-run this script."
    exit 1
  fi
  sleep 2
done

if [[ "$MODE" == "fastboot" ]]; then
  echo "== After patching in Magisk: pull and flash =="
  n=0
  while true; do
    if adb get-state 2>/dev/null | grep -q device$; then
      break
    fi
    n=$((n+1))
    if (( n >= 30 )); then
      echo "No ADB device."
      exit 1
    fi
    sleep 2
  done
  p=$(adb shell "ls -1 /sdcard/Download/magisk_patched*.img 2>/dev/null" 2>/dev/null | tr -d '\r' | head -1 | tr -d '[:space:]') || p=""
  if [[ -z "${p:-}" ]]; then
    echo "No /sdcard/Download/magisk_patched*.img — run Magisk, Install → Select and Patch, pick $BOOT first, then re-run: $SCRIPT fastboot"
    exit 1
  fi
  echo "Patched file on device: $p"
  b=$(basename "$p")
  adb pull "$p" "./$b"
  echo
  read -r -p "Reboot to bootloader and run: fastboot flash boot \"$b\"? [y/N] " a || a=N
  if [[ ! "${a}" =~ [yY] ]]; then
    echo "Flash manually: adb reboot bootloader && fastboot flash boot \"$b\" && fastboot reboot"
    exit 0
  fi
  adb reboot bootloader
  for i in {1..50}; do
    fastboot devices 2>/dev/null | grep -E '\tfastboot$' && break
    sleep 1
  done
  fastboot flash boot "./$b"
  fastboot reboot
  echo "Done. Open Magisk; confirm a Magisk version (not 0) is shown."
  exit 0
fi

if [[ "$MODE" == "twrp" ]]; then
  echo "== TWRP mode: pushing Magisk (APK content as .zip) =="
  adb push "$MAGISK_TWRP" /sdcard/Magisk-twrp-flashable.zip
  echo
  echo "In this adb shell, run (or use the TWRP UI -> Install and pick the zip):"
  echo "  adb shell twrp install /sdcard/Magisk-twrp-flashable.zip"
  echo
  read -r -p "Run twrp install now? [y/N] " a || true
  a=${a:-N}
  if [[ "${a}" =~ [yY] ]]; then
    if adb shell twrp install /sdcard/Magisk-twrp-flashable.zip; then
      echo "Done. Reboot to system, open Magisk; if it asks, run Direct / additional setup, then reboot."
    else
      echo "twrp install failed or your TWRP has no 'twrp' command — use TWRP Install screen manually."
    fi
  fi
  exit 0
fi

echo "== Patching / recommended path: Lineage (Android) must be running =="
adb push "$MAGISK_APK" /sdcard/ || true
adb install -r -d "$MAGISK_APK" || { echo "Magisk app install failed."; exit 1; }
adb push "$BOOT" /sdcard/Download/boot-bacon-lineage-18.1.img
echo
echo "Boot image pushed to: /sdcard/Download/boot-bacon-lineage-18.1.img (same as your flashed Lineage 20240206 build)"
echo
# Launch Magisk (default activity name may differ by version; monkey is resilient)
if ! adb shell monkey -p com.topjohnwu.magisk -c android.intent.category.LAUNCHER 1 &>/dev/null; then
  echo "Start the Magisk app by hand (look for the Magisk icon)."
fi

echo
echo "On the phone, in Magisk (root not required to patch):"
echo "  1) Tap Install in the top Magisk card"
echo "  2) Method: Select and Patch a File"
echo "  3) Pick: Download/boot-bacon-lineage-18.1.img  (or use Files to find it)"
echo "  4) Let it finish — keep the screen on"
echo
echo "If Magisk shows additional setup, accept the reboot, then return here when ready to flash."
echo
echo "When patch output exists as /sdcard/Download/magisk_patched*.img, run (same folder, phone still on USB, Android with adb):"
echo
echo "  $SCRIPT fastboot"
echo
echo "Or by hand: adb pull /sdcard/Download/magisk_patched\\*.img .  && adb reboot bootloader  &&  fastboot flash boot magisk_patched-*.img  &&  fastboot reboot"
echo
echo "Only flash a patched image that was created on *this* phone. Never use someone else’s .img"
