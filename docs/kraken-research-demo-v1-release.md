# Kraken Research Demo v1 Release Checklist

Status: release preparation, not final tagged release.

## Current Branch

- Branch: `android-research-panel-report-viewer`
- APK path: `app-android/app/build/outputs/apk/debug/app-debug.apk`

## Implemented For Demo Candidate

- local identity;
- QR-first invite UX;
- camera QR scanner;
- offline mutual QR handshake;
- local message domain;
- packet envelope and bounded stores;
- loopback trust-gated delivery pipeline;
- mesh diagnostics and metrics;
- direct LAN NSD + TCP transport implementation;
- QR trust-gating hardening;
- simulated store-and-forward relay, disabled by default unless explicit prototype mode;
- crypto envelope interfaces and non-production prototype crypto guard;
- transport hardening helpers;
- multi-transport roadmap;
- P2P research evidence report stubs/checklists.

## Required Checks Before Tag `kraken-research-demo-v1`

- Android unit tests pass.
- `assembleDebug` passes.
- `installDebug` passes on device.
- Two-device QR handshake tested.
- Direct LAN P2P message tested.
- Delivery receipt tested.
- Screenshot pack captured:
  - start screen;
  - My QR;
  - scanner;
  - contacts pending/active;
  - chat with message statuses;
  - Mesh diagnostics;
  - Research mode;
  - settings/limitations.
- `reports/out/two_device_delivery_evidence.md` filled with real device data.
- Repeatable two-device smoke capture helper prepared:
  `scripts/capture_two_phone_smoke_evidence.sh`.
- Fresh post-cleanup capture bundle produced with that helper.
  - `artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/`

## Known Limitations

- Production encryption is not implemented.
- Android Keystore production key storage is not implemented.
- QR proofs and packet proofs are placeholders.
- LAN discovery is not trust.
- Relay is simulated and disabled by default.
- No account/login/phone/email identity.
- No public discovery.
- No cloud/server relay.

## Tag Policy

Do not create `kraken-research-demo-v1` without explicit user approval and a
final release pass. The manual two-device delivery state is now repeatably
captured at the UI level after the cleanup/transport changes; this means the
evidence is repeatable as a capture bundle, not as fully automated message
orchestration. The current state is a demo release candidate with manual
prototype evidence, not final release evidence and not production release
evidence. In short: demo release candidate with manual prototype evidence, not final.
