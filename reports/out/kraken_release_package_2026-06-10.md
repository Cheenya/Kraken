# Kraken Release Package

Report date: 2026-06-10.

Artifact build timestamp from local package metadata:
2026-06-08 10:27:00 MSK.

## Files

- APK:
  `artifacts/releases/20260608-kraken-release-0c37fb8/Kraken_release/Kraken_release.apk`
- ZIP:
  `artifacts/releases/20260608-kraken-release-0c37fb8/Kraken_release_2026-06-08_0c37fb8.zip`
- README:
  `artifacts/releases/20260608-kraken-release-0c37fb8/Kraken_release/README.txt`
- Metadata:
  `artifacts/releases/20260608-kraken-release-0c37fb8/Kraken_release/metadata.json`

## Package Metadata

| Field | Value |
| --- | --- |
| Package | `com.disser.kraken` |
| Version | `0.1.0` |
| Git | `codex/android-research-panel-report-viewer@0c37fb8` |
| Build type | `debug research demo` |
| Debuggable | `true` |
| minSdk | `26` |
| targetSdk | `35` |
| SHA-256 | `10118b7f73f1598c9e91d1de498fa200a506aa382cd660830210569ca0410a45` |
| Size | `19M` |

## Validation

- `./gradlew testDebugUnitTest`: passed.
- `pytest tests/test_android_policy_guards.py tests/test_lan_frame_codec.py tests/test_desktop_lan_relay.py`: 50 passed.
- `./gradlew clean assembleDebug`: passed.
- `apksigner verify --verbose --print-certs`: passed with APK Signature
  Scheme v2 and Android debug certificate.
- `apkanalyzer manifest`: `debuggable=true`, `applicationId=com.disser.kraken`,
  `versionName=0.1.0`, `minSdk=26`, `targetSdk=35`.
- `shasum -a 256 -c SHA256SUMS.txt`: passed.
- `unzip -t Kraken_release_2026-06-08_0c37fb8.zip`: passed.
- Smoke install of `Kraken_release.apk`: passed on Samsung `R5CY22X6MSB` and
  Xiaomi `d948ffd0`.

## Boundary

This is an installable research/demo APK for friend testing. It is not a
production secure messenger release. It does not claim production encryption,
production Keystore identity, signed QR proofs or full Wi-Fi Direct route
evidence.
