# MD View for Android

MD View is a small, offline Android viewer for Markdown documents. It is designed for the case where Android's **Open with** chooser does not offer a useful Markdown app.

## What it does

- Opens `.md`, `.markdown`, `.mdown`, and `.mkd` documents from Android file managers.
- Registers common Markdown MIME types and also handles providers that report Markdown as `text/plain`.
- Shows **Raw**, **Rendered**, or **Split** views. Split is the default.
- Supports CommonMark plus GitHub-style tables and strikethrough.
- Accepts Markdown shared from another app.
- Uses Android's document picker, so broad storage permission is not needed.
- Works offline and requests no network or storage permissions.

## Stability safeguards in 1.0.1

Version 1.0.1 hardens the paths used by complex Markdown documents:

- Markdown parsing happens away from the Android UI thread.
- Parser stack overflows, malformed input failures, and preview memory limits produce a readable fallback instead of terminating the activity.
- Android WebView renderer exits are handled with `onRenderProcessGone`; the app switches to a native preview while preserving Raw view.
- The app can start without a working WebView package and use native preview mode.
- Raw text layout uses Android's simpler line-breaking strategy, with a bounded recovery path if the platform cannot lay out a document.
- Regression tests cover long lines, large tables, fenced code, Unicode punctuation, and thousands of ordered-list markers.

## Install

The GitHub Actions workflow builds `MD-View-v1.0.1.apk` for Android 6.0 or newer. Open the workflow artifact, download the APK, and permit installation from the browser or file manager when Android asks.

After installation, tap a Markdown document and choose **MD View** / **Open as Markdown**. You can choose **Always** in Android's chooser to make it the default. You can also launch MD View and press **Open**.

> The workflow currently generates a fresh self-signed key for each build. Builds made by separate workflow runs cannot update one another in place. Configure a persistent signing key in GitHub Actions before publishing long-lived releases.

## Privacy and rendering behavior

The raw source is read-only and selectable. In the WebView preview, JavaScript, DOM storage, file access, content access, and network loading are disabled. Raw HTML in Markdown is escaped, unsafe URLs are sanitized, and supported external links are passed to another installed app. If WebView is unavailable or its renderer exits, MD View falls back to Android's native HTML display rather than closing.

## Build locally

The project uses Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17, and Android SDK 36.

```bash
gradle clean testReleaseUnitTest lintRelease assembleRelease
```

The Gradle release APK is unsigned. The included GitHub Actions workflow signs the downloadable artifact and verifies it with `apksigner`.

## Source layout

- `app/src/main/java/dev/mdview/app/MainActivity.java`: file intents, document loading, UI, WebView recovery, and native fallback.
- `app/src/main/java/dev/mdview/app/MarkdownRenderer.java`: bounded CommonMark-to-safe-HTML conversion.
- `app/src/test/java/dev/mdview/app/MarkdownRendererTest.java`: rendering and crash-regression tests.
- `app/src/main/AndroidManifest.xml`: launcher and Markdown file associations.
- `.github/workflows/build-apk.yml`: test, lint, build, signing, and verification workflow.
