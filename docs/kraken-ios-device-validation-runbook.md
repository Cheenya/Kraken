# Kraken iOS Device Validation Runbook

Status: prepared harness. This document does not claim physical validation has
passed.

Use this after the iOS Simulator/UI work is green and physical devices are
available.

## Prepare Evidence Packet

```bash
python3 app-ios/Scripts/prepare_ios_device_validation.py \
  --label physical-run \
  --require-ios-count 2 \
  --require-android-count 1
```

The script writes a timestamped packet under `artifacts/ios-device-validation/`
with:

- `evidence.json`: device inventory, command output and gate template;
- `runbook.md`: physical run instructions and pass criteria.

The packet is intentionally local artifact evidence. Do not mark gates complete
until `evidence.json` is filled with real run paths, timestamps and verdicts.

For readiness audit purposes a physical gate is complete only when its gate
entry has:

- `status`: `passed`
- `verifiedAtUtc`: an ISO-8601 timestamp for the physical run
- `artifactPaths`: non-empty repo-relative paths that exist in this checkout and
  point to screenshots, logs or exported evidence JSON
- `verdictNotes`: non-empty notes tying those artifacts to the pass criteria

The iOS readiness audit keeps `physical-device-gates` pending until every
required gate has `status: "passed"`, valid `verifiedAtUtc`, existing
`artifactPaths` and `verdictNotes`.

## Gates

1. `ios-multipeer-two-device`: two real iPhone/iPad devices validate MultipeerConnectivity discovery,
   trusted message delivery and ACK.
2. `android-ios-qr-handshake`: Android invite -> iOS response -> Android confirmation activates the same
   relationship on physical Android and iOS devices.
3. `android-ios-packet-negative-policy`: Android/iOS packet negative cases reject expired, duplicate, wrong-recipient,
   wrong-sender and wrong-relationship packets before timeline mutation.
4. `ios-persistence-lifecycle`: iOS persistence survives force quit and relaunch for identity,
   pending/active relationships, outbox and diagnostics.
5. `ios-physical-visual-review`: physical visual review covers launch, welcome, bottom tabs, QR states,
   long-screen scrolling and iPad layout if iPad support is claimed.

## Boundary

iOS uses local Apple transport via MultipeerConnectivity. Physical iOS
evidence must not be described as Android Wi-Fi Direct parity.
