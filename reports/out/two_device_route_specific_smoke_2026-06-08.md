# Kraken two-device route-specific smoke, 2026-06-08

## Summary

Fresh current APK evidence now closes the retry-delivery gap for both tested paths:

- BLE-observed path: both phones exported `queued=true`, `sent=true`, `delivered=true`, final status `DELIVERED_TO_PEER`, receipt counters, and last-delivery latency samples.
- LAN NSD/TCP over local Wi-Fi path with Bluetooth disabled after stale-peer cleanup: both phones selected `lan-nsd-tcp`, exported `sent=true`, `delivered=true`, final status `DELIVERED_TO_PEER`, receipt counters, and last-delivery latency samples.

The earlier fresh LAN Bluetooth-off run is retained as regression evidence: Samsung -> Xiaomi failed with `ECONNREFUSED` to stale Xiaomi port `42817`, while Xiaomi was actually listening on a different port in `ss`. The current fix removes stale endpoints by peer fingerprint/service name and retries fresh LAN candidates.

Boundary: `prototype_p2p_integrity_and_adamova_admission_only_not_production_secure_messaging`. This is not production secure messaging and not a production reliability claim.

## Build and source

- Branch: `codex/android-research-panel-report-viewer`
- Git SHA: `f6538bd`
- Source state: `dirty_working_tree_based_on_f6538bd`
- APK: `app-android/app/build/outputs/apk/debug/app-debug.apk`
- Current debug APK SHA256: `5810480172db4ae9ec93dd322caace05cfaa3187b010887fe15b3cb8e872f439`
- Previous retry-wait debug APK SHA256: `1b43500aff6594d08f1e4fd99218bb1fd4fb773c17655730d607529a810c8629`
- Physical delivery APK SHA256: `28cbe980833716b94913f67387c305e3baa3799d7beebb2a9ccdc40088534272`
- Timezone: `Europe/Moscow`
- BLE retry-delivered export time: Samsung `2026-06-08 01:33:56 MSK`, Xiaomi `2026-06-08 01:33:56 MSK`
- BLE retry-delivered capture host time: `2026-06-08 01:34:37 MSK`
- LAN Bluetooth-off pre-fix capture host time: `2026-06-08 01:38:26 MSK`
- LAN stale-peer-fix export time: Samsung `2026-06-08 01:44:45 MSK`, Xiaomi `2026-06-08 01:44:47 MSK`
- LAN stale-peer-fix capture host time: `2026-06-08 01:47:25 MSK`

## Devices

| Role | Serial | Device |
| --- | --- | --- |
| Samsung | `R5CY22X6MSB` | `samsung SM-S938B` |
| Xiaomi | `d948ffd0` | `Xiaomi 2201122G` |

## Scenario Status

| Scenario | Status | Evidence |
| --- | --- | --- |
| Samsung -> Xiaomi over LAN NSD/TCP | Passed current LAN after stale-peer fix | Bluetooth-off run after fix: Samsung selected `lan-nsd-tcp`, `sent=true`, `delivered=true`, status `DELIVERED_TO_PEER`, latency `1391 ms` |
| Xiaomi -> Samsung over LAN NSD/TCP | Passed current LAN after stale-peer fix | Bluetooth-off run after fix: Xiaomi selected `lan-nsd-tcp`, recovered after stale-port refusal, then `sent=true`, `delivered=true`, status `DELIVERED_TO_PEER`, latency `929 ms` |
| Samsung -> Xiaomi over BLE direct | Passed current route evidence + older physical UI | Current retry run observed `ble-gatt` and delivered on both exports; older physical UI had `bleS2X2101`, `bleFinal2105` delivered |
| Unknown peer rejection | Passed current debug-only counter evidence | BLE retry-delivered export: Samsung `2`, Xiaomi `2` |
| Wrong recipient rejection | Passed current debug-only counter evidence | BLE retry-delivered export: Samsung `1`, Xiaomi `1` |
| Duplicate packet rejection | Passed current debug-only counter evidence | BLE retry-delivered export: Samsung `1`, Xiaomi `1` |
| Queue/retry after transport restart | Passed for current BLE and LAN paths | BLE current run and LAN Bluetooth-off post-fix run: both phones reached `DELIVERED_TO_PEER` |

## Current APK BLE-Observed Retry Delivered Export

Samsung:

- selectedRoute: `ble-gatt`
- packetsSent: `2`
- packetsReceived: `1`
- receiptsReceived: `1`
- lastDeliveryLatencyMs: `7192`
- queueRetryBody: `queueRetry590867`
- retry: queued `True`, sent `True`, delivered `True`, status `DELIVERED_TO_PEER`

Xiaomi:

- selectedRoute: `ble-gatt`
- packetsSent: `2`
- packetsReceived: `1`
- receiptsReceived: `1`
- lastDeliveryLatencyMs: `7145`
- queueRetryBody: `queueRetry591404`
- retry: queued `True`, sent `True`, delivered `True`, status `DELIVERED_TO_PEER`

## Current APK LAN Bluetooth-Off Export After Stale-Peer Fix

Samsung:

- selectedRoute: `lan-nsd-tcp`
- BLE active: `false`
- queueSize: `0`
- retry: queued `True`, sent `True`, delivered `True`, status `DELIVERED_TO_PEER`
- queueRetryBody: `queueRetry247196`
- lastDeliveryLatencyMs: `1391`
- recent route results: `lan-nsd-tcp:true`, `lan-nsd-tcp:true`

Xiaomi:

- selectedRoute: `lan-nsd-tcp`
- BLE active: `false`
- queueSize: `0`
- retry: queued `True`, sent `True`, delivered `True`, status `DELIVERED_TO_PEER`
- queueRetryBody: `queueRetry247693`
- lastDeliveryLatencyMs: `929`
- recent route results include one stale Samsung endpoint refusal, then fresh `lan-nsd-tcp:true` attempts.

## Pre-Fix LAN Regression Evidence

- Samsung -> Xiaomi before stale-peer cleanup: `lan-nsd-tcp:false`, `ECONNREFUSED` to Xiaomi `192.168.0.121:42817`, while Xiaomi was listening on another port.
- Xiaomi -> Samsung before stale-peer cleanup: `lan-nsd-tcp:true`, `sent=true`, `delivered=false`.
- Root issue class: cached LAN endpoint could outlive the actual listening socket after transport/network restart.

## Raw Artifacts

- Fresh current APK BLE retry delivered export: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-retry-delivered-export`
- Fresh current APK BLE retry delivered screenshots/UI/logcat/bluetooth/network/package dumps: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-retry-delivered-capture`
- Fresh current APK LAN Bluetooth-off mixed export: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-bt-off-mixed-export`
- Fresh current APK LAN Bluetooth-off screenshots/UI/logcat/bluetooth/network/package/ss dumps: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-bt-off-mixed-capture`
- Fresh current APK LAN stale-peer-fix export: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-stale-peer-fix-export`
- Fresh current APK LAN stale-peer-fix screenshots/UI/logcat/bluetooth/network/package/ss dumps: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-stale-peer-fix-capture`
- Older physical post-send UI/screens/logs/dumpsys: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/final-apk-post-sends`
- Older debug route evidence: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/final-debug-exported-route-evidence`

## Limitations

- Current LAN retry delivery is closed for this two-device Bluetooth-off smoke after stale-peer cleanup, but this remains prototype evidence, not a production reliability guarantee.
- The pre-fix Bluetooth-off run exposed stale/non-listening LAN endpoint behavior and is retained as regression evidence.
- Negative scenarios are debug-only counter evidence, not external hostile packet injection.
- Latency values are current BLE/LAN last-delivery samples, not median/p95 over a statistically meaningful sample.
- Older physical LAN/BLE delivery evidence and current APK route-counter evidence are intentionally separated.
- No production reliability or production crypto claim is made.
