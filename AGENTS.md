# AGENTS.md

## Cursor Cloud specific instructions

Secure File Manager is an Android (Kotlin) file-manager app (single `app` module).
Standard commands and architecture live in `README.md`, `docs/`, `CONTRIBUTING.md`,
and `.github/workflows/ci.yml`.

The Cloud VM is already provisioned: OpenJDK 17 and 21, the Android SDK at
`$HOME/android-sdk` (env vars exported in `~/.bashrc`), and a generated
`local.properties` (gitignored) with `sdk.dir`.

- **JDK: use 17.** CI builds this app with Temurin 17. Run Gradle with
  `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
- **Build (debug APK):** `./gradlew :app:assembleDebug -Psecurefilemanager.devAbi=arm64-v8a --no-daemon`
  → `app/build/outputs/apk/debug/secure-file-manager-debug.apk` (applicationId has a
  `.debug` suffix; label "Secure File Manager Beta").
- **Unit tests:** `./gradlew test --no-daemon` (fast).
- **Lint:** `./gradlew :app:lintDebug --no-daemon`. Note the repo currently has
  pre-existing lint findings (117 errors / 228 warnings), so `lintDebug` aborts with a
  non-zero exit by design; the reports under `app/build/reports/lint-results-debug.*`
  are still generated. This is lint working, not an environment failure.

Non-obvious caveats:
- `compileSdk`/`targetSdk` are **37**; installed SDK platforms are `android-37.0` /
  `android-37.1` (no plain `platforms;android-37`). AGP 9.1.1 handles this — do not change it.
- Headless VM with **no `/dev/kvm`** → no Android emulator. Verify via unit tests and by
  inspecting the built APK (`$ANDROID_SDK_ROOT/build-tools/37.0.0/aapt dump badging <apk>`).
