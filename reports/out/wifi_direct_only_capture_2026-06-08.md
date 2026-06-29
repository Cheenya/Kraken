# Wi-Fi Direct Only Capture

Дата: 2026-06-08.

Источник: `artifacts/debug-route-evidence/20260608-085525-wifi-direct-only-after-254b209`.

## Summary

После добавления debug-only transport profile был выполнен ADB export с
`--start-mesh-before-export --transport-profile wifi-direct-only`. APK был
собран из clean commit `254b209`; app export на обоих устройствах содержит
`source_state = clean_commit_254b209`.

| Device | Model | meshState | enabledTransportModes | selectedRoute | Wi-Fi Direct active | route attempts |
| --- | --- | --- | --- | --- | ---: | ---: |
| `R5CY22X6MSB` | `SM-S938B` | `SCANNING` | `wifi-direct` | `none` | true | 0 |
| `d948ffd0` | `2201122G` | `SCANNING` | `wifi-direct` | `none` | true | 0 |

## What This Closes

- Debug capture can now start a Wi-Fi Direct-only transport profile without
  BLE or LAN NSD/TCP fallback.
- Evidence JSON exports `enabled_transport_modes=["wifi-direct"]`, so
  Wi-Fi radio readiness is no longer confused with a started LAN fallback.

## What This Does Not Close

- `selectedRoute` remained `none`, so this is not Wi-Fi Direct message delivery evidence.
- There were no successful Wi-Fi Direct route attempts.
- This is not Wi-Fi Direct negative-test evidence.
- This is not Wi-Fi Direct latency/loss/retry evidence.
- This is not physical hostile packet injection evidence.

## Claim Boundary

Wi-Fi Direct-only debug capture proves that the app can start with only the
Wi-Fi Direct prototype transport enabled and with Wi-Fi Direct readiness active
on both phones. It does not prove Wi-Fi Direct peer discovery, message delivery,
attack rejection, reliability or production security.
