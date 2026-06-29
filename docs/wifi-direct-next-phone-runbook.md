# Следующий телефонный прогон Wi-Fi Direct

Этот runbook фиксирует следующий физический прогон. В текущем этапе он не выполняется: телефоны, ADB-команды и directed harness не запускать без отдельной команды.

## Цель

Проверить, снят ли blocker прав и доведена ли Wi-Fi Direct связка до честного transport verdict:

- `endpoint_bound` показывает, что у sender есть привязанный endpoint;
- `send_attempted` показывает, что debug-send реально пробовал отправку;
- `transport_counter_delivery_observed` показывает только рост target-счётчика входящих пакетов;
- `message_delivery_proven` показывает более сильное доказательство: target-after содержит sender `message_id` и `packet_id`.

## Подготовка

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel/app-android
./gradlew assembleDebug
```

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
adb -s R5CY22X6MSB install -r app-android/app/build/outputs/apk/debug/app-debug.apk
adb -s d948ffd0 install -r app-android/app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.NEARBY_WIFI_DEVICES
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.ACCESS_FINE_LOCATION
adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.NEARBY_WIFI_DEVICES
adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.ACCESS_FINE_LOCATION
```

## Directed прогоны без hint

Samsung -> Xiaomi:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --label samsung-to-xiaomi-after-fine-location
```

Xiaomi -> Samsung:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device d948ffd0 \
  --target-device R5CY22X6MSB \
  --label xiaomi-to-samsung-after-fine-location
```

## Directed прогоны с hint

Hint нужен только как диагностический режим: он добавляет Wi-Fi Direct device address/port target как `DISCOVERED_UNBOUND`, но не доказывает доставляемый endpoint сам по себе.

Samsung -> Xiaomi:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --label samsung-to-xiaomi-after-fine-location-with-hint \
  --hint-target-wifi-direct-peer
```

Xiaomi -> Samsung:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device d948ffd0 \
  --target-device R5CY22X6MSB \
  --label xiaomi-to-samsung-after-fine-location-with-hint \
  --hint-target-wifi-direct-peer
```

## Decision tree

1. Если любой `wifi_direct_permission_warning` не `null`, остановиться. Это всё ещё permission/app-op path.
2. Если `fine_location_granted != true` у sender, target-before или target-after, остановиться. Wi-Fi Direct evidence будет непригоден.
3. Если `relationship_ready == false`, следующий шаг не transport binding, а relationship/sendability диагностика.
4. Если `wifi_direct_discovery_ready == false`, следующий шаг - peer discovery/DNS-SD/visible-device path.
5. Если `endpoint_bound == false`, следующий scoped refactor - peer binding, endpoint resolution и group-owner routing.
6. Если `send_attempted == false`, смотреть debug-send orchestration и sendable relationship wait.
7. Если `transport_counter_delivery_observed == false`, но `endpoint_bound == true`, смотреть socket path, group owner/client routing и stale endpoint.
8. Если `transport_counter_delivery_observed == true`, но `message_delivery_proven == false`, claim остаётся только counter delivery. Следующий шаг - message-id-bound evidence export.
9. Если `message_delivery_proven == true` в обе стороны, можно повышать claim до двустороннего debug Wi-Fi Direct message delivery evidence.

После прогона `20260615-032906-samsung-to-xiaomi-prearm-hint-after-secure-default`
известно, что отдельный случай `wifi_direct_discovery_ready=true` может означать
только Kraken-level DNS-SD/TXT discovery. Это ещё не endpoint delivery.

После установки APK с runtime diagnostic patch прогон
`20260615-035314-samsung-to-xiaomi-after-binding-reason-diagnostic` подтвердил
тот же negative transport verdict, но уже с peer-level диагностикой.

После connect-attempt diagnostic patch прогон
`20260615-041155-samsung-to-xiaomi-connect-attempt-diagnostics` подтвердил
текущий baseline:

- `permissions_ready=true`;
- `relationship_ready=true`;
- `wifi_direct_discovery_ready=true`;
- `kraken_dns_sd_peer_seen=true`;
- target до и после sender attempt остаётся group owner
  `192.168.49.1`;
- `endpoint_bound=false`;
- `transport_counter_delivery_observed=false`;
- `message_delivery_proven=false`;
- `failure_stage=wifi_p2p_group_connect_failed`;
- `sender.wifi_direct_discovered_peers[0].binding_state=FAILED`;
- `sender.wifi_direct_connect_attempts[]` содержит attempts с
  `failure_reason_name=ERROR`;
- sender attempts падают при `group_owner_intent` `0`, `15` и `7`;
- `stop_peer_discovery_result=stopped`.

Reverse baseline
`20260615-042142-xiaomi-to-samsung-connect-attempt-diagnostics` показал тот же
класс отказа в обратном направлении:

- target Samsung держал GO-группу до и после sender attempt:
  `groupFormed=true`, `isGroupOwner=true`, `groupOwnerIpAddress=192.168.49.1`
  в dumpsys Wi-Fi P2P state;
- `permissions_ready=true`;
- `relationship_ready=true`;
- `wifi_direct_discovery_ready=true`;
- `kraken_dns_sd_peer_seen=true`;
- `endpoint_bound=false`;
- sender Xiaomi attempts падают `ERROR(0)` при `group_owner_intent` `0`, `15`
  и `7`;
- target counters не растут.

Если sender видит target TXT record, но `endpoint_bound=false` и
`p2p-group-not-formed:connect=failed:*`, следующий шаг - Wi-Fi P2P
connect negotiation, persistent groups, peer invitation/authorization, group
owner/client routing и endpoint binding, а не permissions. Так как failure
симметричный, начинать лучше с состояния persistent groups и invitation/join
правил на обоих телефонах, а не с sender-specific ветки.

Для будущего mesh-routing не смешивать роли:

- relationship peer - адресат личного сообщения;
- realm relay peer - не контакт, но возможный ретранслятор закрытого пакета при
  совпадении реалма;
- unknown/noise peer - устройство без валидного Kraken/realm маяка.

Directed direct-message trial не должен подставлять realm relay или unknown peer
вместо relationship target. Relay path нужно проверять отдельным harness.

## Manifest поля

Проверять `artifacts/directed-wifi-direct/<run>/manifest.json`:

- `sender.wifi_direct_permission_warning`
- `target.before.wifi_direct_permission_warning`
- `target.after.wifi_direct_permission_warning`
- `sender.wifi_direct_permissions.fine_location_granted`
- `target.before.wifi_direct_permissions.fine_location_granted`
- `target.after.wifi_direct_permissions.fine_location_granted`
- `debug_wifi_direct_peer_hint_requested`
- `debug_wifi_direct_peer_hint.status`
- `verdict.kraken_dns_sd_peer_seen`
- `verdict.transport_route_unavailable`
- `verdict.failure_stage`
- `sender.debug_wifi_direct_peer_status`
- `sender.command_wifi_direct_endpoint_binding_state`
- `sender.command_wifi_direct_endpoint_binding_reason`
- `sender.command_wifi_direct_bound_endpoints`
- `sender.wifi_direct_discovered_peers[].binding_state`
- `sender.wifi_direct_discovered_peers[].binding_reason`
- `target.deltas.accepted_connections`
- `target.deltas.inbound_packets`
- `verdict.endpoint_bound`
- `verdict.send_attempted`
- `verdict.transport_counter_delivery_observed`
- `verdict.message_delivery_proven`
- `verdict.status`

Если `verdict.status == sender_endpoint_not_bound`, но
`verdict.kraken_dns_sd_peer_seen == true` и
`verdict.failure_stage == wifi_p2p_group_connect_failed`, это означает:
Kraken peer найден на уровне DNS-SD/TXT, но Android Wi-Fi P2P не сформировал
группу/endpoint. Такой результат не надо трактовать как `UNKNOWN_PEER` в
смысле trust model.

После connect-attempt diagnostic patch capture должен показывать
`wifi_direct_connect_attempts[]` с `attempt`, `group_owner_intent`, `result`,
`failure_reason_name`, `stop_peer_discovery_result` и
`pre_connect_cancel_result`. Baseline `041155` это уже подтвердил. Если новый
прогон показывает `failure_stage`, но attempts отсутствуют, значит на телефонах
стоит старая APK или сломался экспорт runtime diagnostics.

## Граница утверждений

Текущий физический baseline подтверждает permissions, relationship, Kraken
DNS-SD discovery, target GO state и connect-attempt failure diagnostics в обе
стороны. Он не повышает claim про реальную Wi-Fi Direct доставку между двумя
устройствами: target counters не выросли, message delivery не доказана.
