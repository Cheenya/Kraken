# Windows port audit

Date: 2026-06-24

Reference: `app-macos/` desktop harness and Android transport models.

## Covered

- Local identity, relationships, messages, route snapshots and Adamova admission
  state.
- `RelationshipState.ACTIVE` message gate.
- QR normalization for raw JSON, `kraken:`, `kraken://qr`, `intent://qr` and
  `https://kraken.local/qr?v=2&z=d&p=...`.
- QR import for invite, response and confirmation payloads into active
  relationships.
- Android-compatible LAN envelope codec with 4-byte big-endian length prefix and
  ACK byte `0x06`.
- Local Windows LAN TCP sender/listener for loopback and LAN endpoint tests.
- Android-compatible BLE chunk framing and reassembly.
- Packet policy checks for expiry, zero TTL and duplicate packet IDs.
- LAN/BLE timeline reducers for outbound result and unknown inbound peer
  handling.
- Durable JSON state store and durable outbox retry records with preserved
  `message_id`.
- Evidence export with an explicit claim boundary.

## Not Claimed

- Native Android Wi-Fi Direct parity. Android remains the source of truth for
  Wi-Fi Direct.
- Production cryptographic security.
- Production BLE delivery reliability on Windows hardware.
- Windows release artifact in GitHub Releases. Build script exists, but the
  `.exe` must be produced and checked on Windows.

## Verification

```bash
cd app-windows
python3 -m unittest discover -s tests
python3 -m compileall kraken_windows tests
python3 -m kraken_windows --smoke
```

Expected result: 10 tests pass, compileall succeeds, and smoke prints
`Kraken Windows smoke OK`.
