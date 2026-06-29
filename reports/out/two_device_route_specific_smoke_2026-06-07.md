# Kraken two-device route-specific smoke, 2026-06-07

## Summary

Physical two-phone smoke was completed for LAN/Wi-Fi and BLE direct chat delivery on Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0`.

This is a prototype evidence run only:
`prototype_p2p_integrity_and_adamova_admission_only_not_production_secure_messaging`.

## Build and source

- Branch: `codex/android-research-panel-report-viewer`
- Git SHA: `f6538bd`
- Source state: `dirty_working_tree_based_on_f6538bd`
- APK: `app-android/app/build/outputs/apk/debug/app-debug.apk`
- APK SHA256: `d8ef0c3dcd390ee7e9e7e58c83b18a5ac72eb5deaf66fe651dc6e43e4ec1c9b0`
- Timezone: `Europe/Moscow`
- Final device time sample: `Sun Jun 7 21:12:16 MSK 2026`

## Devices

| Role | Serial | Device |
| --- | --- | --- |
| Samsung | `R5CY22X6MSB` | `samsung SM-S938B` |
| Xiaomi | `d948ffd0` | `Xiaomi 2201122G` |

## Passed physical scenarios

| Scenario | Status | Evidence |
| --- | --- | --- |
| Samsung -> Xiaomi over LAN/Wi-Fi | Passed | `lanS2X2057`, `lanFinal2104`; sender shows delivered, receiver shows message |
| Xiaomi -> Samsung over LAN/Wi-Fi | Passed | `lanX2S2058`; sender shows delivered, receiver shows message |
| Samsung -> Xiaomi over BLE direct | Passed | `bleS2X2101`, `bleFinal2105`; sender shows delivered, receiver shows message; final route export selected `ble-gatt` |

Fresh final UI proof is under:
`artifacts/two-phone-test/route-specific-20260607-205617-baseline/final-apk-post-sends`.

Route evidence export is under:
`artifacts/two-phone-test/route-specific-20260607-205617-baseline/exported-route-evidence`.

## Exported route counters

Samsung export:

- Report version: `kraken.mesh.evidence.snapshot.v2`
- Selected route: `ble-gatt`
- Route attempts: `2`
- Queue size: `0`
- packetsSent: `2`
- packetsReceived: `0`
- receiptsReceived: `2`
- duplicatesDropped: `0`
- expiredDropped: `0`
- unknownPeerRejected: `0`
- wrongRecipientRejected: `0`
- lastDeliveryLatencyMs: `n/a`

Xiaomi export:

- Report version: `kraken.mesh.evidence.snapshot.v2`
- Selected route: `ble-gatt`
- Route attempts: `2`
- Queue size: `0`
- packetsSent: `2`
- packetsReceived: `2`
- receiptsReceived: `0`
- duplicatesDropped: `0`
- expiredDropped: `0`
- unknownPeerRejected: `0`
- wrongRecipientRejected: `0`
- lastDeliveryLatencyMs: `n/a`

## Not completed in this physical run

These counters exist in the exported schema, but the corresponding physical packet-injection scenarios were not executed in this run:

- Unknown peer rejection
- Wrong recipient rejection
- Duplicate packet rejection
- Queue/retry after transport restart

The exported `0` values for these counters should be read as “no such rejection occurred during this run”, not as a passed negative test.

## Raw artifacts

- Baseline and device captures: `artifacts/two-phone-test/route-specific-20260607-205617-baseline`
- Final post-send UI/screens/logs/dumpsys: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/final-apk-post-sends`
- P0 export screen screenshots/UI XML: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/final-export-screen-scrolled`
- Exported JSON/markdown from app-private storage: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/exported-route-evidence`
