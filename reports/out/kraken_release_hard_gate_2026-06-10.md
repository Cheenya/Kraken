# Kraken Release Hard Gate

Date: 2026-06-10.

## What Changed

Added Gradle task `krakenReleasePrototypeGate` and wired it into release-like
tasks:

- `assembleRelease`;
- `bundleRelease`;
- `packageRelease`.

The gate fails release-like builds while the app still uses research-only
prototype security components:

- `PrototypeNoSecurityPacketCrypto`;
- placeholder identity;
- unsigned QR handshake.

Debug builds remain available for friend testing through `assembleDebug`.

## Build Flags

| Build type | `KRAKEN_RESEARCH_DEMO` | `KRAKEN_RELEASE_SECURITY_READY` |
| --- | --- | --- |
| `debug` | `true` | `false` |
| `release` | `false` | `false` |

## Validation

- `./gradlew assembleDebug`: passed.
- `pytest tests/test_android_policy_guards.py`: 41 passed.
- `./gradlew assembleRelease`: failed as expected on
  `:app:krakenReleasePrototypeGate`.

Expected failure text:

`Kraken release/prod build is blocked: PrototypeNoSecurityPacketCrypto, placeholder identity and unsigned QR handshake are still research-only. Use assembleDebug for friend testing, or implement the production readiness roadmap first.`

## Boundary

This closes only the minimal Gradle release-like hard gate gap. It prevents
accidentally presenting the current research prototype as a release/prod build.

It does not implement production crypto, Android Keystore identity, signed QR
handshake, encrypted packet envelope, release signing or security review.
