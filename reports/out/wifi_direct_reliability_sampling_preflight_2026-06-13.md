# Wi-Fi Direct Reliability Sampling Preflight

Дата: 2026-06-13 14:52:50 MSK.

## Status

`blocked_waiting_for_second_phone`

Reliability sampling runner is ready, but the real two-device sampling run was
not started because only one phone is currently visible to ADB.

## Current ADB Evidence

Observed repeatedly over a 30 second polling window:

```text
List of devices attached
d948ffd0               device usb:0-1.2 product:zeus_ru model:2201122G device:zeus
```

Expected Samsung device `R5CY22X6MSB` was not present in `adb devices -l`.
`system_profiler SPUSBDataType` also did not show a matching Samsung/Android
USB entry during this preflight.

## Prepared Runner

The following local runner was added and syntax-checked:

- `scripts/run_wifi_direct_reliability_sampling.sh`

It orchestrates repeated paired directed Wi-Fi Direct trials:

- device A -> device B;
- device B -> device A;
- per-trial target-before / sender-send / target-after artifacts;
- aggregate manifest and Markdown summary;
- rates for `sender_debug_send_success`,
  `transport_counter_delivery_observed`, `message_delivery_proven` and
  permission warnings.

Artifact output is ignored by git under:

- `artifacts/wifi-direct-reliability/`

## Command To Run After Samsung Reconnects

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_wifi_direct_reliability_sampling.sh \
  --device-a R5CY22X6MSB \
  --device-b d948ffd0 \
  --device-a-label samsung \
  --device-b-label xiaomi \
  --iterations 3 \
  --label samsung-xiaomi-wifi-direct-reliability-3x
```

Expected output:

- `artifacts/wifi-direct-reliability/<timestamp>-samsung-xiaomi-wifi-direct-reliability-3x/manifest.json`
- `artifacts/wifi-direct-reliability/<timestamp>-samsung-xiaomi-wifi-direct-reliability-3x/wifi_direct_reliability_sampling.md`

## Claim Boundary

This preflight does not prove repeatable Wi-Fi Direct reliability. It records
that the reliability sampling tooling is ready and that the actual sample set is
waiting for both phones to be connected.
