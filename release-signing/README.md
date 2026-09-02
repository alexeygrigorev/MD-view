# Persistent release signing

MD View 1.0.3 establishes the release key that must sign every future direct-download APK.
Android only accepts an in-place update when the installed APK and replacement APK use the same
signing certificate.

## Repository contents

- `mdview-release.p12.enc.b64` is the PKCS#12 keystore encrypted with OpenSSL AES-256-CBC,
  PBKDF2-SHA-256, and 600,000 iterations, then Base64 encoded so it can be versioned safely.
- `certificate.pem` is public and may be shared.
- `certificate.sha256` pins the expected signing-certificate SHA-256 digest.

The decryption password is **not** in the repository. It is also the PKCS#12 store/key password.
The fixed key alias is `mdview-release`.

## One-time GitHub setup

Create a repository Actions secret named:

```text
MD_VIEW_SIGNING_SECRET
```

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**.
Alternatively, with GitHub CLI authenticated for this repository:

```bash
gh secret set MD_VIEW_SIGNING_SECRET < /secure/path/MD_VIEW_SIGNING_SECRET.txt
```

Keep an offline backup of both the secret and the unencrypted `mdview-release.p12` file. Losing the
key prevents future APKs from updating existing installations. Never commit either the secret or the
unencrypted keystore.

## Release workflow

`.github/workflows/build-apk.yml` always runs compilation, unit tests, lint, and an unsigned build.
When `MD_VIEW_SIGNING_SECRET` is configured, it also:

1. decodes and decrypts the versioned keystore;
2. checks its certificate against `certificate.sha256`;
3. signs and aligns the APK;
4. verifies the APK signature and certificate; and
5. uploads a clearly named signed artifact.

Current certificate SHA-256:

```text
1c1f1c541583a6fab2e7fb876950a853ed9adc64e9ce1ccf9228d3639778dac4
```
