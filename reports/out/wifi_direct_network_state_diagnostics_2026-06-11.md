# Wi-Fi Direct Network-State Diagnostics

Date: `2026-06-11`.

Devices:

- Samsung `R5CY22X6MSB` / `SM_S938B`
- Xiaomi `d948ffd0` / `2201122G`

Artifacts:

- Start/discovery capture:
  `artifacts/debug-route-evidence/20260611-002136-wifi-direct-network-state-diagnostics-2026-06-11/manifest.json`
- Reuse + Xiaomi debug send:
  `artifacts/debug-route-evidence/20260611-002256-wifi-direct-reuse-debug-send-network-state-2026-06-11/manifest.json`
- Samsung post-send export:
  `artifacts/debug-route-evidence/20260611-002328-wifi-direct-samsung-post-xiaomi-debug-send-2026-06-11/manifest.json`

## Result

Wi-Fi Direct is present as a real P2P network on both devices and the app
listeners are visible in the device network state captured by ADB:

| Device | P2P IP | Listener evidence | App evidence |
| --- | --- | --- | --- |
| Samsung `R5CY22X6MSB` | `192.168.49.1` on `p2p-wlan0-0` | `/proc/net/tcp6` listener on app port `40235` | post-send `selected_route=wifi-direct`, `accepted_connections=1`, `inbound_packets=1` |
| Xiaomi `d948ffd0` | `192.168.49.59` on `p2p0` | `/proc/net/tcp6` listener on app port `40849` | reuse capture `selected_route=wifi-direct`, `debug_send_success=true`, route attempt `success=true` |

The repeat path from Xiaomi to Samsung is captured again: Xiaomi selected
`wifi-direct` and sent successfully; Samsung then reported one accepted
connection and one inbound packet.

## Open Blocker

The reverse direction is still not closed. Samsung can discover Xiaomi later in
the run, but its send attempts to `192.168.49.59:40849` timed out after 5000 ms.
This keeps the Wi-Fi Direct route gate open for `10/10`.

Current interpretation:

- not a missing-device problem: both devices are connected through ADB;
- not a missing-P2P-network problem: both devices have `192.168.49.x` P2P IPs;
- not an obvious listener-not-started problem: both app ports appear in
  `/proc/net/tcp6`;
- still an asymmetric Wi-Fi Direct reachability/discovery problem: Xiaomi ->
  Samsung is demonstrated, Samsung -> Xiaomi is not.

## Claim Boundary

This is bounded debug evidence for a research prototype. It supports the claim
that Wi-Fi Direct participates in the prototype route path and can deliver in
the Xiaomi -> Samsung direction. It does not prove bidirectional reliability,
route-bound hostile packet resistance, production security or completed
`10/10`.
