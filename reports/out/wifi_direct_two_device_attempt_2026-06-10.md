# Wi-Fi Direct Two-Device Attempt

Date: `2026-06-10`.

Devices:

- Samsung `R5CY22X6MSB` / `SM_S938B`
- Xiaomi `d948ffd0` / `2201122G`

Fresh artifact:

- `artifacts/debug-route-evidence/20260610-232255-wifi-direct-debug-send-30s-settle-2026-06-10/manifest.json`

## Result

The two-device Wi-Fi Direct gate is still open.

Both devices were visible to ADB and the debug receiver executed successfully on
both phones. The Wi-Fi Direct-only run used:

```bash
scripts/capture_debug_route_evidence.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --label wifi-direct-debug-send-30s-settle-2026-06-10 \
  --start-mesh-before-export \
  --transport-profile wifi-direct-only \
  --start-mesh-settle-ms 30000 \
  --debug-send-body "wifi-direct evidence 30s settle 2026-06-10" \
  --sync-after-debug-send \
  --sync-attempts 8
```

Observed:

| Device | Enabled route | P2P visible devices | Kraken transport peers | Selected route | Debug send |
| --- | --- | ---: | ---: | --- | --- |
| `R5CY22X6MSB` | `wifi-direct` | `2` | `0` | `none` | `false`, `UNKNOWN_PEER` |
| `d948ffd0` | `wifi-direct` | `2` | `0` | `none` | `false`, `UNKNOWN_PEER` |

Interpretation: Android Wi-Fi Direct is available and can see peer devices at
the P2P layer, but Kraken does not yet resolve a peer into the message-capable
transport table. No Wi-Fi Direct route delivery, route attempt, inbound packet
or rejection counter was captured.

## Boundary

This is negative completion evidence. It proves that the current Wi-Fi Direct
two-device route is not ready to claim `10/10`. It does not weaken the bounded
LAN/BLE smoke evidence, and it does not make any production security claim.
