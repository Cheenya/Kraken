# Kraken

Kraken: Android-приложение, настольные сборки и отчёты с результатами проверок.

Основная часть проекта - Android-приложение для проверки QR-сопряжения,
локального хранения связей между устройствами, обмена сообщениями и
диагностических проверок, связанных с эллиптическими кривыми.

## Скачать

Ссылки для установки через GitHub Releases:

- Android APK:
  `https://github.com/Cheenya/Kraken/releases/latest/download/Kraken-android-debug.apk`
- macOS:
  `https://github.com/Cheenya/Kraken/releases/latest/download/KrakenDesktop.app.zip`
- SHA-256:
  `https://github.com/Cheenya/Kraken/releases/latest/download/SHA256SUMS.txt`

Эти же файлы продублированы в `downloads/`.

## Структура

`app-android/` - основной Android-проект на Kotlin/Compose.

`app-macos/` - отдельная macOS-сборка для проверки настольных сценариев и
локального обмена.

`app-windows/` - Windows-сборка для проверки части логики, кадров QR/LAN/BLE,
локального состояния, QR-импорта, LAN-событий и исходящей очереди.

`reports/out/` - отчёты с результатами проверок.

`artifacts/` - дополнительные файлы, которые нужны для подтверждения отдельных
проверок.

`app/src/main/assets/research/` - исследовательские данные для Android-сборки.

`downloads/` - APK, архив macOS-приложения и контрольные суммы.

## Исследовательские данные

Некоторые отчёты и данные читаются от корня этого репозитория:

```text
reports/out/sage_validation
reports/out/large_coefficient_sage_validation
reports/out/random_risk_simulation.md
reports/out/adamova_effectiveness_experiment.md
artifacts/research_backend_benchmark/backend_benchmark_from_device.md
app/src/main/assets/research
```

Android-проект лежит отдельно в `app-android/`, поэтому рабочий путь для тех же
исследовательских файлов внутри Android-сборки такой:

```text
app-android/app/src/main/assets/research/
```

## Отчеты

Основные файлы с результатами:

- `reports/out/adamova_effectiveness_experiment.md`
- `reports/out/random_risk_simulation.md`
- `reports/out/sage_validation/reference_comparison_report.md`
- `reports/out/large_coefficient_sage_validation/reference_comparison_report.md`

В них лежат результаты запусков, сравнения и численные выводы, чтобы быстро
понять, что уже проверялось.

## Android

Нужны JDK 17, Android SDK 35, NDK и CMake.

```bash
cd app-android
./gradlew assembleDebug
```

APK после сборки:

```text
app-android/app/build/outputs/apk/debug/app-debug.apk
```

## macOS

```bash
cd app-macos
swift build
swift run KrakenDesktopCoreSmoke
./script/build_and_run.sh --verify
```

Локальная `.app`-сборка появляется здесь:

```text
app-macos/dist/KrakenDesktop.app
```

## Windows

```powershell
cd app-windows
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m kraken_windows
```

Быстрая проверка логики без GUI:

```bash
cd app-windows
python -m unittest discover -s tests
python -m compileall kraken_windows tests
python -m kraken_windows --smoke
```

## Лицензия

См. `LICENSE.md`.
