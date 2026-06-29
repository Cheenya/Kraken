#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package_name="${KRAKEN_PACKAGE_NAME:-com.disser.kraken}"
samsung_device="R5CY22X6MSB"
xiaomi_device="d948ffd0"
duration_seconds="30"
label="wifi-p2p-appop-noise"
output_dir=""

usage() {
  cat <<'EOF'
Usage: scripts/capture_wifi_p2p_appop_noise.sh [options]

Capture a compact Wi-Fi P2P permission/app-op diagnostic snapshot.
This is diagnostic-only: it does not prove Wi-Fi Direct message delivery.

Options:
  --samsung-device SERIAL   Samsung ADB serial. Default: R5CY22X6MSB.
  --xiaomi-device SERIAL    Xiaomi ADB serial. Default: d948ffd0.
  --duration-seconds N      Logcat observation duration. Default: 30.
  --label LABEL             Output folder label.
  --out-dir PATH            Output directory. Default: artifacts/phone-audit/<timestamp>-<label>.
  --package NAME            Android package. Default: com.disser.kraken.
  -h, --help                Show this help.
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
  safe_label="wifi-p2p-appop-noise"
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

capture_static_state() {
  local serial="$1"
  local role="$2"
  local phase="$3"
  local role_dir="${output_dir}/${role}-${serial}"
  mkdir -p "${role_dir}"

  {
    echo "\$ adb -s ${serial} get-state"
    adb -s "${serial}" get-state || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.product.model"
    adb -s "${serial}" shell getprop ro.product.model || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.build.version.sdk"
    adb -s "${serial}" shell getprop ro.build.version.sdk || true
    echo
    echo "\$ adb -s ${serial} shell appops get ${package_name} android:fine_location android:nearby_wifi_devices"
    adb -s "${serial}" shell appops get "${package_name}" android:fine_location android:nearby_wifi_devices || true
  } > "${role_dir}/${phase}_appops.txt" 2>&1

  adb -s "${serial}" shell dumpsys package "${package_name}" > "${role_dir}/${phase}_package_dump.txt" 2>&1 || true
  adb -s "${serial}" shell dumpsys wifi p2p > "${role_dir}/${phase}_wifi_p2p_dump.txt" 2>&1 || true
}

capture_logcat() {
  local serial="$1"
  local role="$2"
  local role_dir="${output_dir}/${role}-${serial}"
  mkdir -p "${role_dir}"
  adb -s "${serial}" logcat -d -v threadtime > "${role_dir}/logcat.txt" 2>&1 || true
}

prepare_device "${samsung_device}" "samsung"
prepare_device "${xiaomi_device}" "xiaomi"
capture_static_state "${samsung_device}" "samsung" "before"
capture_static_state "${xiaomi_device}" "xiaomi" "before"

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"

sleep "${duration_seconds}"

capture_static_state "${samsung_device}" "samsung" "after"
capture_static_state "${xiaomi_device}" "xiaomi" "after"
capture_logcat "${samsung_device}" "samsung"
capture_logcat "${xiaomi_device}" "xiaomi"

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

def rel(path: Path) -> str:
    return str(path.relative_to(output_dir))

def read_text(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")

def permission_granted(package_dump: str, permission: str) -> bool:
    return f"{permission}: granted=true" in package_dump

def appops_allow(appops: str) -> bool:
    return bool(re.search(r"FINE_LOCATION:\s+allow", appops))

def appops_uid_foreground(appops: str) -> bool:
    return bool(re.search(r"Uid mode:\s+FINE_LOCATION:\s+foreground", appops))

def analyze(role: str, serial: str) -> dict[str, object]:
    role_dir = output_dir / f"{role}-{serial}"
    before_appops = read_text(role_dir / "before_appops.txt")
    after_appops = read_text(role_dir / "after_appops.txt")
    after_package = read_text(role_dir / "after_package_dump.txt")
    logcat = read_text(role_dir / "logcat.txt")
    wifi_p2p = read_text(role_dir / "after_wifi_p2p_dump.txt")

    denial_lines = [
        line for line in logcat.splitlines()
        if package_name in line and "PEERS_CHANGED" in line and "Appop Denial" in line
    ]
    nearby_denial_lines = [
        line for line in logcat.splitlines()
        if "PEERS_CHANGED" in line and "requires android.permission.NEARBY_WIFI_DEVICES" in line
    ]
    p2p_noise_lines = [
        line for line in logcat.splitlines()
        if re.search(r"WIFI_P2P|WifiP2p|PEERS_CHANGED|P2P_DEVICE_FOUND", line)
    ]
    kraken_p2p_lines = [
        line for line in logcat.splitlines()
        if package_name in line and re.search(r"WIFI_P2P|WifiP2p|PEERS_CHANGED|P2P", line)
    ]
    group_formed = "groupFormed: true" in wifi_p2p or "mWifiP2pInfo groupFormed: true" in wifi_p2p
    group_owner = "isGroupOwner: true" in wifi_p2p

    return {
        "serial": serial,
        "fine_location_permission_granted": permission_granted(after_package, "android.permission.ACCESS_FINE_LOCATION"),
        "nearby_wifi_devices_permission_granted": permission_granted(after_package, "android.permission.NEARBY_WIFI_DEVICES"),
        "fine_location_appops_allow": appops_allow(after_appops),
        "fine_location_uid_foreground_mode": appops_uid_foreground(after_appops),
        "kraken_peers_changed_appop_denial_count": len(denial_lines),
        "nearby_wifi_devices_denial_count": len(nearby_denial_lines),
        "wifi_p2p_noise_count": len(p2p_noise_lines),
        "kraken_p2p_line_count": len(kraken_p2p_lines),
        "wifi_p2p_group_formed": group_formed,
        "wifi_p2p_group_owner": group_owner,
        "files": {
            "before_appops": rel(role_dir / "before_appops.txt"),
            "after_appops": rel(role_dir / "after_appops.txt"),
            "after_package_dump": rel(role_dir / "after_package_dump.txt"),
            "after_wifi_p2p_dump": rel(role_dir / "after_wifi_p2p_dump.txt"),
            "logcat": rel(role_dir / "logcat.txt"),
        },
        "sample_kraken_appop_denials": denial_lines[:20],
        "sample_p2p_noise": p2p_noise_lines[:40],
    }

devices = {
    "samsung": analyze("samsung", samsung_device),
    "xiaomi": analyze("xiaomi", xiaomi_device),
}
any_appop_denial = any(device["kraken_peers_changed_appop_denial_count"] > 0 for device in devices.values())
all_permissions_clean = all(
    device["fine_location_permission_granted"]
    and device["nearby_wifi_devices_permission_granted"]
    and device["fine_location_appops_allow"]
    for device in devices.values()
)
if any_appop_denial and all_permissions_clean:
    verdict = "permissions_granted_but_wifi_p2p_appop_denial_seen"
elif any_appop_denial:
    verdict = "wifi_p2p_appop_denial_seen_with_permission_gap"
else:
    verdict = "no_kraken_wifi_p2p_appop_denial_seen"

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
    "scope": "Wi-Fi P2P app-op diagnostic only; no delivery claim",
    "devices": devices,
    "verdict": verdict,
    "claim_boundary": "This captures Android permission/app-op/logcat state. It does not prove Wi-Fi Direct delivery or message transport.",
}
(output_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

filtered: list[str] = []
for role, device in devices.items():
    filtered.append(f"## {role} {device['serial']}")
    for line in device["sample_kraken_appop_denials"]:
        filtered.append(line)
    if not device["sample_kraken_appop_denials"]:
        filtered.append("No Kraken PEERS_CHANGED app-op denial lines captured.")
    filtered.append("")
(output_dir / "filtered_kraken_appop_denials.txt").write_text("\n".join(filtered), encoding="utf-8")

md = [
    f"# Wi-Fi P2P App-Op Noise Diagnostic - {generated_at}",
    "",
    f"- Duration: {duration_seconds}s",
    f"- Package: `{package_name}`",
    f"- Verdict: `{verdict}`",
    "- Scope: permission/app-op/logcat diagnostic only; no delivery claim.",
    "",
    "## Device Summary",
    "",
    "| Device | Fine permission | Nearby permission | Fine app-op allow | UID foreground mode | Kraken app-op denials | P2P noise lines | Group formed | GO |",
    "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
]
for role, device in devices.items():
    md.append(
        "| {role} | {fine} | {nearby} | {allow} | {uidfg} | {denials} | {noise} | {group} | {go} |".format(
            role=role,
            fine=str(device["fine_location_permission_granted"]).lower(),
            nearby=str(device["nearby_wifi_devices_permission_granted"]).lower(),
            allow=str(device["fine_location_appops_allow"]).lower(),
            uidfg=str(device["fine_location_uid_foreground_mode"]).lower(),
            denials=device["kraken_peers_changed_appop_denial_count"],
            noise=device["wifi_p2p_noise_count"],
            group=str(device["wifi_p2p_group_formed"]).lower(),
            go=str(device["wifi_p2p_group_owner"]).lower(),
        )
    )
md.extend(
    [
        "",
        "## Boundary",
        "",
        "Если verdict показывает app-op denial при выданных permissions, это не permission grant blocker. Это Android/OEM foreground/app-op behavior для Wi-Fi P2P broadcasts, которое нужно учитывать отдельно от ANR и отдельно от transport delivery.",
    ]
)
(output_dir / "summary.md").write_text("\n".join(md) + "\n", encoding="utf-8")

print(output_dir)
print(verdict)
PY
