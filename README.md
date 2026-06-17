# Kraken

Материалы по проекту Kraken: исходный код приложения, сборки для установки,
отчеты экспериментов и данные, на которые есть ссылки в ВКР.

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

`app-macos/` - отдельная macOS-сборка. Она нужна для проверки части логики и
локального обмена, но не заменяет Android-версию.

`src/`, `tests/`, `benchmarks/` - Python-код, тесты и небольшие проверки.

`protocol-spec/` - описание форматов сообщений и связанных структур.

`docs/` - технические заметки по отдельным частям проекта.

`reports/out/` - отчеты и сохраненные результаты запусков.

`artifacts/` - дополнительные файлы, которые нужны для подтверждения отдельных
проверок.

## Пути из ВКР

В тексте ВКР некоторые пути указаны без отдельного пояснения корня. В этом
репозитории их нужно читать от корня репозитория:

```text
reports/out/sage_validation
reports/out/large_coefficient_sage_validation
artifacts/research_backend_benchmark/backend_benchmark_from_device.md
app/src/main/assets/research
```

При этом Android-проект лежит отдельно в `app-android/`, поэтому рабочий путь
для тех же research-assets внутри Android-сборки такой:

```text
app-android/app/src/main/assets/research/
```

## Отчеты

Основные файлы с результатами:

- `reports/out/adamova_effectiveness_experiment.md`
- `reports/out/adamova_effectiveness_dissertation_table.md`
- `reports/out/sage_validation/reference_comparison_report.md`
- `reports/out/large_coefficient_sage_validation/reference_comparison_report.md`
- `reports/out/android_p2p_smoke_report.md`
- `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`
- `reports/out/mesh_delivery_simulation.md`
- `reports/out/two_device_delivery_evidence.md`
- `reports/out/two_device_route_specific_smoke_2026-06-08.md`
- `reports/out/wifi_direct_endpoint_binding_refactor_2026-06-13.md`
- `reports/out/wifi_direct_reliability_sampling_2026-06-13.md`
- `reports/out/macos_desktop_transport_bridge_trial_2026-06-14.md`

В отчетах оставлены и успешные результаты, и ограничения. Если в конкретном
месте написано, что сценарий не доказан полностью, это часть результата
эксперимента.

## Android

Нужны JDK 17, Android SDK 35, NDK и CMake.

```bash
cd app-android
./gradlew test
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

## Python

```bash
python -m pytest
python -m compileall src scripts tests benchmarks
```

## Лицензия

См. `LICENSE.md`.
