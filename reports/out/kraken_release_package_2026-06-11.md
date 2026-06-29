# Kraken Release Package

Report date: 2026-06-11.

Artifact build timestamp from local package metadata:
2026-06-11 12:22:10 MSK.

## Files

- APK:
  `artifacts/releases/20260611-kraken-release-05ba28d/Kraken_release/Kraken_release.apk`
- ZIP:
  `artifacts/releases/20260611-kraken-release-05ba28d/Kraken_release_2026-06-11_05ba28d.zip`
- README:
  `artifacts/releases/20260611-kraken-release-05ba28d/Kraken_release/README.txt`
- Metadata:
  `artifacts/releases/20260611-kraken-release-05ba28d/Kraken_release/metadata.json`

## Package Metadata

| Field | Value |
| --- | --- |
| Package | `com.disser.kraken` |
| Version | `0.1.0` |
| Git | `codex/android-research-panel-report-viewer@05ba28d` |
| Build type | `debug research demo` |
| Debuggable | `true` |
| minSdk | `26` |
| targetSdk | `35` |
| SHA-256 | `97f4a65420c173cb48f1b79950edfe89c4c8fe3d9c1682d4f86d8c045b7e341a` |
| Size | `21M` |

## Validation

- `./gradlew :app:testDebugUnitTest --tests com.disser.kraken.mesh.LanPermissionGuardTest --tests com.disser.kraken.mesh.WifiDirectDnsSdTest --tests com.disser.kraken.mesh.MeshServiceTest`: passed.
- `pytest tests/test_android_policy_guards.py -q -p no:cacheprovider`: 53 passed.
- `./gradlew :app:lintDebug`: passed.
- `./gradlew :app:assembleDebug`: passed.
- `apksigner verify --verbose --print-certs`: passed with APK Signature
  Scheme v2 and Android debug certificate.
- `apkanalyzer manifest`: `debuggable=true`, `applicationId=com.disser.kraken`,
  `versionName=0.1.0`, `minSdk=26`, `targetSdk=35`.
- `shasum -a 256 -c SHA256SUMS.txt`: passed.
- `unzip -t Kraken_release_2026-06-11_05ba28d.zip`: passed.
- Smoke install and launch of `Kraken_release.apk`: passed on Samsung
  `R5CY22X6MSB`.
- `git diff --check`: passed.

## Boundary

This is an installable research/demo APK for friend testing. It is not a
production secure messenger release. It does not claim production encryption,
production Keystore identity, signed QR proofs, production security review or
complete Wi-Fi Direct route evidence.

Release-like Gradle builds remain intentionally blocked until the production
crypto, identity, signed QR, release-signing and review gates are implemented.
