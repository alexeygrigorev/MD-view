# MD View for Android

MD View is a small, offline Android viewer for Markdown documents. It is designed for the case where Android's **Open with** chooser does not offer a useful Markdown app.

## What it does

- Opens `.md`, `.markdown`, `.mdown`, and `.mkd` documents from Android file managers.
- Registers common Markdown MIME types and also handles providers that report Markdown as `text/plain`.
- Shows **Raw**, **Rendered**, or **Split** views. Split is the default.
- Uses the same locked-down Android WebView surface for Raw and Rendered, so drag, fling, overscroll, zoom, and text-selection behavior feel consistent.
- Preserves Raw source exactly in a selectable monospaced `<pre>` surface, including horizontal scrolling for long lines.
- Supports CommonMark plus GitHub-style tables and strikethrough.
- Accepts Markdown shared from another app.
- Uses Android's document picker, so broad storage permission is not needed.
- Works offline and requests no network or storage permissions.

## Version 1.0.3

Version 1.0.3 changes Raw mode from an Android `TextView` scroller to the same WebView scrolling engine used by Rendered mode. A native read-only TextView remains available only as a recovery path when Android WebView is missing or terminates.

It also establishes persistent release signing:

- the encrypted PKCS#12 keystore is versioned under `release-signing/`;
- the decryption/key password stays in the `MD_VIEW_SIGNING_SECRET` GitHub Actions secret;
- the workflow verifies the expected signing-certificate SHA-256 before signing;
- every future release signed with this key can update an installed 1.0.3-or-newer APK in place; and
- the public certificate and pinned fingerprint are committed for auditability.

Because versions 1.0.0–1.0.2 were signed with one-build keys that were not retained, moving from one of those builds to 1.0.3 requires one final uninstall. Do not replace the 1.0.3 release key afterward.

## Stability safeguards

- Core-library desugaring supplies the Java functional interfaces used by the Markdown libraries on API 23.
- Markdown parsing happens away from the Android UI thread.
- Parser stack overflows, malformed input failures, linkage failures, and preview memory limits produce a readable fallback instead of terminating the activity.
- Android WebView renderer exits are handled with `onRenderProcessGone`; either pane switches independently to its native fallback.
- The app can start without a working WebView package and use native fallback views.
- Native TextView fallbacks avoid the Android 6 `View.onDrawScrollBars()` null-drawable crash.
- Regression tests cover source escaping, long lines, large tables, fenced code, Unicode punctuation, and thousands of ordered-list markers.
- The Android 6 emulator smoke workflow opens a long Markdown document, exercises vertical and horizontal Raw scrolling plus Rendered scrolling, and checks process survival and Logcat.

## Install

The GitHub Actions workflow builds versioned APK artifacts for Android 6.0 or newer. Use the artifact ending in `-signed`; the `-unsigned` artifact is only for CI inspection and cannot be installed as a normal release.

After installation, tap a Markdown document and choose **MD View** / **Open as Markdown**. You can choose **Always** in Android's chooser to make it the default. You can also launch MD View and press **Open**.

## Configure repeatable GitHub signing

Follow [`release-signing/README.md`](release-signing/README.md). The repository owner adds `MD_VIEW_SIGNING_SECRET` once under **Settings → Secrets and variables → Actions**. The encrypted keystore may remain public because it cannot be decrypted without that secret.

Current release-certificate SHA-256:

```text
1c1f1c541583a6fab2e7fb876950a853ed9adc64e9ce1ccf9228d3639778dac4
```

Keep an offline backup of the secret and the PKCS#12 keystore. Losing either prevents future APKs from updating existing installations.

## Privacy and rendering behavior

Both WebViews disable JavaScript, DOM storage, databases, geolocation, file access, content access, and network loading. Raw Markdown is HTML-escaped before entering its `<pre>` page. Rendered Markdown escapes raw HTML and sanitizes unsafe URLs. Supported external links are handed to another installed app rather than loaded inside MD View.

## Build locally

The project uses Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17, and Android SDK 36.

```bash
gradle clean testReleaseUnitTest lintRelease assembleRelease
```

The Gradle release APK is unsigned. `.github/workflows/build-apk.yml` restores the encrypted persistent key, signs with `apksigner`, verifies the certificate and signature, checks alignment, and uploads the signed release artifact when the Actions secret is available.

## Source layout

- `app/src/main/java/dev/mdview/app/MainActivity.java`: file intents, document loading, pane UI, shared WebView configuration, and independent recovery paths.
- `app/src/main/java/dev/mdview/app/RawSourceRenderer.java`: safe exact-source HTML page for Raw mode.
- `app/src/main/java/dev/mdview/app/MarkdownRenderer.java`: bounded CommonMark-to-safe-HTML conversion.
- `app/src/test/java/dev/mdview/app/RawSourceRendererTest.java`: Raw source escaping and formatting tests.
- `app/src/test/java/dev/mdview/app/MarkdownRendererTest.java`: rendering and crash-regression tests.
- `app/src/main/AndroidManifest.xml`: launcher and Markdown file associations.
- `.github/workflows/build-apk.yml`: test, lint, versioned build, persistent signing, and verification workflow.
- `.github/workflows/api23-smoke.yml`: Android 6 runtime and Raw/Rendered scrolling smoke test.
