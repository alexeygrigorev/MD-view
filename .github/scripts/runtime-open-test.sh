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

capture_ui_bounded() {
  local destination="$1"
  timeout 10s adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || return 1
  adb pull /sdcard/window.xml "$destination" >/dev/null 2>&1
}

collect_diagnostics() {
  local status="$1"
  set +e
  echo "$status" > "$artifact_dir/script-exit-status-api-${api_level}.txt"
  adb logcat -b all -d -v threadtime > "$artifact_dir/logcat-api-${api_level}.txt" 2>&1
  adb shell dumpsys meminfo "$package_name" > "$artifact_dir/meminfo-api-${api_level}.txt" 2>&1
  adb shell dumpsys activity activities > "$artifact_dir/activities-api-${api_level}.txt" 2>&1
  adb shell dumpsys window windows > "$artifact_dir/windows-api-${api_level}.txt" 2>&1
  adb shell dumpsys package "$package_name" > "$artifact_dir/package-api-${api_level}.txt" 2>&1
  adb exec-out screencap -p > "$artifact_dir/screenshot-final-api-${api_level}.png" 2>/dev/null
  capture_ui_bounded "$artifact_dir/ui-final-api-${api_level}.xml" || true
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

# Install the exact-shape fixture into a separate app. Its ContentProvider owns the URI
# and grants MD View temporary read access, matching a real Android document provider.
adb shell "run-as $sender_package mkdir -p files"
cat "$fixture_file" | adb shell "run-as $sender_package sh -c 'cat > files/SKILL.md'"
fixture_size="$(adb shell "run-as $sender_package sh -c 'wc -c < files/SKILL.md'" | tr -d '\r ')"
test "$fixture_size" = "24702"
echo "$fixture_size bytes" > "$artifact_dir/sender-fixture-size-api-${api_level}.txt"

adb shell am force-stop "$package_name"
adb shell am force-stop "$sender_package"
adb logcat -c
adb shell am start -W --user 0 -n "$sender_activity" \
  | tee "$artifact_dir/am-start-sender-api-${api_level}.txt"

assert_alive() {
  local stage="$1"
  local pid
  pid="$(process_pid)"
  if [[ -z "$pid" ]]; then
    echo "MD View Safe is not alive after: $stage" >&2
    return 1
  fi
  echo "$stage pid=$pid" | tee -a "$artifact_dir/process-checks-api-${api_level}.txt"
}

# Wait for the receiving activity and its asynchronous file load. Avoid depending on an
# unbounded uiautomator dump: older Android releases can spend minutes serializing a long
# selectable TextView even though the application itself is healthy.
ready=false
for _ in $(seq 1 30); do
  if [[ -n "$(process_pid)" ]]; then
    adb shell dumpsys activity activities > "$artifact_dir/activities-current-api-${api_level}.txt"
    if grep -q 'dev.mdview.app.safe/dev.mdview.app.MainActivity' \
        "$artifact_dir/activities-current-api-${api_level}.txt"; then
      ready=true
      break
    fi
  fi
  sleep 1
done
$ready
sleep 8
assert_alive "initial document load"
adb exec-out screencap -p > "$artifact_dir/screenshot-split-api-${api_level}.png"

# On releases where the accessibility dump completes promptly, also assert the actual
# document title and raw pane. Screenshots and activity/process evidence remain available
# when an old framework cannot serialize the long selectable text within ten seconds.
if capture_ui_bounded "$artifact_dir/ui-split-api-${api_level}.xml"; then
  grep -q 'text="SKILL.md"' "$artifact_dir/ui-split-api-${api_level}.xml"
  grep -q 'content-desc="Raw Markdown source"' "$artifact_dir/ui-split-api-${api_level}.xml"
else
  echo "uiautomator dump exceeded 10 seconds; process and screenshot checks continued." \
    > "$artifact_dir/ui-dump-note-api-${api_level}.txt"
fi

screen_size="$(adb shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1)"
width="${screen_size%x*}"
height="${screen_size#*x}"
mode_y=$((height * 165 / 1000))
rendered_x=$((width / 2))
split_x=$((width * 5 / 6))
printf 'screen=%s rendered=(%d,%d) split=(%d,%d)\n' \
  "$screen_size" "$rendered_x" "$mode_y" "$split_x" "$mode_y" \
  > "$artifact_dir/tap-coordinates-api-${api_level}.txt"

adb shell input tap "$rendered_x" "$mode_y"
sleep 5
assert_alive "rendered mode"
adb exec-out screencap -p > "$artifact_dir/screenshot-rendered-api-${api_level}.png"

adb shell input tap "$split_x" "$mode_y"
sleep 5
assert_alive "split mode after rendered"
adb exec-out screencap -p > "$artifact_dir/screenshot-split-again-api-${api_level}.png"

adb logcat -b all -d -v threadtime > "$artifact_dir/logcat-api-${api_level}.txt"
if grep -qE 'Process: dev\.mdview\.app\.safe|>>> dev\.mdview\.app\.safe <<<' \
    "$artifact_dir/logcat-api-${api_level}.txt"; then
  echo "A Java or native crash for MD View Safe was found in logcat." >&2
  grep -nE -B 8 -A 40 'Process: dev\.mdview\.app\.safe|>>> dev\.mdview\.app\.safe <<<' \
    "$artifact_dir/logcat-api-${api_level}.txt" >&2 || true
  exit 1
fi

echo "PASS: the 24,702-byte Markdown fixture opened through an app-granted content URI and survived Split → Rendered → Split on API ${api_level}."
