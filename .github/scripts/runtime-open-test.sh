#!/usr/bin/env bash
set -euo pipefail

api_level="${1:?Android API level is required}"
package_name="dev.mdview.app.safe"
sender_package="dev.mdview.testsender"
sender_activity="dev.mdview.testsender/.SenderActivity"
artifact_dir="runtime-artifact"
fixture_b64="app/src/androidTest/assets/skill-shape.md.gz.b64"
fixture_file="/tmp/SKILL.md"

mkdir -p "$artifact_dir"
base64 --decode "$fixture_b64" | gzip --decompress > "$fixture_file"

test "$(wc -c < "$fixture_file")" -eq 24702
test "$(awk 'END { print NR }' "$fixture_file")" -eq 373

process_pid() {
  local output
  output="$(adb shell ps -A 2>/dev/null || adb shell ps 2>/dev/null || true)"
  printf '%s\n' "$output" | tr -d '\r' | awk -v package="$package_name" '$NF == package { print $2; exit }'
}

dump_ui_to() {
  local destination="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || return 1
  adb pull /sdcard/window.xml "$destination" >/dev/null 2>&1
}

collect_diagnostics() {
  local exit_status="$1"
  set +e
  echo "$exit_status" > "$artifact_dir/script-exit-status-api-${api_level}.txt"
  adb logcat -b all -d -v threadtime > "$artifact_dir/logcat-api-${api_level}.txt" 2>&1
  adb shell dumpsys meminfo "$package_name" > "$artifact_dir/meminfo-api-${api_level}.txt" 2>&1
  adb shell dumpsys activity activities > "$artifact_dir/activities-api-${api_level}.txt" 2>&1
  adb shell dumpsys package "$package_name" > "$artifact_dir/package-api-${api_level}.txt" 2>&1
  adb exec-out screencap -p > "$artifact_dir/screenshot-api-${api_level}.png" 2>/dev/null
  dump_ui_to "$artifact_dir/ui-final-api-${api_level}.xml"
}

on_exit() {
  local status=$?
  trap - EXIT
  collect_diagnostics "$status"
  exit "$status"
}
trap on_exit EXIT

gradle --no-daemon --stacktrace :app:assembleDebug :runtime-sender:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r runtime-sender/build/outputs/apk/debug/runtime-sender-debug.apk

# Copy the regression fixture into the sender application's private storage. The sender
# then serves it through its own ContentProvider and grants MD View read access, exactly
# as a normal Android file manager or document provider does.
cat "$fixture_file" | adb shell "run-as $sender_package sh -c 'cat > files/SKILL.md'"
adb shell "run-as $sender_package sh -c 'wc -c files/SKILL.md'" \
  | tee "$artifact_dir/sender-fixture-size-api-${api_level}.txt"

a=$(adb shell "run-as $sender_package sh -c 'wc -c < files/SKILL.md'" | tr -d '\r ')
test "$a" = "24702"

adb shell am force-stop "$package_name"
adb shell am force-stop "$sender_package"
adb logcat -c

adb shell am start -W --user 0 -n "$sender_activity" \
  | tee "$artifact_dir/am-start-sender-api-${api_level}.txt"

wait_for_document() {
  local deadline=$((SECONDS + 30))
  local xml="$artifact_dir/ui-wait-api-${api_level}.xml"
  while (( SECONDS < deadline )); do
    dump_ui_to "$xml" || true
    if [[ -f "$xml" ]] && grep -q 'content-desc="Raw Markdown source"' "$xml"; then
      cp "$xml" "$artifact_dir/ui-split-api-${api_level}.xml"
      return 0
    fi
    if [[ -f "$xml" ]] && grep -Eq 'MD View Safe.*(has stopped|keeps stopping)|Unfortunately, MD View Safe has stopped' "$xml"; then
      echo "Android displayed a crash dialog while opening the document." >&2
      return 1
    fi
    if [[ -z "$(process_pid)" ]]; then
      echo "The MD View Safe process exited while opening the document." >&2
      return 1
    fi
    sleep 1
  done
  echo "The document did not reach the raw/split view within 30 seconds." >&2
  return 1
}

assert_alive() {
  local stage="$1"
  local pid
  pid="$(process_pid)"
  if [[ -z "$pid" ]]; then
    echo "The app process is not alive after: $stage" >&2
    return 1
  fi
  echo "$stage pid=$pid" | tee -a "$artifact_dir/process-checks-api-${api_level}.txt"
}

tap_text() {
  local label="$1"
  local stage="${label,,}"
  local xml="$artifact_dir/ui-before-${stage}-api-${api_level}.xml"
  dump_ui_to "$xml"
  local coords
  coords="$(python3 - "$xml" "$label" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, label = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == label or node.attrib.get("content-desc") == label:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            left, top, right, bottom = map(int, match.groups())
            print((left + right) // 2, (top + bottom) // 2)
            break
PY
)"
  if [[ -z "$coords" ]]; then
    echo "Could not find UI control: $label" >&2
    return 1
  fi
  adb shell input tap $coords
}

wait_for_document
assert_alive "initial document load"
grep -q 'content-desc="Raw Markdown source"' "$artifact_dir/ui-split-api-${api_level}.xml"

tap_text "Rendered"
sleep 5
assert_alive "rendered mode"
dump_ui_to "$artifact_dir/ui-rendered-api-${api_level}.xml"
grep -q 'content-desc="Native rendered Markdown preview"' \
  "$artifact_dir/ui-rendered-api-${api_level}.xml"

tap_text "Split"
sleep 5
assert_alive "split mode after rendered"
dump_ui_to "$artifact_dir/ui-split-again-api-${api_level}.xml"
grep -q 'content-desc="Raw Markdown source"' \
  "$artifact_dir/ui-split-again-api-${api_level}.xml"

adb logcat -b all -d -v threadtime > "$artifact_dir/logcat-api-${api_level}.txt"
if grep -qE 'Process: dev\.mdview\.app\.safe|>>> dev\.mdview\.app\.safe <<<' \
    "$artifact_dir/logcat-api-${api_level}.txt"; then
  echo "A Java or native crash for MD View Safe was found in logcat." >&2
  grep -nE -B 8 -A 40 'Process: dev\.mdview\.app\.safe|>>> dev\.mdview\.app\.safe <<<' \
    "$artifact_dir/logcat-api-${api_level}.txt" >&2 || true
  exit 1
fi

echo "PASS: the 24,702-byte, 373-line Markdown fixture opened from an app-granted content URI and survived Split → Rendered → Split on API ${api_level}."
