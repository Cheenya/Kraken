# Kraken 10/10 Gap Audit

Дата: 2026-06-08.

Scope: текущий checkout `/Users/cheenya/Projects/kraken-android-research-panel`, включая dirty working tree и untracked evidence/report слой на 2026-06-08.

## Короткий вывод

Нет, текущий Kraken нельзя честно назвать `10/10`, если под `10/10` понимать полный route/attack/security coverage. Проект стал сильнее старого snapshot `7.5/10`: появились route-specific LAN/BLE counter evidence, Adamova product-path demo и отдельный production roadmap. Но остаются крупные незакрытые gaps:

1. Wi-Fi Direct реализован как prototype transport, но ещё не имеет
   двухтелефонного route/negative evidence; раз он принципиален для проекта,
   без этого нельзя честно писать `10/10`.
2. Mac-in-the-middle / desktop relay attack не прогнан как inline-сценарий между Android-устройствами.
3. BLE/LAN negative cases частично являются debug-counter evidence, а не внешней hostile packet injection.
4. Нет статистически значимого latency/loss/retry reliability benchmark.
5. Production identity/QR signatures/encrypted packets/security review остаются roadmap, а не выполненной частью.
6. Evidence hygiene риск теперь зафиксирован отдельным validator/report: часть старых per-device summaries расходится с sibling JSON и quarantined как не-source-of-truth.

Честная оценка на сейчас:

| Scope | Оценка | Почему |
| --- | ---: | --- |
| Dissertation research prototype | 8/10 | Есть QR trust, chats, LAN/BLE route evidence, route counters, Adamova product path и аккуратные claim boundaries |
| Route/attack evidence packet | 7/10 | LAN/BLE покрыты лучше, Wi-Fi Direct появился как prototype transport, но ещё без phone evidence; Mac-in-the-middle против Android не закрыт, BLE hostile cases не физические |
| Production secure messenger | не оценивать как готовность | Keystore, signed QR, encrypted packets, review gates и release/prototype separation не закрыты |

## Independent Cross-Check Notes

Параллельный audit по transport, attack/MITM, crypto/security boundary и dissertation evidence consistency дал один и тот же вывод: Kraken можно вести в диссертацию как аккуратно ограниченный research prototype, но нельзя закрывать как безусловный `10/10`.

Дополнительные findings из cross-check:

- transport layer: LAN NSD/TCP, BLE GATT и Wi-Fi Direct prototype transport
  реально реализованы; Wi-Fi Direct остаётся open gate по phone evidence.
- attack layer: текущие negative counters сильнее старого manual smoke, но это не физическая hostile packet injection.
- security layer: identity, QR и packet crypto остаются prototype-only; production claims блокируются до Keystore, signed QR, AEAD/encrypted packets, replay/nonce model и release/prototype hard gate.
- evidence layer: свежие dissertation artifacts готовы; старые readiness docs остаются historical snapshots, а stale per-device summaries quarantined через `reports/out/route_evidence_consistency_audit_2026-06-08.md`.

## Что стало сильнее после старого 7.5/10 snapshot

| Область | Текущий статус | Evidence |
| --- | --- | --- |
| Route-specific LAN/BLE evidence | Существенно сильнее, чем старый manual-only слой | `reports/out/two_device_route_specific_smoke_2026-06-08.md`, `reports/out/two_device_route_specific_smoke_2026-06-08.json`, `reports/out/mesh_metrics_summary.json` |
| LAN Bluetooth-off path | После stale-peer cleanup оба направления дошли до `DELIVERED_TO_PEER` с `lan-nsd-tcp` | `two_device_route_specific_smoke_2026-06-08.md`, Scenario Status |
| BLE observed retry path | Оба телефона экспортировали `ble-gatt`, `sent=true`, `delivered=true`, receipt counters и latency samples | `two_device_route_specific_smoke_2026-06-08.md`, BLE export |
| Unknown/wrong/duplicate counters | Есть exported debug counters | `unknown_peer_rejected`, `wrong_recipient_rejected`, `duplicates_dropped` в route-specific JSON |
| Adamova product path | Алгоритм Адамовой показан как product admission policy для experimental profile | `reports/out/adamova_product_path_demo.md` |
| Dissertation insert packet | Есть consolidated artifact с числами и claim boundaries | `reports/out/dissertation_final_insert_packet_2026-06-08.md` |
| Production roadmap | Отдельно вынесен без смешивания с research prototype | `docs/kraken-production-readiness-roadmap.md` |

## Gap 1: Wi-Fi Direct

Статус: prototype transport implemented, two-phone route evidence pending.

Доказательства:

- `KrakenTransportCatalog.WIFI_DIRECT` has `implemented = true`.
- `implementedTransports()` includes `wifi-direct`, `LAN_NSD_TCP` and `BLE_GATT`.
- current Android tree has `WifiDirectTransport` using `WifiP2pManager`,
  DNS-SD TXT identity, local socket accept loop and `LanFrameCodec` frames.
- `reports/out/wifi_direct_readiness_capture_2026-06-08.md/json` shows both
  phones exporting Wi-Fi Direct readiness `active=true` from
  `clean_commit_cd1adf5`; selected route still remained `lan-nsd-tcp`.
- `reports/out/wifi_direct_only_capture_2026-06-08.md/json` shows debug
  capture with `enabled_transport_modes=["wifi-direct"]` on both phones from
  `clean_commit_254b209`; Wi-Fi Direct remained `active=true`, but
  `selectedRoute` remained `none` and route attempts stayed `0`.
- `reports/out/wifi_direct_diagnostics_2026-06-08.md/json` shows a clean
  `82cb6ff` capture where both phones are Wi-Fi Direct-only,
  permission/radio/service are active, service registration is
  `wifi-direct:registered:_kraken._tcp`, discovery is
  `wifi-direct:discovering:_kraken._tcp`, but discovered peer count remains
  `0` and selected route remains `none`.
- permission path includes Android 13+ `NEARBY_WIFI_DEVICES`, legacy Android
  <=12 `ACCESS_FINE_LOCATION` capped with `maxSdkVersion=32`, `CHANGE_WIFI_STATE`
  and a Settings toggle; Android 12 and below are not treated as unsupported,
  but they require the system location permission for legacy Wi-Fi Direct
  discovery.

Вывод: Wi-Fi Direct теперь есть как prototype implementation, active
service-readiness export и clean debug Wi-Fi Direct-only diagnostics без BLE/LAN
fallback. Текущий blocker сузился до peer discovery: Android service
registration/discovery стартует, но телефоны не обнаруживают друг друга как
Wi-Fi Direct peers. Wi-Fi Direct нельзя считать закрытым для `10/10`, пока нет
selected-route delivery, negative tests and latency/loss samples. Нельзя писать,
что проект `10/10`, пока этот route не проверен на телефонах.

Что нужно для закрытия:

1. Two-device smoke без общего роутера.
2. Route evidence mapping/export, где `wifi-direct` отличается от `lan-nsd-tcp`.
3. Negative scenarios: unknown peer, wrong recipient, duplicate, malformed, replay/TTL over Wi-Fi Direct.
4. Latency/loss/retry samples for Wi-Fi Direct.

## Gap 2: Mac-in-the-middle / desktop relay attack

Статус: частично есть desktop preflight, но нет полного Android inline attack evidence.

Что есть:

- `scripts/kraken_desktop_relay_preflight.py`;
- `tests/test_desktop_lan_relay.py`, который фиксирует локальные relay decisions
  для `normal`, `drop`, `duplicate`, `tamper`, unknown recipient, expired packet
  и TTL exhausted;
- `artifacts/desktop-relay-preflight/20260603-193209/desktop_relay_preflight.md`;
- modes: `normal`, `drop`, `duplicate`, `tamper`;
- artifact explicitly says: “local relay decision logic only” and does not prove Android radio delivery.

Что не закрыто:

- Mac не был поставлен в реальный path между двумя Android-устройствами как observed inline adversary.
- Нет capture, где Android A -> Mac relay/attacker -> Android B.
- Нет comparison of accepted/rejected packet state на Android after Mac tamper/drop/duplicate.
- Нет доказательства cryptographic MITM resistance, потому что current packet crypto remains prototype/no-security.

Вывод: пользовательская формулировка “Mac посередине” пока не доказана. Есть только локальный desktop relay/attacker preflight и unit/sim coverage.

Что нужно для закрытия:

1. Явный `desktop-relay-prototype` mode, включаемый вручную.
2. Android A/B route evidence, где selected route показывает relay/path через Mac.
3. Separate runs: normal, drop, duplicate, tamper.
4. Android-side counters before/after attack: received, rejected, duplicates, wrong recipient/malformed, delivery status.
5. Claim boundary: это prototype attack harness, не production MITM security proof.

## Gap 3: Negative scenarios на телефонах

Статус: улучшено, но не идеально.

Что есть:

- unit tests для unknown peer, wrong recipient, duplicate, expired, malformed, TTL.
- current debug rejection probe теперь прогоняет local hostile packets через
  `MeshInboxProcessor` и только после ожидаемых rejection increments records
  counters; это сильнее прежнего прямого counter increment.
- fresh phone export after the local inbox hostile packet probe is captured in
  `reports/out/debug_route_evidence_capture_2026-06-08.md/json`: Samsung and
  Xiaomi both exported
  `debug_local_inbox_packet_injection_and_queue_retry_probe` with
  unknown/wrong/duplicate counters `1/1/1` from app source
  `clean_commit_6853fc3`.
- `reports/out/lan_malformed_injection_attempt_2026-06-08.md/json` records an
  external host-side malformed LAN injector and transport diagnostics export.
  In the current Wi-Fi environment both phone LAN listeners were visible in
  Android diagnostics and ping-reachable from Mac, but all same-subnet TCP
  injection attempts timed out and Android malformed counters stayed `0 -> 0`.
- route-specific 2026-06-08 JSON с debug counters:
  - `unknown_peer_rejected`;
  - `wrong_recipient_rejected`;
  - `duplicates_dropped`;
  - queue retry after restart.

Что не закрыто:

- Нет внешнего hostile packet injection через LAN/BLE radio.
- BLE-only malicious advertiser spoofing не закрыт.
- Нет отдельного packet corpus, прогнанного через физический transport.

Вывод: для dissertation prototype это сильный слой, но не `10/10` attack
evidence. LAN malformed injector is now present and was attempted, but physical
injection evidence is still open.

## Gap 4: Reliability / latency / loss

Статус: есть samples и агрегатор, но нет достаточного N-run benchmark.

Что есть:

- BLE latency samples around `7192 ms` / `7145 ms` in current export.
- LAN Bluetooth-off post-fix samples `1391 ms` / `929 ms`.
- delivered status and receipt counters for specific runs.
- `reports/out/route_benchmark_summary_2026-06-08.md/json` aggregates the
  current evidence with min gate `10`: BLE has `n=2`, LAN has `n=2`, both
  status `insufficient_n_for_reliability_claim`.

Что не закрыто:

- нет повторного N-run benchmark;
- median/p95 are descriptive only and below the min sample gate;
- нет accepted loss/retry distribution across repeated comparable runs;
- нет separate BLE vs LAN reliability table;
- нет threshold-based acceptance gate.

Вывод: можно писать “fresh route-specific smoke passed”, но нельзя писать “repeatable reliability proven”.

## Gap 5: Production security track

Статус: intentionally separate roadmap.

Не закрыто:

- Android Keystore production identity;
- non-exportable keys;
- signed QR invite/response/final confirmation;
- encrypted packets with reviewed crypto library;
- key agreement, AEAD, nonce/replay model;
- key rotation/revocation;
- security review gates;
- release/prototype build separation as hard technical gate.

Вывод: это не мешает dissertation research prototype, но полностью блокирует любые production security claims.

## Gap 6: Evidence hygiene / stale per-device summaries

Статус: guarded, но raw summaries остаются historical/stale.

Что найдено:

- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-stale-peer-fix-export/R5CY22X6MSB/route_specific_evidence_latest.json` показывает `delivered_after_transport_restart = true` и `message_status_after_restart = DELIVERED_TO_PEER`.
- Соседний `route_specific_evidence_summary_latest.md` в той же папке показывает старое состояние: `deliveredAfterTransportRestart = false` и `messageStatusAfterRestart = SENT_TO_TRANSPORT`.
- `reports/out/route_evidence_consistency_audit_2026-06-08.md/json` сканирует ignored raw `artifacts/two-phone-test`, нашёл 3 mismatched JSON-vs-markdown pairs и 32 mismatched fields, и помечает такие summaries как `raw_summary_mismatch_quarantined`.

Почему это важно:

- consolidated report `reports/out/two_device_route_specific_smoke_2026-06-08.md/json` остается более надежным source-of-truth для свежего LAN/BLE smoke;
- но старые per-device `*_summary_latest.md` нельзя цитировать как current pass/fail без сверки с JSON;
- automated consistency check теперь существует, поэтому evidence hygiene риск не должен превращаться в silent overclaim.

Что закрыто:

1. Добавлен consistency validator/report: `scripts/audit_route_evidence_consistency.py`, `reports/out/route_evidence_consistency_audit_2026-06-08.md/json`.
2. Source hierarchy зафиксирован: consolidated `reports/out/two_device_route_specific_smoke_2026-06-08.md/json` выше ignored raw JSON, raw markdown summaries только после JSON consistency check.
3. Stale raw summaries не переписаны вручную и не удалены, потому что `artifacts/two-phone-test/` является ignored raw evidence area.

Что остается:

1. Если raw summaries нужно использовать как primary evidence, их надо regenerated из sibling JSON fresh capture pipeline.
2. Evidence hygiene не закрывает attack/MITM, hostile injection, reliability benchmark или Wi-Fi Direct gaps.

## 10/10 Criteria Audit

| Критерий | Статус | Комментарий |
| --- | --- | --- |
| QR-established trust, local identity, chats | mostly done | Рабочий prototype flow есть; production identity нет |
| LAN NSD/TCP route evidence | partial | LAN NSD/TCP over local Wi-Fi есть; Wi-Fi Direct реализован как prototype transport, но phone route/negative evidence ещё обязателен для `10/10` |
| BLE direct evidence | mostly done | Есть direct-route evidence и current route counters; hostile BLE cases не физические |
| Route-specific counters/statuses/screenshots | partially done | 2026-06-08 слой закрывает многое; статистики и hostile packet injection нет |
| Adamova product-path integration | done for research prototype | Есть QR/relationship/packet/session policy path; не production crypto |
| Attack/MITM evidence | partial | Unit/sim + executable desktop relay tests/preflight есть; Mac inline Android attack не закрыт |
| Claim boundaries | mostly done | Документы явно блокируют production claims |
| Dissertation insert packet | done | Есть `dissertation_final_insert_packet_2026-06-08.md` |
| Evidence consistency / provenance | guarded | Есть validator/report и source hierarchy; raw summaries остаются ignored/historical |
| Production roadmap separation | done | Есть `docs/kraken-production-readiness-roadmap.md` |
| Release/prototype security gate | partial after 2026-06-10 | Minimal Gradle hard gate blocks release-like builds; production crypto, Keystore identity, signed QR, release signing and security review remain open |

## Что можно честно говорить сейчас

Kraken является сильным dissertation research prototype: есть QR trust, local chats, LAN/BLE nearby route evidence, route-specific counters, controlled rejection metrics и Adamova product-path admission policy for experimental profile.

## Что нельзя говорить сейчас

- Нельзя говорить, что всё сделано на `10/10`, если в критерий входят Wi-Fi Direct, Mac-in-the-middle against Android devices, hostile physical packet injection или production security.
- Нельзя говорить, что Wi-Fi Direct проверен end-to-end или закрыт для `10/10`.
- Нельзя говорить, что Mac-in-the-middle attack был физически прогнан между телефонами.
- Нельзя говорить, что LAN/BLE reliability доказана статистически.
- Нельзя говорить, что prototype packet crypto является подписью или шифрованием.
- Нельзя использовать старые per-device `*_summary_latest.md` как current source-of-truth без сверки с sibling JSON/consolidated report; mismatched summaries quarantined в `route_evidence_consistency_audit_2026-06-08`.

## Recommended Next Gates

P0 для честного `10/10` research/evidence:

1. Mac inline relay attack against Android devices: normal/drop/duplicate/tamper.
2. Physical hostile packet injection over LAN/BLE for unknown/wrong/duplicate/malformed.
3. Repeated LAN/BLE route benchmark: N runs, median/p95 latency, loss, retry count.
4. Wi-Fi Direct phone route/negative tests.

P1:

1. Add BLE malicious advertiser spoofing fixture.
2. Expand the minimal release/prototype hard gate into real production security
   readiness: Keystore identity, signed QR, encrypted packets, release signing
   and security review.

Production track:

1. Keep separate from dissertation prototype.
2. Follow `docs/kraken-production-readiness-roadmap.md`.
3. Do not use production security wording until Keystore, signed QR, encrypted packets and review gates are complete.
