# Mac Inline Relay ADB-Forward Demo

Date: `2026-06-08`.
Mode: `normal`.
Transport profile: `lan-only`.

## Result

Bounded Android A -> Mac -> Android B endpoint delivery passed.

This run proves a bounded end-to-end debug path: Android A sent a Kraken LAN
frame to the Mac inline relay harness, the relay forwarded that frame to an
Android B TCP listener through `adb forward`, and Android B accepted the packet
into the app message store.

| Step | Evidence | Status |
| --- | --- | --- |
| Xiaomi target listener | `192.168.0.121:38891`, `accepted_connections=1`, `inbound_packets=1` after the run | passed |
| ADB forward target | Host `127.0.0.1:54035` forwarded to Xiaomi `tcp:38891` | passed |
| Samsung lan-only sender | `manual-peer-added`, selected route `lan-nsd-tcp`, enabled modes `lan-nsd-tcp` | passed |
| Android A -> Mac relay | Relay received 1 frame from `192.168.0.201:52400` | passed |
| Mac relay -> Android B endpoint | Relay forward attempt to `127.0.0.1:54035` succeeded | passed |
| Frame identity | sender `3C4E D5BA 9DB8 8F9B`, recipient `B42B 3068 934E F618`, packet `packet-9bc33267-1090-44f8-9a89-5cf9db047849` | passed |
| Android B app store | Xiaomi message store contains `message-38306df1-3ba1-4144-9ddc-bd4be9ed911a` as `INCOMING` / `DELIVERED_TO_PEER` with body `inline relay direct normal 0953` | passed |
| Android B seen store | Xiaomi `seen_packet_ids` contains `packet-9bc33267-1090-44f8-9a89-5cf9db047849` | passed |
| Return receipt path | Xiaomi queued receipt `packet-56c00d84-0ad0-4fdc-beb8-9f9f74f39619` with `last_error=UNKNOWN_PEER` | open |

## Artifacts

- `artifacts/debug-route-evidence/20260608-095257-xiaomi-lan-only-target-direct-send/manifest.json`
- `artifacts/debug-route-evidence/20260608-095340-inline-relay-normal-adb-forward-direct-send-samsung/manifest.json`
- `artifacts/desktop-relay-inline/20260608-095344/android_inline_lan_relay.json`
- `artifacts/debug-route-evidence/20260608-095824-inline-relay-normal-adb-forward-direct-xiaomi-sync-after/manifest.json`

## Boundary

This closes only
`android_a_to_mac_to_android_b_debug_endpoint_delivery_via_adb_forward`.
It does not close physical Wi-Fi Mac -> Android B forwarding, drop/duplicate
and tamper modes, full bidirectional receipt delivery, cryptographic MITM
resistance, production relay security, or Wi-Fi Direct route evidence.

The current remaining blocker for the physical inline path is still direct
Mac -> Android B TCP forwarding over the Wi-Fi LAN listener. The ADB-forward
path is useful because it proves the Android packet-processing side can accept
the relayed frame when the frame reaches the listener.
