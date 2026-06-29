# Crypto/Mesh Technical Status - 2026-06-15

## Что изменено

- Основной runtime `MeshService` теперь отправляет обычные сообщения через
  защищённый путь: `ENCRYPTED_MESSAGE_JSON` + `AdamovaMessagePayloadProtector`.
- Входящий plaintext `LOCAL_MESSAGE_JSON` в обычном runtime отклоняется до
  создания сообщения.
- Legacy plaintext оставлен только как явно выбранный debug/fixture режим
  `LEGACY_DEBUG_PLAINTEXT_ALLOWED`.
- Нижний уровень `MeshOutboxProcessor`/`MeshInboxProcessor` теперь тоже по
  умолчанию требует `ADAMOVA_ENCRYPTED_REQUIRED`; plaintext fixtures в тестах
  помечены явно.
- Ключ message payload выводится из QR-связи: общий `sourceInviteId` +
  relationship/session/profile context через HKDF-SHA256.
- Evidence export теперь показывает новый integrity mode:
  `adamova-bound-aes-gcm-message-payload-no-production-signature-or-keystore`.
- Обычный транспортный профиль теперь можно явно выбрать в диагностике:
  оставить точку доступа и запустить LAN/Bluetooth без Wi-Fi Direct, либо
  отдельно пробовать экспериментальный Wi-Fi Direct.
- Для будущей mesh-ретрансляции добавлен отдельный runtime/evidence слой:
  snapshot и evidence export показывают `realm_relay_candidates`, а
  `SimulatedStoreAndForwardRelay` проверяет `RealmRelayPolicy` перед
  пересылкой. Это не превращает relay-узел в личный контакт и не ослабляет
  direct-message правила.

## Что проверено локально

- plaintext injection отклоняется default runtime;
- подмена `admissionDecisionHash` отклоняется до plaintext;
- подмена session/profile binding отклоняется;
- неизвестный отправитель и неправильный получатель отклоняются;
- повтор уже виденного packet отклоняется как duplicate/replay;
- обе стороны QR-связи получают один message key;
- отсутствие QR invite secret не приводит к тихому fallback в plaintext.
- участник того же реалма с `relay_basic` может быть runtime/evidence
  relay-кандидатом без relationship, но direct-message всё ещё требует
  relationship + `send_direct`.

## Evidence

- Локальный attack-suite artifact:
  `artifacts/crypto-attack-unit/20260615-032122/`
- Gradle log:
  `artifacts/crypto-attack-unit/20260615-032122/gradle_crypto_attack_tests.log`
- Policy/docs log:
  `artifacts/crypto-attack-unit/20260615-032122/policy_docs_tests.log`
- Актуальная debug APK сборка:
  `app-android/app/build/outputs/apk/debug/app-debug.apk`
- APK SHA-256:
  `5143331510995c30adb04dffebd9eeb5f3ad0358d186b2e235950e53a361b1f9`
- Phone preflight artifact:
  `artifacts/phone-preflight/20260615-after-wifi-direct-diagnostic-patch/`
- Physical evidence runbook:
  `docs/physical-evidence-next-runbook-2026-06-15.md`
- Установка свежей APK на телефоны:
  Samsung `R5CY22X6MSB`, Xiaomi `d948ffd0`
- Debug route evidence после установки:
  `artifacts/debug-route-evidence/20260615-022752-crypto-path-post-install-lan-ble-debug-evidence/`
- Hotspot-compatible LAN/BLE debug evidence после profile isolation:
  `artifacts/debug-route-evidence/20260615-030913-hotspot-compatible-lan-ble-evidence-after-secure-default/`
- Physical MacBook inline relay:
  `artifacts/physical-inline-relay/20260615-022842-crypto-path-physical-relay-after-encrypted-runtime/`
- Directed Wi-Fi Direct trial:
  `artifacts/directed-wifi-direct/20260615-023158-samsung-to-xiaomi-after-encrypted-runtime/`
- Directed Wi-Fi Direct trial with target prearm/hint:
  `artifacts/directed-wifi-direct/20260615-032906-samsung-to-xiaomi-prearm-hint-after-secure-default/`
- Directed Wi-Fi Direct trial after runtime binding-reason diagnostic patch:
  `artifacts/directed-wifi-direct/20260615-035314-samsung-to-xiaomi-after-binding-reason-diagnostic/`
- Directed Wi-Fi Direct trial after connect-attempt diagnostics:
  `artifacts/directed-wifi-direct/20260615-041155-samsung-to-xiaomi-connect-attempt-diagnostics/`
- Reverse directed Wi-Fi Direct trial after connect-attempt diagnostics:
  `artifacts/directed-wifi-direct/20260615-042142-xiaomi-to-samsung-connect-attempt-diagnostics/`
- Ordinary launch profile-isolation smoke:
  `artifacts/phone-audit/20260615-ordinary-launch-profile-isolation-smoke/`
- Long hotspot-compatible freeze smoke after broadcast hold fix:
  `artifacts/phone-audit/20260615-long-hotspot-compatible-freeze-smoke-after-broadcast-hold-fix/`

Realm relay сейчас доведён до безопасного runtime/evidence слоя:
`MeshServiceSnapshot.realmRelayCandidates`, JSON/Markdown export полей
`realm_relay_candidates` и policy-gated relay simulation. Физический multi-hop
прогон с тремя устройствами ещё не выполнен.

## APK preflight

`scripts/kraken_phone_preflight.py` подтвердил, что APK после Wi-Fi Direct
diagnostic patch готов для install smoke:

- `ready_for_install_smoke`: `true`
- APK size: `21125925` bytes
- `ACCESS_FINE_LOCATION`: присутствует
- `NEARBY_WIFI_DEVICES`: присутствует
- `ACCESS_BACKGROUND_LOCATION`: отсутствует
- forbidden permissions: отсутствуют
- native arm64 library: присутствует

Актуальная APK с SHA `51433315...` после connect-attempt diagnostic patch установлена
на Samsung `R5CY22X6MSB` и Xiaomi `d948ffd0`. Runtime permissions были выданы и
проверены на обоих телефонах: `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`,
`BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`,
`POST_NOTIFICATIONS`.

## Физический прогон после установки

Debug route evidence на актуальной сборке показал:

- оба телефона в LAN-only профиле выбирают `lan-nsd-tcp`;
- Wi-Fi Direct permissions в evidence чистые: `NEARBY_WIFI_DEVICES=true`,
  `ACCESS_FINE_LOCATION=true`, `fine_location_app_op=allowed`, warning отсутствует;
- counters в этом debug capture не доказывают доставку сообщения:
  `accepted_connections=0`, `inbound_packets=0`.

Дополнительный hotspot-compatible capture после изоляции профилей показал:

- на обоих телефонах включены только `ble-gatt` и `lan-nsd-tcp`;
- Wi-Fi Direct не активен, P2P group не сформирована;
- Samsung зарегистрировал BLE advertising и LAN NSD service, но ещё не выбрал
  peer route в момент capture;
- Xiaomi выбрал `lan-nsd-tcp` и увидел одного transport peer;
- `wifi_direct_permission_warning=null`;
- counters доставки сообщений в этом capture также нулевые, поэтому это
  evidence выбора транспортного профиля, а не message delivery proof.

Physical MacBook inline relay дал более сильный сетевой слой:

| Режим | Relay | Target delta | Вывод |
|---|---|---|---|
| `normal` | `framesReceived=1`, `forwarded=1` | `accepted +1`, `inbound +1` | нормальный packet/message дошёл до target: `packet-87013439...`, `message-0ae8e11e...` |
| `drop` | `framesReceived=1`, `dropped=1` | `accepted +0`, `inbound +0` | drop на Mac не создаёт входящий пакет на target |
| `duplicate` | `framesReceived=1`, `forwarded=1`, `duplicated=1` | `accepted +2`, `inbound +2` | повтор увиден; target фиксирует `DUPLICATE`; это не claim про две пользовательские доставки |
| `tamper` | `framesReceived=1`, `forwarded=1`, `tampered=1` | `accepted +1`, `inbound +1` | искажённый пакет увиден; target фиксирует `MALFORMED`; текущий tampered message не доставлен |

Это physical/debug evidence для packet path через MacBook relay. Оно хорошо
подходит для диссертационного раздела про проверку поведения при drop,
duplicate и tamper. Оно не означает, что закрыты production key agreement,
подписи, replay window и внешний security review.

## Wi-Fi Direct directed run

Первый узкий directed Wi-Fi Direct прогон Samsung -> Xiaomi был выполнен, но не
доказал доставку:

- `permissions_ready=true`;
- `relationship_ready=true`;
- `wifi_direct_discovery_ready=true`;
- `endpoint_bound=false`;
- `send_attempted=true`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- status: `sender_endpoint_not_bound`;
- причина binding: `relationship-peer-not-visible-after-fresh-discovery:Xiaomi`.

Повторный directed run с prearm target group owner и hint дал более точный
диагноз:

- artifact:
  `artifacts/directed-wifi-direct/20260615-032906-samsung-to-xiaomi-prearm-hint-after-secure-default/`;
- `debug_wifi_direct_peer_hint.status=ready`;
- sender увидел Xiaomi через DNS-SD TXT как Kraken peer:
  `fingerprint_prefix=B42B 3068 93`, `device_name=Xiaomi 12 Pro`,
  `port=48381`;
- `wifi_direct_permission_warning=null` на sender и target;
- `kraken_dns_sd_peer_seen=true`;
- `endpoint_bound=false`;
- `transport_route_unavailable=true`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- `failure_stage=wifi_p2p_group_connect_failed`;
- причина binding: `p2p-group-not-formed:connect=failed:2:15:0`;
- target counters: `accepted_connections +0`, `inbound_packets +0`.

Итог: permission blocker снят, а Kraken-level discovery уже видит Xiaomi.
Wi-Fi Direct как доставляющий транспорт всё ещё не готов: текущий blocker
сместился в Android Wi-Fi P2P group/connect path и последующее endpoint binding.
Directed manifest builder теперь сохраняет это отдельным machine-readable
полем `failure_stage`, чтобы следующий прогон не смешивал такой случай с
настоящим `UNKNOWN_PEER`.

Дополнительно runtime diagnostics теперь помечают peer-level endpoint binding
как `FAILED` и сохраняют `binding_reason`, когда Kraken peer найден, но
`resolveSendPeer()` не смог получить Wi-Fi Direct endpoint. Это не меняет
transport semantics и не делает Wi-Fi Direct готовым, но свежий evidence уже
точнее показывает failure на уровне `wifi_direct_discovered_peers[]`.

Свежий directed run после установки APK с этим diagnostic patch подтвердил, что
runtime evidence теперь действительно экспортирует peer-level failure:

- artifact:
  `artifacts/directed-wifi-direct/20260615-035314-samsung-to-xiaomi-after-binding-reason-diagnostic/`;
- `permissions_ready=true`, `relationship_ready=true`,
  `wifi_direct_discovery_ready=true`;
- sender увидел Xiaomi через DNS-SD TXT как Kraken peer:
  `fingerprint_prefix=B42B 3068 93`, `device_name=Xiaomi 12 Pro`,
  `port=48381`;
- `wifi_direct_discovered_peers[0].binding_state=FAILED`;
- `wifi_direct_discovered_peers[0].binding_reason=p2p-group-not-formed:connect=failed:1:0:0`;
- `endpoint_bound=false`;
- `send_attempted=true`;
- `transport_route_unavailable=true`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- `failure_stage=wifi_p2p_group_connect_failed`;
- target counters: `accepted_connections +0`, `inbound_packets +0`.

Итог не изменился в сторону успеха доставки: Wi-Fi Direct всё ещё не готов.
Но диагностика стала точнее: проблема уже зафиксирована как failure в Wi-Fi P2P
group/connect path, а не как отсутствие разрешений, не как отсутствие
relationship и не как слепой `UNKNOWN_PEER`.

Следующий узкий прогон после connect-attempt diagnostics подтвердил тот же
negative verdict, но уже показал историю Android P2P connect attempts:

- artifact:
  `artifacts/directed-wifi-direct/20260615-041155-samsung-to-xiaomi-connect-attempt-diagnostics/`;
- `permissions_ready=true`, `relationship_ready=true`,
  `wifi_direct_discovery_ready=true`;
- `kraken_dns_sd_peer_seen=true`;
- target оставался Wi-Fi Direct group owner до и после sender attempt:
  `groupFormed=true`, `role=owner`, `groupOwnerAddress=192.168.49.1`;
- sender видел Xiaomi как Wi-Fi Direct peer, но `endpoint_bound=false`;
- target counters: `accepted_connections +0`, `inbound_packets +0`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- `failure_stage=wifi_p2p_group_connect_failed`;
- `wifi_direct_connect_attempts` теперь экспортируются в JSON;
- попытки sender падали как `reason=ERROR(0)` при `group_owner_intent` `0`,
  `15` и `7`;
- `stop_peer_discovery_result=stopped`, поэтому текущий failure уже не выглядит
  как проблема stop-discovery warning.

Итог: мягкий pre-connect reset не сделал Wi-Fi Direct доставляющим транспортом.
Зато следующий blocker стал уже: Android `WifiP2pManager.connect()` возвращает
generic `ERROR(0)` при попытке присоединиться к живой target GO-группе.

Обратный directed run Xiaomi -> Samsung показал тот же класс отказа:

- artifact:
  `artifacts/directed-wifi-direct/20260615-042142-xiaomi-to-samsung-connect-attempt-diagnostics/`;
- target Samsung до и после sender attempt держал GO-группу:
  `groupFormed=true`, `isGroupOwner=true`, `groupOwnerIpAddress=192.168.49.1`
  в dumpsys Wi-Fi P2P state;
- `permissions_ready=true`, `relationship_ready=true`,
  `wifi_direct_discovery_ready=true`, `kraken_dns_sd_peer_seen=true`;
- `endpoint_bound=false`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- sender Xiaomi attempts падали `ERROR(0)` при `group_owner_intent` `0`, `15`
  и `7`;
- target counters: `accepted_connections +0`, `inbound_packets +0`.

Итог по паре Samsung/Xiaomi: failure симметричный. Это уже не выглядит как
односторонний sender bug. Следующий Wi-Fi Direct срез должен проверять
persistent group/invitation/authorization state и OEM-особенности join к
автономной GO-группе.

Посторонний Wi-Fi Direct peer не надо автоматически считать полезным адресатом
или мусором. Для прямого личного сообщения target должен совпадать с
relationship peer. Для будущего mesh-routing нужен отдельный класс
realm-scoped relay peer: устройство не является контактом, но может переслать
закрытый пакет, если у него есть валидный fingerprint/маяк того же реалма.
Текущий directed harness это ещё не доказывает и не должен подставлять
non-relationship peer вместо адресата.

## ANR / freeze

На Samsung в logcat был зафиксирован реальный ANR:

- `Application Not Responding: com.disser.kraken`;
- `Skipped 6113 frames`.

P0-фикс внесён в `MeshForegroundService`: `onStartCommand()` больше не вызывает
`runtime.start()`, `runtime.stop()` и `runtime.snapshot()` синхронно на service
main thread. Тяжёлая mesh-работа перенесена в `serviceScope` на `Dispatchers.IO`,
а foreground notification стартует сразу с коротким статусом `Запуск mesh...`.

После фикса:

- `./gradlew test assembleDebug` прошёл;
- APK после profile-isolation фикса установлена на Samsung и Xiaomi;
- короткий smoke обычного запуска сохранил logcat в
  `artifacts/phone-audit/20260615-ordinary-launch-profile-isolation-smoke/`;
- в 45-секундном окне на обоих телефонах не найдено `Application Not
  Responding`, `Skipped ... frames`, `PEERS_CHANGED`, `WIFI_P2P`, `Appop Denial`
  или `fine_location`.

Это означает, что обычный LAN/Bluetooth запуск больше не поднимает Wi-Fi Direct
случайно из сохранённого экспериментального профиля. Полный freeze-monitoring
при Bluetooth/nearby подтверждении всё ещё нужен, но P0 ANR-сценарий и
unexpected Wi-Fi Direct activity не воспроизвелись в свежем коротком smoke.

Отдельно был найден debug-only ANR в `DebugEvidenceReceiver`: длинный
`hold-after-export-ms=120000` удерживал broadcast receiver слишком долго, и
Android фиксировал `ANR in com.disser.kraken -- Broadcast of Intent
EXPORT_ROUTE_EVIDENCE`. Это исправлено: post-hold refresh теперь уходит в
отдельный поток `kraken-debug-evidence-post-hold-refresh`, а broadcast
завершается после первичного export. Повторный 120-секундный hotspot-compatible
smoke на Samsung и Xiaomi не показал `Application Not Responding`, `Skipped ...
frames`, `PEERS_CHANGED`, `WIFI_P2P`, `Appop Denial` или `fine_location`.

Позже отдельный Wi-Fi P2P app-op diagnostic подтвердил более узкую проблему на
Xiaomi, уже не в grant-модели: artifact
`artifacts/phone-audit/20260615-061419-wifi-p2p-appop-noise-current/` дал
verdict `permissions_granted_but_wifi_p2p_appop_denial_seen`. На обоих
устройствах `ACCESS_FINE_LOCATION` и `NEARBY_WIFI_DEVICES` выданы, `FINE_LOCATION`
app-op в `allow`, но Xiaomi за 30 секунд записал 32 строки
`PEERS_CHANGED ... com.disser.kraken ... Appop Denial ... fine_location`.
Samsung таких строк для Kraken не записал. Это foreground/app-op/OEM поведение
Wi-Fi P2P broadcasts, а не отсутствие runtime permission.

## Граница результата

Это уже защищённый message payload path для исследовательского приложения:
сообщение шифруется, а crypto envelope привязан к relationship, session и
допущенному crypto profile.

Это ещё не весь промышленный мессенджерный security stack. Отдельно остаются:

- Android Keystore для неэкспортируемых identity/session ключей;
- public-key key agreement вместо QR invite secret как основного источника key material;
- подписи QR invite/response/confirmation;
- подписи packet envelope;
- replay window/key rotation;
- более строгие физические инъекции поверх BLE/Wi-Fi Direct;
- ручной freeze-monitoring именно на Bluetooth/nearby подтверждении;
- обработка Xiaomi Wi-Fi P2P foreground/app-op поведения или сохранение
  Wi-Fi Direct строго в явном экспериментальном режиме;
- внешний review.

## Транспортная граница

LAN/BLE используем как основной доказуемый транспортный контур для следующих
проверок. В UI это отражено явно: пользователь может оставить точку доступа
включённой и запускать LAN/Bluetooth без Wi-Fi Direct.

Wi-Fi Direct не считаем готовым транспортом: он реализован и диагностируется,
но по Samsung/Xiaomi остаётся нестабильным и требует отдельного
endpoint-binding/group-owner routing прохода. В UI он вынесен в отдельный
экспериментальный запуск “Пробовать Wi-Fi Direct”.

Для будущего полноценного mesh надо разделить три типа соседей:

- relationship peer: адресат личного сообщения;
- realm relay peer: узел того же реалма, который может переслать закрытый пакет;
- unknown/noise peer: устройство без валидного Kraken/realm маяка.

Это позволит не терять mesh-свойство, но и не ослаблять trust model личных
сообщений.
