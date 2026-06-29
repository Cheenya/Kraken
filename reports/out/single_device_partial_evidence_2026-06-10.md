# Single-device Partial Evidence

Date: 2026-06-10.

Device: Xiaomi `d948ffd0`, model `2201122G`.

Installed APK:

- Version: `0.1.0`;
- lastUpdateTime: `2026-06-10 22:47:26`;
- source capture git: `cfc0171`, source state: `clean`.

## Captures

| Capture | Artifact | Result |
| --- | --- | --- |
| Local hostile probe | `artifacts/debug-route-evidence/20260610-224732-single-device-current-local-hostile-cfc0171/manifest.json` | all transports enabled; local debug hostile counters `unknown/wrong/duplicate = 1/1/1`; selected route `none`; discovered peers `0` |
| Wi-Fi Direct-only diagnostics | `artifacts/debug-route-evidence/20260610-224803-single-device-wifi-direct-rediscovery-cfc0171/manifest.json` | Wi-Fi Direct active; registration `wifi-direct:registered:_kraken._tcp`; discovery `wifi-direct:discovering:_kraken._tcp`; P2P visible devices `0`; selected route `none` |

## What This Closes

- Current debug APK installs on Xiaomi.
- The debug evidence receiver exports clean `cfc0171` source state.
- Local hostile probe counters are observable on one device.
- Wi-Fi Direct-only diagnostics include the new rediscovery fields.

## What This Does Not Close

- This is not Wi-Fi Direct peer discovery.
- This is not Wi-Fi Direct message delivery.
- This is not two-device LAN/BLE/Wi-Fi Direct route evidence.
- This is not Mac inline relay evidence.
- This is not physical hostile packet injection.
- This is not N-run reliability evidence.

## Next Required Device Step

Connect the second phone and run:

`scripts/capture_debug_route_evidence.sh --device R5CY22X6MSB --device d948ffd0 --label wifi-direct-rediscovery-two-device --start-mesh-before-export --transport-profile wifi-direct-only`

Then run:

`scripts/run_route_benchmark_trials.sh --device R5CY22X6MSB --device d948ffd0 --label route-benchmark-after-wifi-direct-diagnostics --transport-profile all --trials 10 --min-samples-per-route 10`
