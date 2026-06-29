# Mac Inline Relay TCP Preflight

Date: `2026-06-08`.
Mac IP: `192.168.0.123`.

## Result

Both connected phones reached a Mac system TCP listener on the current LAN:

| Device | Model | Ping to Mac | System listener payload observed on Mac |
| --- | --- | --- | --- |
| `R5CY22X6MSB` | `SM-S938B` | 2 transmitted, 2 received, 0% packet loss | `samsung-nc-listener` |
| `d948ffd0` | `2201122G` | 2 transmitted, 2 received, 0% packet loss | `xiaomi-nc-listener` |

macOS firewall state during the check:

| Field | Value |
| --- | --- |
| Firewall enabled | `true` |
| Block all | `false` |
| Stealth mode | `false` |

## Boundary

This closes only Android-to-Mac TCP reachability for a system listener. It does not prove Android A -> Mac -> Android B inline relay/MITM, LAN frame forwarding, drop/duplicate/tamper rejection behavior on phones, or production security.

Python listener preflight timed out without accepts, while `/usr/bin/nc` accepted
both phones. The next relay run should either approve the Python listener in the
local macOS firewall prompt or use another signed/allowed listener wrapper.

## Not Closed

- `android_a_to_mac_to_android_b_inline_relay_run`
- `android_lan_frame_relay_normal_drop_duplicate_tamper`
- `android_before_after_delivery_and_rejection_counters`
- `cryptographic_mitm_resistance`
- `production_relay_security`
