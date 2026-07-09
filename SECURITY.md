# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 0.1.x-beta | Yes |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

1. Use **GitHub Private Vulnerability Reporting** (Security → Advisories → Report a vulnerability) on this repository.
2. Or email the maintainers with:
   - Affected version(s)
   - Steps to reproduce
   - Impact assessment (file exposure, encryption bypass, app-lock bypass, etc.)

We aim to acknowledge reports within **72 hours** and provide a fix or mitigation timeline within **14 days** for confirmed issues.

## Scope

In scope:

- Secure File Manager application code in this repository
- Gradle build scripts and GitHub Actions workflows
- File hiding, encryption, and Zip encryption features
- App lock (password/biometric authentication)

Out of scope:

- Third-party Android OS bugs
- **Rooted devices** — this app's file-hiding and app-lock features provide no protection against a rooted device or an attacker with root/physical access (see [FAQ](https://github.com/Secure-File-Manager/Secure-File-Manager/wiki/Frequently-Asked-Questions))
- Weak user-chosen passwords/PINs
- Attacks requiring physical device access with an unlocked screen

## Security Design

- **No hardcoded secrets**: signing keys, SDK paths, and BrowserStack credentials are never committed (`keystore.properties`, `secrets.properties`, `local.properties` are gitignored; see `.example` files).
- **Encryption**: file/Zip encryption via [Argon2Kt](https://github.com/lambdapioneer/argon2kt) (key derivation) and [Zip4j](https://github.com/srikanth-lingala/zip4j).
- **App lock**: password or biometric authentication gate, `FLAG_SECURE` screenshot blocking (optional).
- **Release hardening**: R8 minification, resource shrinking, privacy-safe logging (see `gradle/privacy-logging.pro`).
- **CI signing**: release keystores are provided via GitHub Actions encrypted secrets only (`RELEASE_KEYSTORE_*`).
- **Backup disabled**: sensitive data excluded from Android auto-backup.

## Recommended Repository Settings

Enable these free GitHub security features under **Settings → Code security**:

- Dependabot alerts
- Dependabot security updates
- Secret scanning + push protection
- Code scanning (CodeQL workflow included in `.github/workflows/ci.yml`)
- Private vulnerability reporting

## CI Secrets (maintainers only)

| Secret | Description |
|--------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.keystore` or `.jks` file |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias (default: `securefilemanager`) |
| `RELEASE_KEY_PASSWORD` | Key password (optional if same as keystore) |

Generate a keystore locally with `scripts/generate-release-keystore.sh` — never commit the output.
