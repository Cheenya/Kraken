#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="macos-ble-probe"
duration_seconds="20"
output_dir=""
device=""
start_android_mesh="false"
probe_bundle_id="${KRAKEN_BLE_PROBE_BUNDLE_ID:-com.disser.kraken.desktop}"
probe_display_name="${KRAKEN_BLE_PROBE_DISPLAY_NAME:-Kraken Desktop}"
probe_executable_name="${KRAKEN_BLE_PROBE_EXECUTABLE:-KrakenDesktop}"

usage() {
  cat <<'EOF'
Usage: scripts/run_macos_ble_probe.sh [options]

Run the macOS CoreBluetooth BLE probe and write an evidence artifact.

Options:
  --duration-seconds N   Probe duration. Default: 20.
  --label LABEL          Artifact label. Default: macos-ble-probe.
  --out-dir PATH         Output directory. Default: artifacts/macos-ble-probe/<timestamp>-<label>.
  --device SERIAL        ADB device serial for optional Android mesh startup.
  --start-android-mesh   Start Android debug mesh with transport-profile=all during the macOS probe.
  -h, --help             Show this help.

The artifact records macOS BLE role startup state and Android-compatible UUID/framing.
Role startup is proven only if probe.json has success=true. Phone discovery/delivery is
proven only if probe.json has peer_observed=true or BLE events.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --duration-seconds)
      duration_seconds="${2:-}"
      shift 2
      ;;
    --label)
      label="${2:-}"
      shift 2
      ;;
    --out-dir)
      output_dir="${2:-}"
      shift 2
      ;;
    --device)
      device="${2:-}"
      shift 2
      ;;
    --start-android-mesh)
      start_android_mesh="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/macos-ble-probe/${timestamp}-${label}"
fi
mkdir -p "${output_dir}"

adb devices >"${output_dir}/adb_devices.txt" 2>&1 || true
system_profiler SPBluetoothDataType >"${output_dir}/bluetooth_system_profiler.txt" 2>&1 || true

android_pid=""
cleanup() {
  if [[ -n "${android_pid}" ]]; then
    wait "${android_pid}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if [[ "${start_android_mesh}" == "true" ]]; then
  android_dir="${output_dir}/android_debug_route_evidence"
  android_args=(
    "${repo_root}/scripts/capture_debug_route_evidence.sh"
    --out-dir "${android_dir}"
    --label "${label}-android-ble-start"
    --start-mesh-before-export
    --transport-profile all
    --launch-app-before-broadcast
    --hold-after-export-ms "$((duration_seconds * 1000 + 5000))"
  )
  if [[ -n "${device}" ]]; then
    android_args+=(--device "${device}")
  fi
  bash "${android_args[@]}" >"${output_dir}/android_mesh_start.log" 2>&1 &
  android_pid="$!"
  sleep 4
fi

set +e
(
  cd "${repo_root}/app-macos"
  swift build --product KrakenDesktopBleProbe
  build_dir="$(swift build --show-bin-path)"
  probe_bundle="${output_dir}/KrakenDesktopBleProbe.app"
  probe_contents="${probe_bundle}/Contents"
  probe_macos="${probe_contents}/MacOS"
  probe_resources="${probe_contents}/Resources"
  rm -rf "${probe_bundle}"
  mkdir -p "${probe_macos}" "${probe_resources}"
  cp "${build_dir}/KrakenDesktopBleProbe" "${probe_macos}/${probe_executable_name}"
  chmod +x "${probe_macos}/${probe_executable_name}"
  find "${build_dir}" -maxdepth 1 -name '*.bundle' -exec cp -R {} "${probe_resources}/" \;
  cat >"${probe_contents}/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleExecutable</key>
  <string>${probe_executable_name}</string>
  <key>CFBundleIdentifier</key>
  <string>${probe_bundle_id}</string>
  <key>CFBundleName</key>
  <string>${probe_display_name}</string>
  <key>CFBundleDisplayName</key>
  <string>${probe_display_name}</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>LSMinimumSystemVersion</key>
  <string>14.0</string>
  <key>NSPrincipalClass</key>
  <string>NSApplication</string>
  <key>NSBluetoothAlwaysUsageDescription</key>
  <string>Kraken uses Bluetooth to test local BLE transport with nearby Android devices.</string>
  <key>NSBluetoothPeripheralUsageDescription</key>
  <string>Kraken advertises a local BLE GATT service for transport tests.</string>
</dict>
</plist>
PLIST
  /usr/bin/codesign --force --deep --sign - "${probe_bundle}" >/dev/null
  if [[ "${probe_executable_name}" == "KrakenDesktop" ]]; then
    /usr/bin/pkill -x KrakenDesktop >/dev/null 2>&1 || true
    sleep 1
  fi
  /usr/bin/open -n -W "${probe_bundle}" --args \
    --duration-seconds "${duration_seconds}" \
    --out "${output_dir}/probe.json"
) >"${output_dir}/probe.stdout.json" 2>"${output_dir}/probe.stderr.log"
launcher_exit=$?
set -e

probe_exit="${launcher_exit}"
if [[ -f "${output_dir}/probe.json" ]]; then
  if grep -q '"success"[[:space:]]*:[[:space:]]*true' "${output_dir}/probe.json"; then
    probe_exit="0"
  else
    probe_exit="1"
  fi
fi

/usr/bin/log show --last 5m --style compact \
  --predicate "process == \"${probe_executable_name}\" AND (subsystem == \"com.apple.bluetooth\" OR eventMessage CONTAINS \"TCC\")" \
  >"${output_dir}/corebluetooth_tcc.log" 2>&1 || true

cat >"${output_dir}/README.md" <<EOF
# macOS BLE Probe

- label: ${label}
- duration_seconds: ${duration_seconds}
- launcher_exit: ${launcher_exit}
- probe_exit: ${probe_exit}
- android_mesh_started: ${start_android_mesh}
- adb_device: ${device:-not specified}
- probe_bundle_id: ${probe_bundle_id}
- probe_display_name: ${probe_display_name}
- probe_executable_name: ${probe_executable_name}

## Граница проверки

Этот артефакт фиксирует запуск CoreBluetooth на macOS и Android-совместимые
BLE UUID/кадры. Запуск ролей подтверждён только если в \`probe.json\`
\`success=true\`. Обнаружение телефона подтверждено только если в \`probe.json\`
\`peer_observed=true\` или есть BLE-события передачи. Сам по себе файл не
доказывает стабильность Bluetooth-доставки.

## Files

- \`probe.json\` - structured BLE status samples, UUIDs, role plan and events.
- \`probe.stdout.json\` - stdout copy of the same report.
- \`probe.stderr.log\` - SwiftPM/probe stderr.
- \`adb_devices.txt\` - connected Android devices at run time.
- \`bluetooth_system_profiler.txt\` - host Bluetooth adapter state.
- \`corebluetooth_tcc.log\` - filtered CoreBluetooth/TCC runtime log.
- \`KrakenDesktopBleProbe.app\` - bundled probe with Bluetooth usage strings.
- \`android_debug_route_evidence/\` - optional Android debug evidence if enabled.

## macOS Bluetooth permission note

By default this probe uses the main Kraken Desktop bundle identifier so it checks
the same Bluetooth permission bucket as the app. It also runs the probe binary as
\`KrakenDesktop\`, matching the main app process name more closely. The script
stops a running \`KrakenDesktop\` before the probe starts to keep the TCC/log
evidence unambiguous. Override \`KRAKEN_BLE_PROBE_BUNDLE_ID\` or
\`KRAKEN_BLE_PROBE_EXECUTABLE\` only when testing TCC behavior for a separate
probe app.
EOF

exit "${probe_exit}"
