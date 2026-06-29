# Debug Route Evidence Capture

Дата: 2026-06-08.

Источник: `artifacts/debug-route-evidence/20260608-083438-local-hostile-probe-after-6853fc3`.

## Summary

Fresh debug route evidence export снят с двух подключённых телефонов после
debug-only local inbox hostile packet probe. APK был собран из clean commit
`6853fc3`; app export на обоих устройствах содержит
`source_state = clean_commit_6853fc3`.

| Device | Model | selectedRoute | evidenceMode | unknownPeerRejected | wrongRecipientRejected | duplicatesDropped |
| --- | --- | --- | --- | ---: | ---: | ---: |
| `R5CY22X6MSB` | `SM-S938B` | `none` | `debug_local_inbox_packet_injection_and_queue_retry_probe` | 1 | 1 | 1 |
| `d948ffd0` | `2201122G` | `none` | `debug_local_inbox_packet_injection_and_queue_retry_probe` | 1 | 1 | 1 |

## What This Closes

- Fresh phone export after the local inbox hostile packet probe change.
- Evidence mode no longer depends only on stale counter-only summaries.
- The captured phone JSON proves that unknown peer, wrong recipient and
  duplicate local hostile packets pass through the debug probe path and produce
  rejection/drop counters on both phones.

## What This Does Not Close

- This is not physical hostile packet injection over LAN, BLE or Wi-Fi Direct.
- This does not prove Wi-Fi Direct route selection; `selectedRoute` is `none`.
- This does not prove Mac inline relay/MITM against Android devices.
- This is not a repeated reliability benchmark.

## Claim Boundary

Debug-only ADB capture of in-app route evidence. Local hostile probe does not
prove physical LAN/BLE/Wi-Fi Direct hostile packet injection, production
reliability or production security.
