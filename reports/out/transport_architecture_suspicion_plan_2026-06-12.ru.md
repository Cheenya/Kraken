# Kraken: план подозрений по транспортной архитектуре

Дата: 2026-06-12.
Этап: без телефонов. Все проверки на устройствах ниже вынесены в отдельный план.

## Короткий вывод

Похоже, проблема не в одном локальном баге Wi-Fi Direct, а в архитектурной
модели транспорта. Kraken местами обращается с Android-транспортами как с
простым синхронным интерфейсом `peer -> send`, хотя Wi-Fi Direct, BLE и LAN на
Android зависят от состояния сессии, foreground/background режима, разрешений,
роли устройства и поведения конкретной оболочки.

Главные подозрения:

1. Запуск foreground service из background/debug broadcast уже доказанно
   запрещается на Xiaomi artifact'ах.
2. Wi-Fi Direct endpoint сейчас слишком неявный: peer, host, port, ARP fallback
   и relationship fingerprint могут смешиваться в одну картину.
3. `debug_send_success=true` нельзя считать доставкой. Это максимум transport
   ACK; доставку доказывают target counters, сохранённое входящее сообщение,
   receipt и admission result.
4. Adamova должна оставаться выше транспорта: транспорт не доказывает
   криптографическую эффективность алгоритма, он только переносит пакет.

## Что доказано сейчас

Доказано:

- `pytest` установлен через Homebrew и виден из текущего shell:
  `/opt/homebrew/bin/pytest`, версия `9.0.3`.
- `adb devices` сейчас пустой, поэтому live-проверки Xiaomi невозможны.
- В сохранённых Xiaomi artifacts есть ошибка:
  `startForegroundService() not allowed due to mAllowStartForeground false`.
- В текущем коде debug receiver может вызвать foreground service из background
  context.
- В pipeline видны Adamova/admission checks; прямого обхода admission gate по
  текущему срезу не найдено.

Не доказано:

- что Xiaomi падает именно через `FATAL EXCEPTION`;
- что это именно MIUI/HyperOS battery killer;
- что Wi-Fi Direct невозможно стабилизировать;
- что sender-side ACK равен доставке на target.

## Почему ощущение регресса выглядит обоснованным

Текущий транспортный слой слишком сильно полагается на моментальный снимок:

- `observePeers()`;
- `send(peer, packet)`;
- общий `CompositePeerTransport`;
- fallback, если конкретный transport сейчас peer не видит.

Для LAN это ещё может быть терпимо. Для Wi-Fi Direct это хрупко, потому что:

- discovery асинхронный;
- group owner и client видят разные адреса;
- endpoint может появиться позже;
- ARP fallback зависит от текущей group-сессии;
- оболочка может иначе обрабатывать background start и радио-состояния;
- старый host/port может стать stale после смены группы.

## Уроки из Briar, Berty и Meshtastic

### Briar

Briar держит transport как plugin со своим состоянием: включается, выключается,
становится active/inactive, создаёт соединения и отдаёт их выше. Crypto,
identity и sync не живут внутри транспорта.

Для Kraken: BLE/LAN/Wi-Fi Direct должны отдавать packets, route evidence и
diagnostics. Trust, Adamova admission, ordering, sync и delivery claim должны
оставаться выше.

### Berty

Berty разделяет BLE, Android Nearby, mDNS, relay/DHT/local discovery как
конфигурируемые network/proximity drivers. Android Nearby там выглядит как
отдельный transport driver, а не как identity/security слой.

Для Kraken: Android Nearby можно рассмотреть как отдельный экспериментальный
`PeerTransport`, если raw Wi-Fi Direct продолжит быть нестабильным. QR trust и
Adamova туда не переносить.

### Meshtastic

Meshtastic полезен не как phone-to-phone messenger, а как пример Android
lifecycle. Они ловят `ForegroundServiceStartNotAllowedException` и используют
expedited WorkManager fallback. Отправка также завязана на connection state, а
не на слепую попытку отправить.

Для Kraken: foreground/background ограничения должны стать частью runtime
архитектуры, а не случайной ошибкой в debug capture.

## P0 подозрения

### P0.1: background foreground-service start

Текущий путь:

- `DebugEvidenceReceiver` получает debug broadcast.
- При `--start-foreground-wifi-direct` вызывает
  `MeshForegroundService.startDebugWifiDirectOnly(appContext)`.
- Внутри вызывается `context.startForegroundService(intent)`.
- Android отказывает: `mAllowStartForeground false`.

Это не надо лечить как "Xiaomi странный". Это надо лечить как Android lifecycle
policy, которую MIUI может делать более заметной.

Что сделать:

- вынести запуск service в `AndroidShellRuntimeController`;
- ловить `ForegroundServiceStartNotAllowedException`;
- писать отказ в diagnostics/export JSON;
- стартовать foreground path из явного user-visible действия;
- добавить WorkManager fallback там, где это корректно;
- добавить `ApplicationExitInfo` на Android 11+.

### P0.2: Wi-Fi Direct endpoint freshness

Сейчас Wi-Fi Direct peer cache может сохранять host без TTL. При смене P2P
группы или роли это может стать stale endpoint.

Что сделать:

- вынести endpoint resolution в отдельный helper;
- хранить `observedAt`, `expiresAt`, `source`, `confidence`;
- различать `dns-sd`, `group-owner-address`, `arp-device-match`,
  `arp-single-client`, `manual-debug`;
- помечать ARP single-client fallback как fallback, а не bound-peer proof;
- сбрасывать endpoint cache при group lost/adapter off/role changed.

### P0.3: transport ACK не равен доставке

Нужно переименовать и разделить evidence:

- `transport_ack_success`;
- `target_accepted_connections`;
- `target_inbound_packets`;
- `target_message_delivered`;
- `receipt_applied`;
- `admission_accepted/rejected`.

До этого `debug_send_success=true` может переубеждать нас сильнее, чем имеет
право.

## P1 улучшения архитектуры

### Transport session state

Нужен слой состояния транспорта, например:

```kotlin
data class TransportSessionState(
    val transportId: String,
    val capabilityState: CapabilityState,
    val permissionState: PermissionState,
    val lifecycleState: LifecycleState,
    val discoveryState: DiscoveryState,
    val connectionState: ConnectionState,
    val endpointState: EndpointState,
    val lastError: String?,
)
```

Начать с Wi-Fi Direct, потом подтянуть BLE/LAN.

### Runtime policy из TransportCatalog

`KrakenTransportCatalog` уже содержит capabilities/caveats. Его стоит
использовать не только как справочник, а как runtime-политику:

- supported/unsupported;
- permission missing;
- requires user action;
- background blocked;
- foreground required;
- diagnostics required.

### Route attempt как объект

Для каждого message attempt сохранять:

- `messageId`;
- `transportId`;
- `attemptIndex`;
- `endpointResolutionSource`;
- `endpointObservedAt`;
- `groupFormed`;
- `isGroupOwner`;
- `host`;
- `port`;
- `transportAck`;
- `targetAcceptedConnections`;
- `targetInboundPackets`;
- `receiptApplied`;
- `admissionDecision`.

Это позволит честно отличить "peer был виден", "socket ACK был", "target принял
packet" и "message реально доставлен".

## Adamova: как сохранить и доказать алгоритм

Принципиально: Adamova не должна зависеть от Wi-Fi Direct/BLE/LAN. Транспорт
только переносит packet. Доказательство эффективности Adamova должно быть
transport-independent.

Нужные доказательства:

1. Positive path: approved Adamova profile создаёт packet с ожидаемыми
   `cryptoProfileId`, `sessionProfileId`, `admissionDecisionHash`,
   `profilePolicyVersion`.
2. Negative path: weak/unknown/mismatched profile отклоняется до отправки или
   до сохранения во входящие.
3. Downgrade path: relationship, approved для одного profile, не принимает
   packet с другим profile/session/hash.
4. Route independence: одинаковое admission-поведение на loopback, LAN, BLE,
   Wi-Fi Direct.
5. Benchmark path: отдельно замерить overhead admission/evaluation и отдельно
   radio delivery latency.

Корректная формулировка для диссертации:

> Kraken демонстрирует транспортно-независимую Adamova admission policy для
> experimental crypto-profile metadata, с positive/negative evidence на разных
> маршрутах.

Некорректная формулировка:

> Wi-Fi Direct delivery доказывает production message security Adamova.

## Тесты без телефонов

Сначала сделать:

1. Unit tests для `WifiDirectEndpointResolver`:
   - client uses group owner address;
   - group owner uses DNS-SD host;
   - ARP device match;
   - ARP single-client fallback;
   - stale TTL;
   - group lost clears endpoint.
2. Unit tests для foreground-service wrapper:
   - normal start;
   - `ForegroundServiceStartNotAllowedException`;
   - fallback scheduled;
   - diagnostics exported.
3. Tests для evidence semantics:
   - transport ACK не равен delivered;
   - delivery claim требует target counters/receipt.
4. Adamova negative probes:
   - wrong `cryptoProfileId`;
   - wrong `sessionProfileId`;
   - wrong `admissionDecisionHash`;
   - wrong `profilePolicyVersion`.

## Тесты потом, с одним телефоном

Один Xiaomi нужен не для transport delivery, а для lifecycle/crash evidence.

Сценарий:

1. Установить debug APK.
2. Запустить приложение видимо.
3. Start mesh из UI.
4. Экспортировать diagnostics в foreground.
5. Увести app в background, выключить экран, ждать 5/15/30 минут.
6. Снять:
   - `adb logcat -b crash`;
   - `adb logcat -b main,system`;
   - `dumpsys activity processes`;
   - `dumpsys deviceidle`;
   - `ApplicationExitInfo`;
   - notification permission state;
   - battery optimization state.
7. Повторить отдельно для debug broadcast start и user-visible UI start.

Цель: отличить crash, ANR, killed-by-system, user force-stop,
background-start-blocked и OEM battery policy.

## Тесты потом, с двумя телефонами

Samsung + Xiaomi:

1. Samsung -> Xiaomi Wi-Fi Direct, оба foreground.
2. Xiaomi -> Samsung Wi-Fi Direct, оба foreground.
3. Samsung -> Xiaomi, target foreground service active, app background.
4. Xiaomi -> Samsung, target foreground service active, app background.
5. Wi-Fi Direct only, без общего router path.
6. LAN/BLE comparison с тем же route-attempt schema.
7. Optional Android Nearby adapter comparison, если будет реализован.

Обязательные поля evidence:

- sender `transport_ack_success`;
- target `accepted_connections`;
- target `inbound_packets`;
- incoming message stored;
- receipt applied или explicit no-receipt reason;
- route attempt bound to message ID;
- endpoint source/freshness;
- Adamova admission result.

## Phone audit update: 2026-06-12

После подключения телефонов этот план был проверен на Samsung `R5CY22X6MSB`
и Xiaomi `d948ffd0`. Детальный отчёт:
`reports/out/phone_transport_audit_2026-06-12.md`.

Новые факты:

1. На Xiaomi API 35 в logcat многократно зафиксировано, что
   `WIFI_P2P_PEERS_CHANGED_ACTION` для `com.disser.kraken` пропускается:
   системе требуется `android.permission.ACCESS_FINE_LOCATION`.
2. В текущем manifest `ACCESS_FINE_LOCATION` ограничен
   `android:maxSdkVersion="32"`, а `WifiDirectPermissions` на Android 13+
   запрашивает только `NEARBY_WIFI_DEVICES`.
3. На обоих телефонах `NEARBY_WIFI_DEVICES` granted, но `FINE_LOCATION` в
   appops находится в `ignore`, потому что permission фактически не запрошен.
4. Forced Wi-Fi Direct-only discovery работает: оба телефона доходят до
   `selected_route=wifi-direct`, `transport_discovered_peer_count=1`,
   `p2p_txt_bound_peer_count=1`.
5. Доставка Wi-Fi Direct всё ещё не доказана. Samsung -> Xiaomi в направленном
   тесте падает на стороне Samsung как group owner: после group formation
   `transport_discovered_peer_count=0`, попытки заканчиваются `UNKNOWN_PEER`,
   Xiaomi target counters остаются `accepted_connections=0`,
   `inbound_packets=0`.
6. Xiaomi -> Samsung даёт sender-side success, но target counters на Samsung не
   увеличиваются, поэтому это не fresh delivery proof.
7. Свежего `FATAL EXCEPTION` именно для `com.disser.kraken` в ходе аудита не
   найдено. Старый `mAllowStartForeground=false` остаётся валидным artifact, но
   в текущей сессии не воспроизвёлся.

Что меняется в трактовке:

- главная конкретная причина сейчас не абстрактная "MIUI странная", а
  permission mismatch вокруг Wi-Fi Direct на современных Android/OEM;
- гипотеза stale endpoint/session state остаётся, но её надо проверять после
  исправления permission model;
- multi-device debug capture нельзя принимать как route proof без направленного
  сценария `target-before -> sender -> target-after`.

## Приоритеты

1. Исправить Wi-Fi Direct permission model: requestable `ACCESS_FINE_LOCATION`
   на Android 13+ там, где включён Wi-Fi Direct, плюс понятное UI-пояснение,
   что это permission для peer discovery, а не background tracking.
2. Добавить permission diagnostics в route export: `nearbyWifiDevicesGranted`,
   `fineLocationDeclared`, `fineLocationGranted`, `fineLocationAppOp`,
   `wifiP2pBroadcastDeniedByPermission`.
3. Исправить evidence wording/fields: ACK не должен выглядеть как delivery.
4. Перевести phone evidence на directed harness:
   `target-before -> sender -> target-after`.
5. Добавить foreground-service start wrapper и diagnostics.
6. Добавить `ApplicationExitInfo` export.
7. Вынести и протестировать Wi-Fi Direct endpoint resolver.
8. Добавить route attempt state machine и TTL endpoint cache.
9. Добавить Adamova negative admission probes.
10. Только потом повторять full Wi-Fi Direct delivery evidence.

## Уверенность

Высокая:

- background FGS start failure реален;
- sender-side Wi-Fi Direct ACK недостаточен для delivery proof;
- endpoint resolution требует source/freshness/confidence;
- Adamova должна быть transport-independent.
- Wi-Fi Direct permission model сейчас неполный для tested Xiaomi/API 35 path:
  `NEARBY_WIFI_DEVICES` granted, но система всё равно требует
  `ACCESS_FINE_LOCATION` для `PEERS_CHANGED`.

Средняя:

- stale endpoint/peer cache участвует в reverse Samsung -> Xiaomi flakiness;
- MIUI/HyperOS усиливает lifecycle-проблемы.

Низкая после текущих phone tests:

- Xiaomi падает именно через `FATAL EXCEPTION`;
- Xiaomi убивает процесс именно из-за battery/autostart policy;
- raw Wi-Fi Direct невозможно стабилизировать для research prototype.

## Источники

- Android Wi-Fi Direct:
  https://developer.android.com/develop/connectivity/wifi/nsd-wifi-direct
- Android `WifiP2pManager`:
  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- Android foreground service background restrictions:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android Doze/App Standby:
  https://developer.android.com/training/monitoring-device-state/doze-standby
- Briar:
  https://briarproject.org/
- Berty protocol:
  https://berty.tech/ar/docs/protocol
- Meshtastic Android:
  https://github.com/meshtastic/Meshtastic-Android
