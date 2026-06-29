# Manual Review Guide

This guide is for morning review of the current Kraken branch. It focuses on verifying build health, installability, screenshots and product constraints.

## 1. Git State Commands

From repository root:

```bash
cd /tmp/kraken-android-research-report-viewer
git status
git log --oneline --decorate -50
git diff --stat
```

Useful branch comparison:

```bash
git diff --stat origin/chore/research-mvp-scaffold-v2...HEAD
```

## 2. Build Commands

From repository root:

```bash
python3 -m compileall .
.venv/bin/python -m pytest
```

Android checks:

```bash
cd /tmp/kraken-android-research-report-viewer/app-android
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

Notes:

- Use `.venv/bin/python -m pytest` as the reliable Python test command.
- System `python3 -m pytest` may fail if pytest is not installed in the system Python.
- `installDebug` requires an authorized emulator or physical device.

## 3. Emulator And Device Commands

List devices:

```bash
adb devices
```

Start the app after install:

```bash
adb shell monkey -p com.disser.kraken 1
```

Capture a screenshot of the current device screen:

```bash
adb exec-out screencap -p > screenshot.png
```

Repository helper script:

```bash
cd /tmp/kraken-android-research-report-viewer
./scripts/capture_android_screenshots.sh home
```

The script writes PNG files to:

```text
artifacts/screenshots/android/
```

If multiple devices are connected, pass `-s <serial>` to `adb`.

## 4. Screenshot Checklist

Capture portrait screenshots first. Landscape can wait unless the app visibly breaks.

- Home without demo data.
- Home after loading demo data.
- UI Lab entry in Settings.
- UI Lab main screen.
- Icon Lab.
- Home UX variants.
- Chat UX variants.
- Welcome.
- Create Identity.
- My QR.
- Import Invite.
- Contacts.
- Chat with active relationship.
- Chat with pending or blocked relationship.
- Realms.
- Pending Approvals.
- Channels.
- Mesh Status.
- Settings.
- Research.

For every screenshot, check:

- no text overflow;
- bottom navigation remains visible and readable;
- dark theme contrast is acceptable;
- disabled states are visually distinct;
- experimental screens are clearly labeled.

## 5. Manual Product Verification

Verify these flows in the app:

- No phone, email, login, password or account registration UI appears.
- No public discovery, public realm search, nearby realm search or public user directory appears.
- Identity creation accepts display name only.
- Created identity shows display name and fingerprint.
- Editing display name does not change fingerprint.
- My QR shows a scannable one-time invite QR and clearly says QR starts a handshake.
- Import Invite with another identity payload creates pending import/handshake only.
- QR/import does not create active membership automatically.
- QR response and final confirmation complete the offline mutual handshake.
- Contacts show pending, active, unlinked or blocked relationship states.
- Sending/composer is enabled only for `ACTIVE` relationship.
- Unlinked or blocked relationship requires new QR/invite and handshake.
- Demo realm creation works locally.
- Realm capacity and local realm state are visible.
- Pending approvals are local placeholders unless actual pending requests exist.
- Research screen says diagnostic-only and not production encryption.
- UI Lab variants are accessible from Settings and do not replace production screens silently.
- Demo data helper is clearly local/demo and reversible.

## 6. Known Limitations

### Crypto

- Identity key provider is a secure-random placeholder, not Android Keystore production storage.
- Safe crypto layer is abstraction-only.
- Fake/test crypto providers must remain test-only.
- Do not treat the prototype as production-secure.

### QR

- Real QR rendering and QR scanner import are implemented locally.
- Product review should treat QR as the primary user flow.
- JSON payloads may still exist internally for tests/debugging and must not be presented as user-facing exchange UX.
- Invite signatures are placeholder/null.
- Imported invite must remain pending until reciprocal offline QR handshake.
- The current offline flow is invite QR -> response QR -> final confirmation QR.

### LAN Transport

- LAN transport now has a prototype Direct LAN NSD + TCP implementation.
- `DebugLanTransportAdapter` still exists for isolated model tests, but it is no longer the only transport artifact.
- Runtime messaging now has an explicit Mesh diagnostics action to send/receive the local queue through the active transport.
- Manual Samsung/Xiaomi two-device LAN/Wi-Fi prototype delivery is summarized in
  `reports/out/two_device_delivery_evidence.md`; fresh post-cleanup UI capture
  is local under
  `artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/`.
- Repeatable automated message orchestration and latency/loss metrics are still
  missing.
- No public discovery or external server should exist.
- `INTERNET` permission is present and documented as local LAN socket usage only, not cloud/server networking.

### Simulator

- Delivery simulator exists in Python under `src/disser_messenger/mesh/`.
- It is not a production Android transport.
- Experiment plan exists, but experiments have not been run as a dissertation dataset.

### Demo Data

- Demo data is local-only and developer-facing through UI Lab.
- Demo data is for screenshots and walkthroughs, not protocol proof.
- Reset demo data should remove demo artifacts without wiping a real local identity.

### UI

- UI is still experimental in places.
- UI Lab variants are not final production UI.
- Screenshots are required before choosing the production direction.

## 7. What To Review First

Recommended order:

1. Build and test commands.
2. Install app on emulator or phone.
3. Open Settings and confirm UI Lab is easy to find.
4. Load demo data from UI Lab.
5. Capture Home, Icon Lab, Home variants and Chat variants.
6. Verify forbidden-scope UI does not appear.
7. Choose preferred Home and Chat direction.
8. Only after that, ask Codex to migrate selected variants into production UI.

## 8. Quick Failure Triage

If build fails:

```bash
cd /tmp/kraken-android-research-report-viewer/app-android
./gradlew test --stacktrace
./gradlew assembleDebug --stacktrace
```

If app does not start:

```bash
adb devices
adb shell monkey -p com.disser.kraken 1
adb logcat -d | tail -200
```

If UI Lab is missing:

- check Settings screen for Developer / UI Lab;
- check `KrakenRoute.UiLab`;
- check `KrakenNavHost` has a `ui-lab` route.

If demo data looks unsafe:

- do not use it for screenshots;
- inspect `DemoDataSeeder`;
- reset app data from Android settings or reinstall.
