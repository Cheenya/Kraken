# Kraken

Kraken: Android-приложение, настольные сборки и отчёты с результатами проверок.

Основная часть проекта - Android-приложение для хранения связей между устройствами, обмена сообщениями и
диагностических проверок, связанных с эллиптическими кривыми (в исследовательском режиме).


## Скачать

Ссылки для установки через GitHub Releases:

- Android APK:
  `https://github.com/Cheenya/Kraken/releases/latest/download/Kraken-android-debug.apk`
- macOS:
  `https://github.com/Cheenya/Kraken/releases/latest/download/Kraken-Desktop-macOS.app.zip`
- iOS Simulator:
  `https://github.com/Cheenya/Kraken/releases/latest/download/Kraken-iOS-simulator.app.zip`
- Windows:
  `https://github.com/Cheenya/Kraken/releases/latest/download/Kraken-Windows-portable-source.zip`
- SHA-256:
  `https://github.com/Cheenya/Kraken/releases/latest/download/SHA256SUMS.txt`

Либо в `downloads/`.

## Структура

`app-android/` - основной Android-проект на Kotlin/Compose.

`app-macos/` - нативная macOS-сборка.

`app-ios/` - нативная iOS-сборка.

`app-windows/` - Windows-сборка.

`reports/out/` - отчёты с результатами проверок.

`artifacts/` - артефакты разработки.

`app/src/main/assets/research/` - проверочные данные для Android-сборки.

`downloads/` - APK, архив macOS-приложения и контрольные суммы.


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
app-macos/dist/Kraken Desktop.app
```

## iOS

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --package-path app-ios
```

Сборка из Release предназначена для iOS Simulator.

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
