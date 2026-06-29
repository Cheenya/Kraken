# LAN Malformed Injection Attempt

Дата: 2026-06-08.

Источник:

- baseline capture: `artifacts/debug-route-evidence/20260608-090206-lan-injection-baseline-after-e4eedb6`;
- after capture: `artifacts/debug-route-evidence/20260608-090341-lan-injection-after-e4eedb6`;
- injector output: `artifacts/debug-route-evidence/20260608-090206-lan-injection-baseline-after-e4eedb6/lan_malformed_injection_wifi_subnet_only.json`.

## Summary

Был добавлен внешний host-side injector
`scripts/inject_lan_malformed_frames.py`, который читает Android LAN endpoint
из route evidence и шлёт malformed length-prefixed frames. APK был собран из
clean commit `e4eedb6`; оба телефона экспортировали `source_state =
clean_commit_e4eedb6`.

| Device | Model | Wi-Fi endpoint | Listener observed by Android | Ping from Mac | Injector attempts | Successful TCP writes | Android malformed delta |
| --- | --- | --- | --- | --- | ---: | ---: | ---: |
| `R5CY22X6MSB` | `SM-S938B` | `192.168.0.201:38147` | `*:38147` | yes | 4 | 0 | 0 |
| `d948ffd0` | `2201122G` | `192.168.0.121:43113` | `*:43113` | yes | 4 | 0 | 0 |

Cases attempted against each Wi-Fi endpoint:

- `zero_length_prefix`;
- `oversized_length_prefix`;
- `invalid_json_payload`;
- `truncated_payload`.

## What This Closes

- The project now has a reusable external LAN malformed-frame injector.
- Route evidence now exports `local_port`, `local_addresses`,
  `accepted_connections`, `inbound_packets`, `malformed_frames_dropped` and
  `send_failures`.
- The current Wi-Fi environment was tested from Mac to Android listener ports,
  and the result is recorded instead of being inferred.

## What This Does Not Close

- This does not prove physical hostile LAN packet rejection because all
  same-subnet TCP attempts timed out.
- Android malformed counters remained `0 -> 0` on both phones.
- This does not cover BLE or Wi-Fi Direct hostile injection.
- This does not cover unknown-peer, wrong-recipient or duplicate valid-packet
  injection over a physical transport.

## Claim Boundary

The run proves that the current Mac/Wi-Fi environment could ping both phones
and that Android reported listening Kraken LAN ports, but Mac TCP connections
to those ports timed out. Therefore physical hostile LAN injection remains
open for `10/10`.
