# Kraken Android

Android-first Kotlin/Compose prototype for Kraken.

Kraken is an invite-only dissertation prototype. It has no account registration, no phone/email login, no public discovery and no production crypto claim.

## Requirements

- JDK 17.
- Android Studio with Android SDK platform 35.
- Android NDK and CMake installed through Android Studio SDK Manager.
- A running emulator or authorized physical device for install tests.

On macOS, `ANDROID_HOME` commonly points to:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

Check toolchain and device state:

```bash
java -version
./gradlew --version
adb devices -l
```

## Build

From this directory:

```bash
./gradlew test
./gradlew assembleDebug
```

The debug APK is written under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

With an emulator or authorized Android device connected:

```bash
./gradlew installDebug
```

If `adb devices -l` shows `unauthorized`, unlock the phone and approve the ADB authorization dialog.

## Screenshots

Emulator screenshot from command line:

```bash
adb exec-out screencap -p > kraken-screen.png
```

For physical devices, keep the device awake and unlocked before running screenshot or install commands.

## Current Feature Status

- Android-first app shell with dark Material 3 theme.
- Local identity creation using display name only.
- SecureRandom-backed placeholder identity key provider.
- Fingerprint display derived from public key bytes.
- Real invite QR rendering and camera QR scanning.
- One-time invite lifecycle controls for regenerate/revoke states.
- Offline mutual QR handshake using invite, response and confirmation QR payloads.
- Relationship state machine with pending, active, unlink and blocked states.
- Chat composer is gated by `ACTIVE` relationships.
- Realm, membership certificate, invite edge, policy and capacity models.
- Pending approval, delivery simulator, packet buffer, receipts/tombstones, safe crypto interfaces, battery policy, Courier Score, moderation, channels, small groups and research panel MVP models.
- Local-only demo data helper for screenshot review.
- CMake/NDK native backend for accelerated rational-curve torsion diagnostics and benchmark comparison.
- Bundled Research Panel reports for rational elliptic curve diagnostics.

## Storage

- MVP persistence uses SharedPreferences-backed stores.
- Store names and keys are centralized in `KrakenStorageKeys`.
- Stores write a schema version placeholder for future local migrations.
- No server storage.
- No cloud sync.
- No account recovery.
- `privateKeyReference` is not production non-exportable key storage.
- Future hardening should migrate private key handling to Android Keystore.

## Native Library Compatibility

The native library currently exposes accelerated rational-curve torsion diagnostics used by the experimental crypto-profile admission policy, plus Kotlin-vs-C++ benchmark support. It still contains no protocol runtime, production cryptography, networking or message delivery logic. CMake links the native library with 16 KB page-size compatible alignment where supported by the Android toolchain.

## Current Limitations

- Product UX is moving toward QR-only. JSON payloads may still exist as internal/debug/test artifacts and should not be presented as the primary user flow.
- LAN NSD/TCP exists as a local prototype transport and opens local sockets when mesh is enabled or a send/sync flow starts.
- Manual two-phone LAN NSD/TCP over local Wi-Fi prototype message exchange and receipt-level delivered UI evidence exists in `../reports/out/two_device_delivery_evidence.md`; treat it as prototype delivery evidence, not Wi-Fi Direct, production reliability or production security proof.
- Safe crypto is abstraction-only; no production encryption implementation.
- Messages are local-first and can be queued for the LAN prototype, but payload/signature/encryption are still prototype-only.
- Offline QR handshake uses prototype proof placeholders, not production signatures.
- No cloud/server relay is configured; the Android `INTERNET` permission is used for local LAN sockets only.
- Android Gradle Plugin warnings may remain for compatibility flags required by the current AGP/Kotlin plugin combination.
