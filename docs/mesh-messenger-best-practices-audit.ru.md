# Аудит best practices mesh-мессенджеров

Статус: исследовательская заметка.
Дата: 2026-06-05.
Область: сравнение текущей Android-реализации Kraken с практиками других
offline, mesh и delay-tolerant мессенджеров.

## Краткий вывод

Kraken движется в правильную сторону: нет облачного аккаунта, доверие строится
через QR, данные хранятся локально, mesh запускается явно через foreground
service, BLE и LAN разделены как разные транспорты, есть receipts, retry/delete
UX, notification channels и diagnostics.

Главный текущий разрыв не в том, что у нас нет GIF, звонков или стикеров.
Главный разрыв в том, что приложение пока не знает маршрут до конкретного
контакта как отдельное пользовательское состояние. В UI всё ещё есть места, где
прямой Bluetooth может отображаться как общее `доступен через mesh`, хотя по
смыслу это разные режимы с разной ценой, пропускной способностью и набором
доступных функций.

Практичный следующий вектор:

1. ввести route-aware состояния: `нет маршрута`, `Bluetooth напрямую`,
   `LAN/Wi-Fi напрямую`, `через mesh`;
2. привязать доступные функции к текущему route-классу;
3. только после этого добавлять voice notes, stickers, media и calls;
4. держать routed mesh трафик маленьким, TTL-limited и budgeted.

## Источники

Использованы первичные или близкие к первичным источники:

- Briar:
  - https://briarproject.org/
  - https://briarproject.org/how-it-works/
  - https://briarproject.org/manual/
- Berty Protocol:
  - https://berty.tech/ar/docs/protocol
- Bridgefy:
  - https://docs.bridgefy.me/sdk/android/overview
  - https://bridgefy.notion.site/Bridgefy-Mesh-Network-Basics-002ecd4c7bb64986bf00beb6d43d3036
  - https://www.usenix.org/system/files/sec22fall_albrecht.pdf
- Meshtastic:
  - https://meshtastic.org/
  - https://meshtastic.org/docs/overview/
- Bluetooth Mesh:
  - https://www.bluetooth.com/learn-about-bluetooth/feature-enhancements/mesh/mesh-glossary/
  - https://www.bluetooth.com/bluetooth-mesh-primer/
- Reticulum / LXMF / Columba:
  - https://reticulum.network/
  - https://github.com/markqvist/LXMF
  - https://columba.network/

## Текущий снимок Kraken

По текущему дереву проекта:

- `KrakenTransportCatalog` считает `lan-nsd-tcp` и `ble-gatt`
  реализованными transport modes.
- `TransportManager.startLan(...)` запускает `BleGattTransport` и
  `DirectLanTransport` внутри `CompositePeerTransport`.
- `CompositePeerTransport` объединяет найденных peers по fingerprint и пишет
  recent route attempts, но после merge `DiscoveredPeer` не хранит, каким
  транспортом он был найден.
- `MeshServiceSnapshot` содержит общий `discoveredPeers`,
  `transportDiagnostics`, queue metrics и foreground-service flags, но не
  содержит per-contact route state.
- В `ChatScreen` есть `meshAwareContactSubtitle(...)`, который может показать
  `доступен через mesh` просто потому, что peer видим напрямую.
- Уже есть проектные документы:
  - `docs/route-aware-capability-model.md`
  - `docs/route-aware-implementation-plan.md`
- В текущем dirty tree уже есть foreground service, local notifications,
  notification inbox, mute, delete/clear для сообщений и локальный retry.

## Общие практики других проектов

### 1. Прямой маршрут и routed mesh нельзя смешивать

Briar разделяет синхронизацию через Bluetooth/Wi-Fi и интернет/Tor.
Berty отдельно описывает direct transports и online rendezvous, подчёркивая, что
direct transport требует одновременной доступности устройств. Bridgefy в своей
модели различает direct one-to-one delivery и relay через mesh. Meshtastic
явно работает с hop limit и relay-поведением.

Вывод для Kraken:

- прямой BLE нельзя называть просто `mesh`;
- LAN/Wi-Fi direct должен быть отдельным high-bandwidth состоянием;
- `через mesh` можно показывать только при наличии relay/path evidence;
- `нет маршрута` должен быть нормальным состоянием, а не ошибкой.

### 2. Mesh требует traffic budget, TTL и duplicate suppression

Meshtastic использует rebroadcasting с duplicate detection и hop limit.
Bluetooth Mesh использует managed flooding, TTL и message cache.
Это не косметика, а базовая защита mesh-сети от самозасорения.

У Kraken уже есть packet IDs, seen-store и TTL-подобные идеи, но UI и
per-contact route layer пока не навязывают пользовательский traffic budget.
Поэтому реакции, стикеры и пинги нельзя пускать в routed mesh без route cost,
relay policy и backoff.

### 3. Доверие к контакту должно быть явным и проверяемым

Briar для nearby contact exchange использует взаимное сканирование QR.
Berty исходит из того, что requester получил identity responder через доверенный
канал, например QR. Bridgefy показывает отрицательный пример: взять известную
криптобиблиотеку недостаточно, если key binding, verification и integration
сделаны неправильно.

Сильные стороны Kraken:

- QR trust уже является центральной моделью;
- чаты разрешены только для `ACTIVE` relationship;
- fingerprints уже есть в contact/profile flows;
- публичный discovery намеренно не вводится.

Чего не хватает:

- production crypto provider;
- понятного UX для проверки ключей;
- аудированного session/key agreement;
- сохранения явной границы: текущий prototype не production-secure.

### 4. Offline messaging обычно delay-tolerant, а не online presence

Briar говорит пользователю: если контакт offline, сообщение будет доставлено,
когда оба снова будут online, либо через Mailbox. Berty обсуждает replication
devices, которые хранят контент, не расшифровывая его. LXMF использует
propagation nodes для store-and-forward, когда endpoint недоступен напрямую.

У Kraken уже есть local queues и foreground service, но пока нет зрелой
delay-tolerant routing модели:

- нет явной роли relay/courier для телефонов;
- нет route cost или hop-count based scheduler;
- нет per-contact last-seen/route expiry;
- нет пользовательского разделения между `очередь локально` и `известен mesh route`.

### 5. Богатые функции должны зависеть от маршрута

Bridgefy заявляет text, images, locations, game moves и database sync через
Bluetooth/Wi-Fi mesh. Columba заявляет multiple transports и voice calls,
опираясь на Reticulum/LXMF. Berty отмечает, что Wi-Fi direct-style links быстрее
и надёжнее BLE. Meshtastic показывает противоположный полюс: маленькие payload,
position/telemetry, но не тяжёлые media.

Для Kraken rich actions не должны быть статичными:

- text: любой relationship route, queue если маршрута нет;
- reactions: только direct BLE или direct LAN;
- built-in sticker IDs: direct BLE или лучше;
- custom stickers/GIF/photo/video: только direct LAN/Wi-Fi;
- short voice note: direct BLE или лучше, с жёстким лимитом размера/длительности;
- voice/video calls: только direct high-bandwidth;
- routed mesh: только text/control/receipts, пока не доказан traffic budget.

### 6. Для групп и multi-device нужны logs, CRDT или явная replication model

Berty использует immutable logs и CRDT/eventual consistency, чтобы сливать
offline-ветки разговоров. LXMF рассматривает сообщения как signed
self-contained objects и поддерживает propagation nodes.

У Kraken пока проще: локальный список сообщений, relationship state и realm
models. Для P0/P1 это нормально. Но если появятся группы, multi-device и
offline merge conflicts, понадобится более явная event/log model.

## Сравнительная таблица

| Область | Briar | Berty | Bridgefy | Meshtastic | Reticulum/LXMF/Columba | Kraken сейчас |
| --- | --- | --- | --- | --- | --- | --- |
| Identity | локальный аккаунт, без cloud recovery | account/group keys, без личных данных | UUID/display name | node/channel identity | cryptographic destinations | local identity, QR trust, prototype warning |
| Contact trust | QR рядом и distant link | rendezvous + QR/URL + handshake | уроки слабой verification | shared channels/keys | destination keys | QR и relationship gating уже хорошие |
| Transports | Bluetooth, Wi-Fi, Tor, memory cards | BLE, Android Nearby, Multipeer, libp2p | BLE/Wi-Fi Direct SDK | LoRa + companion transport | разные Reticulum media | BLE GATT + LAN NSD/TCP + Wi-Fi Direct prototype; Wi-Fi Direct phone evidence pending |
| Route semantics | direct sync vs Tor/mailbox | direct vs rendezvous/replication | direct vs multi-hop/broadcast | hop limit/rebroadcast | path lookup/propagation | только global peers; нет per-contact route kind |
| Offline model | direct при одновременной online; Mailbox optional | async logs; replication devices | mesh relay через SDK | маленькие packets + rebroadcast | propagation nodes | local queue + foreground service; нет relay role policy |
| Traffic control | transport-specific sync | CRDT/log sync | SDK opaque; security papers warn | duplicate cache, hop limit, small payloads | routing/queues/receipts | seen store и TTL есть; нет route budget/capability UX |
| Rich media | консервативный messenger/forum | attachments в protocol model | images/location/db sync claims | tiny payloads, position/telemetry | extensible fields, Columba calls | только text; policy задокументирована, не реализована |
| Security posture | privacy-first, audited positioning | подробные crypto/protocol docs | cautionary tale | channel encryption, metadata visible | E2E/default encryption claims | prototype crypto, не production-secure |

## Достоинства Kraken

- Чёткая продуктовая граница: без cloud, без phone/email/account identity, без
  public discovery.
- QR trust и relationship states хорошо совпадают с подходами Briar/Berty.
- BLE, LAN и Wi-Fi Direct уже разделены как разные prototype transports и объединены через
  `CompositePeerTransport`.
- Foreground service и local notifications двигают приложение к реальной
  фоновой работе Android.
- Outbox/inbox/seen/receipt stores дают базу для delay-tolerant messaging.
- Diagnostics и smoke scripts делают prototype проверяемым на реальных телефонах.
- Новые route-aware документы правильно фиксируют главный UX/protocol gap.

## Недостатки и упущения Kraken

### P0/P1

- Нет per-contact route state.
- UI всё ещё может показать `доступен через mesh` для direct peer-visible case.
- `DiscoveredPeer` теряет transport evidence после merge по fingerprint.
- Нет route TTL/cache/last-seen model.
- Нет настоящего routed mesh evidence: hop count, relay path, path cost,
  relay budget.
- Нет capability policy в composer.
- Нет production crypto provider/session protocol.
- Notification/foreground behavior ещё стабилизируется на реальных устройствах.

### P2/P3

- Нет attachment framework: chunking, resume, checksums, storage quotas.
- Route-specific rich-feature policy пока не enforced in code.
- Wi-Fi Direct ещё требует двухтелефонного route/negative evidence перед любым
  claim `10/10`.
- Нет courier/relay role selection и battery-aware relay scheduler.
- Нет CRDT/event log model для group/multi-device offline conflicts.
- Нет replication/mailbox node concept.
- Нет формального metadata-leakage budget для direct transport discovery.

## Векторы развития

### 1. Route-aware UX foundation

Сначала реализовать `PeerRouteSnapshot` и route aggregation.

Минимальный результат:

- `Bluetooth напрямую` для direct BLE;
- `Wi-Fi/LAN напрямую` или `Быстрый прямой канал` для LAN/Wi-Fi;
- `Через mesh` только при relay/path evidence;
- `нет маршрута` или `последний контакт: HH:mm` иначе;
- route state истекает по TTL.

Это главный следующий шаг, потому что UI перестанет обещать сетевое состояние,
которого на самом деле нет.

### 2. Capability-based composer

До rich features добавить `RouteCapabilityPolicy`.

Начальная политика:

- text остаётся доступным;
- reactions и built-in stickers только direct-route;
- short voice notes: direct BLE или лучше;
- photo/video/GIF/calls: только direct LAN/Wi-Fi;
- routed mesh запрещает rich actions, пока нет relay budgets.

### 3. Mesh traffic budget и relay policy

Не включать routed reactions/presence pings без:

- packet TTL;
- duplicate cache;
- per-route/hop cost;
- retry budget;
- relay role и battery policy;
- backoff и queue pressure metrics.

Meshtastic и Bluetooth Mesh показывают одно и то же: uncontrolled rebroadcasting
является главным scalability risk.

### 4. Crypto hardening

Bridgefy — предупреждение: известный crypto protocol сам по себе не делает
мессенджер безопасным. Kraken нужны:

- выбранные reviewed primitives;
- явный session/key agreement;
- key verification UX;
- tamper/wrong-recipient tests;
- отсутствие production-security claims до реализации.

### 5. Delay-tolerant store-and-forward

Kraken со временем должен различать:

- local queue у отправителя;
- direct delivery;
- routed mesh relay;
- optional mailbox/replication node, который не читает содержимое;
- expired/failed packets.

Это похоже на Briar Mailbox, Berty replication devices и LXMF propagation nodes,
но для Kraken должно оставаться optional и local-first.

### 6. Rich media в правильном порядке

Рекомендуемый порядок после route-aware foundation:

1. built-in sticker IDs на direct routes;
2. reactions только на direct routes;
3. short voice notes с жёсткими лимитами;
4. attachment framework;
5. photo/GIF/video только на direct LAN/Wi-Fi;
6. voice/video calls только на direct high-speed.

Не начинать с video/calls. Для них нужны стабильный direct high-bandwidth route,
session lifecycle, battery policy и нормальный failure UX.

## Ближайший backlog

1. Реализовать `PeerRouteModels.kt`.
2. Сохранять `fingerprint + transportId + observedAt` в diagnostics.
3. Добавить `PeerRouteAggregatorTest`.
4. Заменить `meshAwareContactSubtitle(...)`.
5. Добавить `RouteCapabilityPolicyTest`.
6. Добавить в Mesh Status per-contact route evidence.
7. Обновить screenshot/evidence scripts, чтобы они фиксировали route evidence.

## Итог

Kraken не отстаёт из-за отсутствия GIF, звонков или стикеров. Он начнёт
отставать, если приложение не будет понимать, как именно достижим контакт.
Главный урок других проектов: route truth, traffic budget, explicit trust и
delay-tolerant semantics должны появиться до богатых функций мессенджера.
