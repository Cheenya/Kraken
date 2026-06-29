# Android P2P Smoke Report

Implementation baseline before this report refresh: `f4a03cf`

## Status

Direct LAN NSD + TCP implementation exists, builds, and is wired to Mesh
diagnostics queue processing. This historical May 30 report was originally a
one-device smoke checklist; it is superseded for two-phone delivery status by
`reports/out/two_device_delivery_evidence.md`, which records the June 1
Samsung/Xiaomi manual LAN/Wi-Fi smoke.

## Automated Checks

- `./gradlew test`: passed.
- `./gradlew assembleDebug`: passed.
- `./gradlew installDebug`: passed on Samsung `SM-S938B / R5CY22X6MSB`.
- `git diff --check`: passed.
- Packet frame codec tests: passed.
- Permission guard: `CAMERA` and local-LAN `INTERNET`; no contacts/phone/location/Bluetooth/account permissions.
- Runtime queue sync tests: passed.
- Loopback/two-node service delivery and receipt tests: passed.
- Unknown/pending/blocked/wrong-recipient rejection tests: passed.
- Simulated relay tests: passed with relay disabled by default.

## One-Device UI Evidence

Fresh screenshots were captured from Samsung `SM-S938B / R5CY22X6MSB` for:

- branded start screen;
- Overview without duplicate top title;
- merged Chats/Contacts screen;
- Settings diagnostic tools;
- Research Panel / computational experiment screens;
- Mesh diagnostics / local transport status.

Curated copies for dissertation are prepared separately. Raw live-audit screenshots
remain local because the directory also contains QA-only and accidental/private
captures.

## Manual Smoke Plan

1. Install APK on two Android devices connected to the same Wi-Fi/LAN.
2. Create identity on both devices.
3. Complete offline QR handshake until relationship is `ACTIVE`.
4. Enable Mesh diagnostics.
5. Verify LAN peer discovery is visible only as transport diagnostics.
6. Send a chat message from A to B.
7. Verify B receives message.
8. Verify B sends receipt.
9. Verify A shows delivered/acked state.
10. Replay duplicate packet if debug tooling is available; verify duplicate does not duplicate message.

## Current Limitation

This file remains a historical smoke checklist and automated-build evidence
record. Use `reports/out/two_device_delivery_evidence.md` for current manual
two-phone delivery evidence.

## Evidence Scope

Local P2P transport smoke only. Account/login, public discovery and cloud relay are outside this report.
