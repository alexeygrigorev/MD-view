# Changelog

## 1.0.2 — 2026-09-01

- Fixed an Android 6 framework crash in `View.onDrawScrollBars()` caused by null scrollbar drawables on programmatically created text views.
- Kept Raw and native-preview content scrollable while disabling the unsafe visual scrollbar drawing path on API 23.
- Enabled core-library desugaring so CommonMark's Java functional-interface references work on Android 6.
- Lazily initializes the Markdown engine and contains linkage failures with a plain-text preview.
- Added an Android 6 emulator smoke test that opens and draws a long Markdown document and checks process survival and Logcat.

## 1.0.1 — 2026-09-01

- Prevented complex Markdown parsing failures from crashing the activity.
- Moved preview generation off the UI thread.
- Added bounded fallback rendering for unusually large or deeply nested documents.
- Handled Android WebView renderer termination and added a native preview fallback.
- Allowed installation and operation when WebView is unavailable.
- Added stress tests for complex Markdown structures and long lines.

## 1.0.0 — 2026-09-01

- Initial raw, rendered, and split Markdown viewer.
