#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package_name="${KRAKEN_PACKAGE_NAME:-com.disser.kraken}"
samsung_device="R5CY22X6MSB"
xiaomi_device="d948ffd0"
duration_seconds="180"
label="manual-nearby-bluetooth-freeze-smoke"
output_dir=""

usage() {
  cat <<'EOF'
Usage: scripts/capture_manual_nearby_freeze_smoke.sh [options]

Capture ANR/freeze evidence while a human performs a nearby/Bluetooth action
in Kraken. The script does not tap screen coordinates and does not drive the UI.

Options:
  --samsung-device SERIAL     Samsung ADB serial. Default: R5CY22X6MSB.
  --xiaomi-device SERIAL      Xiaomi ADB serial. Default: d948ffd0.
  --duration-seconds N        Observation duration. Default: 180.
  --label LABEL               Output folder label.
  --out-dir PATH              Output directory. Default: artifacts/phone-audit/<timestamp>-<label>.
  --package NAME              Android package. Default: com.disser.kraken.
  -h, --help                  Show this help.

Suggested use:
  1. Open the Kraken screen you want to test on both phones.
  2. Start this script.
  3. During the observation window, manually perform the Bluetooth/nearby
     confirmation flow.
  4. Use summary.json and summary.md as evidence.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --samsung-device)
      samsung_device="${2:-}"
      shift 2
      ;;
    --xiaomi-device)
      xiaomi_device="${2:-}"
      shift 2
      ;;
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
    --package)
      package_name="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

if ! [[ "${duration_seconds}" =~ ^[0-9]+$ ]] || [[ "${duration_seconds}" -le 0 ]]; then
  echo "--duration-seconds must be a positive integer." >&2
  exit 2
fi

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="manual-nearby-bluetooth-freeze-smoke"
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/phone-audit/${timestamp}-${safe_label}"
fi
mkdir -p "${output_dir}"

device_state() {
  local serial="$1"
  adb -s "${serial}" get-state 2>/dev/null || true
}

prepare_device() {
  local serial="$1"
  local role="$2"
  local state
  state="$(device_state "${serial}")"
  if [[ "${state}" != "device" ]]; then
    echo "${role} ${serial} is not connected as device: ${state}" >&2
    exit 1
  fi
  adb -s "${serial}" shell svc power stayon true >/dev/null 2>&1 || true
  adb -s "${serial}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb -s "${serial}" logcat -c >/dev/null 2>&1 || true
}

capture_device() {
  local serial="$1"
  local role="$2"
  local role_dir="${output_dir}/${role}-${serial}"
  mkdir -p "${role_dir}"

  {
    echo "\$ adb -s ${serial} get-state"
    adb -s "${serial}" get-state || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.product.manufacturer"
    adb -s "${serial}" shell getprop ro.product.manufacturer || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.product.model"
    adb -s "${serial}" shell getprop ro.product.model || true
    echo
    echo "\$ adb -s ${serial} shell settings get global stay_on_while_plugged_in"
    adb -s "${serial}" shell settings get global stay_on_while_plugged_in || true
  } > "${role_dir}/device_state.txt" 2>&1

  adb -s "${serial}" shell dumpsys window > "${role_dir}/window_dump.txt" 2>&1 || true
  adb -s "${serial}" shell dumpsys activity activities > "${role_dir}/activity_dump.txt" 2>&1 || true
  adb -s "${serial}" shell dumpsys package "${package_name}" > "${role_dir}/package_dump.txt" 2>&1 || true
  adb -s "${serial}" exec-out screencap -p > "${role_dir}/screen.png" || true
  adb -s "${serial}" logcat -d -v threadtime > "${role_dir}/logcat.txt" 2>&1 || true
}

prepare_device "${samsung_device}" "samsung"
prepare_device "${xiaomi_device}" "xiaomi"

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"

adb devices -l > "${output_dir}/adb_devices_before.txt"
git -C "${repo_root}" status --short --branch > "${output_dir}/git_status.txt" 2>&1 || true

started_epoch="$(date +%s)"
deadline=$((started_epoch + duration_seconds))
cycle=0
{
  echo "started_at=${generated_at}"
  echo "duration_seconds=${duration_seconds}"
  echo "samsung_device=${samsung_device}"
  echo "xiaomi_device=${xiaomi_device}"
} > "${output_dir}/keepawake.log"

while [[ "$(date +%s)" -lt "${deadline}" ]]; do
  cycle=$((cycle + 1))
  for serial in "${samsung_device}" "${xiaomi_device}"; do
    adb -s "${serial}" shell svc power stayon true >/dev/null 2>&1 || true
    adb -s "${serial}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  done
  printf 'cycle=%s now=%s samsung_state=%s xiaomi_state=%s\n' \
    "${cycle}" \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "$(device_state "${samsung_device}")" \
    "$(device_state "${xiaomi_device}")" >> "${output_dir}/keepawake.log"
  sleep 30
done

adb devices -l > "${output_dir}/adb_devices_after.txt"
capture_device "${samsung_device}" "samsung"
capture_device "${xiaomi_device}" "xiaomi"

python3 - "${output_dir}" "${generated_at}" "${duration_seconds}" "${package_name}" "${git_sha}" "${git_branch}" "${git_source_state}" "${samsung_device}" "${xiaomi_device}" <<'PY'
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

output_dir = Path(sys.argv[1])
generated_at = sys.argv[2]
duration_seconds = int(sys.argv[3])
package_name = sys.argv[4]
git_sha = sys.argv[5]
git_branch = sys.argv[6]
git_source_state = sys.argv[7]
samsung_device = sys.argv[8]
xiaomi_device = sys.argv[9]

signal_patterns = {
    "anr": re.compile(r"Application Not Responding|ANR in|am_anr|Input dispatching timed out", re.IGNORECASE),
    "skipped_frames": re.compile(r"Skipped\s+\d+\s+frames", re.IGNORECASE),
    "fatal_exception": re.compile(r"FATAL EXCEPTION|E AndroidRuntime|Process .* has died", re.IGNORECASE),
    "broadcast_timeout": re.compile(r"Timeout of broadcast|Receiver during timeout|Broadcast of Intent .* timed out", re.IGNORECASE),
    "wifi_direct_noise": re.compile(r"WIFI_P2P|WifiP2p|PEERS_CHANGED|Appop Denial.*fine_location", re.IGNORECASE),
    "bluetooth_nearby": re.compile(r"Bluetooth|BLE|Gatt|Nearby", re.IGNORECASE),
}

def relative(path: Path) -> str:
    return str(path.relative_to(output_dir))

def read_text(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")

def analyze(role: str, serial: str) -> dict[str, object]:
    role_dir = output_dir / f"{role}-{serial}"
    logcat_path = role_dir / "logcat.txt"
    logcat = read_text(logcat_path)
    matches: dict[str, list[str]] = {}
    for name, pattern in signal_patterns.items():
        lines = [line for line in logcat.splitlines() if pattern.search(line)]
        matches[name] = lines[:80]
    state = read_text(role_dir / "device_state.txt")
    disconnected = "device" not in state
    return {
        "serial": serial,
        "logcat": relative(logcat_path),
        "screen_png": relative(role_dir / "screen.png"),
        "device_state": relative(role_dir / "device_state.txt"),
        "activity_dump": relative(role_dir / "activity_dump.txt"),
        "window_dump": relative(role_dir / "window_dump.txt"),
        "disconnected": disconnected,
        "signals": {
            "anr": bool(matches["anr"]),
            "skipped_frames": bool(matches["skipped_frames"]),
            "fatal_exception": bool(matches["fatal_exception"]),
            "broadcast_timeout": bool(matches["broadcast_timeout"]),
            "wifi_direct_noise": bool(matches["wifi_direct_noise"]),
            "bluetooth_nearby_mentions": bool(matches["bluetooth_nearby"]),
        },
        "matched_lines": matches,
    }

devices = {
    "samsung": analyze("samsung", samsung_device),
    "xiaomi": analyze("xiaomi", xiaomi_device),
}
blocking_flags = [
    device["signals"]["anr"]
    or device["signals"]["skipped_frames"]
    or device["signals"]["fatal_exception"]
    or device["signals"]["broadcast_timeout"]
    or device["disconnected"]
    for device in devices.values()
]
wifi_noise_flags = [device["signals"]["wifi_direct_noise"] for device in devices.values()]
if any(blocking_flags):
    verdict = "freeze_signals_observed"
elif any(wifi_noise_flags):
    verdict = "no_freeze_signals_observed_but_wifi_direct_noise_seen"
else:
    verdict = "no_freeze_signals_observed"

summary = {
    "artifact": str(output_dir),
    "generated_at": generated_at,
    "duration_seconds": duration_seconds,
    "package": package_name,
    "git": {
        "sha": git_sha,
        "branch": git_branch,
        "source_state": git_source_state,
    },
    "scope": "manual nearby/Bluetooth freeze observer; user drives UI manually; no message delivery claim",
    "method": "clear logcat, keep phones awake with KEYCODE_WAKEUP, capture logcat/window/activity/screens after observation",
    "devices": devices,
    "verdict": verdict,
    "claim_boundary": "This only checks freeze/ANR/logcat signals during the observation window. It does not prove transport delivery or Wi-Fi Direct readiness.",
}

(output_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

filtered_lines: list[str] = []
for role, device in devices.items():
    filtered_lines.append(f"## {role} {device['serial']}")
    for name, lines in device["matched_lines"].items():
        if not lines:
            continue
        filtered_lines.append(f"### {name}")
        filtered_lines.extend(lines)
        filtered_lines.append("")
(output_dir / "filtered_signals.txt").write_text("\n".join(filtered_lines).strip() + "\n", encoding="utf-8")

md = [
    f"# Manual Nearby/Bluetooth Freeze Smoke - {generated_at}",
    "",
    f"- Duration: {duration_seconds}s",
    f"- Package: `{package_name}`",
    f"- Samsung: `{samsung_device}`",
    f"- Xiaomi: `{xiaomi_device}`",
    f"- Verdict: `{verdict}`",
    "- Scope: ручной nearby/Bluetooth сценарий; скрипт не нажимал по UI и не доказывает доставку сообщений.",
    "",
    "## Signals",
    "",
    "| Device | ANR | Skipped frames | Fatal exception | Broadcast timeout | Wi-Fi Direct noise | Bluetooth/Nearby mentions |",
    "|---|---:|---:|---:|---:|---:|---:|",
]
for role, device in devices.items():
    signals = device["signals"]
    md.append(
        "| {role} | {anr} | {skipped} | {fatal} | {broadcast} | {wifi} | {bt} |".format(
            role=role,
            anr=str(signals["anr"]).lower(),
            skipped=str(signals["skipped_frames"]).lower(),
            fatal=str(signals["fatal_exception"]).lower(),
            broadcast=str(signals["broadcast_timeout"]).lower(),
            wifi=str(signals["wifi_direct_noise"]).lower(),
            bt=str(signals["bluetooth_nearby_mentions"]).lower(),
        )
    )
md.extend(
    [
        "",
        "## Files",
        "",
        "- `summary.json`",
        "- `filtered_signals.txt`",
        "- `samsung-*/logcat.txt`, `xiaomi-*/logcat.txt`",
        "- `samsung-*/screen.png`, `xiaomi-*/screen.png`",
        "",
        "## Boundary",
        "",
        "Это freeze/ANR evidence. Оно не заменяет directed transport harness и не повышает Wi-Fi Direct claim.",
    ]
)
(output_dir / "summary.md").write_text("\n".join(md) + "\n", encoding="utf-8")

print(output_dir)
print(verdict)
PY
