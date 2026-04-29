#!/usr/bin/env bash
# ADB Eval Test Runner with Output Capture
# Sends test commands to Android app and captures responses via logcat
set -euo pipefail

PKG="com.kali.nethunter.mcpchat"
ACT="${PKG}/.MainActivity"
RESULTS_DIR="/tmp/kaliyai-eval-results"
mkdir -p "$RESULTS_DIR"

# Clear logcat before starting
adb logcat -c 2>/dev/null || true

# Test cases with expected behaviors
declare -a TESTS=(
  "What am I connected to?|wifi_info|SSID|wifi"
  "scan wifi|wifi_scan|network|AP"
  "check kali status|kali_nethunter_info|su|chroot"
  "scan network|kali_nethunter_exec|nmap|scan"
  "what tools do you have|kali_nethunter_list_tools|nmap|metasploit"
)

run_test() {
  local test_input="$1"
  local expected_tool="$2"
  local expected_output1="$3"
  local expected_output2="$4"
  local test_id="${5:-unknown}"
  
  echo ""
  echo "=== Test $test_id ==="
  echo "Input: $test_input"
  echo "Expected tool: $expected_tool"
  
  # Clear previous logs
  adb logcat -c 2>/dev/null || true
  
  # Send intent with proper quoting
  adb shell am start \
    -a "com.kali.nethunter.mcpchat.debug.COMMAND" \
    -n "${ACT}" \
    --es "cmd" "send" \
    --es "message" "$test_input" 2>&1 | tail -1
  
  # Wait for processing
  sleep 8
  
  # Capture output from logcat
  local output_file="$RESULTS_DIR/test_${test_id}.log"
  adb logcat -d --pid=$(adb shell pidof "$PKG" 2>/dev/null) 2>/dev/null > "$output_file" || true
  
  # Check for expected output
  if grep -q "$expected_output1" "$output_file" 2>/dev/null || grep -q "$expected_output2" "$output_file" 2>/dev/null; then
    echo "✅ PASSED - Found expected output"
    return 0
  else
    echo "⚠️  CHECK - Review output manually"
    echo "  (Log saved to: $output_file)"
    return 1
  fi
}

echo "=========================================="
echo "Kaliyai ADB Eval Test Suite with Output"
echo "=========================================="
echo ""
echo "Device: $(adb shell getprop ro.product.model 2>/dev/null || echo 'Unknown')"
echo "Time: $(date)"
echo ""

passed=0
failed=0
test_num=1

for test in "${TESTS[@]}"; do
  IFS='|' read -r input tool out1 out2 <<< "$test"
  if run_test "$input" "$tool" "$out1" "$out2" "$test_num"; then
    ((passed++))
  else
    ((failed++))
  fi
  ((test_num++))
done

echo ""
echo "=========================================="
echo "Results: $passed passed, $failed failed"
echo "=========================================="
echo ""
echo "Full logs in: $RESULTS_DIR"
echo ""

# Show summary of outputs
echo "Captured outputs:"
for f in "$RESULTS_DIR"/*.log; do
  if [ -f "$f" ]; then
    name=$(basename "$f" .log)
    lines=$(wc -l < "$f" | tr -d ' ')
    echo "  $name: $lines lines"
  fi
done
