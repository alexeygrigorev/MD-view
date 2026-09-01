# Changelog

## 1.0.1 — 2026-09-01

- Prevented complex Markdown parsing failures from crashing the activity.
- Moved preview generation off the UI thread.
- Added bounded fallback rendering for unusually large or deeply nested documents.
- Handled Android WebView renderer termination and added a native preview fallback.
- Allowed installation and operation when WebView is unavailable.
- Added stress tests for complex Markdown structures and long lines.

## 1.0.0 — 2026-09-01

- Initial raw, rendered, and split Markdown viewer.
