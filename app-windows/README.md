# Kraken Desktop for Windows

Windows desktop test harness for the Kraken research prototype.

## Scope

This app mirrors the macOS desktop harness at `app-macos/` for Windows-oriented
testing:

- local identity state;
- relationship states and the `ACTIVE` message gate;
- message status transitions;
- peer route snapshots for BLE, LAN and routed mesh;
- Adamova admission-gate semantics;
- Android-compatible LAN frame encoding/decoding;
- Android-compatible BLE chunk encoding/reassembly;
- manual QR payload normalization for invite/response/confirmation flows;
- local evidence export under `output/windows-evidence/`.

It is still a research prototype. The Windows app does not claim production
cryptographic security and does not replace Android Wi-Fi Direct/BLE proof
artifacts.

## Run on Windows

```powershell
cd app-windows
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m kraken_windows
```

Or use:

```cmd
run_windows.bat
```

## Development Checks

The core smoke tests use only the Python standard library, so they can run even
when PySide6 is not installed:

```bash
cd app-windows
python -m unittest discover -s tests
python -m compileall kraken_windows tests
```

## Build a Windows EXE

Run on Windows:

```powershell
cd app-windows
.\build_windows.ps1
```

The script installs `PySide6` and `pyinstaller` into `.venv` and writes the
bundle to `dist/KrakenWindows/`.

## Boundary

The Windows port uses a LAN/BLE/QR-compatible desktop harness model. It is not a
native Windows Wi-Fi Direct implementation and should not be described as a
production transport layer.
