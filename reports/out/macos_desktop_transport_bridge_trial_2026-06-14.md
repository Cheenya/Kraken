# macOS Desktop transport bridge trial, 2026-06-14

## Что проверено

Добавлен и выполнен воспроизводимый сценарий `scripts/run_macos_android_lan_adb_bridge_trial.sh`.

Сценарий поднимает Android LAN-only transport через debug receiver, собирает macOS LAN probe/listener, настраивает:

- `adb forward`: macOS -> Android LAN listener;
- `adb reverse`: Android debug-send -> macOS LAN listener.

Успешный прогон:

- artifact: `artifacts/macos-android-lan-adb-bridge/20260614-114139-device-b-lan-adb-bridge-2026-06-14/summary.json`;
- device: `ANDROID_DEVICE_B`, Device B ANDROID_MODEL_B;
- macOS -> Android ACK: `true`;
- Android debug send success: `true`;
- macOS listener received frame: `true`;
- overall success: `true`.

## Идентичность и endpoint

Для этого trial использовались:

- Android identity fingerprint: `B42B 3068 934E F618`;
- macOS endpoint fingerprint: `3C4E D5BA 9DB8 8F9B`;
- relationship id: `relationship-adb-bridge-B42B3068934E-3C4ED5BA9DB8`.

ADB host/port в этом сценарии являются только текущими transport endpoints. Они не являются стабильной идентичностью peer. Стабильная идентичность по-прежнему должна строиться вокруг fingerprint/relationship.

## Что изменено для воспроизводимости

- `DebugEvidenceReceiver` теперь экспортирует полные debug-only fingerprints:
  - `identity_fingerprint`;
  - `first_sendable_relationship_fingerprint`;
  - `observed_peer_fingerprints`.
- `capture_debug_route_evidence.sh` переносит эти поля в manifest.
- Новый `run_macos_android_lan_adb_bridge_trial.sh` оркестрирует двунаправленный Mac<->Android LAN/ADB trial и пишет self-contained artifact.

## Что подключено к UI

- В настройках `LAN/ADB мост` и в chat inspector добавлено действие `Выбрать peer`.
- Endpoint fingerprint теперь явно превращается в выбранный UI peer и route `DIRECT_LAN` / `macos-lan-adb-bridge`.
- Отправка из чата через LAN/ADB больше не выполняется, если fingerprint endpoint не совпадает с выбранным peer.
- В chat header добавлена кнопка открытия LAN/ADB inspector для выбранного диалога.
- LAN evidence artifact теперь сохраняет выбранный peer и selected route рядом с transport events.

## BLE / CoreBluetooth probe

Добавлен воспроизводимый macOS BLE probe:

- SwiftPM product: `KrakenDesktopBleProbe`;
- harness: `scripts/run_macos_ble_probe.sh`;
- latest artifact: `artifacts/macos-ble-probe/20260614-125510-diagnostic-power-alert/probe.json`.

Что уже проверяется автоматически:

- macOS BLE service UUID совпадает с Android `BleGattTransport`: `58a1257c-f4a8-48c8-99d5-917b9863d7c4`;
- identity characteristic UUID совпадает: `58a1257d-f4a8-48c8-99d5-917b9863d7c4`;
- packet characteristic UUID совпадает: `58a1257e-f4a8-48c8-99d5-917b9863d7c4`;
- BLE chunk JSON использует Android-compatible snake_case keys: `frame_version`, `payload_base64`;
- BLE identity JSON использует `peer_id`, `display_name`;
- Android `BleFrameCodecTest` остаётся зелёным.

Runtime probe на текущем Mac пока не доказал `scanning/advertising`: `probe.json` завершился с `success=false`, `lastError=ble-state-callback-timeout`. В latest artifact дополнительно сохранены:

- `bluetooth_system_profiler.txt`: Bluetooth controller включён (`State: On`, `Transport: PCIe`);
- `corebluetooth_tcc.log`: CoreBluetooth дошёл до TCC-запроса (`TCCAccessRequest`, `TCC available 1, req 1 complete 0`), но permission/state callback не завершился.

Probe теперь запускается как bundled AppKit `.app` с Bluetooth usage strings, foreground window и ad-hoc signing. Поэтому текущий BLE blocker выглядит как macOS Bluetooth/TCC approval state, а не как несовпадение UUID/framing или отсутствие Bluetooth hardware. Peer discovery и BLE delivery пока не заявляются как доказанные.

## Граница claim

Этот прогон доказывает Android-compatible LAN/TCP frame exchange через ADB tunnels.

Он не доказывает:

- native macOS Wi-Fi Direct;
- runtime BLE discovery/delivery с телефоном;
- production reliability;
- стабильность ADB endpoint между сетями;
- полноценную desktop identity onboarding через QR.

## Следующий технический шаг

1. Подключить этот bridge сценарий к UI как явный `LAN/ADB мост`, а не как Wi-Fi Direct.
2. Убрать необходимость притворяться существующим phone relationship fingerprint: desktop identity должна добавляться на Android через QR и становиться настоящим relationship peer.
3. Довести UI-send path до evidence: выбранный peer -> route state -> send -> delivery/error -> saved artifact.
4. Для BLE: довести probe до завершённого CoreBluetooth state (`scanning/advertising`) после Bluetooth permission, затем повторить с Android mesh рядом и зафиксировать `peer_observed=true` или transfer events.
