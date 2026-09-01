# Changelog

## 1.0.2 — Safe renderer

- Removes the system WebView from the preview path.
- Renders Markdown with Android's native text stack in software mode.
- Keeps Raw, Rendered, and Split modes.
- Uses a separate application ID (`dev.mdview.app.safe`) so it can be installed alongside earlier test builds.
- Adds a byte/line/structure-preserving Android runtime regression fixture for the reported document.

## 1.0.1

- Added parser and WebView recovery paths.

## 1.0.0

- Initial release.
