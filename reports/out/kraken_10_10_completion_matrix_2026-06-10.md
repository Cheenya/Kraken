# Kraken 10/10 Completion Matrix

Date: `2026-06-10`.

Scope: current branch `codex/android-research-panel-report-viewer`.

## Verdict

`10/10` is not complete for full route/attack/security coverage.

Machine verdict: `not_complete`.

The project is dissertation-usable as a bounded research prototype, but the
completion matrix still has open gates. Samsung and Xiaomi are now visible to
ADB. Wi-Fi Direct has repeated Xiaomi -> Samsung route-delivery evidence with
network-state captures, but full `10/10` still requires stable bidirectional
delivery, route-bound negative tests, physical attack evidence and benchmark
coverage.

## Matrix

| Gate | Required evidence for `10/10` | Current evidence | Status |
| --- | --- | --- | --- |
| Wi-Fi Direct route | Two phones using `wifi-direct` only; peer discovery succeeds; selected route is `wifi-direct`; message delivery and route attempts are captured | 2026-06-10 captured one-way Xiaomi -> Samsung delivery smoke. 2026-06-11 repeated Xiaomi -> Samsung with network-state captures. Later P2P-bind diagnostics narrowed the blocker: P2P ping and shell TCP work in both directions, app listeners bind to P2P IPv4 and are locally reachable, but remote TCP to Kraken app listeners still times out | open |
| Wi-Fi Direct negative tests | Unknown peer, wrong recipient, duplicate/replay, TTL/malformed packet rejection captured while Wi-Fi Direct is the active route | Local hostile debug counters exist; not route-bound to Wi-Fi Direct radio path | open |
| Physical Mac inline relay | Android A -> Mac -> Android B physical LAN forwarding for `normal/drop/duplicate/tamper`, with before/send/relay/after artifacts and Android-side counters | Android A -> Mac frame capture exists; ADB-forward endpoint demo exists; physical Mac -> Android B forwarding timed out | open |
| Physical hostile packet injection | LAN/BLE/Wi-Fi Direct external malformed/unknown/wrong/duplicate cases reach the phone listeners and increment rejection counters | LAN injector exists but TCP writes timed out; local debug probe is not radio-path injection | open |
| Route benchmark | N-run two-device route benchmark with min sample gate met for every claimed route; median/p95/loss/retry reported | Summary exists with LAN `n=2`, BLE `n=2`, below min gate `10`; 2026-06-11 runner preflight executed on both phones but produced 0 delivered latency samples and `insufficient_n_for_reliability_claim` | open |
| Release/prototype hard gate | Release-like builds fail while prototype crypto/identity/QR remain research-only | `krakenReleasePrototypeGate` blocks release-like builds | closed for minimal gate |
| Production security track | Keystore identity, signed QR, reviewed encrypted packet envelope, release signing and security review | Roadmap only; production secure messenger is not claimed | open if production security is in scope |
| Adamova product path | Experimental profile metadata flows through QR/relationship/session/packet; weak/mismatch/native-unavailable blocked; standard profile is not Adamova-claimed | `reports/out/adamova_product_path_demo.md` and tests cover this research path | closed for research prototype |
| LAN/BLE dissertation smoke | Route-specific LAN/BLE two-phone prototype evidence with counters and claim boundary | 2026-06-08 LAN/BLE route-specific smoke and BLE evidence exist | closed for smoke, not reliability |

## Commands To Close Open Gates

Wi-Fi Direct route capture:

```bash
scripts/capture_debug_route_evidence.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --label wifi-direct-repeatability-check \
  --transport-profile wifi-direct-only \
  --reuse-running-mesh \
  --debug-send-body "wifi-direct repeatability check" \
  --sync-after-debug-send \
  --sync-attempts 8
```

Route benchmark:

```bash
scripts/run_route_benchmark_trials.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --trials 10 \
  --transport-profile all \
  --min-samples-per-route 10
```

Physical inline relay:

```bash
scripts/run_physical_inline_relay_trials.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --mac-host <MAC_LAN_IP>
```

## Current Device Evidence

The latest ADB check in this pass saw both devices:

- Samsung `R5CY22X6MSB`
- Xiaomi `d948ffd0`

That removes the earlier device-visibility blocker. The current blocker is now
functional: Wi-Fi Direct route delivery has repeated Xiaomi -> Samsung evidence,
but Samsung group-owner -> Xiaomi client reply and route-bound negative tests
remain unproven.

Latest Wi-Fi Direct diagnostic report:

- `reports/out/wifi_direct_network_state_diagnostics_2026-06-11.md`
- `reports/out/wifi_direct_p2p_bind_diagnostics_2026-06-11.md`
- `reports/out/wifi_direct_af_inet_listener_attempt_2026-06-11.md`

Latest route benchmark preflight:

- `reports/out/route_benchmark_preflight_2026-06-11.md`

## Claim Boundary

This matrix is a completion-control artifact. It does not claim production
security, production reliability or completed `10/10`; it records the opposite:
which evidence is still required before those claims become valid.
