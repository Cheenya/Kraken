# Kraken 10/10 Live Audit

Complete: `false`
Connected device count: `2`
Open gate count: `5`

## Connected Devices

| Serial | State | Model |
| --- | --- | --- |
| `R5CY22X6MSB` | `device` | `SM_S938B` |
| `d948ffd0` | `device` | `2201122G` |

## Items

| Requirement | Status | Evidence |
| --- | --- | --- |
| Current blocker audit JSON | `complete` | `reports/out/kraken_10_10_current_blocker_audit_2026-06-11.json` |
| Friend-test package JSON | `complete` | `reports/out/kraken_release_package_2026-06-11.json` |
| Current blocker audit forbids false 10/10 claim | `complete` | `completion_claim_allowed=False; current_10_10_verdict=not_complete` |
| At least two Android devices visible for physical route validation | `complete` | `connected_device_count=2` |
| Required still-open gate is tracked: physical_hostile_packet_injection_lan_ble_wifi_direct | `complete` | `status=open` |
| Required still-open gate is tracked: physical_mac_inline_relay_modes | `complete` | `status=open` |
| Required still-open gate is tracked: route_benchmark_min_n | `complete` | `status=open` |
| Required still-open gate is tracked: wifi_direct_negative_tests | `complete` | `status=open` |
| Required still-open gate is tracked: wifi_direct_route_delivery | `complete` | `status=open` |
| Friend-test package validation recorded: apkanalyzer_manifest | `complete` | `passed` |
| Friend-test package validation recorded: apksigner_verify | `complete` | `passed_v2_debug_certificate` |
| Friend-test package validation recorded: assemble_debug | `complete` | `passed` |
| Friend-test package validation recorded: checksum_verify | `complete` | `passed` |
| Friend-test package validation recorded: git_diff_check | `complete` | `passed` |
| Friend-test package validation recorded: lint_debug | `complete` | `passed` |
| Friend-test package validation recorded: pytest_android_policy_guards | `complete` | `53 passed` |
| Friend-test package validation recorded: smoke_install_samsung | `complete` | `passed` |
| Friend-test package validation recorded: smoke_launch_samsung | `complete` | `passed` |
| Friend-test package validation recorded: targeted_gradle_unit_tests | `complete` | `passed` |
| Friend-test package validation recorded: zip_integrity | `complete` | `passed` |
| Next-step command script exists: closure_suite | `complete` | `scripts/run_10_10_closure_suite.sh --sender-device R5CY22X6MSB --target-device d948ffd0 --mac-host <MAC_LAN_IP>` |
| Next-step command script exists: physical_inline_relay | `complete` | `scripts/run_physical_inline_relay_trials.sh --sender-device R5CY22X6MSB --target-device d948ffd0 --mac-host <MAC_LAN_IP>` |
| Next-step command script exists: route_benchmark | `complete` | `scripts/run_route_benchmark_trials.sh --device R5CY22X6MSB --device d948ffd0 --min-samples-per-route 10` |
| Next-step command script exists: wifi_direct_two_device | `complete` | `scripts/capture_debug_route_evidence.sh --device R5CY22X6MSB --device d948ffd0 --label wifi-direct-binding-diagnostics-2026-06-11 --start-mesh-before-export --transport-profile wifi-direct-only --start-mesh-settle-ms 16000 --debug-send-body wifi-direct-binding-diagnostics --sync-after-debug-send --sync-attempts 1` |

## Boundary

This is a live/current-state audit only. It does not close two-device
Wi-Fi Direct, physical attack, route benchmark or production security
gates; it records whether current evidence is sufficient to claim closure.
