# Kraken Crypto/Mesh Goal Completion Audit - 2026-06-15

## Итог

Цель почти закрыта по crypto/evidence части, но не закрыта полностью из-за
незавершённого Wi-Fi Direct transport path и необходимости ручного
freeze-прогона именно на Bluetooth/nearby подтверждении.

Криптографический путь обычных сообщений сейчас доведён до проверяемого состояния:
обычный runtime отправляет message payload через `ENCRYPTED_MESSAGE_JSON`, plaintext
в обычном входящем пути отклоняется, lower-level processors тоже default-secure,
а debug/plaintext оставлен только для явно выбранных тестовых режимов.

Транспортная часть доведена до честной границы: LAN/Bluetooth остаются основным
доказуемым контуром, Wi-Fi Direct не называется готовым транспортом и вынесен в
отдельный экспериментальный запуск. Пользователь может оставить точку доступа
включённой и запускать LAN/Bluetooth без Wi-Fi Direct.

После первичного preflight телефоны появились по ADB, свежая APK была
установлена на Samsung `R5CY22X6MSB` и Xiaomi `d948ffd0`, permissions были
выданы. Physical MacBook inline relay выполнен и сохранён. Directed Wi-Fi Direct
прогон выполнен честно, но показал `endpoint_bound=false`, поэтому Wi-Fi Direct
остаётся экспериментальным и не считается готовым транспортом. Повторный прогон
с prearm/hint уточнил blocker: Xiaomi уже виден через Kraken DNS-SD TXT, но
Android Wi-Fi P2P group/connect path падает до получения доставляемого endpoint.
Третий directed run после runtime diagnostic patch подтвердил тот же negative
verdict, но теперь в evidence видно peer-level `binding_state=FAILED` и
`binding_reason=p2p-group-not-formed:connect=failed:1:0:0`.
Следующий directed run после connect-attempt diagnostics снова не доказал
доставку, но уточнил blocker: target GO-группа жива, sender видит Kraken peer,
а Android `WifiP2pManager.connect()` падает `ERROR(0)` при `groupOwnerIntent`
`0`, `15` и `7`.
Обратный directed run Xiaomi -> Samsung дал тот же класс отказа, поэтому
текущий Wi-Fi Direct blocker выглядит симметричным для пары Samsung/Xiaomi.

Отдельно на Samsung зафиксирован реальный ANR: `Application Not Responding:
com.disser.kraken` и большие пропуски кадров (`Skipped 6113 frames`). P0-фикс
внесён в `MeshForegroundService`: тяжёлый запуск/остановка/snapshot mesh больше
не выполняются синхронно на service main thread. После фикса APK пересобрана,
переустановлена на оба телефона. Дополнительный profile-isolation фикс сделал
обычный старт LAN/Bluetooth независимым от сохранённого Wi-Fi Direct профиля.
Свежий 45-секундный smoke обычного запуска на Samsung и Xiaomi не показал ANR,
`Skipped ... frames`, Wi-Fi P2P broadcasts или `fine_location` app-op denial.
Мониторинг freeze при Bluetooth/nearby подтверждении ещё нужен, но исходный
ANR-сценарий и unexpected Wi-Fi Direct activity в коротком прогоне не повторились.

## Проверено

| Требование | Статус | Доказательство |
|---|---:|---|
| Защищённый путь отправки сообщений включён как основной | done | `MeshService` использует `MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED`; `MeshServiceTest.syncNowSendsReadyMessagesToDiscoveredActivePeer` проверяет `PacketPayloadType.ENCRYPTED_MESSAGE_JSON` |
| Plaintext оставлен только для debug/test | done | `MessagePayloadProtectionPolicy.LEGACY_DEBUG_PLAINTEXT_ALLOWED` используется только в явных debug/fixture ветках; default runtime и lower-level processors требуют encrypted path |
| Подмена crypto profile/admission hash ломает приём | done | `AdamovaBoundCryptoEnvelopeTest`, `MeshDeliveryPipelineTest`, attack-suite artifact |
| Подмена relationship/session ломает приём | done | `AdamovaPacketCryptoBinding`, `MeshDeliveryPipelineTest`, attack-suite artifact |
| Plaintext injection отклоняется | done | `MeshServiceTest.syncNowRejectsInboundPlaintextMessageByDefault`; `recentRejectedInboundPackets.reason == CRYPTO_PROFILE_REJECTED` |
| Unknown sender / wrong recipient / duplicate replay | done-local | covered by local unit/policy attack suite; physical radio-path injection still open |
| Attack logs сохранены | done-local | `artifacts/crypto-attack-unit/20260615-032122/` |
| Gradle tests | done | `./gradlew test` passed |
| pytest policy/docs | done | `/opt/homebrew/bin/pytest tests/test_android_policy_guards.py tests/test_protocol_spec_docs.py` passed: `70 passed` |
| evidence JSON/Markdown | done | `reports/out/crypto_mesh_technical_status_2026-06-15.md` and `.json` |
| Актуальный debug APK собран | done | `./gradlew assembleDebug` passed; APK SHA-256 `5143331510995c30adb04dffebd9eeb5f3ad0358d186b2e235950e53a361b1f9` |
| APK preflight | done | `artifacts/phone-preflight/20260615-after-wifi-direct-diagnostic-patch/`; актуальная APK прошла preflight |
| Phone install + permissions | done | актуальная APK с SHA `51433315...` установлена на Samsung `R5CY22X6MSB` и Xiaomi `d948ffd0`; runtime permissions выданы и проверены |
| LAN-only debug route evidence | done-partial | `artifacts/debug-route-evidence/20260615-022752-crypto-path-post-install-lan-ble-debug-evidence/`; оба телефона выбрали `lan-nsd-tcp`, Wi-Fi Direct permissions clean, но без message delivery counters |
| Hotspot-compatible LAN/BLE evidence | done-partial | `artifacts/debug-route-evidence/20260615-030913-hotspot-compatible-lan-ble-evidence-after-secure-default/`; enabled modes `ble-gatt,lan-nsd-tcp`, Wi-Fi Direct inactive, без message delivery counters |
| Physical MacBook inline relay | done | `artifacts/physical-inline-relay/20260615-022842-crypto-path-physical-relay-after-encrypted-runtime/`; normal/drop/duplicate/tamper modes сохранены |
| Wi-Fi Direct не заявлен готовым | done | technical report and policy guards keep Wi-Fi Direct as diagnostic/experimental |
| Hotspot vs Wi-Fi Direct UX | done | `MeshStatusScreen`: “Оставить точку доступа и запустить LAN/Bluetooth” / “Пробовать Wi-Fi Direct” |
| Directed Wi-Fi Direct run | done-negative | `artifacts/directed-wifi-direct/20260615-023158-samsung-to-xiaomi-after-encrypted-runtime/`; permissions/relationship/discovery ready, но `endpoint_bound=false`, delivery не доказана |
| Directed Wi-Fi Direct prearm/hint run | done-negative-better-diagnosis | `artifacts/directed-wifi-direct/20260615-032906-samsung-to-xiaomi-prearm-hint-after-secure-default/`; Xiaomi виден через DNS-SD TXT, hint ready, но P2P group/connect failed и target counters не выросли |
| Directed Wi-Fi Direct after binding-reason diagnostic patch | done-negative-machine-readable-failure | `artifacts/directed-wifi-direct/20260615-035314-samsung-to-xiaomi-after-binding-reason-diagnostic/`; peer-level `binding_state=FAILED`, `binding_reason=p2p-group-not-formed:connect=failed:1:0:0`, target counters не выросли |
| Directed Wi-Fi Direct after connect-attempt diagnostics | done-negative-more-precise | `artifacts/directed-wifi-direct/20260615-041155-samsung-to-xiaomi-connect-attempt-diagnostics/`; target GO жив, sender attempts падают `ERROR(0)` при intents `0/15/7`, target counters не выросли |
| Reverse directed Wi-Fi Direct after connect-attempt diagnostics | done-negative-symmetric | `artifacts/directed-wifi-direct/20260615-042142-xiaomi-to-samsung-connect-attempt-diagnostics/`; target GO жив, sender attempts падают `ERROR(0)` при intents `0/15/7`, target counters не выросли |
| ANR / UI freeze | patched-smoke-passed | `MeshForegroundService` больше не стартует runtime на main thread; debug broadcast long-hold ANR исправлен; 120s hotspot-compatible smoke на обоих телефонах чистый |
| Realm relay boundary | done-runtime-evidence-simulated | `RealmRelayPolicyTest`, `MeshServiceTest`, `MeshEvidenceExportTest`, `SimulatedStoreAndForwardRelayTest`: участник реалма с `relay_basic` может быть relay-кандидатом без relationship, evidence export показывает кандидатов, simulated relay проверяет policy перед forwarding, direct-message всё ещё требует relationship + `send_direct` |
| Manual nearby/Bluetooth freeze observer | done-tooling-only | `scripts/capture_manual_nearby_freeze_smoke.sh`; dry-run artifact `artifacts/phone-audit/20260615-060736-tool-dry-run-filtered-no-manual-ui/` показал no ANR/skipped/fatal/timeout, но Wi-Fi Direct noise присутствует |
| Wi-Fi P2P app-op noise diagnostic | done-negative-diagnostic | `artifacts/phone-audit/20260615-061419-wifi-p2p-appop-noise-current/`; permissions/app-op clean, но Xiaomi записал 32 `PEERS_CHANGED` app-op denial строки для Kraken |

## Свежие артефакты

- Crypto/attack summary:
  `artifacts/crypto-attack-unit/20260615-032122/crypto_attack_unit_summary.md`
- Gradle attack log:
  `artifacts/crypto-attack-unit/20260615-032122/gradle_crypto_attack_tests.log`
- Policy/docs attack log:
  `artifacts/crypto-attack-unit/20260615-032122/policy_docs_tests.log`
- Technical status:
  `reports/out/crypto_mesh_technical_status_2026-06-15.md`
- Machine-readable technical status:
  `reports/out/crypto_mesh_technical_status_2026-06-15.json`
- Phone preflight:
  `artifacts/phone-preflight/20260615-after-wifi-direct-diagnostic-patch/preflight.md`
- Debug APK:
  `app-android/app/build/outputs/apk/debug/app-debug.apk`
- Physical evidence runbook:
  `docs/physical-evidence-next-runbook-2026-06-15.md`
- Debug route evidence:
  `artifacts/debug-route-evidence/20260615-022752-crypto-path-post-install-lan-ble-debug-evidence/`
- Hotspot-compatible LAN/BLE debug evidence:
  `artifacts/debug-route-evidence/20260615-030913-hotspot-compatible-lan-ble-evidence-after-secure-default/`
- Physical MacBook inline relay:
  `artifacts/physical-inline-relay/20260615-022842-crypto-path-physical-relay-after-encrypted-runtime/`
- Directed Wi-Fi Direct negative run:
  `artifacts/directed-wifi-direct/20260615-023158-samsung-to-xiaomi-after-encrypted-runtime/`
- Directed Wi-Fi Direct negative run with target prearm/hint:
  `artifacts/directed-wifi-direct/20260615-032906-samsung-to-xiaomi-prearm-hint-after-secure-default/`
- Directed Wi-Fi Direct negative run after binding-reason diagnostic patch:
  `artifacts/directed-wifi-direct/20260615-035314-samsung-to-xiaomi-after-binding-reason-diagnostic/`
- Directed Wi-Fi Direct negative run after connect-attempt diagnostics:
  `artifacts/directed-wifi-direct/20260615-041155-samsung-to-xiaomi-connect-attempt-diagnostics/`
- Reverse directed Wi-Fi Direct negative run after connect-attempt diagnostics:
  `artifacts/directed-wifi-direct/20260615-042142-xiaomi-to-samsung-connect-attempt-diagnostics/`
- Ordinary launch profile-isolation smoke:
  `artifacts/phone-audit/20260615-ordinary-launch-profile-isolation-smoke/`
- Long hotspot-compatible freeze smoke after broadcast hold fix:
  `artifacts/phone-audit/20260615-long-hotspot-compatible-freeze-smoke-after-broadcast-hold-fix/`
- Manual nearby/Bluetooth freeze observer dry-run:
  `artifacts/phone-audit/20260615-060736-tool-dry-run-filtered-no-manual-ui/`
- Wi-Fi P2P app-op noise diagnostic:
  `artifacts/phone-audit/20260615-061419-wifi-p2p-appop-noise-current/`

## APK для следующего физического прогона

- Path: `app-android/app/build/outputs/apk/debug/app-debug.apk`
- Size: `21125925` bytes
- SHA-256: `5143331510995c30adb04dffebd9eeb5f3ad0358d186b2e235950e53a361b1f9`
- Preflight: `ready_for_install_smoke=true`
- Preflight artifact:
  `artifacts/phone-preflight/20260615-after-wifi-direct-diagnostic-patch/`
- Installed on phones after this rebuild: Samsung `R5CY22X6MSB`, Xiaomi `d948ffd0`

## Physical relay verdict

| Режим | Что сделал Mac relay | Что увидел target | Статус |
|---|---|---|---|
| `normal` | принял 1 frame и forward сделал 1 раз | `accepted +1`, `inbound +1` | packet path delivery observed |
| `drop` | принял 1 frame и dropped 1 раз | `accepted +0`, `inbound +0` | drop сработал |
| `duplicate` | принял 1 frame, forward + duplicate | `accepted +2`, `inbound +2`, rejection `DUPLICATE` | replay/duplicate path observed |
| `tamper` | принял 1 frame, forward tampered frame | `accepted +1`, `inbound +1`, rejection `MALFORMED` | tamper rejection observed |

Граница: это debug physical relay через MacBook, а не доказательство всей
production security модели.

## Wi-Fi Direct verdict

Directed run Samsung -> Xiaomi:

- `permissions_ready=true`;
- `relationship_ready=true`;
- `wifi_direct_discovery_ready=true`;
- `endpoint_bound=false`;
- `send_attempted=true`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- status: `sender_endpoint_not_bound`.

Итог: Wi-Fi Direct permission blocker снят, но transport blocker остаётся в
peer visibility / endpoint binding. Ранее Xiaomi логировал `Appop Denial ...
fine_location` для `android.net.wifi.p2p.PEERS_CHANGED`; после изоляции
профилей обычный запуск LAN/Bluetooth больше не воспроизвёл эту unexpected
Wi-Fi Direct activity. Сам
explicit Wi-Fi Direct mode всё равно требует отдельного directed retest.

Directed run Samsung -> Xiaomi с prearm target group owner и hint:

- `debug_wifi_direct_peer_hint.status=ready`;
- sender увидел Xiaomi через DNS-SD TXT как Kraken peer:
  `fingerprint_prefix=B42B 3068 93`, `device_name=Xiaomi 12 Pro`,
  `port=48381`;
- `wifi_direct_permission_warning=null`;
- `kraken_dns_sd_peer_seen=true`;
- `endpoint_bound=false`;
- `send_attempted=true`;
- `transport_route_unavailable=true`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- `failure_stage=wifi_p2p_group_connect_failed`;
- причина binding: `p2p-group-not-formed:connect=failed:2:15:0`;
- target counters: `accepted_connections +0`, `inbound_packets +0`.

Уточнение: текущий blocker уже не выглядит как permission blocker и не выглядит
как отсутствие Kraken discovery. Он находится в Wi-Fi P2P group/connect и
endpoint binding. Directed manifest builder теперь сохраняет этот случай как
`failure_stage=wifi_p2p_group_connect_failed`, чтобы не трактовать его как
настоящий `UNKNOWN_PEER`.

Актуальная APK экспортирует peer-level `binding_reason`: при failed Wi-Fi P2P
group/connect соответствующий `wifi_direct_discovered_peers[]` переходит из
`DISCOVERED_UNBOUND` в `FAILED` с причиной
`p2p-group-not-formed:connect=...`. Это диагностический фикс, не claim о
доставке.

После установки APK с этим runtime diagnostic patch directed run подтвердил
экспорт peer-level failure:

- artifact:
  `artifacts/directed-wifi-direct/20260615-035314-samsung-to-xiaomi-after-binding-reason-diagnostic/`;
- `permissions_ready=true`, `relationship_ready=true`,
  `wifi_direct_discovery_ready=true`;
- `kraken_dns_sd_peer_seen=true`;
- `wifi_direct_discovered_peers[0].binding_state=FAILED`;
- `wifi_direct_discovered_peers[0].binding_reason=p2p-group-not-formed:connect=failed:1:0:0`;
- `endpoint_bound=false`;
- `transport_route_unavailable=true`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- target counters: `accepted_connections +0`, `inbound_packets +0`.

После connect-attempt diagnostic patch directed run подтвердил, что проблема
остаётся в Android Wi-Fi P2P connect path:

- artifact:
  `artifacts/directed-wifi-direct/20260615-041155-samsung-to-xiaomi-connect-attempt-diagnostics/`;
- target до и после sender attempt оставался group owner:
  `groupFormed=true`, `role=owner`, `groupOwnerAddress=192.168.49.1`;
- `permissions_ready=true`;
- `relationship_ready=true`;
- `wifi_direct_discovery_ready=true`;
- `kraken_dns_sd_peer_seen=true`;
- `endpoint_bound=false`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- `failure_stage=wifi_p2p_group_connect_failed`;
- `wifi_direct_connect_attempts` экспортируются в manifest;
- sender attempts падали `ERROR(0)` при `group_owner_intent` `0`, `15` и `7`;
- `stop_peer_discovery_result=stopped`, поэтому это не stop-discovery warning.

Reverse directed run Xiaomi -> Samsung дал тот же класс отказа:

- artifact:
  `artifacts/directed-wifi-direct/20260615-042142-xiaomi-to-samsung-connect-attempt-diagnostics/`;
- target Samsung до и после sender attempt держал GO-группу:
  `groupFormed=true`, `isGroupOwner=true`, `groupOwnerIpAddress=192.168.49.1`
  в dumpsys Wi-Fi P2P state;
- `permissions_ready=true`;
- `relationship_ready=true`;
- `wifi_direct_discovery_ready=true`;
- `kraken_dns_sd_peer_seen=true`;
- `endpoint_bound=false`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- `failure_stage=wifi_p2p_group_connect_failed`;
- sender Xiaomi attempts падали `ERROR(0)` при `group_owner_intent` `0`, `15`
  и `7`;
- target counters: `accepted_connections +0`, `inbound_packets +0`.

Архитектурная граница для mesh: non-relationship peer нельзя подставлять как
адресата личного сообщения, но такой peer может быть будущим relay-кандидатом,
если он доказывает принадлежность к тому же реалму. Для этого нужен отдельный
realm-scoped relay path, а не ослабление relationship matching в direct-message
trial.

В код добавлен первый безопасный слой этой модели: `RealmRelayPolicy` отдельно
проверяет возможность использовать участника реалма как relay по сертификату
реалма и capability `relay_basic`. Это не подключает runtime-retlay и не меняет
правила direct-message: для личного сообщения по-прежнему нужен ACTIVE
relationship и `send_direct`.

## ANR / freeze verdict

Что было:

- Samsung logcat: `Application Not Responding: com.disser.kraken`;
- Samsung logcat: `Skipped 6113 frames`.

Что исправлено:

- `MeshForegroundService.onStartCommand()` больше не вызывает `runtime.start()`,
  `runtime.stop()` и `runtime.snapshot()` синхронно на service main thread;
- foreground notification стартует сразу со статусом `Запуск mesh...`;
- тяжёлая mesh-работа уходит в `serviceScope` на `Dispatchers.IO`.
- debug evidence `hold-after-export-ms` больше не удерживает broadcast receiver:
  post-hold refresh выполняется в отдельном потоке, чтобы не ловить background
  broadcast ANR.

Проверка после фикса:

- `./gradlew test assembleDebug` прошёл;
- свежая APK установлена на Samsung и Xiaomi;
- profile-isolation smoke обычного запуска 45 секунд сохранил logcat в
  `artifacts/phone-audit/20260615-ordinary-launch-profile-isolation-smoke/`;
- на Samsung и Xiaomi не найдено `Application Not Responding`, `Skipped ...
  frames`, `PEERS_CHANGED`, `WIFI_P2P`, `Appop Denial` или `fine_location`.
- первый длинный debug-hold smoke воспроизвёл ANR в `DebugEvidenceReceiver`;
  после исправления повторный 120-секундный hotspot-compatible smoke на обоих
  телефонах чистый:
  `artifacts/phone-audit/20260615-long-hotspot-compatible-freeze-smoke-after-broadcast-hold-fix/`.

Freeze-monitoring не закрыт насовсем, потому что отдельный ручной сценарий
Bluetooth/nearby подтверждения ещё нужно прогнать дольше. Но прежний service
main-thread ANR, debug broadcast long-hold ANR и случайный запуск Wi-Fi Direct
в автоматических hotspot-compatible прогонах сейчас не воспроизводятся.

Для ручного nearby/Bluetooth сценария добавлен отдельный наблюдатель:
`scripts/capture_manual_nearby_freeze_smoke.sh`. Dry-run без ручного UI-сценария
создал artifact
`artifacts/phone-audit/20260615-060736-tool-dry-run-filtered-no-manual-ui/` и
показал `no_freeze_signals_observed_but_wifi_direct_noise_seen`: ANR/skipped
frames/fatal exception/broadcast timeout не найдены, но системный Wi-Fi P2P шум
в logcat есть. Это tooling evidence, а не закрытие ручного confirmation smoke.

Отдельный Wi-Fi P2P app-op diagnostic:
`artifacts/phone-audit/20260615-061419-wifi-p2p-appop-noise-current/`.
Вердикт: `permissions_granted_but_wifi_p2p_appop_denial_seen`. На обоих
телефонах `ACCESS_FINE_LOCATION` и `NEARBY_WIFI_DEVICES` выданы, `FINE_LOCATION`
app-op в `allow`; Samsung не записал Kraken `PEERS_CHANGED` app-op denials, а
Xiaomi записал 32 строки `PEERS_CHANGED ... com.disser.kraken ... Appop Denial
... fine_location` за 30 секунд. Значит, открытый пункт теперь не “выдать
permission”, а Xiaomi/Android foreground app-op behavior для Wi-Fi P2P
broadcasts.

## Команды проверки

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel/app-android
./gradlew test
```

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_crypto_attack_unit_suite.sh
/opt/homebrew/bin/pytest tests/test_android_policy_guards.py tests/test_protocol_spec_docs.py
python3 -m py_compile scripts/kraken_phone_preflight.py scripts/audit_10_10_closure_suite_output.py scripts/build_directed_wifi_direct_trial_manifest.py
bash -n scripts/capture_debug_route_evidence.sh scripts/run_directed_wifi_direct_route_trial.sh scripts/run_10_10_closure_suite.sh scripts/run_crypto_attack_unit_suite.sh scripts/capture_manual_nearby_freeze_smoke.sh scripts/capture_wifi_p2p_appop_noise.sh
git diff --check
```

## Что осталось до полного закрытия цели

1. Прогнать `scripts/capture_manual_nearby_freeze_smoke.sh` во время реального
   ручного Bluetooth/nearby подтверждения.
2. Для Wi-Fi Direct отдельным проходом чинить peer visibility / endpoint binding /
   group-owner routing.
3. Прогнать physical realm-scoped relay harness: relationship peer для адресата,
   realm relay peer для пересылки закрытого пакета, unknown peer как шум.
   Runtime/evidence candidate layer и policy-gated simulation уже добавлены;
   физический multi-hop delivery ещё не снят.
4. Для Xiaomi Wi-Fi P2P app-op behavior выбрать mitigation: держать Wi-Fi
   Direct строго foreground/explicit, либо оставить его экспериментальным режимом
   с честным warning.
5. После Wi-Fi Direct фикса прогнать directed harness хотя бы в одну сторону,
   лучше в обе.
6. Production-hardening crypto: Keystore, public-key key agreement, подписи,
   replay window/key rotation, review.

Точный порядок команд зафиксирован в:
`docs/physical-evidence-next-runbook-2026-06-15.md`.
