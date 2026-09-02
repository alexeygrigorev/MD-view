# Changelog

## 1.0.4 — 2026-09-02

- Added a persistent **Wrap** toggle for Raw Markdown.
- Wrapped mode fits long source lines to the pane; unwrapped mode retains horizontal scrolling.
- Kept Raw and Rendered on the same WebView scrolling engine.
- Reduced GitHub Actions to one build/release workflow.
- Changed release delivery from ZIP-wrapped Actions artifacts to a directly downloadable signed APK on GitHub Releases.
- Continued using the persistent release certificate introduced in 1.0.3 for in-place updates.

## 1.0.3 — 2026-09-02

- Changed Raw mode to use the same locked-down Android WebView scrolling surface as Rendered mode.
- Matched drag, fling, overscroll, zoom, and text-selection behavior between Raw and Rendered panes.
- Preserved exact monospaced Raw source with horizontal scrolling for long lines.
- Kept the native Raw TextView only as a WebView-unavailable recovery path.
- Added source-page escaping tests and an Android 6 vertical/horizontal scrolling smoke test.
- Established a persistent encrypted PKCS#12 release key and pinned public certificate.
- Updated GitHub Actions to restore the key from `MD_VIEW_SIGNING_SECRET`, verify the certificate, sign, align, and verify every release APK.

## 1.0.2 — 2026-09-01

- Fixed an Android 6 framework crash in `View.onDrawScrollBars()` caused by null scrollbar drawables on programmatically created text views.
- Kept Raw and native-preview content scrollable while disabling the unsafe visual scrollbar drawing path on API 23.
- Enabled core-library desugaring so CommonMark's Java functional-interface references work on Android 6.
- Lazily initialized the Markdown engine and contained linkage failures with a plain-text preview.
- Added an Android 6 emulator smoke test that opened and drew a long Markdown document and checked process survival and Logcat.

## 1.0.1 — 2026-09-01

- Prevented complex Markdown parsing failures from crashing the activity.
- Moved preview generation off the UI thread.
- Added bounded fallback rendering for unusually large or deeply nested documents.
- Handled Android WebView renderer termination and added a native preview fallback.
- Allowed installation and operation when WebView is unavailable.
- Added stress tests for complex Markdown structures and long lines.

## 1.0.0 — 2026-09-01

- Initial raw, rendered, and split Markdown viewer.
