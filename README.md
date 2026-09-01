# MD View for Android

MD View is a small, offline Android viewer for Markdown documents. It is designed for the exact case where Android's **Open with** chooser does not offer a useful Markdown app.

## What it does

- Opens `.md`, `.markdown`, `.mdown`, and `.mkd` documents from Android file managers.
- Registers common Markdown MIME types and also handles providers that report Markdown as `text/plain`.
- Shows **Raw**, **Rendered**, or **Split** views. Split is the default.
- Supports CommonMark plus GitHub-style tables and strikethrough.
- Accepts Markdown shared from another app.
- Uses Android's document picker, so broad storage permission is not needed.
- Works offline and requests no network or storage permissions.

## Install

Install `MD-View-v1.0.0.apk` on a device running Android 6.0 or newer. Because this build is distributed directly rather than through an app store, Android may ask you to allow installs from the browser or file manager you use to open the APK.

After installation, tap a Markdown document and choose **MD View** / **Open as Markdown**. You can choose **Always** in Android's chooser to make it the default.

## Privacy and rendering behavior

The raw source is read-only and selectable. The rendered pane has JavaScript, DOM storage, file access, content access, and network loading disabled. Raw HTML in Markdown is escaped, unsafe URLs are sanitized, and external web links are passed to another installed app.

## Build locally

The project uses Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17, and Android SDK 36.

```bash
gradle clean testReleaseUnitTest lintRelease assembleRelease
```

A release APK produced by Gradle is unsigned. The included GitHub Actions workflow signs the downloadable artifact with a one-build self-signed key and verifies the result with `apksigner`.

## Source layout

- `MainActivity.java`: file intents, document picker, and native UI.
- `MarkdownRenderer.java`: CommonMark-to-safe-HTML conversion.
- `AndroidManifest.xml`: launcher and Markdown file associations.
- `.github/workflows/build-apk.yml`: tested, signed APK build.
