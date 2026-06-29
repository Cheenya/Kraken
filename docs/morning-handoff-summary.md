# Kraken Android Review Handoff

Date: 2026-05-23

## 1. Current Branch

```text
codex/android-research-panel-report-viewer
```

No push or PR was performed in this cleanup pass.

## 2. Latest Local Commits

```text
bfd763a Polish Russian production UI copy
c195a40 Remove b-zero guided curve examples
8753754 Add native research backend benchmark
0869377 Add Kraken branded launch experience
4229663 Move QR UX to QR-first copy
b1bef91 Update QR implementation audit status
6f448e5 Add current research MVP scaffold
```

## 3. What Changed In This Stabilization Pass

- Added a current scaffold/status document for the Android research MVP.
- Replaced user-facing manual payload invite copy with QR-first product copy.
- Added branded Kraken launcher/splash/start assets from the Kraken Core direction.
- Added startup research attack logging; the log is shown from Research mode, not as a production crypto claim.
- Added native C++ Adamova Stage A diagnostic backend and Kotlin-vs-C++ benchmark support.
- Removed visible `b = 0` and singular teaching shortcuts from guided Research Panel examples.
- Switched the production-facing copy toward Russian and compact messenger-first wording.
- Added ignore rules for local screenshot churn and invalid local benchmark pull files.

## 4. Current Honest Status

Implemented locally:

- local identity creation;
- real invite QR rendering;
- camera QR scanner;
- QR lifecycle controls;
- three-step offline mutual QR handshake;
- pending/active relationship state;
- compact chat/contact/realm/settings UI;
- Research Panel with bundled diagnostic reports;
- native C++ research diagnostics and benchmark path.

Not implemented:

- real P2P message transport;
- socket/LAN/Bluetooth/Nearby runtime;
- production E2EE;
- Android Keystore production key handling;
- production signatures for invite/handshake proofs;
- public discovery, accounts, phone/email login or cloud sync.

Accurate product sentence:

```text
Kraken currently demonstrates local identity, QR trust establishment and research diagnostics.
It does not yet implement real P2P message delivery or production cryptography.
```

## 5. Device Validation Snapshot

Last successful install target:

```text
R5CY22X6MSB / SM-S938B
```

Commands used:

```bash
cd /tmp/kraken-android-research-report-viewer/app-android
./gradlew test
./gradlew assembleDebug
PATH="$HOME/Library/Android/sdk/platform-tools:$PATH" ./gradlew installDebug
```

Latest APK:

```text
/tmp/kraken-android-research-report-viewer/app-android/app/build/outputs/apk/debug/app-debug.apk
```

## 6. Visual QA Notes

Recent screenshots were captured under:

```text
artifacts/phone-screens/
```

Those screenshots are local QA artifacts and are ignored by default. Commit only curated screenshots with `git add -f` if they are needed as review evidence.

Known UI issues to review manually:

- splash/start should be compared against the Kraken Core reference on device;
- status and navigation bars are still visible, so the app is not fully immersive;
- start screen is now messenger-first, but the lockup should still be judged by eye;
- Research mode remains text-heavy and should stay behind Settings.

## 7. What To Review First

1. Splash/start screen on the physical phone.
2. Create Identity.
3. My QR.
4. QR scanner.
5. Offline QR handshake:
   - invite QR;
   - response QR;
   - final confirmation QR.
6. Chat screen after an active relationship.
7. Realms list and Realm Detail.
8. Settings -> Research mode.
9. Research guided examples and native benchmark card.

## 8. What Not To Claim

- Do not claim production security.
- Do not claim production cryptography is implemented.
- Do not claim real P2P messaging is ready.
- Do not claim the rational curve diagnostics prove modern finite-field ECC security.
- Do not present JSON payload exchange as the normal user workflow.

## 9. Artifact Policy

Tracked evidence/assets:

- `artifacts/brand-crops/*.png`
- `artifacts/research_backend_benchmark/backend_benchmark_from_device.md`

Ignored local churn:

- `artifacts/phone-screens/`
- `artifacts/screenshots/`
- invalid/raw local benchmark pulls such as `backend_benchmark.json` and `backend_benchmark.md`.

## 10. Recommended Next Step

Run one manual phone QA pass and fix only visible blockers:

```text
No new features. Fix splash/start alignment, QR flow copy, unreadable text, or broken navigation only.
```
