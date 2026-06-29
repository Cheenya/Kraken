# BLE Two-Device Delivery Evidence

Status: manual Samsung/Xiaomi BLE direct-route evidence captured.

Capture date: 2026-06-06.

Raw evidence directory:

```text
artifacts/two-phone-test/ble-live-20260606-204431/
```

## What Was Captured

The capture contains two connected Android devices:

| Role | Device | Evidence |
| --- | --- | --- |
| Device A | Samsung `R5CY22X6MSB` | `after-open/samsung-R5CY22X6MSB/screen.png`, `after-open/samsung-R5CY22X6MSB/window.xml`, `samsung-R5CY22X6MSB/bluetooth-manager.txt` |
| Device B | Xiaomi `d948ffd0` | `xiaomi-d948ffd0/bluetooth-manager.txt`, `after-open/xiaomi-d948ffd0/screen.png`, `after-open/xiaomi-d948ffd0/window.xml` |

## App-Level Evidence

Samsung `after-open/samsung-R5CY22X6MSB/window.xml` shows an open chat with
`Xiaomi` and the route label:

```text
Bluetooth напрямую
```

The same chat screen shows multiple outgoing messages with:

```text
доставлено
```

The matching screenshot is:

```text
artifacts/two-phone-test/ble-live-20260606-204431/after-open/samsung-R5CY22X6MSB/screen.png
```

This is direct UI evidence that the route-aware layer selected BLE for the
Samsung -> Xiaomi chat at capture time. It is stronger than merely proving that
Bluetooth was enabled.

## Android BLE Stack Evidence

Both Bluetooth dumps contain Kraken's BLE GATT service UUID:

```text
58a1257c-f4a8-48c8-99d5-917b9863d7c4
```

Samsung evidence includes:

- registered `com.disser.kraken` LE GATT apps;
- service `58a1257c-f4a8-48c8-99d5-917b9863d7c4` started;
- characteristics `58a1257d-...` and `58a1257e-...`;
- scan filters for the Kraken service UUID.

Xiaomi evidence includes:

- registered `com.disser.kraken` scanners;
- Kraken service UUID scan filters;
- service `58a1257c-f4a8-48c8-99d5-917b9863d7c4` started;
- recent `BLE-CONNECTED` entries during the manual run window.

## Claim Boundary

Allowed claim:

> Kraken has manual two-phone evidence of a BLE direct route label and delivered
> chat messages between QR-trusted Samsung/Xiaomi devices.

Not allowed:

- production-secure Bluetooth messaging;
- audited encryption/signature claims;
- broad BLE reliability/latency claims;
- Wi-Fi Direct support;
- full multi-hop mesh over BLE.

## Remaining Work

1. Add an automated or semi-automated route-specific smoke that sends a fresh
   message while forcing/recording the `ble-gatt` route.
2. Export authoritative in-app counters for route attempts, packets sent,
   packets received and receipts received.
3. Add a BLE-only rejection smoke for unknown peer, wrong recipient and duplicate
   packet scenarios.
4. Keep production crypto claims blocked until signed/encrypted packet envelope
   and Android Keystore work are complete.
