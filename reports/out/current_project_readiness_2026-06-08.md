# Kraken Current Project Readiness

Дата: 2026-06-08.

Scope: текущая ветка `codex/android-research-panel-report-viewer`.

Этот snapshot supersedes старые readiness entrypoints:

- `reports/out/current_project_readiness_2026-06-07.md`;
- `reports/out/kraken_10_10_readiness_plan_2026-06-07.md`.

Старые файлы остаются historical snapshots. Этот файл является 2026-06-08
snapshot; для актуальной оценки после 2026-06-10 нужно использовать его вместе
с follow-up слоем:

- `reports/out/kraken_10_10_gap_audit_2026-06-08.md`;
- `reports/out/kraken_10_10_followup_audit_2026-06-10.md/json`;
- `reports/out/kraken_10_10_completion_matrix_2026-06-10.md/json`;
- `reports/out/kraken_10_10_completion_matrix_audit_2026-06-10.md/json`;
- `reports/out/kraken_release_hard_gate_2026-06-10.md/json`;
- `reports/out/route_benchmark_runner_2026-06-10.md/json`;
- `reports/out/route_benchmark_preflight_2026-06-11.md/json`;
- `reports/out/physical_inline_relay_runner_2026-06-10.md/json`;
- `reports/out/single_device_partial_evidence_2026-06-10.md/json`;
- `reports/out/wifi_direct_two_device_delivery_2026-06-10.md`;
- `reports/out/wifi_direct_network_state_diagnostics_2026-06-11.md`;
- `reports/out/two_device_route_specific_smoke_2026-06-08.md/json`;
- `reports/out/route_evidence_consistency_audit_2026-06-08.md/json`;
- `reports/out/wifi_direct_only_capture_2026-06-08.md/json`;
- `reports/out/wifi_direct_diagnostics_2026-06-08.md/json`;
- `reports/out/lan_malformed_injection_attempt_2026-06-08.md/json`;
- `reports/out/mac_inline_relay_tcp_preflight_2026-06-08.md/json`;
- `reports/out/mac_inline_relay_attempt_2026-06-08.md/json`;
- `reports/out/mac_inline_relay_adb_forward_demo_2026-06-08.md/json`;
- `reports/out/route_benchmark_summary_2026-06-08.md/json`;
- `reports/out/adamova_product_path_demo.md`;
- `reports/out/dissertation_final_insert_packet_2026-06-08.md`;
- `docs/kraken-production-readiness-roadmap.md`.

## Короткий вывод

Kraken сейчас можно защищать как сильный dissertation research prototype и
evidence packet. Но его нельзя честно закрыть как безусловный `10/10`, если
в `10/10` входят full route/attack/security coverage, Wi-Fi Direct, Mac
inline MITM, physical hostile packet injection, statistically meaningful
reliability benchmark или production security.

Текущая оценка:

| Scope | Оценка | Почему |
| --- | ---: | --- |
| Dissertation research prototype | 8/10 | QR trust, chats, LAN/BLE evidence, route counters, Adamova product path, dissertation insert packet и claim boundaries готовы |
| Route/attack evidence packet | 7/10 | LAN/BLE smoke стал сильнее, но Wi-Fi Direct, Mac inline MITM, hostile physical injection и benchmark не закрыты |
| Production secure messenger | not ready / not scored | Minimal release-like hard gate now blocks accidental release builds; Keystore identity, signed QR, encrypted packets, release signing и security review остаются production roadmap |

## Что закрыто после snapshot 2026-06-07

| Area | Current status | Evidence |
| --- | --- | --- |
| Route-specific LAN/BLE smoke | Done for current two-device research smoke | `reports/out/two_device_route_specific_smoke_2026-06-08.md/json` |
| LAN Bluetooth-off path | Passed both directions after stale-peer cleanup | Samsung -> Xiaomi latency sample `1391 ms`; Xiaomi -> Samsung `929 ms` |
| BLE observed retry path | Passed with `ble-gatt`, receipts and latency samples | Samsung `7192 ms`; Xiaomi `7145 ms` |
| Adamova product path | Done for research prototype | `reports/out/adamova_product_path_demo.md` |
| Dissertation insert packet | Done | `reports/out/dissertation_final_insert_packet_2026-06-08.md` |
| Evidence consistency guard | Done | `reports/out/route_evidence_consistency_audit_2026-06-08.md/json` |
| Production claim separation | Done as roadmap boundary | `docs/kraken-production-readiness-roadmap.md` |
| Branded welcome tagline | Installed on visible phones | Live ADB check showed `ПРИВАТНО • ЛОКАЛЬНО • СВОБОДНО` on Xiaomi and Samsung |
| Debug rejection probe | Fresh phone export captured | `reports/out/debug_route_evidence_capture_2026-06-08.md/json`; local hostile packets now pass through `MeshInboxProcessor`; still not physical LAN/BLE packet injection |
| Wi-Fi Direct transport | Implemented prototype; one-way Xiaomi -> Samsung route evidence captured; bidirectional/reliability/negative evidence still open | `reports/out/wifi_direct_readiness_capture_2026-06-08.md/json`, `reports/out/wifi_direct_only_capture_2026-06-08.md/json`, `reports/out/wifi_direct_diagnostics_2026-06-08.md/json`, `reports/out/wifi_direct_two_device_delivery_2026-06-10.md`, `reports/out/wifi_direct_network_state_diagnostics_2026-06-11.md`; Samsung and Xiaomi have P2P IPs/listeners and repeated Xiaomi -> Samsung delivery, but Samsung -> Xiaomi still times out |
| LAN malformed injection attempt | Injector and diagnostics added; current Mac-to-phone TCP path blocked | `reports/out/lan_malformed_injection_attempt_2026-06-08.md/json`; ping reached both phones and Android listeners existed, but same-subnet TCP attempts timed out and malformed counters stayed `0` |
| Mac inline relay TCP preflight | Android-to-Mac TCP reachability confirmed for a system listener; inline relay still open | `reports/out/mac_inline_relay_tcp_preflight_2026-06-08.md/json`; Samsung and Xiaomi both reached `/usr/bin/nc` listener on Mac, but this is not Android A -> Mac -> Android B relay evidence |
| Mac inline relay attempt | Android A -> Mac LAN frame capture proved; physical Mac -> Android B forwarding still blocked | `reports/out/mac_inline_relay_attempt_2026-06-08.md/json`; Samsung lan-only sender delivered one Kraken LAN frame to the Mac relay, but direct Wi-Fi relay forward to Xiaomi timed out and Xiaomi counters stayed `0` |
| Mac inline relay ADB-forward demo | Bounded Android A -> Mac -> Android B endpoint delivery proved through `adb forward` | `reports/out/mac_inline_relay_adb_forward_demo_2026-06-08.md/json`; relay forwarded packet `packet-9bc33267-1090-44f8-9a89-5cf9db047849` to Xiaomi endpoint, Xiaomi stored message `message-38306df1-3ba1-4144-9ddc-bd4be9ed911a` as `INCOMING` / `DELIVERED_TO_PEER`; receipt path remained queued with `UNKNOWN_PEER` |
| Route benchmark gate | Aggregator and runner added; current sample count still insufficient | `reports/out/route_benchmark_summary_2026-06-08.md/json` and `reports/out/route_benchmark_preflight_2026-06-11.md/json`; BLE has 2 delivered latency samples, LAN has 2 unique delivered latency samples in the older summary; fresh two-phone runner preflight produced 0 delivered latency samples and remains below the min gate |
| Release-like hard gate | Minimal Gradle hard gate done after this snapshot | `reports/out/kraken_release_hard_gate_2026-06-10.md/json`; `assembleRelease` is blocked by `:app:krakenReleasePrototypeGate`, while production crypto/Keystore/signed QR remain open |
| Route benchmark runner | Repeatable runner added and preflight executed after this snapshot | `reports/out/route_benchmark_runner_2026-06-10.md/json` and `reports/out/route_benchmark_preflight_2026-06-11.md/json`; latest execution does not prove reliability because it produced no delivered latency samples |
| Physical inline relay runner | Repeatable physical Mac relay runner added after this snapshot | `reports/out/physical_inline_relay_runner_2026-06-10.md/json`; requires two phones and does not prove attack evidence until executed |
| Single-device 06-10 partial evidence | Current APK install and one-phone diagnostics captured on Xiaomi | `reports/out/single_device_partial_evidence_2026-06-10.md/json`; not two-device route/attack/benchmark evidence |

## Verified Numbers

| Block | Current evidence | Boundary |
| --- | --- | --- |
| Adamova gate | 20 profiles; weak/invalid 10; baseline accepted weak 8/10; discriminant-only accepted weak 6/10; Adamova accepted weak 0/10; rejected/blocked weak 10/10; controls accepted 10/10 | Admission policy for experimental profile, not production message crypto |
| Adamova latency | median `10.632542 ms`; p95 `15.835666 ms` | Local controlled profile gate latency |
| LAN smoke | `lan-nsd-tcp`; delivered both directions after stale-peer cleanup; latency samples `1391 ms` and `929 ms` | Fresh two-device smoke, not reliability benchmark |
| BLE smoke | `ble-gatt`; delivered retry path with receipts; latency samples `7192 ms` and `7145 ms` | Fresh route-counter evidence plus older physical UI evidence |
| Debug local hostile probe export | Samsung and Xiaomi both exported `debug_local_inbox_packet_injection_and_queue_retry_probe`; unknown/wrong/duplicate counters `1/1/1`; app source `clean_commit_6853fc3` | Debug-only phone export, not physical hostile packet injection |
| Wi-Fi Direct readiness export | Samsung and Xiaomi both exported Wi-Fi Direct `active=true` from app source `clean_commit_cd1adf5`; selected route stayed `lan-nsd-tcp` | Service readiness only, not Wi-Fi Direct delivery or negative-test evidence |
| Wi-Fi Direct-only export | Samsung and Xiaomi both exported `enabled_transport_modes=["wifi-direct"]`, Wi-Fi Direct `active=true`, `selectedRoute=none`, route attempts `0`, app source `clean_commit_254b209` | Fallback is removed for debug capture, but Wi-Fi Direct peer route is still not proven |
| Wi-Fi Direct diagnostics export | Samsung and Xiaomi both exported from clean commit `82cb6ff`: `enabled_transport_modes=["wifi-direct"]`, `registration_state=wifi-direct:registered:_kraken._tcp`, `discovery_state=wifi-direct:discovering:_kraken._tcp`, `transport_discovered_peer_count=0`, `selectedRoute=none` | Permission/radio/service and registration/discovery start are proven; peer discovery, route delivery and negative tests remain open |
| LAN malformed injection attempt | Host-side injector attempted 8 same-subnet malformed TCP cases against `192.168.0.201:38147` and `192.168.0.121:43113`; successful writes `0`; Android malformed counters `0 -> 0` | Injector exists, but physical LAN rejection evidence is not closed in this Wi-Fi environment |
| Mac inline relay TCP preflight | Samsung and Xiaomi both pinged Mac `192.168.0.123` with 0% loss and delivered payloads to a system `/usr/bin/nc` listener | TCP reachability only; not Android LAN frame forwarding or MITM evidence |
| Mac inline relay attempt | Samsung `lan-only` sent packet `packet-fbfa95d2-d77d-4507-ae07-36ce62dc4f9c` to Mac relay; Mac relay received 1 frame and decoded sender/recipient fingerprints | Mac -> Xiaomi forward timed out; Android B accepted/inbound counters remained `0` |
| Mac inline relay ADB-forward demo | Samsung `lan-only` sent packet `packet-9bc33267-1090-44f8-9a89-5cf9db047849`; Mac relay forwarded 1 frame to `127.0.0.1:54035`; Xiaomi counters showed `accepted_connections=1`, `inbound_packets=1`, `packets_received=1`; Xiaomi message store contains the body `inline relay direct normal 0953` as `INCOMING` / `DELIVERED_TO_PEER` | Bounded debug endpoint proof only; not physical Wi-Fi Mac -> Android B forwarding, not MITM resistance, not full receipt delivery |
| Route benchmark summary | BLE `n=2`, median `7168.5 ms`, p95 `7189.65 ms`; LAN `n=2`, median `1160.0 ms`, p95 `1367.9 ms`; overall status `insufficient_n_for_reliability_claim` | Descriptive aggregation only; not repeatable reliability evidence |
| Route evidence consistency | 28 raw JSON/markdown pairs scanned; 3 stale markdown summaries quarantined; 32 mismatched fields | Raw markdown summaries are not source-of-truth when they disagree with JSON |

## What Can Be Claimed

- Kraken is a research Android prototype with local identity, QR-established
  relationships, chats and route-aware LAN/BLE evidence.
- Adamova participates in product policy for the experimental crypto profile:
  accepted metadata flows through QR, relationship/session and packet policy.
- Weak or native-unavailable experimental profile is blocked before becoming
  message-capable.
- Standard reviewed profile is not blocked by Adamova and is not claimed as
  Adamova-protected.
- Current two-phone smoke demonstrates specific LAN/BLE prototype scenarios.

## What Must Not Be Claimed

- Do not call Kraken a production secure messenger.
- Do not claim production E2EE, production packet signatures or encrypted
  packet payloads.
- Do not claim Wi-Fi Direct is bidirectional, repeatably reliable or
  route-negative-tested end-to-end.
- Do not call the project `10/10` while Wi-Fi Direct remains without
  bidirectional two-phone route, route-negative and reliability evidence.
- Do not claim Mac-in-the-middle was physically run between Android devices.
- Do not claim LAN/BLE repeatable reliability from current smoke samples.
- Do not use ignored raw `route_specific_evidence_summary_latest.md` files as
  current source-of-truth without JSON consistency check.

## Open Gates Before Full 10/10

P0 for full route/attack/security coverage:

1. Mac inline relay attack against Android devices: normal, drop, duplicate,
   tamper, with Android-side counters before/after. Current status: Android
   to Mac TCP reachability is confirmed for a system listener; Android A to
   Mac LAN frame capture is confirmed; and a bounded ADB-forward demo proves
   relay delivery into Android B's app store when the frame reaches the Android
   listener. Physical Wi-Fi Mac -> Android B forwarding, attack modes and
   cryptographic MITM resistance remain open.
2. Physical hostile packet injection over LAN/BLE/Wi-Fi Direct for unknown
   peer, wrong recipient, duplicate/replay and malformed frames. Current
   status: LAN malformed injector exists, but Mac-to-phone TCP attempts timed
   out in the current Wi-Fi environment; BLE/Wi-Fi Direct external injection is
   not captured.
3. Repeated LAN/BLE/Wi-Fi Direct route benchmark: N runs, median/p95 latency,
   loss and retry distribution. Current status: aggregator and runner exist,
   but BLE/LAN have only 2 delivered latency samples per route in the older
   summary; the 2026-06-11 two-phone preflight produced 0 delivered latency
   samples and all claimed routes remain below min gate `10`.
4. Wi-Fi Direct route evidence and negative tests on phones. Current status:
   prototype transport implemented; readiness active; repeated Xiaomi ->
   Samsung delivery and P2P network/listener state are captured. Samsung ->
   Xiaomi delivery still times out, and route-bound Wi-Fi Direct negative tests
   are not captured.

Production track remains separate:

1. Android Keystore identity.
2. Signed QR invite/response/final confirmation.
3. Reviewed encrypted packet envelope with key agreement, KDF, AEAD and replay
   model.
4. Minimal release-like hard gate is now present; production signing and
   security review remain open.

## Next Best Work

If the goal is dissertation handoff, use this snapshot,
`kraken_10_10_followup_audit_2026-06-10.md` and
`dissertation_final_insert_packet_2026-06-08.md`.

If the goal is literal `10/10` route/attack/security evidence, next work should
start with Mac inline relay attack or physical hostile packet injection. Those
are stronger blockers than further documentation polish.
