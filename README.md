# Kraken

Kraken is a research prototype for offline-first Android messaging experiments and elliptic-curve diagnostic materials used in dissertation work.

The repository contains:

- Android Kotlin/Compose application source in `app-android/`;
- native C++ diagnostic bridge used by the Android research panel;
- macOS SwiftUI desktop test harness in `app-macos/`;
- Python research modules in `src/`;
- technical boundary documents in `docs/`;
- protocol notes and schemas in `protocol-spec/`;
- selected reproducible reports in `reports/out/`;
- packaged prototype downloads in `downloads/`.

## Scope

Kraken is not a production-secure messenger. The current build is a research prototype for QR pairing, local relationship state, message pipeline experiments, transport diagnostics and rational elliptic-curve diagnostic reporting.

Transport work is interpreted by evidence boundary:

- LAN and BLE have saved prototype evidence in the published reports;
- Wi-Fi Direct remains a diagnostic direction unless a report explicitly states message delivery proof;
- macOS Desktop is a LAN/ADB bridge and packet handling test harness, not native macOS Wi-Fi Direct.

## Downloads

Stable download paths for QR codes:

- Android APK: `https://github.com/Cheenya/Kraken/releases/latest/download/Kraken-android-debug.apk`
- macOS app bundle archive: `https://github.com/Cheenya/Kraken/releases/latest/download/KrakenDesktop.app.zip`

Repository copies are also stored under `downloads/`.

Checksums are in `downloads/SHA256SUMS.txt`.

## Android Build

Requirements:

- JDK 17;
- Android SDK platform 35;
- Android NDK and CMake installed through Android Studio SDK Manager.

Commands:

```bash
cd app-android
./gradlew test
./gradlew assembleDebug
```

The debug APK is produced at:

```text
app-android/app/build/outputs/apk/debug/app-debug.apk
```

## macOS Build

Requirements:

- macOS 14 or newer;
- Swift 5.9 or newer.

Commands:

```bash
cd app-macos
swift build
swift run KrakenDesktopCoreSmoke
./script/build_and_run.sh --verify
```

The local app bundle is staged at:

```text
app-macos/dist/KrakenDesktop.app
```

## Python Checks

Requirements:

- Python 3.11 or newer;
- dependencies from `pyproject.toml`.

Commands:

```bash
python -m pytest
python -m compileall .
```

## Published Report Set

Selected reports are included under `reports/out/`:

- `adamova_effectiveness_experiment.md`;
- `adamova_effectiveness_experiment.json`;
- `adamova_effectiveness_experiment.csv`;
- `adamova_effectiveness_dissertation_table.md`;
- `sage_validation/reference_comparison_report.md`;
- `large_coefficient_sage_validation/reference_comparison_report.md`;
- `android_p2p_smoke_report.md`;
- `ble_two_device_delivery_evidence_2026-06-06.md`;
- `mesh_delivery_simulation.md`;
- `mesh_metrics_summary.json`;
- `two_device_delivery_evidence.md`;
- `two_device_route_specific_smoke_2026-06-08.md`;
- `two_device_route_specific_smoke_2026-06-08.json`;
- `wifi_direct_endpoint_binding_refactor_2026-06-13.md`;
- `wifi_direct_reliability_sampling_2026-06-13.md`;
- `macos_desktop_transport_bridge_trial_2026-06-14.md`.

Android-compatible research assets are included under the dissertation path:

```text
app/src/main/assets/research/
```

The buildable Android project keeps the same assets under:

```text
app-android/app/src/main/assets/research/
```

The Kotlin/C++ benchmark report is included at:

```text
artifacts/research_backend_benchmark/backend_benchmark_from_device.md
```

All paths in this repository are read from the repository root. The original dissertation text used the same relative paths without naming the root.

## License

See `LICENSE.md`.
