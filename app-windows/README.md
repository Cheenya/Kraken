# Kraken Desktop для Windows

Настольный Windows-стенд Kraken.

## Назначение

Это приложение повторяет настольный стенд из `app-macos/` для проверок на
Windows:

- локальное состояние идентификатора;
- состояния связей между участниками и пропуск сообщений только для `ACTIVE`;
- переходы статусов сообщений;
- снимки маршрутов одноранговых узлов для BLE, LAN и маршрутизируемой mesh-сети;
- семантику допуска по критерию Адамовой;
- совместимое с Android кодирование и декодирование LAN-кадров;
- совместимое с Android разбиение и сборку BLE-фрагментов;
- ручную нормализацию QR-полезной нагрузки для invite/response/confirmation;
- импорт QR invite/response/confirmation в локальное состояние связи;
- устойчивое JSON-состояние и записи повторной отправки в исходящей очереди;
- локальные LAN TCP-отправитель и слушатель с обработкой ACK для loopback/LAN;
- редьюсеры LAN/BLE-хронологии для входящих и исходящих транспортных событий;
- локальный экспорт подтверждений в `output/windows-evidence/`.

Windows-версия нужна для стендовых проверок QR, LAN/BLE-кадров и локального
состояния. Android остаётся основной мобильной сборкой проекта.

## Запуск на Windows

```powershell
cd app-windows
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m kraken_windows
```

Либо можно запустить:

```cmd
run_windows.bat
```

## Проверки при разработке

Базовые smoke-тесты используют только стандартную библиотеку Python, поэтому их
можно запускать даже без установленного PySide6:

```bash
cd app-windows
python -m unittest discover -s tests
python -m compileall kraken_windows tests
python -m kraken_windows --smoke
```

## Сборка Windows EXE

Команда для Windows:

```powershell
cd app-windows
.\build_windows.ps1
```

Скрипт устанавливает `PySide6` и `pyinstaller` в `.venv`, а затем кладёт сборку
в `dist/KrakenWindows/`.

## Связь с Android-частью

Windows-порт использует модель настольного стенда, совместимую с LAN/BLE/QR.
Проверки Wi-Fi Direct остаются в Android-части проекта.

Текущий аудит соответствия находится в `AUDIT.md`.
