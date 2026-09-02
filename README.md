# MD View for Android

MD View is a small, offline Android viewer for Markdown documents. It is designed for the case where Android's **Open with** chooser does not offer a useful Markdown app.

## What it does

- Opens `.md`, `.markdown`, `.mdown`, and `.mkd` documents from Android file managers.
- Registers common Markdown MIME types and also handles providers that report Markdown as `text/plain`.
- Shows **Raw**, **Rendered**, or **Split** views. Split is the default.
- Uses the same locked-down Android WebView surface for Raw and Rendered, so drag, fling, overscroll, zoom, and text-selection behavior feel consistent.
- Adds a persistent **Wrap** toggle for Raw Markdown. Turn it off to preserve horizontal scrolling for long source lines; turn it on to fit source text to the pane width.
- Supports CommonMark plus GitHub-style tables and strikethrough.
- Accepts Markdown shared from another app.
- Uses Android's document picker, so broad storage permission is not needed.
- Works offline and requests no network or storage permissions.

## Install

Download the APK directly from [GitHub Releases](https://github.com/alexeygrigorev/MD-view/releases/latest). Release downloads are `.apk` files, not ZIP archives, so they can be opened directly on an Android phone.

After installation, tap a Markdown document and choose **MD View** / **Open as Markdown**. You can choose **Always** in Android's chooser to make it the default. You can also launch MD View and press **Open**.

## Version 1.0.4

Version 1.0.4 adds Raw word wrapping and simplifies release delivery:

- **Wrap** is available next to Raw, Rendered, and Split.
- The setting is remembered across files and app restarts.
- Raw text still uses the same WebView scrolling engine as Rendered text.
- The repository contains only one GitHub Actions workflow: build, test, lint, sign, verify, and publish.
- A successful `main` build creates or updates the matching GitHub Release and uploads the signed APK as a direct download.

## Repeatable signing and updates

The encrypted PKCS#12 keystore is versioned under `release-signing/`, while its password stays in the `MD_VIEW_SIGNING_SECRET` repository Actions secret. The workflow verifies the pinned signing-certificate fingerprint before signing.

Every release signed with this key can update an installed persistent-key build in place, provided `versionCode` increases. Versions 1.0.0–1.0.2 used temporary keys, so moving from those versions required one final uninstall. Version 1.0.3 and later use the persistent certificate.

Current release-certificate SHA-256:

```text
1c1f1c541583a6fab2e7fb876950a853ed9adc64e9ce1ccf9228d3639778dac4
```

Keep an offline backup of the secret and the PKCS#12 keystore. Losing either prevents future APKs from updating existing installations.

## Stability and privacy

- Core-library desugaring supports the Markdown libraries on Android 6/API 23.
- Markdown parsing happens away from the Android UI thread.
- Parser, memory, linkage, and WebView-renderer failures switch to bounded native fallbacks rather than closing the activity.
- Native TextView fallbacks avoid the Android 6 `View.onDrawScrollBars()` null-drawable crash.
- Both WebViews disable JavaScript, DOM storage, databases, geolocation, file access, content access, and network loading.
- Raw Markdown is HTML-escaped before entering its `<pre>` page. Rendered Markdown escapes raw HTML and sanitizes unsafe URLs.

## Build locally

The project uses Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17, and Android SDK 36.

```bash
gradle clean testReleaseUnitTest lintRelease assembleRelease
```

The local Gradle release APK is unsigned. `.github/workflows/build-apk.yml` restores the persistent key, signs with `apksigner`, verifies the certificate and APK, and publishes the direct GitHub Release asset.

## Source layout

- `app/src/main/java/dev/mdview/app/MainActivity.java`: file intents, document loading, pane UI, Raw wrapping preference, shared WebView configuration, and recovery paths.
- `app/src/main/java/dev/mdview/app/RawSourceRenderer.java`: safe exact-source HTML with wrapped and unwrapped modes.
- `app/src/main/java/dev/mdview/app/MarkdownRenderer.java`: bounded CommonMark-to-safe-HTML conversion.
- `app/src/test/java/dev/mdview/app/RawSourceRendererTest.java`: Raw source escaping and wrapping tests.
- `app/src/test/java/dev/mdview/app/MarkdownRendererTest.java`: rendering and crash-regression tests.
- `app/src/main/AndroidManifest.xml`: launcher and Markdown file associations.
- `.github/workflows/build-apk.yml`: the single build, test, signing, verification, and release workflow.
