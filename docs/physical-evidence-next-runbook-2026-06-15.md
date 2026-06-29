# Physical Evidence Next Runbook - 2026-06-15

Этот runbook нужен для следующего физического прогона Kraken после включения
защищённого message payload path и нового transport UX.

Он не повышает claims сам по себе. Claims повышаются только после свежих
manifest/log artifacts.

## Текущий APK

- Path: `app-android/app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `5143331510995c30adb04dffebd9eeb5f3ad0358d186b2e235950e53a361b1f9`
- Size: `21125925` bytes
- Phone preflight:
  `artifacts/phone-preflight/20260615-after-wifi-direct-diagnostic-patch/`
- Preflight status: `ready_for_install_smoke=true`

Эта APK собрана после Wi-Fi Direct diagnostic patch: peer-level endpoint failure
экспортирует `binding_state=FAILED` и `binding_reason`. После этого rebuild APK
была установлена на Samsung `R5CY22X6MSB` и Xiaomi `d948ffd0`, runtime
permissions были выданы и проверены.

Для hotspot-compatible профиля сохранён отдельный диагностический capture:
`artifacts/debug-route-evidence/20260615-030913-hotspot-compatible-lan-ble-evidence-after-secure-default/`.

Последний Wi-Fi Direct baseline после установки этой APK:
`artifacts/directed-wifi-direct/20260615-041155-samsung-to-xiaomi-connect-attempt-diagnostics/`.
Он показал чистые permissions, готовую relationship/discovery часть,
`kraken_dns_sd_peer_seen=true`, target как живой group owner
`192.168.49.1`, но `endpoint_bound=false`,
`transport_counter_delivery_observed=false`, `message_delivery_proven=false`,
`failure_stage=wifi_p2p_group_connect_failed`. Sender attempts падают
`ERROR(0)` при `group_owner_intent` `0`, `15` и `7`; `stop_peer_discovery_result`
при этом `stopped`.

## Устройства

Ожидаемые serials из предыдущих прогонов:

- Samsung: `R5CY22X6MSB`
- Xiaomi: `d948ffd0`

Проверка:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
adb devices -l
```

Ожидаемо: оба устройства в состоянии `device`.

## Установка APK

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
adb -s R5CY22X6MSB install -r app-android/app/build/outputs/apk/debug/app-debug.apk
adb -s d948ffd0 install -r app-android/app/build/outputs/apk/debug/app-debug.apk
```

## Runtime permissions

```bash
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.BLUETOOTH_SCAN
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.BLUETOOTH_ADVERTISE
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.BLUETOOTH_CONNECT
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.NEARBY_WIFI_DEVICES
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.ACCESS_FINE_LOCATION
adb -s R5CY22X6MSB shell pm grant com.disser.kraken android.permission.POST_NOTIFICATIONS

adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.BLUETOOTH_SCAN
adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.BLUETOOTH_ADVERTISE
adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.BLUETOOTH_CONNECT
adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.NEARBY_WIFI_DEVICES
adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.ACCESS_FINE_LOCATION
adb -s d948ffd0 shell pm grant com.disser.kraken android.permission.POST_NOTIFICATIONS
```

Если `pm grant` вернёт ошибку по notifications или OEM-permission path, открыть
приложение вручную и выдать разрешение через системный prompt.

## Запуск приложения

```bash
adb -s R5CY22X6MSB shell monkey -p com.disser.kraken 1
adb -s d948ffd0 shell monkey -p com.disser.kraken 1
```

На экране `Связь рядом` для основного evidence выбрать:

- `Оставить точку доступа и запустить LAN/Bluetooth`

Wi-Fi Direct запускать отдельно только для dedicated directed trial.

ADB-эквивалент для диагностического capture без Wi-Fi Direct:

```bash
scripts/capture_debug_route_evidence.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --label hotspot-compatible-lan-ble-evidence-after-secure-default \
  --start-mesh-before-export \
  --transport-profile hotspot-compatible \
  --force-stop-mesh-before-start \
  --start-mesh-settle-ms 5000
```

## LAN/Bluetooth physical smoke

Сценарий вручную:

1. Оба телефона открыты, Kraken foreground.
2. На обоих телефонах есть локальные личности.
3. QR/nearby handshake доведён до `ACTIVE` контактов с обеих сторон.
4. Samsung -> Xiaomi: отправить короткое сообщение.
5. Xiaomi -> Samsung: отправить короткое сообщение.
6. Проверить UI: сообщение появилось на target, sender показывает честный статус.
7. Сохранить route/evidence capture.

Capture:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/capture_two_phone_smoke_evidence.sh \
  --device-a R5CY22X6MSB \
  --device-b d948ffd0 \
  --label crypto-path-lan-ble-after-encrypted-runtime
```

Claim boundary:

- Этот smoke подтверждает конкретный физический LAN/Bluetooth сценарий.
- Он не доказывает production reliability и не закрывает Wi-Fi Direct.

## Manual nearby/Bluetooth freeze observer

Для ручного nearby/Bluetooth подтверждения использовать отдельный наблюдатель,
чтобы не смешивать ручные UI-действия с evidence capture:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/capture_manual_nearby_freeze_smoke.sh \
  --samsung-device R5CY22X6MSB \
  --xiaomi-device d948ffd0 \
  --duration-seconds 180 \
  --label manual-nearby-bluetooth-confirmation
```

Порядок:

1. Открыть нужные экраны Kraken на обоих телефонах.
2. Запустить скрипт.
3. В течение окна наблюдения вручную выполнить nearby/Bluetooth подтверждение.
4. Смотреть `summary.json`, `summary.md`, `filtered_signals.txt` и logcat.

Скрипт не нажимает координаты на экране. Он только держит устройства awake через
`KEYCODE_WAKEUP`, очищает/снимает logcat, сохраняет screen/window/activity dump
и разделяет:

- настоящие freeze-сигналы: ANR, skipped frames, fatal exception, broadcast
  timeout;
- Wi-Fi Direct шум: P2P broadcasts/app-op записи, которые не считаются ANR сами
  по себе.

Dry-run без ручного UI-сценария:

- artifact:
  `artifacts/phone-audit/20260615-060736-tool-dry-run-filtered-no-manual-ui/`;
- verdict: `no_freeze_signals_observed_but_wifi_direct_noise_seen`.

Этот dry-run проверяет инструмент, но не закрывает ручной Bluetooth/nearby
freeze-smoke.

## MacBook physical relay / injection

Получить LAN IP Mac:

```bash
ipconfig getifaddr en0 || ipconfig getifaddr en1
```

Запустить physical relay modes:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_physical_inline_relay_trials.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --mac-host <MAC_LAN_IP> \
  --label crypto-path-physical-relay-after-encrypted-runtime
```

Проверять в manifest:

- `normal` mode: Mac получил frame и target counters/packets изменились;
- `drop` mode: frame принят Mac, но не доставлен target;
- `duplicate` mode: duplicate не создаёт двойную доставку сообщения;
- `tamper` mode: payload/context tamper не создаёт plaintext message.

Claim boundary:

- Это physical relay evidence только при наличии before/send/relay/after artifacts.
- Это не доказывает production MITM resistance без полного key agreement/signature/replay stack.

## Wi-Fi Direct directed trial

Wi-Fi Direct запускать отдельно, после основного LAN/Bluetooth evidence.
Если hotspot нужен пользователю, оставить его включённым и не запускать этот
этап.

Samsung -> Xiaomi:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --label samsung-to-xiaomi-after-encrypted-runtime
```

Xiaomi -> Samsung:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device d948ffd0 \
  --target-device R5CY22X6MSB \
  --label xiaomi-to-samsung-after-encrypted-runtime
```

Если discovery/binding не хватает:

```bash
scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --label samsung-to-xiaomi-after-encrypted-runtime-with-hint \
  --hint-target-wifi-direct-peer

scripts/run_directed_wifi_direct_route_trial.sh \
  --sender-device d948ffd0 \
  --target-device R5CY22X6MSB \
  --label xiaomi-to-samsung-after-encrypted-runtime-with-hint \
  --hint-target-wifi-direct-peer
```

Wi-Fi Direct success criteria:

- `wifi_direct_permission_warning == null`;
- `fine_location_granted == true` на sender и target captures;
- `verdict.endpoint_bound == true`;
- `verdict.send_attempted == true`;
- `verdict.transport_counter_delivery_observed == true` фиксирует только counter delivery;
- `verdict.message_delivery_proven == true` нужно для более сильного message-level claim.

Если результат похож на baseline `041155` (`kraken_dns_sd_peer_seen=true`, target
GO живой, но `endpoint_bound=false` и connect attempts падают `ERROR(0)`),
следующий scoped фикс должен идти в Android Wi-Fi P2P connect negotiation:
persistent groups, peer invitation/authorization, OEM-specific P2P state,
group owner/client routing и endpoint binding. Это уже не permission blocker и
не stop-discovery blocker.

Reverse baseline
`artifacts/directed-wifi-direct/20260615-042142-xiaomi-to-samsung-connect-attempt-diagnostics/`
дал тот же class of failure: target GO живой, sender attempts `ERROR(0)` при
intents `0`, `15`, `7`, target counters не растут. Поэтому следующий Wi-Fi
Direct проход должен рассматривать пару устройств как симметрично failing, а не
как проблему одного sender.

## После прогона

Обновить:

- `reports/out/crypto_mesh_goal_completion_audit_2026-06-15.md`;
- `reports/out/crypto_mesh_goal_completion_audit_2026-06-15.json`;
- `reports/out/crypto_mesh_technical_status_2026-06-15.md`;
- `reports/out/crypto_mesh_technical_status_2026-06-15.json`.

Закрывать `open` пункты только по свежим artifacts, не по факту запуска команд.
