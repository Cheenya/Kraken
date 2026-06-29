# Wi-Fi Direct Two-Device Delivery Smoke

Date: `2026-06-10`.

Devices:

- Samsung `R5CY22X6MSB` / `SM_S938B`
- Xiaomi `d948ffd0` / `2201122G`

Primary artifacts:

- Send: `artifacts/debug-route-evidence/20260610-235735-wifi-direct-ipv4-bind-retry-2026-06-10/manifest.json`
- Receive/sync: `artifacts/debug-route-evidence/20260610-235851-wifi-direct-samsung-sync-after-inbound-2026-06-10/manifest.json`
- Fresh repeat attempt: `artifacts/debug-route-evidence/20260611-000347-wifi-direct-bidir-step1-xiaomi-send-2026-06-10/manifest.json`
- Follow-up network-state diagnostics:
  `reports/out/wifi_direct_network_state_diagnostics_2026-06-11.md`

## Result

Wi-Fi Direct is no longer only a service-readiness path. A one-way two-device
delivery smoke was captured from Xiaomi to Samsung:

| Step | Device | Evidence |
| --- | --- | --- |
| Send | `d948ffd0` | `selected_route=wifi-direct`, `debug_send_success=true`, route attempt `success=true`, `packets_sent=1` |
| Receive | `R5CY22X6MSB` | `selected_route=wifi-direct`, `accepted_connections=1`, `inbound_packets=1`, `last_sync_summary=sent=0 received=1 receipts=0 rejected=10`, `packets_received=1` |

The successful run followed an IPv4 listener fix in `WifiDirectTransport`: the
Wi-Fi Direct socket must be reachable on the P2P IPv4 path used by Android
Wi-Fi Direct clients.

## Still Open

This is not a full `10/10` close:

- fresh repeat after reinstall did not reproduce delivery;
- Samsung group-owner to Xiaomi client reply still failed;
- Wi-Fi Direct radio-path negative tests are not captured;
- no N-run Wi-Fi Direct reliability benchmark exists.

Follow-up diagnostics on 2026-06-11 repeated Xiaomi -> Samsung delivery and
captured P2P interface/listener state on both phones, but Samsung -> Xiaomi
still timed out.

## Boundary

This report proves a bounded prototype Wi-Fi Direct route-delivery smoke on two
phones. It does not claim production security, production reliability,
bidirectional reliability, hostile packet resistance or completed `10/10`.
