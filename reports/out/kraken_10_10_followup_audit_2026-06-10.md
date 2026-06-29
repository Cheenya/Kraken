# Kraken 10/10 Follow-up Audit

Date: 2026-06-10.

Scope: current branch `codex/android-research-panel-report-viewer`, after
release package documentation commit `86a1d67`, plus current Wi-Fi Direct
rediscovery diagnostics WIP.

Update note: this follow-up audit is now a historical layer. The current
completion-control source of truth is
`reports/out/kraken_10_10_completion_matrix_2026-06-10.md`, plus
`reports/out/wifi_direct_network_state_diagnostics_2026-06-11.md` for the
latest Wi-Fi Direct evidence.

## Short Verdict

Kraken still cannot be called full `10/10` if that means complete
route/attack/security coverage.

The main reports from 2026-06-08 are internally consistent: dissertation
research prototype around `8/10`, route/attack evidence around `7/10`,
production secure messenger not ready/not scored. The current WIP makes the
Wi-Fi Direct peer-discovery investigation stronger. On 2026-06-11, Samsung and
Xiaomi are both visible through ADB and repeated Xiaomi -> Samsung Wi-Fi Direct
delivery evidence exists, but bidirectional Wi-Fi Direct, route-bound negative
tests, physical attack evidence and route benchmark evidence are still open.

## Subagent Cross-checks

Three independent read-only audits converged on the same boundary.

| Area | Result | Still open |
| --- | --- | --- |
| Attack / MITM / physical injection | ADB-forward relay demo and desktop relay tests are useful but not full physical attack evidence | Physical Android A -> Mac -> Android B without `adb forward`; `normal/drop/duplicate/tamper` physical runs; hostile LAN/BLE/Wi-Fi Direct injection; cryptographic MITM resistance |
| Release / security boundary | Friend-test APK documentation is honest as `debug research demo`; release-like Gradle builds are now blocked by `krakenReleasePrototypeGate` | Production crypto, Android Keystore identity, signed QR handshake, release signing/security review |
| Evidence consistency | Main readiness/gap/dissertation reports do not overclaim production security; completion matrix is the current gate tracker | Wi-Fi Direct has repeated one-way Xiaomi -> Samsung evidence but not bidirectional/reliability evidence; raw stale summaries remain quarantined; release package should be cited only as packaging/installability evidence |

## Wi-Fi Direct WIP

Current code changes target the exact blocker found in
`reports/out/wifi_direct_diagnostics_2026-06-08.md`: DNS-SD registration and
discovery start, but peer count remains `0` and `selectedRoute=none`.

Added code-side support:

- periodic Wi-Fi Direct rediscovery loop;
- `WIFI_P2P_PEERS_CHANGED_ACTION` handling;
- `requestPeers` diagnostics;
- `p2p_visible_device_count` export;
- `p2p_this_device_status` export;
- `discovery_cycle_count` export.

This does not fully close Wi-Fi Direct. Later captures in
`reports/out/wifi_direct_two_device_delivery_2026-06-10.md` and
`reports/out/wifi_direct_network_state_diagnostics_2026-06-11.md` prove a
bounded Xiaomi -> Samsung route-delivery path and P2P network state, but
Samsung -> Xiaomi delivery, route-bound negative tests and N-run reliability
remain open.

## Release Hard Gate

`reports/out/kraken_release_hard_gate_2026-06-10.md` records a minimal
release-like build gate:

- `assembleDebug` still passes for friend testing;
- `assembleRelease` fails as expected on `:app:krakenReleasePrototypeGate`;
- release-like builds are blocked while `PrototypeNoSecurityPacketCrypto`,
  placeholder identity and unsigned QR handshake remain research-only.

This closes only the accidental release/prod build gap. It does not implement
production crypto, Android Keystore identity, signed QR handshake, encrypted
packet envelope, release signing or security review.

## Route Benchmark Runner

`reports/out/route_benchmark_runner_2026-06-10.md` records a repeatable N-run
benchmark harness:

- `scripts/run_route_benchmark_trials.sh` requires at least two ADB devices;
- it runs repeated debug route-evidence captures;
- it aggregates captured JSON files through `scripts/build_route_benchmark_summary.py`;
- raw outputs go under ignored `artifacts/route-benchmark/`.

This closes only the benchmark procedure gap. A fresh 2026-06-11 two-device
preflight ran the harness, but it still produced
`insufficient_n_for_reliability_claim` with zero delivered latency samples.
Reliability evidence remains open until every claimed route passes the minimum
sample gate.

## Physical Inline Relay Runner

`reports/out/physical_inline_relay_runner_2026-06-10.md` records a repeatable
physical Mac inline relay harness:

- `scripts/run_physical_inline_relay_trials.sh` requires distinct sender and
  target ADB devices;
- it runs target before-capture, relay startup, sender debug-send through a
  manual peer pointed at the Mac, target after-capture and a consolidated
  manifest;
- default modes are `normal/drop/duplicate/tamper`;
- raw outputs go under ignored `artifacts/physical-inline-relay/`.

This closes only the physical relay procedure gap. It does not close physical
attack evidence until executed on two phones with successful before/send/relay/
after artifacts for the claimed modes.

## Completion Matrix

`reports/out/kraken_10_10_completion_matrix_2026-06-10.md` is the current
completion-control artifact. It records every open/closed gate, required
evidence, current evidence and exact commands for the remaining two-device
captures. Its verdict is still `not_complete`.

`reports/out/kraken_10_10_completion_matrix_audit_2026-06-10.md` validates that
the matrix remains internally consistent, all referenced evidence files exist,
the active entrypoints point to it and the open gates are still open. Its
expected result is `complete=false` until two-device route/attack/benchmark
evidence closes the matrix.

## Validation So Far

- `./gradlew testDebugUnitTest --tests com.disser.kraken.mesh.MeshEvidenceExportTest --tests com.disser.kraken.mesh.MeshServiceTest --tests com.disser.kraken.mesh.MeshTransportSelectionTest`: passed.
- `pytest tests/test_android_policy_guards.py`: 46 passed.
- `python3 -m py_compile scripts/audit_10_10_completion_matrix.py`: passed.
- `scripts/audit_10_10_completion_matrix.py`: passed; audit result `complete=false`, `open_gate_count=5`.
- `bash -n scripts/capture_debug_route_evidence.sh`: passed.
- `bash -n scripts/run_physical_inline_relay_trials.sh`: passed.
- `pytest tests/test_desktop_lan_relay.py tests/test_lan_frame_codec.py`: 12 passed.
- `git diff --check`: passed.
- `./gradlew assembleDebug`: passed.

## Device Validation Status

Partially completed in this pass.

`reports/out/single_device_partial_evidence_2026-06-10.md` records the earlier
current APK install and debug exports on Xiaomi `d948ffd0`. It is superseded for
device visibility by the completion matrix and 2026-06-11 Wi-Fi Direct reports,
which use both Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0`.

What it proves:

- current APK installs on Xiaomi;
- local hostile debug counters are observable on one device;
- Wi-Fi Direct-only diagnostics export the new rediscovery fields.

What it does not prove:

- bidirectional Wi-Fi Direct message delivery;
- Wi-Fi Direct route-bound negative tests;
- two-device LAN/BLE/Wi-Fi Direct route evidence;
- Mac inline relay evidence;
- physical hostile packet injection;
- N-run reliability.

Required next captures:

- `scripts/capture_debug_route_evidence.sh --device R5CY22X6MSB --device d948ffd0 --label wifi-direct-bidir-preflight --start-mesh-before-export --transport-profile wifi-direct-only --start-mesh-settle-ms 30000`
- `scripts/run_route_benchmark_trials.sh --device R5CY22X6MSB --device d948ffd0 --trials 10 --transport-profile all --min-samples-per-route 10`

## Open P0 Gates

1. Wi-Fi Direct peer discovery, route delivery and negative tests.
2. Physical Mac inline relay attack modes: `normal/drop/duplicate/tamper`.
3. Physical hostile packet injection over LAN/BLE/Wi-Fi Direct.
4. Repeatable route benchmark with enough samples for median/p95/loss/retry
   claims.
5. Production release/security track, if `10/10` is intended to include
   production secure messenger rather than dissertation research prototype.
