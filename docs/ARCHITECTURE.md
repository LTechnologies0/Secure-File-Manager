# Secure File Manager — Architecture

Secure File Manager is a **single-module** Android app (`:app`), based on [Simple File Manager](https://github.com/SimpleMobileTools/Simple-File-Manager) and rebranded/extended as part of the [OnionPhone](https://onionphone.org) app family. It is undergoing a gradual Java → Kotlin conversion; new code is written in Kotlin.

## Package layout

All source lives under `ltechnologies.onionphone.securefilemanager`:

| Package | Role |
|---------|------|
| `activities` | Screens: file browser, viewers, settings, onboarding, app lock |
| `adapters` | RecyclerView adapters for file lists, storage picker, etc. |
| `dialogs` | Modal dialogs (rename, compress, password prompts, remote connect) |
| `fragments` | Reusable UI fragments (grid/list views, tab pages) |
| `helpers` | Cross-cutting utilities: constants, config, `PrivacyLog`/`DebugTrace` logging, permission helpers |
| `interfaces` | Callback and listener contracts |
| `models` | Data classes: `FileDirItem`, storage entries, remote server configs |
| `observers` | `FileObserver`/content observer wrappers for live directory updates |
| `openpgp` | OpenPGP-related file handling |
| `providers` | `ContentProvider` / `DocumentsProvider` implementations |
| `receivers` | `BroadcastReceiver`s (boot, media scan, etc.) |
| `services` | Background services (copy/move/compress operations, remote transfer) |
| `storage` | Local + remote (SFTP/FTP) storage abstractions |
| `transfer` | File transfer engine (copy, move, compress/decompress) |
| `viewers` | Media/text/document viewers |
| `views` | Custom Views |

## Key features and where they live

| Feature | Primary location |
|---------|-------------------|
| File hiding | `helpers/` config flags + file naming convention (`.` prefix / hidden marker) |
| File/Zip encryption | `transfer/` + [Argon2Kt](https://github.com/lambdapioneer/argon2kt) key derivation, [Zip4j](https://github.com/srikanth-lingala/zip4j) for archive read/write |
| App lock | `activities/` authentication activity — password or biometric (`androidx.biometric`) |
| Checksums (MD5/SHA1/SHA256/SHA512) | `helpers/` hashing utilities |
| Remote file access (SFTP) | `storage/` + [sshj](https://github.com/hierynomus/sshj) |
| Thumbnails | [Glide](https://github.com/bumptech/glide) with configurable cache clearing |
| Open-source license attribution | [AboutLibraries](https://github.com/mikepenz/AboutLibraries) |

## Signing and CI

- `gradle/abi-release.gradle` — per-ABI release splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`)
- `gradle/release-signing.gradle` — resolves signing credentials from `keystore.properties` (local) or `RELEASE_KEYSTORE_*` environment variables (CI); unsigned if neither is present
- `gradle/privacy-logging.pro` — strips `DebugAgentLog`/`SessionLog`/`DebugTrace` verbose logging from release builds
- `.github/workflows/release.yml` — builds and signs all four ABIs, publishes to GitHub Releases with SHA-256 checksums

## Upstream lineage

This is a maintained fork of [SimpleMobileTools/Simple-File-Manager](https://github.com/SimpleMobileTools/Simple-File-Manager) via [Secure-File-Manager/Secure-File-Manager](https://github.com/Secure-File-Manager/Secure-File-Manager). This repository (`LTechnologies0/Secure-File-Manager`) tracks the OnionPhone-branded, Kotlin-converted continuation.
