# Kraken Desktop for macOS

Native macOS Desktop test harness for the Kraken research prototype.

## Scope

This app is a desktop testing surface for:

- local identity state;
- relationship states and the `ACTIVE` message gate;
- message status transitions;
- peer route snapshots for BLE, LAN and routed mesh;
- Adamova admission-gate semantics;
- a real LAN/TCP frame path compatible with Android `LanFrameCodec`;
- a macOS TCP listener that ACKs Android LAN frames with `0x06`;
- outbound Kraken LAN frames to an Android endpoint;
- evidence export for macOS LAN transport events;
- Android probe commands through ADB and the existing desktop relay preflight script.

It is still a research prototype. The desktop app does not claim production
cryptographic security and does not replace the Android Wi-Fi Direct/BLE proof
artifacts.

## Build and Run

```bash
cd app-macos
./script/build_and_run.sh --verify
```

The app stages a local bundle at `app-macos/dist/KrakenDesktop.app`.

## Android Probe

Open `Маршруты` and use:

- `ADB devices` to verify the connected Android device;
- `Relay preflight` to run `scripts/kraken_desktop_relay_preflight.py` from the repo root.
- `Start listener` to accept Android LAN frames on the selected macOS port;
- `Send LAN frame` to send a length-prefixed Kraken envelope to the configured Android endpoint;
- `Save evidence` to write `artifacts/macos-lan-transport/<timestamp>/`.

This is explicitly a `LAN/ADB bridge` path. It is not Android Wi-Fi Direct.

For long phone checks, keep the Android device awake only while USB-powered:

```bash
adb shell svc power stayon usb
```
