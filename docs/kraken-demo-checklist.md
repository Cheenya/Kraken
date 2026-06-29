# Kraken Demo Checklist

This checklist is for a local dissertation/demo run. It must not be presented as a production security demonstration.

## Pre-Demo Setup

- Confirm the branch and APK build are the intended review version.
- Run `cd app-android && ./gradlew test && ./gradlew assembleDebug`.
- Keep the demo offline/local unless a later LAN debug phase is explicitly being reviewed.
- Prepare one emulator or Android device per participant role.
- Do not use real personal accounts, phone numbers, email addresses or external servers.

## Device Or Emulator Setup

- Install Android Studio, Android SDK platform 35, NDK and CMake.
- Start emulator or connect physical device.
- Confirm authorization:

```bash
adb devices -l
```

- If using a phone, unlock it and approve the USB debugging prompt.

## Install APK

From `app-android/`:

```bash
./gradlew installDebug
```

If install fails, capture:

- `adb devices -l`
- `./gradlew assembleDebug` result
- device Android version

## Demo Flow

### 1. Launch

- Open Kraken.
- Confirm dark theme and invite-only prototype wording.
- Confirm there is no phone/email/login/account registration path.

### 2. Create Identity

- Open `Create Identity`.
- Enter a display name only.
- Create local identity.
- Confirm Home shows display name and fingerprint.
- State clearly: display name is a label; fingerprint/public key is the identity.

### 3. Show Invite QR

- Open `My QR`.
- Show the real one-time invite QR.
- Use QR as the normal user-facing exchange path.
- State clearly: QR starts a handshake and does not grant membership directly.

### 4. Scan Invite QR

- On a second device/emulator, create a local identity.
- Open `Import Invite` -> `Scan QR`.
- Scan the invite QR from the first device.
- Confirm the result is pending import or pending handshake, not active membership.

### 5. Pending Relationship

- Open `Contacts`.
- Show pending relationship state badge.
- Confirm chat composer is disabled while pending.

### 6. Offline Mutual QR Handshake

- On the responder device, open the pending contact and show response QR.
- On the inviter device, scan response QR and show final confirmation QR.
- On the responder device, scan final confirmation QR.
- Confirm relationship becomes `ACTIVE` only after reciprocal confirmation.
- Explain that this is an offline prototype handshake, not production cryptographic verification.

### 7. Active Relationship

- Open `Chat`.
- Confirm composer is enabled only for `ACTIVE`.
- Do not claim real message delivery is implemented.

### 8. Unlink

- End relationship with a neutral reason.
- Confirm chat becomes disabled.
- Explain that reactivation requires a new invite and handshake.

### 9. Demo Realm

- Open `Realms`.
- Create local demo realm.
- Show local state, capacity, policy and membership certificate status.
- Pause, resume, archive or leave only if those actions are part of the planned run.

### 10. Message Status Model

- Open `Chat` or the relevant message status view.
- Show local/prototype statuses only if the current build implements them.
- Explain read receipts are optional and tombstone deletion is best-effort.

### 11. Delivery Simulator

- Show the local delivery simulator or demo notes:
  A creates packet, A meets B, B meets C, C meets Y, Y receives, receipt returns, tombstone clears honest relay buffers.
- Explain transport and relay behavior as prototype/research-only until the P2P phase is under review.

### 12. Research Panel

- Open `Research`.
- Show diagnostic-only warning.
- Run or describe curve diagnostic placeholders.
- State clearly: the dissertation investigates measurable benefit in a research workflow; this is not production encryption.

### 13. Courier Score

- Open `Mesh Status` or Home summary.
- Show local/demo contribution summary if available.
- Explain there is no GPS, no leaderboard and no route/recipient detail.

## Honest Limitations To State

- QR rendering and camera QR scanning are implemented locally; invite and handshake proofs are still prototype-only.
- Network transport is interface/debug-prototype level until the explicit LAN P2P phase is reviewed.
- Crypto is abstraction-only; no production crypto implementation is claimed.
- Local storage is MVP-level and not Android Keystore-backed private key storage yet.
- Message sending is not production delivery.
- Tombstones only instruct honest nodes to delete; malicious devices can ignore them.
- Root governance cannot decrypt messages.

## Post-Demo Capture

- Take screenshots of Welcome, Home, Create Identity, My QR, QR Scanner, Contacts pending/active, Chat, Realms, Mesh Status, Settings and Research.
- Save build command output and APK path.
- Record any UI overflow, warning, crash or confusing wording as backlog items.
