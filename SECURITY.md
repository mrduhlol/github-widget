# Security Policy

## Release Signing & Key Rotation

Older versions of this repository tracked `keystore/release.p12` with hardcoded credentials. That signing key was **exposed in Git history** and is **retired for all future releases**:

- `keystore/release.p12` has been removed from the working tree and keystores (`*.p12`, `*.jks`, `*.keystore`) are ignored in `.gitignore`.
- Because the old key remains in public Git history, **existing installs signed with that key still trust it** until they are uninstalled.
- Tagged releases in `.github/workflows/build-apk.yml` now require a new private signing key injected via GitHub Actions Secrets:
  - `RELEASE_KEYSTORE_BASE64`
  - `RELEASE_STORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`
- **User migration:** Because Android enforces signature continuity on updates, existing sideloaded installs signed with the retired repository key must be uninstalled once before installing a release signed with the new private key.

## Reporting a Vulnerability

Please report security issues privately via GitHub Security Advisories on this repository rather than opening a public issue.
