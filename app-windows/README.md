# Kraken Desktop для Windows

Настольная Windows-версия Kraken для проверки QR, локальных маршрутов и формата пакетов.
Общие правила текста и границ утверждений: `../docs/kraken-cross-platform-copy-rules.md`.

## Что проверяет

Приложение повторяет ключевые механики `app-macos/` в Windows-окружении:

- профиль Kraken на устройстве;
- состояния контакта и правило, что сообщения доступны только для активной связи;
- переходы статусов сообщений;
- снимки маршрутов BLE, LAN и mesh-ретрансляции;
- контур допуска криптографического профиля;
- кодирование и чтение LAN-кадров, совместимых с Android;
- кодирование и сборку BLE-фрагментов, совместимых с Android;
- ручную нормализацию QR-данных для приглашения, ответа и подтверждения;
- локальный экспорт evidence в `output/windows-evidence/`.

Windows-версия нужна как настольная проверочная поверхность. Радиоповедение Android
Wi-Fi Direct и BLE подтверждается отдельными Android-артефактами.

## Запуск в Windows

```powershell
cd app-windows
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m kraken_windows
```

Или через готовый скрипт:

```cmd
run_windows.bat
```

## Проверки

Smoke-тесты ядра используют только стандартную библиотеку Python, поэтому могут
запускаться даже без PySide6:

```bash
cd app-windows
python -m unittest discover -s tests
python -m compileall kraken_windows tests
python -m kraken_windows --smoke
```

## Сборка EXE

Запускать в Windows:

```powershell
cd app-windows
.\build_windows.ps1
```

Скрипт ставит `PySide6` и `pyinstaller` в `.venv`, затем пишет bundle в
`dist/KrakenWindows/`.

## Граница версии

Windows-порт проверяет совместимость LAN/BLE/QR-форматов и локальное состояние
приложения. Нативный Windows Wi-Fi Direct здесь не реализуется.
