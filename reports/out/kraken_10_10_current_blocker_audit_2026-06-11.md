# Kraken 10/10 Current Blocker Audit

Audit time: 2026-06-11 15:46:40 MSK.

Git: `codex/android-research-panel-report-viewer@7d5f27a + report update pending commit`.

Current verdict: `not_complete`.

This audit exists to prevent a false closure after the friend-test APK was
rebuilt. The APK package is current and installable, but the full `10/10`
question is still not closed for route, physical attack and benchmark evidence.

## Current Device State

Observed through `adb devices -l` during this audit:

| Serial | Model | State |
| --- | --- | --- |
| `R5CY22X6MSB` | `SM_S938B` | `device` |
| `d948ffd0` | `2201122G` | `device` |

Current correction: Samsung and Xiaomi are both visible in ADB at this audit
point. The previous same-day blocker text that said Xiaomi was absent is stale
and must not be used as the current device-state evidence.

## Latest Two-Device Closure Attempt

Artifact root:
`artifacts/10-10-closure-suite/20260611-132456-closure-suite-2026-06-11`.

| Gate | Latest observed result | Verdict |
| --- | --- | --- |
| Wi-Fi Direct sender -> target | Wi-Fi Direct service active on both phones, but `selected_route=none`, `debug_send_success=false`, `debug_send_error=UNKNOWN_PEER` | still open |
| Wi-Fi Direct target -> sender | Samsung observed Xiaomi over Wi-Fi Direct, but debug send still ended as `UNKNOWN_PEER`; no successful message-capable Wi-Fi Direct send | still open |
| Route benchmark | `overall_status=insufficient_n_for_reliability_claim`; latency samples: BLE `0`, LAN `0`, Wi-Fi Direct `0` | still open |
| Physical Mac inline relay | Retry artifact `artifacts/physical-inline-relay/20260611-133356-physical-inline-relay-2026-06-11-retry`; Mac received one frame in each mode, but normal forwarding to Xiaomi failed (`timed out` / `connection refused`) and target inbound stayed `0` | still open |

## Latest Fix Validation

| Fix | Evidence | Result |
| --- | --- | --- |
| Wi-Fi Direct group-owner host fallback | Added `WifiDirectArpTable` and `WifiDirectTransport` ARP/single-P2P-client fallback; installed debug APK on Samsung and Xiaomi; latest Samsung send still had `transport_discovered_peer_count=0` in `artifacts/debug-route-evidence/20260611-135810-wifi-direct-p2p-arp-samsung-send-2026-06-11` | helper is tested, but Wi-Fi Direct delivery still open |
| Wi-Fi Direct binding diagnostics | `artifacts/debug-route-evidence/20260611-141456-wifi-direct-binding-diagnostics-2026-06-11`; Samsung: `p2p_visible_device_count=2`, `p2p_service_found_count=0`, `p2p_txt_record_count=0`; Xiaomi: `p2p_txt_bound_peer_count=2`, `relationship_peer_seen_by_transport=true`, but TCP connect to Samsung `192.168.49.1:37549` timed out | diagnosis improved; Wi-Fi Direct delivery still open |
| Wi-Fi Direct Java `ServerSocket` listener | `artifacts/debug-route-evidence/20260611-142132-wifi-direct-server-socket-2026-06-11`; Xiaomi still bound Samsung TXT and selected `wifi-direct`, but TCP connect to Samsung `192.168.49.1:39259` timed out | raw socket hypothesis not sufficient; delivery still open |
| Wi-Fi Direct shell TCP control | Android shell `nc` from Xiaomi to a Samsung shell listener on `192.168.49.1:45678` delivered `kraken`; app UID listeners were absent from `/proc/net/tcp` shortly after debug broadcast completion | radio/system TCP path can work; remaining gap is app listener lifecycle/orchestration, not only device network reachability |
| Wi-Fi Direct sender wait + post-hold target capture | `artifacts/debug-route-evidence/20260611-150651-wifi-direct-sender-ack-short-wait-2026-06-11` and `artifacts/debug-route-evidence/20260611-150620-wifi-direct-target-ack-short-wait-post-hold-2026-06-11`; sender artifact is valid and shows `debug_send_wait_satisfied=false`, `debug_send_success=false`, `debug_send_error=UNKNOWN_PEER`; target artifact waited `post_hold_refresh_wait_sec=93` and still shows `accepted_connections=0`, `inbound_packets=0` | orchestration evidence is now more honest; Wi-Fi Direct route still not message-capable in this run |
| TCP frame ACK semantics | `LanFrameCodec.writeAck/readAck`, `DirectLanTransport`, `WifiDirectTransport`, `LanFrameCodecTest`; sender-side success now requires an ACK after receiver decode/enqueue instead of only socket write/flush | improves evidence quality; does not by itself close Wi-Fi Direct discovery/delivery |
| Wi-Fi Direct fixed-port fallback | `4794326`; `WifiDirectTransport` binds Wi-Fi Direct listener to `DEFAULT_WIFI_DIRECT_PORT=48381` with dynamic fallback if occupied, and creates a relationship fallback peer from group owner address or single P2P client ARP when DNS-SD/TXT is missing | removes DNS-SD/TXT as the only endpoint source; does not close full bidirectional proof by itself |
| Wi-Fi Direct Xiaomi -> Samsung ACK-backed delivery slice | `artifacts/debug-route-evidence/20260611-152444-wifi-direct-sender-fixed-port-foreground-target-2026-06-11` and `artifacts/debug-route-evidence/20260611-152406-wifi-direct-target-fixed-port-foreground-2026-06-11`; sender: `selected_route=wifi-direct`, `debug_send_success=true`, `wifi_direct_last_send_host=192.168.49.1`, `wifi_direct_last_send_port=48381`; target post-send: `selected_route=wifi-direct`, `accepted_connections=1`, `inbound_packets=1`, `relationship_peer_seen_by_transport=true` | closes one foreground-target Xiaomi -> Samsung Wi-Fi Direct delivery slice; not yet bidirectional/reliability/negative evidence |
| Wi-Fi Direct Samsung -> Xiaomi fallback sender slice | `artifacts/debug-route-evidence/20260611-152749-wifi-direct-sender-samsung-fixed-port-foreground-target-2026-06-11`; Samsung sender selected `wifi-direct`, used fallback endpoint `192.168.49.59:48381`, and `debug_send_success=true` with `p2p_txt_bound_peer_count=0` | demonstrates reverse sender-side fallback success, but paired Xiaomi target artifact was pre-send and does not prove target counters |
| Debug Wi-Fi Direct foreground evidence mode | `7d5f27a`; `MeshForegroundService.ACTION_START_DEBUG_WIFI_DIRECT_ONLY`, `DebugEvidenceReceiver` extra `start_foreground_wifi_direct`, `capture_debug_route_evidence.sh --start-foreground-wifi-direct` | gives a controlled target-lifecycle tool for evidence captures; Android still denies background FGS start unless the app is foreground |
| Reverse Wi-Fi Direct foreground-service attempt | Background start failed in `artifacts/debug-route-evidence/20260611-153635-wifi-direct-target-xiaomi-foreground-service-start-2026-06-11` with `startForegroundService() not allowed`; foreground app start succeeded in `20260611-153947-wifi-direct-target-xiaomi-foreground-service-settled-2026-06-11` with Xiaomi listener `192.168.49.59:48381`; Samsung sender attempts `20260611-153816...` and `20260611-154023...` still ended `UNKNOWN_PEER`/invalid missing files | target lifecycle tooling improved, but Samsung -> Xiaomi route proof remains open |
| Xiaomi debug receiver ANR guard | `artifacts/debug-route-evidence/20260611-140151-xiaomi-debug-receiver-anr-guard-2026-06-11`; `last_sync_summary=skipped_after_failed_direct_send`; no fresh ANR in checked logcat window | debug capture robustness improved |
| Physical relay stale target port | `artifacts/physical-inline-relay/20260611-140357-physical-relay-fresh-port-normal-2026-06-11-retry`; target port stayed `39279 -> 39279`, relay used `39279` | runner stale-port bug closed; actual Mac -> Xiaomi forward still timed out |

## Closed Since Previous Audit

| Gate | Status | Evidence | Boundary |
| --- | --- | --- | --- |
| Legacy Wi-Fi Direct location permission lint | closed | `05ba28d`; `./gradlew :app:lintDebug` passed; Android <=12 has capped `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`; Android 13+ remains `NEARBY_WIFI_DEVICES` with `neverForLocation` | Closes manifest/lint policy only, not Wi-Fi Direct delivery |
| Friend-test package | closed for research/demo packaging | `reports/out/kraken_release_package_2026-06-11.md`; ZIP under `artifacts/releases/20260611-kraken-release-05ba28d/`; Samsung install/launch passed | Debug research demo package, not production secure release evidence |
| Physical relay stale target port in runner | closed for runner correctness | `scripts/run_physical_inline_relay_trials.sh` now captures fresh target port per mode and target-after without restarting the listener; normal smoke kept target port stable | Does not close physical attack evidence because Mac -> Xiaomi forwarding still timed out |
| Debug receiver ANR on failed direct send | closed for debug capture robustness | `DebugEvidenceReceiver` skips long sync retry after failed direct debug send; Xiaomi capture returned JSON instead of ANR | Does not close Wi-Fi Direct delivery |
| Wi-Fi Direct TXT binding observability | closed for diagnostics | `WifiDirectPeerBinding`, `p2p_service_found_count`, `p2p_txt_record_count`, `p2p_txt_bound_peer_count`, `p2p_unbound_visible_device_count` exported in route evidence and debug capture manifests | Does not close delivery; it only separates P2P visibility, DNS-SD binding and TCP send failures |
| Wi-Fi Direct raw listener replacement | closed as implementation experiment | `WifiDirectTransport` now uses Java `ServerSocket` like LAN transport instead of raw `Os.socket`; targeted Gradle tests and `assembleDebug` passed; installed on both phones | Does not close delivery; live capture still timed out |
| TCP frame write-only success | closed as a misleading success criterion | `LanFrameCodec` ACK support plus Direct LAN/Wi-Fi Direct `readAck` before `TransportSendResult(true)`; `LanFrameCodecTest` and policy guards pass | Closes sender-side overclaim only; bidirectional physical route evidence remains open |
| Wi-Fi Direct fixed-port endpoint fallback | closed for implementation slice | `4794326 Add Wi-Fi Direct fixed-port fallback`; policy guard requires `DEFAULT_WIFI_DIRECT_PORT`, `fallbackPeerForRelationshipPeer`, and explicit `wifi-direct-dns-sd-fallback` diagnostics | Closes DNS-SD-only endpoint dependency; does not close full two-direction/reliability/negative route evidence |
| Debug foreground Wi-Fi Direct evidence mode | closed for diagnostic tooling | `7d5f27a Add debug Wi-Fi Direct foreground evidence mode`; policy guard requires `ACTION_START_DEBUG_WIFI_DIRECT_ONLY`, `BuildConfig.DEBUG`, and `--start-foreground-wifi-direct` | Helps lifecycle captures; does not override Android background FGS restrictions and does not close reverse route proof |

## Still Open

| Gate | Required Before `10/10` | Current blocker |
| --- | --- | --- |
| Wi-Fi Direct route delivery | Two connected phones on current build; Wi-Fi Direct-only peer discovery/fallback; selected route `wifi-direct`; ACK-backed delivery; bidirectional delivery and post-send counters | Improved but still open: fixed-port fallback produced a proven foreground-target Xiaomi -> Samsung slice with target `accepted_connections=1`, `inbound_packets=1`; Samsung -> Xiaomi has one sender-side fallback success, but later foreground-service attempts still failed to produce a valid reverse target-counter proof (`UNKNOWN_PEER` or missing sender files). Remaining blocker is reverse route formation plus reliable post-send target counters, then repeated/bidirectional evidence |
| Wi-Fi Direct negative tests | Unknown peer, wrong recipient, duplicate/replay, TTL and malformed rejection while Wi-Fi Direct is the active route | Requires a working two-phone Wi-Fi Direct message route first; current Wi-Fi Direct attempt is not message-capable |
| Physical Mac inline relay modes | Android A -> Mac -> Android B physical LAN forwarding for `normal/drop/duplicate/tamper`, with before/send/relay/after artifacts | Runner now keeps target port stable, but Mac -> Xiaomi normal forwarding still times out and target inbound stays `0` |
| Physical hostile packet injection | External malformed, unknown, wrong-recipient and duplicate cases reach LAN/BLE/Wi-Fi Direct phone listeners and increment rejection counters | LAN/BLE/Wi-Fi Direct radio-path injection evidence is missing |
| Route benchmark min-n | N-run two-device benchmark with min sample gate met for each claimed route; median/p95/loss/retry reported | Current two-device benchmark has zero delivered latency samples and fails the min sample gate |
| Production security track, if in scope | Keystore identity, signed QR, reviewed encrypted packet envelope, release signing and security review | Production secure messenger remains out of current research-demo claims |

## Closed For Research Prototype

| Gate | Status | Evidence | Boundary |
| --- | --- | --- | --- |
| Adamova product path | closed for research prototype | `reports/out/adamova_product_path_demo.md`, `ProductCryptoAdmissionGate`, `OfflineHandshakeServiceTest`, `MeshDeliveryPipelineTest`, `AdamovaAdmissionAttackDemoRunnerTest` | Adamova participates in experimental profile admission policy; it is not production encryption or a production crypto security proof |
| Release-like prototype hard gate | closed for minimal gate | `reports/out/kraken_release_hard_gate_2026-06-10.md`; `assembleRelease` blocked by `:app:krakenReleasePrototypeGate` | Blocks accidental release-like builds while prototype crypto remains; does not implement production security |

## Next Commands

When continuing closure work:

```bash
scripts/run_10_10_closure_suite.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --mac-host <MAC_LAN_IP>
```

```bash
scripts/capture_debug_route_evidence.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --label wifi-direct-ack-wait-two-device-2026-06-11 \
  --start-mesh-before-export \
  --transport-profile wifi-direct-only \
  --start-mesh-settle-ms 16000 \
  --debug-send-wait-ms 25000 \
  --debug-send-body wifi-direct-binding-diagnostics \
  --sync-after-debug-send \
  --sync-attempts 1
```

```bash
scripts/run_route_benchmark_trials.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --min-samples-per-route 10
```

```bash
scripts/run_physical_inline_relay_trials.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --mac-host <MAC_LAN_IP>
```

## Boundary

This is a blocker audit, not a completion certificate. It proves the opposite
of a finished `10/10`: the friend-test APK and lint policy are current, but
route/attack/benchmark gates remain open.
