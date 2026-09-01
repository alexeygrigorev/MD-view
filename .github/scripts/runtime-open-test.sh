#!/usr/bin/env bash
set -euo pipefail

api_level="${1:?Android API level is required}"
package_name="dev.mdview.app.safe"
activity_name="dev.mdview.app.safe/dev.mdview.app.MainActivity"
artifact_dir="runtime-artifact"
fixture_b64="app/src/androidTest/assets/skill-shape.md.gz.b64"
fixture_file="/tmp/SKILL.md"
remote_file="/sdcard/Download/SKILL.md"
document_uri="content://com.android.externalstorage.documents/document/primary%3ADownload%2FSKILL.md"

mkdir -p "$artifact_dir"
base64 --decode "$fixture_b64" | gzip --decompress > "$fixture_file"

test "$(wc -c < "$fixture_file")" -eq 24702
test "$(awk 'END { print NR }' "$fixture_file")" -eq 373

gradle --no-daemon --stacktrace :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /sdcard/Download
adb push "$fixture_file" "$remote_file"
adb shell am force-stop "$package_name"
adb logcat -c

# The normal Android shell UID cannot grant an ExternalStorageProvider URI to another
# process. These are AOSP userdebug emulator images, so restart adbd as root solely to
# act as the file-manager sender and issue the read grant. The receiving app itself still
# runs under its ordinary unprivileged application UID.
adb root | tee "$artifact_dir/adb-root-api-${api_level}.txt"
adb wait-for-device
adb shell id | tee "$artifact_dir/sender-identity-api-${api_level}.txt"

adb shell am start -W --user 0 \
  -a android.intent.action.VIEW \
  -d "$document_uri" \
  -t text/markdown \
  -n "$activity_name" \
  --grant-read-uri-permission \
  | tee "$artifact_dir/am-start-api-${api_level}.txt"

sleep 8

assert_alive() {
  local stage="$1"
  local pid
  pid="$(adb shell pidof "$package_name" 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$pid" ]]; then
    pid="$(adb shell ps | awk -v package="$package_name" '$NF == package { print $2; exit }' | tr -d '\r')"
  fi
  if [[ -z "$pid" ]]; then
    echo "The app process is not alive after: $stage" >&2
    return 1
  fi
  echo "$stage pid=$pid" | tee -a "$artifact_dir/process-checks-api-${api_level}.txt"
}

dump_ui() {
  local stage="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$artifact_dir/ui-${stage}-api-${api_level}.xml" >/dev/null
}

tap_text() {
  local label="$1"
  dump_ui "before-${label,,}"
  local xml="$artifact_dir/ui-before-${label,,}-api-${api_level}.xml"
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

assert_alive "initial document load"
dump_ui "split"
grep -q 'text="SKILL.md"' "$artifact_dir/ui-split-api-${api_level}.xml"
grep -q 'content-desc="Raw Markdown source"' "$artifact_dir/ui-split-api-${api_level}.xml"

tap_text "Rendered"
sleep 5
assert_alive "rendered mode"
dump_ui "rendered"

tap_text "Split"
sleep 5
assert_alive "split mode after rendered"
dump_ui "split-again"
grep -q 'content-desc="Raw Markdown source"' "$artifact_dir/ui-split-again-api-${api_level}.xml"

adb logcat -d -v threadtime > "$artifact_dir/logcat-api-${api_level}.txt"
adb shell dumpsys meminfo "$package_name" > "$artifact_dir/meminfo-api-${api_level}.txt" || true
adb shell dumpsys activity activities > "$artifact_dir/activities-api-${api_level}.txt" || true
adb exec-out screencap -p > "$artifact_dir/screenshot-api-${api_level}.png" || true

if grep -qE 'Process: dev\.mdview\.app\.safe|>>> dev\.mdview\.app\.safe <<<' "$artifact_dir/logcat-api-${api_level}.txt"; then
  echo "A Java or native crash for MD View Safe was found in logcat." >&2
  grep -nE -B 8 -A 30 'Process: dev\.mdview\.app\.safe|>>> dev\.mdview\.app\.safe <<<' "$artifact_dir/logcat-api-${api_level}.txt" >&2 || true
  exit 1
fi

echo "PASS: the 24,702-byte, 373-line Markdown fixture opened from a granted content URI and survived Split → Rendered → Split on API ${api_level}."
