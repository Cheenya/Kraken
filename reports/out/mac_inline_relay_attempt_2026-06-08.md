# Mac Inline Relay Attempt

Date: `2026-06-08`.
Mode: `normal`.
Transport profile: `lan-only`.

## Result

This attempt proves a narrower but important step: Android A sent a Kraken LAN
frame to the Mac inline relay harness.

| Step | Evidence | Status |
| --- | --- | --- |
| Samsung lan-only sender setup | `manual-peer-added`, selected route `lan-nsd-tcp`, enabled modes `lan-nsd-tcp` | passed |
| Android A -> Mac relay | Relay received 1 frame from `192.168.0.201:48078` | passed |
| Frame identity | sender `3C4E D5BA 9DB8 8F9B`, recipient `B42B 3068 934E F618`, packet `packet-fbfa95d2-d77d-4507-ae07-36ce62dc4f9c` | passed |
| Mac -> Xiaomi forward | target `192.168.0.121:42735`, error `timed out` | failed |
| Xiaomi after counters | accepted connections `0`, inbound packets `0`, malformed frames `0` | no delivery |

## Artifacts

- `artifacts/debug-route-evidence/20260608-094116-lan-only-endpoints-before-inline-relay/manifest.json`
- `artifacts/debug-route-evidence/20260608-094149-inline-relay-normal-lan-only-send-samsung/manifest.json`
- `artifacts/desktop-relay-inline/20260608-094157/android_inline_lan_relay.json`
- `artifacts/debug-route-evidence/20260608-094220-inline-relay-normal-lan-only-after-xiaomi/manifest.json`

## Boundary

This closes only `android_a_to_mac_lan_frame_capture_for_inline_relay_harness`.
It does not close Android A -> Mac -> Android B delivery, drop/duplicate/tamper
modes, Android-side MITM rejection counters, cryptographic MITM resistance or
production relay security.

The current remaining blocker for the physical inline path is Mac -> Android B
TCP forwarding to the phone LAN listener.
