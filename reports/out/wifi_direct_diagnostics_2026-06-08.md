# Wi-Fi Direct Diagnostics

Дата: 2026-06-08.

Источник:
`artifacts/debug-route-evidence/20260608-101915-wifi-direct-diagnostics-clean-82cb6ff/manifest.json`.

Коммит: `82cb6ff`, source state: `clean`.

## Permission Boundary

Wi-Fi Direct остаётся основным локальным route, а не optional afterthought.
Permission policy сейчас такая:

- Android 13+: `NEARBY_WIFI_DEVICES` с `neverForLocation`;
- Android 12 и ниже: `ACCESS_FINE_LOCATION`, capped
  `android:maxSdkVersion="32"`, потому что legacy Wi-Fi Direct discovery
  требует location permission;
- `ACCESS_BACKGROUND_LOCATION` не запрашивается;
- `ACCESS_COARSE_LOCATION` не запрашивается;
- в Settings есть Wi-Fi Direct switch: если route отключён, runtime permission
  для него не запрашивается.

## Clean Two-Phone Capture

Capture был запущен как:

`scripts/capture_debug_route_evidence.sh --device R5CY22X6MSB --device d948ffd0 --label wifi-direct-diagnostics-clean-82cb6ff --start-mesh-before-export --transport-profile wifi-direct-only`

| Device | Model | enabledTransportModes | permission | radio | service | registrationState | discoveryState | discovered peers | selectedRoute | route attempts |
| --- | --- | --- | --- | --- | --- | --- | --- | ---: | --- | ---: |
| `R5CY22X6MSB` | `SM-S938B` | `wifi-direct` | true | true | true | `wifi-direct:registered:_kraken._tcp` | `wifi-direct:discovering:_kraken._tcp` | 0 | `none` | 0 |
| `d948ffd0` | `2201122G` | `wifi-direct` | true | true | true | `wifi-direct:registered:_kraken._tcp` | `wifi-direct:discovering:_kraken._tcp` | 0 | `none` | 0 |

## What This Closes

- Runtime permission/radio/service readiness is active on both phones.
- Wi-Fi Direct-only debug profile disables BLE/LAN fallback for the capture.
- Native Android Wi-Fi Direct service registration starts on both phones.
- Native Android Wi-Fi Direct service discovery starts on both phones.
- The export now exposes registration state, discovery state, discovered peer
  count and manual peer count.

## What This Does Not Close

- This is not Wi-Fi Direct peer discovery success: discovered peers remained
  `0`.
- This is not Wi-Fi Direct message delivery evidence: selected route remained
  `none`.
- This is not Wi-Fi Direct negative-test evidence.
- This is not latency/loss/retry benchmark evidence.
- This is not a production security claim.

## Current Blocker

The current blocker has narrowed from "is Wi-Fi Direct wired at all?" to:
Android Wi-Fi Direct DNS-SD registration/discovery starts cleanly, but the two
phones do not discover each other as Wi-Fi Direct peers in the current capture.

Next engineering step should target peer discovery itself: rediscovery cadence,
peer/device state diagnostics, service listener behavior and OEM-specific
`WifiP2pManager` stop conditions.
