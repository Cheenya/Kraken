# Wi-Fi Direct P2P Bind Diagnostics

Date: `2026-06-11`.

Devices:

- Samsung `R5CY22X6MSB` / `SM_S938B`
- Xiaomi `d948ffd0` / `2201122G`

Primary artifact:

- `artifacts/debug-route-evidence/20260611-004859-wifi-direct-p2p-bind-preflight-2026-06-11/manifest.json`

## Change Under Test

`WifiDirectTransport` now prefers binding its server socket to the active P2P
IPv4 address when one is available, and exports
`wifi_direct_server_bind_address` in route evidence. This makes the listener
binding explicit instead of relying only on a wildcard socket.

Captured bind addresses:

| Device | P2P IP | App bind address | App port |
| --- | --- | --- | ---: |
| Samsung `R5CY22X6MSB` | `192.168.49.1` | `192.168.49.1` | `34273` |
| Xiaomi `d948ffd0` | `192.168.49.59` | `192.168.49.59` | `41817` |

## Network Findings

P2P layer-3 reachability works in both directions:

- Samsung -> Xiaomi `ping 192.168.49.59`: `0% packet loss`.
- Xiaomi -> Samsung `ping 192.168.49.1`: `0% packet loss`.

Shell TCP listeners also work in both directions over the P2P network:

- Samsung connected to a Xiaomi shell listener on `192.168.49.59:45678`.
- Xiaomi connected to a Samsung shell listener on `192.168.49.1:45679`.

The Kraken app listener is locally reachable on each device's own P2P IP, but
remote P2P TCP probes to the app listener still timed out:

| Probe | Result |
| --- | --- |
| Samsung shell -> Xiaomi Kraken app listener `192.168.49.59:41817` | timeout |
| Xiaomi shell -> Samsung Kraken app listener `192.168.49.1:34273` | timeout |
| Samsung shell -> Samsung Kraken app listener `192.168.49.1:34273` | success |
| Xiaomi shell -> Xiaomi Kraken app listener `192.168.49.59:41817` | success |

## App-Level Result

After the P2P bind change, a fresh Xiaomi app-level debug send to Samsung still
failed with TCP connect timeouts to Samsung's app listener. The Wi-Fi Direct
gate therefore remains open.

Current narrowed blocker: Android P2P TCP works between devices, but remote
traffic to the Kraken app listener is not accepted even when the listener is
bound to the device's P2P IPv4 address and is locally reachable.

## Claim Boundary

This is diagnostic evidence only. It does not prove Wi-Fi Direct delivery,
bidirectional reliability, route-bound hostile packet resistance, production
network reliability or production security.
