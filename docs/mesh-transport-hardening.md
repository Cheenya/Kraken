# Mesh Transport Hardening

Status: research MVP hardening layer. This is not a production security review.

## Implemented Guards

- persistent packet stores have bounded limits;
- duplicate packets are rejected by `packetId`;
- expired packets are rejected;
- TTL exhausted packets are rejected for forwarding;
- retry policy uses bounded exponential backoff;
- rate-limit helper prevents unbounded send loops;
- malformed frame size is isolated before processing;
- trust gate rejects unknown, pending, blocked/unlinked and wrong-recipient packets.

## User-Visible Error States

The app should report safe statuses, not raw packet payloads:

- peer unavailable;
- packet expired;
- rejected by trust gate;
- transport disabled;
- malformed inbound packet.

## Lifecycle Rules

1. Outbox stays persisted through app/service restart.
2. Transport restart resumes eligible queued packets only after explicit mesh enable.
3. Expired queued packet becomes failed/expired.
4. Corrupted inbound packet must not crash the app.
5. Blocked peer packets are dropped by trust gate.
6. Rate limits apply before transport send.

## Remaining Manual Work

- repeatable LAN latency/loss metrics and pass/fail history;
- route-specific BLE send/rejection smoke with exported counters;
- network-change behavior on actual Android Wi-Fi;
- background execution/battery observation;
- updated Mesh diagnostics screenshot audit after route-aware UI changes.

## Current Physical Evidence

- Manual Samsung/Xiaomi LAN/Wi-Fi prototype delivery evidence is summarized in
  `reports/out/two_device_delivery_evidence.md`.
- Fresh post-cleanup chat-state capture is stored locally under
  `artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/`.
- Manual Samsung/Xiaomi BLE direct-route evidence is summarized in
  `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`.
- This evidence proves UI-visible prototype delivery state, not production
  reliability, BLE route reliability/latency or production security.
