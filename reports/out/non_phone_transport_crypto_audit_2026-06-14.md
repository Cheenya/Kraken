# Non-phone transport/crypto audit, 2026-06-14

Статус: локальный audit/refactor без телефонов, без UI-вмешательств, без commit/push.

## Что локально доказано

- Wi-Fi Direct debug peer hint создаёт только `DISCOVERED_UNBOUND`, а не притворяется отправляемым endpoint.
- `fingerprint` остаётся stable relationship identity; IP/port/group route остаются dynamic endpoint binding.
- Peer не считается sendable без `BOUND` endpoint с host и port.
- Локальная state machine покрывает `UNSEEN`, `DISCOVERED_UNBOUND`, `BOUND`, `STALE`, `FAILED`.
- Directed harness manifest различает:
  - `endpoint_bound`;
  - `send_attempted`;
  - `transport_counter_delivery_observed`;
  - `message_delivery_proven`.
- `--hint-target-wifi-direct-peer` parsing вынесен в тестируемый helper и покрыт fixture tests.
- Crypto/profile admission path покрывает:
  - слабый experimental profile не входит в outbox;
  - profile, ограниченный по размеру/сложности, не считается успешным допуском;
  - mismatched profile packet не входит в inbox;
  - receipt сохраняет `cryptoProfileId`, `sessionProfileId`, `admissionDecisionHash`, `profilePolicyVersion`;
  - retry message/receipt не обходит admission metadata checks.

## Что без телефонов не доказано

- Реальная Wi-Fi Direct доставка Samsung -> Xiaomi.
- Реальная Wi-Fi Direct доставка Xiaomi -> Samsung.
- Что Android framework даст актуальный DNS-SD TXT или visible-device data в нужный момент.
- Что group-owner/client routing даст рабочий socket endpoint на конкретных устройствах.
- Что `message_delivery_proven == true` появится в physical run.

## Поля manifest для следующего решения

Проверять `artifacts/directed-wifi-direct/<run>/manifest.json`:

- `sender.wifi_direct_permission_warning`
- `target.before.wifi_direct_permission_warning`
- `target.after.wifi_direct_permission_warning`
- `sender.wifi_direct_permissions.fine_location_granted`
- `target.before.wifi_direct_permissions.fine_location_granted`
- `target.after.wifi_direct_permissions.fine_location_granted`
- `debug_wifi_direct_peer_hint_requested`
- `debug_wifi_direct_peer_hint.status`
- `sender.debug_wifi_direct_peer_status`
- `sender.command_wifi_direct_endpoint_binding_state`
- `sender.command_wifi_direct_endpoint_binding_reason`
- `sender.command_wifi_direct_bound_endpoints`
- `target.deltas.accepted_connections`
- `target.deltas.inbound_packets`
- `verdict.permissions_ready`
- `verdict.relationship_ready`
- `verdict.wifi_direct_discovery_ready`
- `verdict.endpoint_bound`
- `verdict.send_attempted`
- `verdict.transport_counter_delivery_observed`
- `verdict.message_delivery_proven`
- `verdict.status`

## Decision tree

1. Любой permission warning или `fine_location_granted != true` означает, что blocker всё ещё permission/app-op.
2. `relationship_ready == false` означает проблему не в transport endpoint, а в relationship/sendability.
3. `wifi_direct_discovery_ready == false` означает DNS-SD/visible-device discovery gap.
4. `endpoint_bound == false` означает peer binding / endpoint resolution / group-owner routing gap.
5. `send_attempted == false` означает orchestration/debug-send wait gap.
6. `transport_counter_delivery_observed == true`, но `message_delivery_proven == false` разрешает только counter-delivery claim.
7. `message_delivery_proven == true` в обе стороны разрешает повышать claim до двустороннего debug Wi-Fi Direct message delivery evidence.

## Следующий физический runbook

Точный сценарий сборки, установки, выдачи permissions и directed прогонов записан в:

- `docs/wifi-direct-next-phone-runbook.md`

Запускать его только после явной команды и подключённых двух телефонов.
