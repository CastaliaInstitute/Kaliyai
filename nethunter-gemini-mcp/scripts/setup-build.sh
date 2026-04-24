#!/usr/bin/env bash
# Configures the environment and builds a debug APK (NetHunter Gemini MCP).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log() { printf '%s\n' "$*"; }

# --- Java: Gradle 8 + Kotlin DSL may fail on very new JDKs; prefer 17 or 21 ---
pick_java() {
  if [[ -n "${JAVA_HOME:-}" && -d "${JAVA_HOME:-}" ]]; then
    if "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE 'version "1[7-9]\.|2[0-2]\.'; then
      return
    fi
  fi
  local c
  for c in \
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"; do
    if [[ -d "$c" && -x "$c/bin/java" ]]; then
      export JAVA_HOME="$c"
      return
    fi
  done
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    for v in 17 21; do
      if h="$(/usr/libexec/java_home -v "$v" 2>/dev/null)"; then
        export JAVA_HOME="$h"
        return
      fi
    done
  fi
  log "ERROR: Need JDK 17–22 for this Android build. Install e.g. brew install openjdk@17"
  log "  Then: export JAVA_HOME=\"\$(/usr/libexec/java_home -v 17)\""
  exit 1
}

# --- Android SDK ---
ensure_sdk() {
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/platforms" ]]; then
    return
  fi
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    return
  fi
  if [[ -d "$HOME/Android/Sdk" ]]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    return
  fi
  log "ERROR: ANDROID_HOME not set and default SDK paths not found."
  log "  Install Android Studio or cmdline-tools, then set ANDROID_HOME or add sdk.dir to local.properties"
  exit 1
}

write_local_properties() {
  if [[ -f "$ROOT/local.properties" ]]; then
    return
  fi
  if [[ -z "${ANDROID_HOME:-}" ]]; then
    return
  fi
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/local.properties"
  log "Wrote $ROOT/local.properties (sdk.dir=$ANDROID_HOME)"
}

pick_java
log "Using JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version

ensure_sdk
log "Using ANDROID_HOME=$ANDROID_HOME"
write_local_properties

if [[ ! -x "$ROOT/gradlew" ]]; then
  log "ERROR: gradlew missing. Run: gradle wrapper (from a machine with Gradle installed)"
  exit 1
fi

log "Running ./gradlew assembleDebug …"
exec "$ROOT/gradlew" assembleDebug
