# Wi-Fi Direct Readiness Capture

Дата: 2026-06-08.

Источник: `artifacts/debug-route-evidence/20260608-084228-mesh-start-readiness-after-cd1adf5`.

## Summary

После фикса `WifiDirectTransport` clear-service warnings был выполнен
debug-only ADB export с `--start-mesh-before-export`. APK был собран из clean
commit `cd1adf5`; app export на обоих устройствах содержит
`source_state = clean_commit_cd1adf5`.

| Device | Model | meshState | selectedRoute | Wi-Fi Direct permission | Wi-Fi Direct radio | Wi-Fi Direct service | Wi-Fi Direct active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `R5CY22X6MSB` | `SM-S938B` | `PEER_FOUND` | `lan-nsd-tcp` | true | true | true | true |
| `d948ffd0` | `2201122G` | `PEER_FOUND` | `lan-nsd-tcp` | true | true | true | true |

## What This Closes

- Wi-Fi Direct no longer fails at `clearServiceRequests` / `clearLocalServices`
  during debug mesh-start readiness capture.
- Both phones export Wi-Fi Direct permission/radio/service readiness as active.
- This proves the installed debug APK can start the Wi-Fi Direct service layer on
  Samsung and Xiaomi.

## What This Does Not Close

- `selectedRoute` remained `lan-nsd-tcp`, so this is not Wi-Fi Direct message delivery evidence.
- This is not Wi-Fi Direct negative-test evidence.
- This is not Wi-Fi Direct latency/loss/retry evidence.
- This is not physical hostile packet injection evidence.

## Claim Boundary

Wi-Fi Direct service readiness was exported from the debug app on both phones,
but selected route remained `lan-nsd-tcp`. This is readiness evidence, not
Wi-Fi Direct route delivery or attack evidence.
