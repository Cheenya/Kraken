# Kraken Desktop для macOS

Нативная macOS-версия Kraken для проверки desktop-сценариев, QR-сопряжения,
LAN/ADB-моста и совместимости форматов с Android.
Общие правила текста и границ утверждений: `../docs/kraken-cross-platform-copy-rules.md`.

## Что проверяет

Приложение используется как настольная проверочная поверхность для:

- профиля Kraken на Mac;
- состояний контакта и правила, что сообщения доступны только для активной связи;
- переходов статусов сообщений;
- снимков маршрутов BLE, LAN и mesh-ретрансляции;
- контура допуска криптографического профиля;
- реального LAN/TCP-кадра, совместимого с Android `LanFrameCodec`;
- TCP-приёмника macOS, который подтверждает Android LAN-кадры байтом `0x06`;
- исходящих LAN-кадров Kraken на Xiaomi/Android-адрес;
- экспорта evidence по событиям LAN/BLE на macOS;
- ADB-проверок Xiaomi/Android и предпроверка desktop relay.

macOS-версия не заменяет телефонные прогоны Android Wi-Fi Direct/BLE. Она
фиксирует настольный LAN/ADB и CoreBluetooth-контур, чтобы проверять формат
пакетов, маршрутизацию и evidence без постоянного участия двух телефонов.

## Сборка и запуск

```bash
cd app-macos
./script/build_and_run.sh --verify
```

Скрипт собирает локальный bundle `app-macos/dist/Kraken Desktop.app`.

Для обычной установки в пользовательскую папку приложений:

```bash
cd app-macos
./script/build_and_run.sh --install
```

После установки приложение лежит в `~/Applications/Kraken Desktop.app`.
Если нужно сразу открыть установленную копию:

```bash
cd app-macos
./script/build_and_run.sh --install-run
```

## Проверка Xiaomi

Откройте `Маршруты` и используйте:

- `ADB-устройства`, чтобы увидеть подключённый телефон, например Xiaomi `d948ffd0`;
- `Предпроверка relay`, чтобы запустить `scripts/kraken_desktop_relay_preflight.py`;
- `Включить приём`, чтобы принимать Android LAN-кадры на выбранном порту macOS;
- `Отправить LAN-кадр`, чтобы отправить Kraken envelope на настроенный Android-адрес;
- `Сохранить артефакт`, чтобы записать `artifacts/macos-lan-transport/<timestamp>/`.

Это именно путь `LAN/ADB-мост`, а не Android Wi-Fi Direct.

Для долгих телефонных проверок держите Xiaomi бодрствующим только при USB-питании:

```bash
adb -s d948ffd0 shell svc power stayon usb
```
