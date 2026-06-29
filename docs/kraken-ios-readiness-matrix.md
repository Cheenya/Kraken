# Kraken iOS Readiness Matrix

Date: 2026-06-24

Branch: `codex/kraken-ios`

This matrix separates proven Simulator/code/docs readiness from physical-device
gates. It must not be used to claim real-device completion.

Generate the current table with:

```bash
python3 app-ios/Scripts/audit_ios_port_readiness.py
```

Check that this Markdown matrix is not stale:

```bash
python3 app-ios/Scripts/audit_ios_port_readiness.py \
  --fail-on-missing-proven \
  --check-matrix-doc docs/kraken-ios-readiness-matrix.md
```

## Current Matrix

| Requirement | Status | Evidence | Note |
|---|---|---|---|
| `ui-smoke-evidence` | `proven` | `artifacts/ios-smoke/current-manifest.json`, `app-ios/Scripts/verify_ios_smoke.py` | Current Simulator evidence lists 17 screenshots across native iPhone, compact iPhone and iPad, with pixel guards against early `LaunchGlyph` frames. |
| `welcome-and-copy` | `proven` | `app-ios/KrakenIOS/Views/KrakenIOSRootView.swift`, `app-android/app/src/main/java/com/disser/kraken/ui/screens/WelcomeScreen.kt`, `README.md` | Welcome screen uses `StartBackground`, `LaunchMark`, `K R A K E N`, `I O S` and Android-backed `ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО` source-of-truth copy; first post-welcome surface is `Главная` with Android home structure. |
| `launch-screen-and-icons` | `proven` | `app-ios/KrakenIOS/Info.plist`, `AppIcon.appiconset`, `artifacts/ios-smoke/kraken-ios-launch-reference.png` | Launch proof is structural plus generated reference, not physical launch timing evidence. |
| `native-ios-ui-and-layout` | `proven` | `app-ios/KrakenIOS/Views/KrakenIOSRootView.swift` | Native tabs, Android-backed home overview, Liquid Glass grouping, inline titles and wide-screen constraints are guarded. |
| `qr-confirmation-binding` | `proven` | `KrakenModels.swift`, `KrakenIOSSimulator.swift`, `KrakenIOSStoreTests.swift` | Confirmation activation binds invite id, generated response id and local responder fingerprint. |
| `packet-policy-before-timeline` | `proven` | `KrakenIOSStore.swift`, `IOSNearbyTransport.swift`, `KrakenIOSStoreTests.swift` | Live packet receive validates packet policy and relationship binding before timeline mutation; outbound sends target the selected connected peer instead of broadcasting to every Multipeer peer. |
| `physical-device-gates` | `pending_external_devices` | `docs/kraken-ios-device-validation-runbook.md`, `app-ios/Scripts/prepare_ios_device_validation.py`, latest `artifacts/ios-device-validation/*/evidence.json` | Requires real iPhone/iPad MultipeerConnectivity and Android/iOS QR/packet interop evidence. Every required gate must be `passed` with valid `verifiedAtUtc`, existing `artifactPaths` and non-empty `verdictNotes` before this can close. |

## Completion Boundary

The current work proves Simulator UI/UX, source-of-truth copy, launch/icon
configuration and core technical safeguards. It does not prove:

- physical iPhone/iPad MultipeerConnectivity;
- Android/iOS physical QR invite -> response -> confirmation interop;
- Android/iOS physical packet-envelope negative cases;
- physical iPad review.
