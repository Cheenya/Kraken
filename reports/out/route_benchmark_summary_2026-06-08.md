# Route Benchmark Summary

Date: `2026-06-08`.

## Summary

- Minimum sample gate per route: `10`.
- Overall status: `insufficient_n_for_reliability_claim`.
- Claim boundary: Descriptive route benchmark aggregation from existing evidence. Do not claim repeatable reliability unless each claimed route passes the minimum sample gate with fresh comparable runs.

## Routes

| Route | Latency samples | Delivered records | Failed delivery records | Route attempt success/failure | Median ms | P95 ms | Status |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| ble-gatt | 2 | 2 | 0 | 4/1 | 7168.5 | 7189.65 | insufficient_n_for_reliability_claim |
| lan-nsd-tcp | 2 | 2 | 4 | 9/15 | 1160.0 | 1367.9 | insufficient_n_for_reliability_claim |

## Latency Samples

- `ble-gatt` `7145 ms` from `reports/out/two_device_route_specific_smoke_2026-06-08.json` at `$.route_evidence.current_apk_ble_retry_delivered_export.xiaomi`
- `ble-gatt` `7192 ms` from `reports/out/two_device_route_specific_smoke_2026-06-08.json` at `$.route_evidence.current_apk_ble_retry_delivered_export.samsung`
- `lan-nsd-tcp` `929 ms` from `reports/out/two_device_route_specific_smoke_2026-06-08.json` at `$.scenarios[1].current_apk_lan_after_stale_peer_fix`
- `lan-nsd-tcp` `1391 ms` from `reports/out/two_device_route_specific_smoke_2026-06-08.json` at `$.scenarios[0].current_apk_lan_after_stale_peer_fix`

## What This Does Not Prove

- It does not prove repeatable reliability while any route is below the minimum sample gate.
- It does not prove production network reliability or production security.
- It does not cover Wi-Fi Direct unless the route appears with delivered samples.
