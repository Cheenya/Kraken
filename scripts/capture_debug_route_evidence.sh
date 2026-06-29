#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package_name="${KRAKEN_PACKAGE_NAME:-com.disser.kraken}"
action_name="com.disser.kraken.debug.EXPORT_ROUTE_EVIDENCE"
receiver_name="${package_name}/com.disser.kraken.debug.DebugEvidenceReceiver"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="debug-route-evidence"
run_local_hostile_probe="false"
start_mesh_before_export="false"
transport_profile="all"
manual_peer_fingerprint=""
manual_peer_host=""
manual_peer_port=""
debug_wifi_direct_peer_device_address=""
debug_wifi_direct_peer_device_name=""
debug_wifi_direct_peer_port=""
debug_send_body=""
debug_send_wait_ms="0"
debug_send_attempts="1"
debug_send_retry_delay_ms="1000"
sync_after_debug_send="false"
sync_before_export="false"
sync_attempts="1"
start_mesh_settle_ms="2000"
reuse_running_mesh="false"
force_stop_mesh_before_start="false"
start_foreground_wifi_direct="false"
ensure_wifi_direct_group_owner="false"
launch_app_before_broadcast="false"
hold_after_export_ms="0"
output_dir=""
devices=()

usage() {
  cat <<'EOF'
Usage: scripts/capture_debug_route_evidence.sh [options]

Capture debug-only in-app route-specific evidence JSON/MD through ADB.

Options:
  --device SERIAL       ADB serial. May be passed more than once. If omitted, all connected devices are used.
  --label LABEL         Scenario label. Default: debug-route-evidence.
  --out-dir PATH        Output directory. Default: artifacts/debug-route-evidence/<timestamp>-<label>.
  --package NAME        Android package. Default: com.disser.kraken.
  --start-mesh-before-export
                        Ask the debug receiver to start mesh transports before export.
  --transport-profile PROFILE
                        Debug start profile: all, hotspot-compatible, wifi-direct-only or lan-only.
                        Default: all.
  --manual-peer-fingerprint FINGERPRINT
                        Debug-only LAN manual peer fingerprint to add before export.
  --manual-peer-host HOST
                        Debug-only LAN manual peer host/IP to add before export.
  --manual-peer-port PORT
                        Debug-only LAN manual peer TCP port to add before export.
  --debug-wifi-direct-peer-device-address ADDRESS
                        Debug-only Wi-Fi Direct peer device/MAC address to bind to the
                        first sendable relationship before export.
  --debug-wifi-direct-peer-device-name NAME
                        Debug-only Wi-Fi Direct peer display name for evidence only.
  --debug-wifi-direct-peer-port PORT
                        Debug-only Wi-Fi Direct peer TCP port to bind before export.
  --debug-send-body BODY
                        Debug-only message body to enqueue for the first sendable relationship.
  --debug-send-wait-ms N
                        Wait up to N ms for a sendable relationship to appear in transport before debug send.
  --debug-send-attempts N
                        Debug-send attempts after a sendable relationship is observed. Default: 1.
  --debug-send-retry-delay-ms N
                        Delay between debug-send attempts. Default: 1000.
  --sync-after-debug-send
                        Run mesh sync after debug-send/manual-peer setup.
  --sync-before-export
                        Run mesh sync before exporting evidence, even without a debug send.
  --sync-attempts N
                        Sync attempts for debug-send path. Default: 1.
  --start-mesh-settle-ms N
                        Milliseconds to wait after starting transports before debug actions. Default: 2000.
  --reuse-running-mesh
                        Do not restart transports for debug-send/manual-peer; use a previously started mesh.
  --force-stop-mesh-before-start
                        Stop the in-app mesh runtime before starting the requested debug transport profile.
  --start-foreground-wifi-direct
                        Start the debug Wi-Fi Direct-only foreground mesh service before export.
  --ensure-wifi-direct-group-owner
                        Debug-only: ask the Wi-Fi Direct transport to create a local group owner group.
  --launch-app-before-broadcast
                        Bring the app activity to foreground before sending the debug broadcast.
                        This is implied by --start-foreground-wifi-direct.
  --hold-after-export-ms N
                        Keep debug receiver process alive after export for N ms. Default: 0.
  --run-local-hostile-probe
                        Ask the debug receiver to run the local inbox hostile packet probe before export.
  -h, --help            Show this help.

This script requires a debug APK. It does not prove physical LAN/BLE/Wi-Fi Direct hostile injection.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      devices+=("${2:-}")
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
      receiver_name="${package_name}/com.disser.kraken.debug.DebugEvidenceReceiver"
      shift 2
      ;;
    --run-local-hostile-probe)
      run_local_hostile_probe="true"
      shift
      ;;
    --start-mesh-before-export)
      start_mesh_before_export="true"
      shift
      ;;
    --transport-profile)
      transport_profile="${2:-}"
      if [[ "${transport_profile}" != "all" && "${transport_profile}" != "hotspot-compatible" && "${transport_profile}" != "wifi-direct-only" && "${transport_profile}" != "lan-only" ]]; then
        echo "Unsupported --transport-profile: ${transport_profile}" >&2
        usage >&2
        exit 2
      fi
      shift 2
      ;;
    --manual-peer-fingerprint)
      manual_peer_fingerprint="${2:-}"
      shift 2
      ;;
    --manual-peer-host)
      manual_peer_host="${2:-}"
      shift 2
      ;;
    --manual-peer-port)
      manual_peer_port="${2:-}"
      shift 2
      ;;
    --debug-wifi-direct-peer-device-address)
      debug_wifi_direct_peer_device_address="${2:-}"
      shift 2
      ;;
    --debug-wifi-direct-peer-device-name)
      debug_wifi_direct_peer_device_name="${2:-}"
      shift 2
      ;;
    --debug-wifi-direct-peer-port)
      debug_wifi_direct_peer_port="${2:-}"
      shift 2
      ;;
    --debug-send-body)
      debug_send_body="${2:-}"
      shift 2
      ;;
    --debug-send-wait-ms)
      debug_send_wait_ms="${2:-}"
      shift 2
      ;;
    --debug-send-attempts)
      debug_send_attempts="${2:-}"
      shift 2
      ;;
    --debug-send-retry-delay-ms)
      debug_send_retry_delay_ms="${2:-}"
      shift 2
      ;;
    --sync-after-debug-send)
      sync_after_debug_send="true"
      shift
      ;;
    --sync-before-export)
      sync_before_export="true"
      shift
      ;;
    --sync-attempts)
      sync_attempts="${2:-}"
      shift 2
      ;;
    --start-mesh-settle-ms)
      start_mesh_settle_ms="${2:-}"
      shift 2
      ;;
    --reuse-running-mesh)
      reuse_running_mesh="true"
      shift
      ;;
    --force-stop-mesh-before-start)
      force_stop_mesh_before_start="true"
      shift
      ;;
    --start-foreground-wifi-direct)
      start_foreground_wifi_direct="true"
      shift
      ;;
    --ensure-wifi-direct-group-owner)
      ensure_wifi_direct_group_owner="true"
      start_foreground_wifi_direct="true"
      shift
      ;;
    --launch-app-before-broadcast)
      launch_app_before_broadcast="true"
      shift
      ;;
    --hold-after-export-ms)
      hold_after_export_ms="${2:-}"
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

if [[ -n "${manual_peer_fingerprint}${manual_peer_host}${manual_peer_port}" ]]; then
  if [[ -z "${manual_peer_fingerprint}" || -z "${manual_peer_host}" || -z "${manual_peer_port}" ]]; then
    echo "Manual peer setup requires --manual-peer-fingerprint, --manual-peer-host and --manual-peer-port." >&2
    exit 2
  fi
  if ! [[ "${manual_peer_port}" =~ ^[0-9]+$ ]] || (( manual_peer_port < 1 || manual_peer_port > 65535 )); then
    echo "Invalid --manual-peer-port: ${manual_peer_port}" >&2
    exit 2
  fi
fi

if [[ -n "${debug_wifi_direct_peer_device_address}${debug_wifi_direct_peer_device_name}${debug_wifi_direct_peer_port}" ]]; then
  if [[ -z "${debug_wifi_direct_peer_device_address}" || -z "${debug_wifi_direct_peer_port}" ]]; then
    echo "Debug Wi-Fi Direct peer setup requires --debug-wifi-direct-peer-device-address and --debug-wifi-direct-peer-port." >&2
    exit 2
  fi
  if ! [[ "${debug_wifi_direct_peer_device_address}" =~ ^([[:xdigit:]]{2}:){5}[[:xdigit:]]{2}$ ]]; then
    echo "Invalid --debug-wifi-direct-peer-device-address: ${debug_wifi_direct_peer_device_address}" >&2
    exit 2
  fi
  if ! [[ "${debug_wifi_direct_peer_port}" =~ ^[0-9]+$ ]] || (( debug_wifi_direct_peer_port < 1 || debug_wifi_direct_peer_port > 65535 )); then
    echo "Invalid --debug-wifi-direct-peer-port: ${debug_wifi_direct_peer_port}" >&2
    exit 2
  fi
fi

if ! [[ "${sync_attempts}" =~ ^[0-9]+$ ]] || (( sync_attempts < 0 || sync_attempts > 8 )); then
  echo "Invalid --sync-attempts: ${sync_attempts}" >&2
  exit 2
fi

if ! [[ "${debug_send_wait_ms}" =~ ^[0-9]+$ ]] || (( debug_send_wait_ms < 0 || debug_send_wait_ms > 120000 )); then
  echo "Invalid --debug-send-wait-ms: ${debug_send_wait_ms}" >&2
  exit 2
fi

if ! [[ "${debug_send_attempts}" =~ ^[0-9]+$ ]] || (( debug_send_attempts < 1 || debug_send_attempts > 8 )); then
  echo "Invalid --debug-send-attempts: ${debug_send_attempts}" >&2
  exit 2
fi

if ! [[ "${debug_send_retry_delay_ms}" =~ ^[0-9]+$ ]] || (( debug_send_retry_delay_ms < 0 || debug_send_retry_delay_ms > 30000 )); then
  echo "Invalid --debug-send-retry-delay-ms: ${debug_send_retry_delay_ms}" >&2
  exit 2
fi

if ! [[ "${start_mesh_settle_ms}" =~ ^[0-9]+$ ]] || (( start_mesh_settle_ms < 0 || start_mesh_settle_ms > 60000 )); then
  echo "Invalid --start-mesh-settle-ms: ${start_mesh_settle_ms}" >&2
  exit 2
fi

if ! [[ "${hold_after_export_ms}" =~ ^[0-9]+$ ]] || (( hold_after_export_ms < 0 || hold_after_export_ms > 120000 )); then
  echo "Invalid --hold-after-export-ms: ${hold_after_export_ms}" >&2
  exit 2
fi

if [[ "${start_foreground_wifi_direct}" == "true" ]]; then
  launch_app_before_broadcast="true"
fi

capture_wait_timeout_sec=$(( start_mesh_settle_ms / 1000 + debug_send_wait_ms / 1000 + (debug_send_attempts - 1) * debug_send_retry_delay_ms / 1000 + debug_send_attempts * 50 + 25 ))
if (( capture_wait_timeout_sec < 20 )); then
  capture_wait_timeout_sec=20
elif (( capture_wait_timeout_sec > 240 )); then
  capture_wait_timeout_sec=240
fi
post_hold_refresh_wait_sec=0
if (( hold_after_export_ms > 0 )); then
  post_hold_refresh_wait_sec=$(( hold_after_export_ms / 1000 + 3 ))
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

if [[ "${#devices[@]}" -eq 0 ]]; then
  while IFS= read -r serial; do
    devices+=("${serial}")
  done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
fi

if [[ "${#devices[@]}" -eq 0 ]]; then
  echo "No connected adb devices." >&2
  adb devices >&2
  exit 1
fi

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="debug-route-evidence"
fi

if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/debug-route-evidence/${timestamp}-${safe_label}"
fi
mkdir -p "${output_dir}"

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"

adb devices -l > "${output_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${output_dir}/git_status.txt" 2>&1 || true

pull_file() {
  local serial="$1"
  local remote_name="$2"
  local local_path="$3"
  if adb -s "${serial}" shell run-as "${package_name}" ls "files/${remote_name}" >/dev/null 2>&1; then
    adb -s "${serial}" exec-out run-as "${package_name}" cat "files/${remote_name}" > "${local_path}"
  else
    printf 'missing: files/%s\n' "${remote_name}" > "${local_path}.missing"
  fi
}

wait_for_remote_file() {
  local serial="$1"
  local remote_name="$2"
  local timeout_sec="$3"
  local waited=0
  while (( waited < timeout_sec )); do
    if adb -s "${serial}" shell run-as "${package_name}" ls "files/${remote_name}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

write_shell_capture() {
  local serial="$1"
  local local_path="$2"
  shift 2
  {
    printf '$ adb -s %s shell' "${serial}"
    printf ' %q' "$@"
    echo
    adb -s "${serial}" shell "$@" || true
  } > "${local_path}" 2>&1
}

capture_device_network_state() {
  local serial="$1"
  local state_dir="$2"
  mkdir -p "${state_dir}"
  write_shell_capture "${serial}" "${state_dir}/ip_addr.txt" ip addr show
  write_shell_capture "${serial}" "${state_dir}/ip_route.txt" ip route show table all
  write_shell_capture "${serial}" "${state_dir}/proc_net_tcp.txt" cat /proc/net/tcp
  write_shell_capture "${serial}" "${state_dir}/proc_net_tcp6.txt" cat /proc/net/tcp6
  write_shell_capture "${serial}" "${state_dir}/dumpsys_wifip2p.txt" dumpsys wifip2p
  write_shell_capture "${serial}" "${state_dir}/cmd_wifi_status.txt" cmd wifi status
}

for serial in "${devices[@]}"; do
  device_dir="${output_dir}/${serial}"
  mkdir -p "${device_dir}"
  {
    echo "\$ adb -s ${serial} shell getprop ro.product.manufacturer"
    adb -s "${serial}" shell getprop ro.product.manufacturer || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.product.model"
    adb -s "${serial}" shell getprop ro.product.model || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.build.version.release"
    adb -s "${serial}" shell getprop ro.build.version.release || true
  } > "${device_dir}/device_identity.txt" 2>&1

  capture_device_network_state "${serial}" "${device_dir}/network_state_before_broadcast"

  if [[ "${launch_app_before_broadcast}" == "true" ]]; then
    {
      echo "\$ adb -s ${serial} shell am start -n ${package_name}/.MainActivity"
      adb -s "${serial}" shell am start -n "${package_name}/.MainActivity" || true
      sleep 2
      echo
      echo "\$ adb -s ${serial} shell dumpsys window | grep focus"
      adb -s "${serial}" shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' || true
    } > "${device_dir}/launch_app_before_broadcast.txt" 2>&1
  fi

  adb -s "${serial}" shell run-as "${package_name}" rm -f \
    "files/route_specific_evidence_latest.json" \
    "files/route_specific_evidence_summary_latest.md" \
    "files/debug_evidence_command_result.json" \
    > "${device_dir}/cleanup.txt" 2>&1 || true

  broadcast_args=(
    -n "${receiver_name}" \
    -a "${action_name}" \
    --ez start_mesh_before_export "${start_mesh_before_export}" \
    --ez run_local_hostile_probe "${run_local_hostile_probe}" \
    --es transport_profile "${transport_profile}" \
    --ei debug_send_wait_ms "${debug_send_wait_ms}" \
    --ei debug_send_attempts "${debug_send_attempts}" \
    --ei debug_send_retry_delay_ms "${debug_send_retry_delay_ms}" \
    --ez sync_after_debug_send "${sync_after_debug_send}" \
    --ez sync_before_export "${sync_before_export}" \
    --ei sync_attempts "${sync_attempts}" \
    --ei start_mesh_settle_ms "${start_mesh_settle_ms}" \
    --ei hold_after_export_ms "${hold_after_export_ms}" \
    --ez start_foreground_wifi_direct "${start_foreground_wifi_direct}" \
    --ez ensure_wifi_direct_group_owner "${ensure_wifi_direct_group_owner}" \
    --ez force_stop_mesh_before_start "${force_stop_mesh_before_start}" \
    --ez reuse_running_mesh "${reuse_running_mesh}"
  )
  if [[ -n "${manual_peer_fingerprint}" ]]; then
    broadcast_args+=(--es manual_peer_fingerprint "${manual_peer_fingerprint}")
  fi
  if [[ -n "${manual_peer_host}" ]]; then
    broadcast_args+=(--es manual_peer_host "${manual_peer_host}")
  fi
  if [[ -n "${manual_peer_port}" ]]; then
    broadcast_args+=(--ei manual_peer_port "${manual_peer_port}")
  fi
  if [[ -n "${debug_wifi_direct_peer_device_address}" ]]; then
    broadcast_args+=(--es debug_wifi_direct_peer_device_address "${debug_wifi_direct_peer_device_address}")
  fi
  if [[ -n "${debug_wifi_direct_peer_device_name}" ]]; then
    broadcast_args+=(--es debug_wifi_direct_peer_device_name "${debug_wifi_direct_peer_device_name}")
  fi
  if [[ -n "${debug_wifi_direct_peer_port}" ]]; then
    broadcast_args+=(--ei debug_wifi_direct_peer_port "${debug_wifi_direct_peer_port}")
  fi
  if [[ -n "${debug_send_body}" ]]; then
    broadcast_args+=(--es debug_send_body "${debug_send_body}")
  fi

  remote_args=(am broadcast "${broadcast_args[@]}")
  printf -v remote_command '%q ' "${remote_args[@]}"
  adb -s "${serial}" shell "${remote_command}" > "${device_dir}/broadcast.txt" 2>&1 || true
  {
    printf 'capture_wait_timeout_sec=%s\n' "${capture_wait_timeout_sec}"
    wait_for_remote_file "${serial}" "debug_evidence_command_result.json" "${capture_wait_timeout_sec}" \
      && echo "debug_evidence_command_result.json=present" \
      || echo "debug_evidence_command_result.json=missing_after_wait"
    wait_for_remote_file "${serial}" "route_specific_evidence_latest.json" "${capture_wait_timeout_sec}" \
      && echo "route_specific_evidence_latest.json=present" \
      || echo "route_specific_evidence_latest.json=missing_after_wait"
    if (( post_hold_refresh_wait_sec > 0 )); then
      printf 'post_hold_refresh_wait_sec=%s\n' "${post_hold_refresh_wait_sec}"
      sleep "${post_hold_refresh_wait_sec}"
      echo "post_hold_refresh_wait=done"
    fi
  } > "${device_dir}/evidence_file_wait.txt" 2>&1
  capture_device_network_state "${serial}" "${device_dir}/network_state_after_broadcast"

  pull_file "${serial}" "route_specific_evidence_latest.json" "${device_dir}/route_specific_evidence_latest.json"
  pull_file "${serial}" "route_specific_evidence_summary_latest.md" "${device_dir}/route_specific_evidence_summary_latest.md"
  pull_file "${serial}" "debug_evidence_command_result.json" "${device_dir}/debug_evidence_command_result.json"
  adb -s "${serial}" exec-out screencap -p > "${device_dir}/screen.png" || true
done

python3 - "${output_dir}" "${generated_at}" "${git_branch}" "${git_sha}" "${git_source_state}" "${package_name}" "${start_mesh_before_export}" "${run_local_hostile_probe}" "${hold_after_export_ms}" "${post_hold_refresh_wait_sec}" "${debug_send_wait_ms}" "${debug_send_attempts}" "${debug_send_retry_delay_ms}" "${start_foreground_wifi_direct}" "${ensure_wifi_direct_group_owner}" "${force_stop_mesh_before_start}" "${launch_app_before_broadcast}" "${transport_profile}" "${manual_peer_fingerprint}" "${manual_peer_host}" "${manual_peer_port}" "${debug_wifi_direct_peer_device_address}" "${debug_wifi_direct_peer_device_name}" "${debug_wifi_direct_peer_port}" "${debug_send_body}" "${sync_after_debug_send}" "${sync_before_export}" "${sync_attempts}" "${devices[@]}" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

output_dir = Path(sys.argv[1])
generated_at = sys.argv[2]
git_branch = sys.argv[3]
git_sha = sys.argv[4]
git_source_state = sys.argv[5]
package_name = sys.argv[6]
start_mesh_before_export = sys.argv[7] == "true"
run_local_hostile_probe = sys.argv[8] == "true"
hold_after_export_ms = int(sys.argv[9])
post_hold_refresh_wait_sec = int(sys.argv[10])
debug_send_wait_ms = int(sys.argv[11])
debug_send_attempts = int(sys.argv[12])
debug_send_retry_delay_ms = int(sys.argv[13])
start_foreground_wifi_direct = sys.argv[14] == "true"
ensure_wifi_direct_group_owner = sys.argv[15] == "true"
force_stop_mesh_before_start = sys.argv[16] == "true"
launch_app_before_broadcast = sys.argv[17] == "true"
transport_profile = sys.argv[18]
manual_peer_fingerprint = sys.argv[19]
manual_peer_host = sys.argv[20]
manual_peer_port = sys.argv[21]
debug_wifi_direct_peer_device_address = sys.argv[22]
debug_wifi_direct_peer_device_name = sys.argv[23]
debug_wifi_direct_peer_port = sys.argv[24]
debug_send_body = sys.argv[25]
sync_after_debug_send = sys.argv[26] == "true"
sync_before_export = sys.argv[27] == "true"
sync_attempts = int(sys.argv[28])
devices = sys.argv[29:]


def _clean_line(line: str) -> str:
    return " ".join(line.strip().split())


def _first_line_containing(lines: list[str], needle: str) -> str | None:
    for line in lines:
        if needle in line:
            return _clean_line(line)
    return None


def _last_lines_containing(lines: list[str], needles: tuple[str, ...], limit: int) -> list[str]:
    matches = [
        _clean_line(line)
        for line in lines
        if any(needle in line for needle in needles)
    ]
    return matches[-limit:]


def _block_after_line(lines: list[str], marker: str, max_lines: int = 24) -> list[str]:
    for index, line in enumerate(lines):
        if marker not in line:
            continue
        block = [_clean_line(line)]
        for next_line in lines[index + 1 : index + max_lines]:
            stripped = next_line.strip()
            if not stripped:
                continue
            if stripped.startswith("m") and block:
                break
            block.append(_clean_line(next_line))
        return block
    return []


def _blocks_after_lines(lines: list[str], marker: str, max_lines: int = 18, limit: int = 4) -> list[list[str]]:
    blocks: list[list[str]] = []
    for index, line in enumerate(lines):
        if marker not in line:
            continue
        block = [_clean_line(line)]
        for next_line in lines[index + 1 : index + max_lines]:
            stripped = next_line.strip()
            if not stripped:
                continue
            if stripped.startswith("m") or stripped.startswith("network:"):
                break
            block.append(_clean_line(next_line))
        blocks.append(block)
    return blocks[-limit:]


def parse_wifi_p2p_state(path: Path) -> dict[str, object]:
    if not path.exists():
        return {"available": False, "path": str(path)}
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    connection_events = _last_lines_containing(
        lines,
        ("connectionType=", "connectivityLevelFailureCode="),
        limit=10,
    )
    group_events = _last_lines_containing(lines, ("mGroupEvents", "GROUP_", "groupState="), limit=10)
    return {
        "available": True,
        "path": str(path.relative_to(output_dir)),
        "cur_state": _first_line_containing(lines, "curState="),
        "wifi_p2p_info": _first_line_containing(lines, "mWifiP2pInfo"),
        "group": _first_line_containing(lines, "mGroup "),
        "saved_peer_config": _block_after_line(lines, "mSavedPeerConfig"),
        "autonomous_group": _first_line_containing(lines, "mAutonomousGroup"),
        "join_existing_group": _first_line_containing(lines, "mJoinExistingGroup"),
        "active_clients": _first_line_containing(lines, "mActiveClients"),
        "recent_connection_events": connection_events,
        "recent_group_events": group_events,
        "recent_persistent_groups": _blocks_after_lines(lines, "network:", limit=3),
    }


def capture_wifi_p2p_state_summary(device_dir: Path) -> dict[str, object]:
    before = parse_wifi_p2p_state(device_dir / "network_state_before_broadcast" / "dumpsys_wifip2p.txt")
    after = parse_wifi_p2p_state(device_dir / "network_state_after_broadcast" / "dumpsys_wifip2p.txt")
    summary = {
        "before_broadcast": before,
        "after_broadcast": after,
    }
    (device_dir / "wifi_p2p_state_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return summary


device_entries = []
for serial in devices:
    device_dir = output_dir / serial
    route_json = device_dir / "route_specific_evidence_latest.json"
    command_result = device_dir / "debug_evidence_command_result.json"
    route_payload = json.loads(route_json.read_text(encoding="utf-8")) if route_json.exists() else {}
    command_payload = json.loads(command_result.read_text(encoding="utf-8")) if command_result.exists() else {}
    transport = route_payload.get("transport", {})
    wifi_direct = transport.get("wifi_direct")
    wifi_direct_permissions = transport.get("wifi_direct_permissions")
    debug_smoke = route_payload.get("debug_smoke", {})
    target_delivery = route_payload.get("target_delivery", {})
    metrics = route_payload.get("metrics", {})
    wifi_p2p_state_summary = capture_wifi_p2p_state_summary(device_dir)
    device_entries.append(
        {
            "serial": serial,
            "selected_route": transport.get("selected_route"),
            "enabled_transport_modes": transport.get("enabled_transport_modes", []),
            "registration_state": transport.get("registration_state"),
            "discovery_state": transport.get("discovery_state"),
            "transport_discovered_peer_count": transport.get("transport_discovered_peer_count"),
            "manual_peer_count": transport.get("manual_peer_count"),
            "p2p_visible_device_count": transport.get("p2p_visible_device_count"),
            "p2p_this_device_status": transport.get("p2p_this_device_status"),
            "discovery_cycle_count": transport.get("discovery_cycle_count"),
            "p2p_service_found_count": transport.get("p2p_service_found_count"),
            "p2p_txt_record_count": transport.get("p2p_txt_record_count"),
            "p2p_txt_rejected_count": transport.get("p2p_txt_rejected_count"),
            "p2p_txt_bound_peer_count": transport.get("p2p_txt_bound_peer_count"),
            "p2p_unbound_visible_device_count": transport.get("p2p_unbound_visible_device_count"),
            "wifi_direct_last_binding_error": transport.get("wifi_direct_last_binding_error"),
            "local_port": transport.get("local_port"),
            "local_addresses": transport.get("local_addresses", []),
            "p2p_interface_addresses": transport.get("p2p_interface_addresses", []),
            "wifi_direct_group_formed": transport.get("wifi_direct_group_formed"),
            "wifi_direct_is_group_owner": transport.get("wifi_direct_is_group_owner"),
            "wifi_direct_group_role": transport.get("wifi_direct_group_role"),
            "wifi_direct_group_owner_address": transport.get("wifi_direct_group_owner_address"),
            "wifi_direct_local_p2p_address": transport.get("wifi_direct_local_p2p_address"),
            "wifi_direct_server_bind_address": transport.get("wifi_direct_server_bind_address"),
            "wifi_direct_last_send_host": transport.get("wifi_direct_last_send_host"),
            "wifi_direct_last_send_port": transport.get("wifi_direct_last_send_port"),
            "wifi_direct_endpoint_binding_state": transport.get("wifi_direct_endpoint_binding_state"),
            "wifi_direct_endpoint_binding_reason": transport.get("wifi_direct_endpoint_binding_reason"),
            "wifi_direct_relationship_peer_fingerprint_prefix": transport.get("wifi_direct_relationship_peer_fingerprint_prefix"),
            "wifi_direct_last_connect_device_address": transport.get("wifi_direct_last_connect_device_address"),
            "wifi_direct_last_connect_device_name": transport.get("wifi_direct_last_connect_device_name"),
            "wifi_direct_last_connect_group_owner_intent": transport.get("wifi_direct_last_connect_group_owner_intent"),
            "wifi_direct_last_connect_result": transport.get("wifi_direct_last_connect_result"),
            "wifi_direct_last_connect_failure_reason": transport.get("wifi_direct_last_connect_failure_reason"),
            "wifi_direct_connect_attempts": transport.get("wifi_direct_connect_attempts", []),
            "wifi_direct_discovered_peers": transport.get("wifi_direct_discovered_peers", []),
            "wifi_direct_visible_devices": transport.get("wifi_direct_visible_devices", []),
            "wifi_direct_txt_records": transport.get("wifi_direct_txt_records", []),
            "wifi_direct_bound_endpoints": transport.get("wifi_direct_bound_endpoints", []),
            "accepted_connections": transport.get("accepted_connections"),
            "inbound_packets": transport.get("inbound_packets"),
            "malformed_frames_dropped": transport.get("malformed_frames_dropped"),
            "send_failures": transport.get("send_failures"),
            "wifi_direct": wifi_direct,
            "wifi_direct_permissions": wifi_direct_permissions,
            "wifi_direct_permission_warning": command_payload.get("wifi_direct_permission_warning"),
            "command_wifi_direct_endpoint_binding_state": command_payload.get("wifi_direct_endpoint_binding_state"),
            "command_wifi_direct_endpoint_binding_reason": command_payload.get("wifi_direct_endpoint_binding_reason"),
            "command_wifi_direct_last_connect_device_address": command_payload.get("wifi_direct_last_connect_device_address"),
            "command_wifi_direct_last_connect_device_name": command_payload.get("wifi_direct_last_connect_device_name"),
            "command_wifi_direct_last_connect_group_owner_intent": command_payload.get("wifi_direct_last_connect_group_owner_intent"),
            "command_wifi_direct_last_connect_result": command_payload.get("wifi_direct_last_connect_result"),
            "command_wifi_direct_last_connect_failure_reason": command_payload.get("wifi_direct_last_connect_failure_reason"),
            "command_wifi_direct_connect_attempts": command_payload.get("wifi_direct_connect_attempts", []),
            "command_wifi_direct_visible_devices": command_payload.get("wifi_direct_visible_devices", []),
            "command_wifi_direct_bound_endpoints": command_payload.get("wifi_direct_bound_endpoints", []),
            "debug_wifi_direct_peer_requested": command_payload.get("debug_wifi_direct_peer_requested"),
            "debug_wifi_direct_peer_device_address": command_payload.get("debug_wifi_direct_peer_device_address"),
            "debug_wifi_direct_peer_device_name": command_payload.get("debug_wifi_direct_peer_device_name"),
            "debug_wifi_direct_peer_port": command_payload.get("debug_wifi_direct_peer_port"),
            "debug_wifi_direct_peer_status": command_payload.get("debug_wifi_direct_peer_status"),
            "target_delivery": target_delivery,
            "target_recent_message_ids": command_payload.get("target_recent_message_ids", []),
            "target_received_packet_ids": command_payload.get("target_received_packet_ids", []),
            "target_received_packet_message_ids": command_payload.get("target_received_packet_message_ids", []),
            "recent_rejected_inbound_packets": command_payload.get("recent_rejected_inbound_packets", []),
            "recent_route_attempt_count": len(transport.get("recent_route_attempts", [])),
            "debug_send_wait_ms": command_payload.get("debug_send_wait_ms"),
            "debug_send_attempts": command_payload.get("debug_send_attempts"),
            "debug_send_retry_delay_ms": command_payload.get("debug_send_retry_delay_ms"),
            "debug_send_results": command_payload.get("debug_send_results", []),
            "debug_send_wait_satisfied": command_payload.get("debug_send_wait_satisfied"),
            "identity_fingerprint": command_payload.get("identity_fingerprint"),
            "first_sendable_relationship_id": command_payload.get("first_sendable_relationship_id"),
            "first_sendable_relationship_fingerprint": command_payload.get("first_sendable_relationship_fingerprint"),
            "first_sendable_relationship_crypto_profile_id": command_payload.get("first_sendable_relationship_crypto_profile_id"),
            "first_sendable_relationship_session_profile_id": command_payload.get("first_sendable_relationship_session_profile_id"),
            "first_sendable_relationship_admission_decision_hash": command_payload.get("first_sendable_relationship_admission_decision_hash"),
            "first_sendable_relationship_profile_policy_version": command_payload.get("first_sendable_relationship_profile_policy_version"),
            "observed_peer_fingerprints": command_payload.get("observed_peer_fingerprints", []),
            "evidence_mode": debug_smoke.get("evidence_mode"),
            "unknown_peer_injected": debug_smoke.get("unknown_peer_injected"),
            "wrong_recipient_injected": debug_smoke.get("wrong_recipient_injected"),
            "duplicate_injected": debug_smoke.get("duplicate_injected"),
            "unknown_peer_rejected": metrics.get("unknown_peer_rejected"),
            "wrong_recipient_rejected": metrics.get("wrong_recipient_rejected"),
            "duplicates_dropped": metrics.get("duplicates_dropped"),
            "command_result": command_payload,
            "ensure_wifi_direct_group_owner_result": command_payload.get("ensure_wifi_direct_group_owner_result"),
            "wifi_p2p_state_summary": wifi_p2p_state_summary,
            "files": {
                "route_json": f"{serial}/route_specific_evidence_latest.json",
                "route_markdown": f"{serial}/route_specific_evidence_summary_latest.md",
                "command_result": f"{serial}/debug_evidence_command_result.json",
                "wifi_p2p_state_summary": f"{serial}/wifi_p2p_state_summary.json",
                "screen": f"{serial}/screen.png",
                "device_identity": f"{serial}/device_identity.txt",
                "broadcast": f"{serial}/broadcast.txt",
                "launch_app_before_broadcast": f"{serial}/launch_app_before_broadcast.txt",
                "network_state_before_broadcast": f"{serial}/network_state_before_broadcast",
                "network_state_after_broadcast": f"{serial}/network_state_after_broadcast",
            },
        }
    )

manifest = {
    "report_version": "kraken.debug_route_evidence_capture.v1",
    "generated_at": generated_at,
    "package": package_name,
    "git": {
        "branch": git_branch,
        "sha": git_sha,
        "source_state": git_source_state,
        "status_file": "git_status.txt",
    },
    "adb_devices_file": "adb_devices.txt",
    "start_mesh_before_export": start_mesh_before_export,
    "run_local_hostile_probe": run_local_hostile_probe,
    "hold_after_export_ms": hold_after_export_ms,
    "post_hold_refresh_wait_sec": post_hold_refresh_wait_sec,
    "start_foreground_wifi_direct": start_foreground_wifi_direct,
    "ensure_wifi_direct_group_owner": ensure_wifi_direct_group_owner,
    "force_stop_mesh_before_start": force_stop_mesh_before_start,
    "launch_app_before_broadcast": launch_app_before_broadcast,
    "transport_profile": transport_profile,
    "manual_peer": {
        "requested": bool(manual_peer_fingerprint or manual_peer_host or manual_peer_port),
        "fingerprint": manual_peer_fingerprint or None,
        "host": manual_peer_host or None,
        "port": int(manual_peer_port) if manual_peer_port else None,
    },
    "debug_wifi_direct_peer": {
        "requested": bool(debug_wifi_direct_peer_device_address or debug_wifi_direct_peer_port),
        "device_address": debug_wifi_direct_peer_device_address or None,
        "device_name": debug_wifi_direct_peer_device_name or None,
        "port": int(debug_wifi_direct_peer_port) if debug_wifi_direct_peer_port else None,
    },
    "debug_send": {
        "requested": bool(debug_send_body),
        "body": debug_send_body or None,
        "wait_ms": debug_send_wait_ms,
        "attempts": debug_send_attempts,
        "retry_delay_ms": debug_send_retry_delay_ms,
        "sync_after_debug_send": sync_after_debug_send,
        "sync_before_export": sync_before_export,
        "sync_attempts": sync_attempts,
    },
    "devices": device_entries,
    "claim_boundary": (
        "Debug-only ADB capture of in-app route evidence. Local hostile probe "
        "does not prove physical LAN/BLE/Wi-Fi Direct hostile packet injection, "
        "production reliability or production security."
    ),
}
(output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [
    "# Kraken Debug Route Evidence Capture",
    "",
    f"Generated: `{generated_at}`",
    f"Git: `{git_branch}` / `{git_sha}` / `{git_source_state}`",
    f"Start mesh before export: `{str(start_mesh_before_export).lower()}`",
    f"Run local hostile probe: `{str(run_local_hostile_probe).lower()}`",
    f"Hold after export ms: `{hold_after_export_ms}`",
    f"Post-hold refresh wait sec: `{post_hold_refresh_wait_sec}`",
    f"Start foreground Wi-Fi Direct: `{str(start_foreground_wifi_direct).lower()}`",
    f"Ensure Wi-Fi Direct group owner: `{str(ensure_wifi_direct_group_owner).lower()}`",
    f"Force stop mesh before start: `{str(force_stop_mesh_before_start).lower()}`",
    f"Launch app before broadcast: `{str(launch_app_before_broadcast).lower()}`",
    f"Debug send wait ms: `{debug_send_wait_ms}`",
    f"Debug send attempts: `{debug_send_attempts}`",
    f"Debug send retry delay ms: `{debug_send_retry_delay_ms}`",
    f"Sync before export: `{str(sync_before_export).lower()}`",
    f"Transport profile: `{transport_profile}`",
    "",
    "## Devices",
    "",
]
for device in device_entries:
    lines.append(
        f"- `{device['serial']}`: selectedRoute=`{device.get('selected_route')}`, "
        f"enabledTransportModes=`{','.join(device.get('enabled_transport_modes') or [])}`, "
        f"registrationState=`{device.get('registration_state')}`, "
        f"discoveryState=`{device.get('discovery_state')}`, "
        f"transportDiscoveredPeers=`{device.get('transport_discovered_peer_count')}`, "
        f"p2pVisibleDevices=`{device.get('p2p_visible_device_count')}`, "
        f"p2pThisDeviceStatus=`{device.get('p2p_this_device_status')}`, "
        f"discoveryCycles=`{device.get('discovery_cycle_count')}`, "
        f"p2pServiceFound=`{device.get('p2p_service_found_count')}`, "
        f"p2pTxtRecords=`{device.get('p2p_txt_record_count')}`, "
        f"p2pTxtRejected=`{device.get('p2p_txt_rejected_count')}`, "
        f"p2pTxtBoundPeers=`{device.get('p2p_txt_bound_peer_count')}`, "
        f"p2pUnboundVisibleDevices=`{device.get('p2p_unbound_visible_device_count')}`, "
        f"wifiDirectLastBindingError=`{device.get('wifi_direct_last_binding_error')}`, "
        f"debugSendWaitMs=`{device.get('debug_send_wait_ms')}`, "
        f"debugSendAttempts=`{device.get('debug_send_attempts')}`, "
        f"debugSendRetryDelayMs=`{device.get('debug_send_retry_delay_ms')}`, "
        f"debugSendWaitSatisfied=`{device.get('debug_send_wait_satisfied')}`, "
        f"identityFingerprint=`{device.get('identity_fingerprint')}`, "
        f"firstSendableRelationshipFingerprint=`{device.get('first_sendable_relationship_fingerprint')}`, "
        f"firstSendableRelationshipCryptoProfile=`{device.get('first_sendable_relationship_crypto_profile_id')}`, "
        f"firstSendableRelationshipSessionProfile=`{device.get('first_sendable_relationship_session_profile_id')}`, "
        f"endpoint=`{','.join(device.get('local_addresses') or [])}:{device.get('local_port')}`, "
        f"p2pInterfaceAddresses=`{','.join(device.get('p2p_interface_addresses') or [])}`, "
        f"wifiDirectGroupFormed=`{device.get('wifi_direct_group_formed')}`, "
        f"wifiDirectGroupRole=`{device.get('wifi_direct_group_role')}`, "
        f"wifiDirectGroupOwnerAddress=`{device.get('wifi_direct_group_owner_address')}`, "
        f"wifiDirectLocalP2pAddress=`{device.get('wifi_direct_local_p2p_address')}`, "
        f"wifiDirectServerBindAddress=`{device.get('wifi_direct_server_bind_address')}`, "
        f"wifiDirectEndpointBindingState=`{device.get('wifi_direct_endpoint_binding_state')}`, "
        f"wifiDirectEndpointBindingReason=`{device.get('wifi_direct_endpoint_binding_reason')}`, "
        f"wifiDirectLastConnect=`{device.get('wifi_direct_last_connect_device_name')}/{device.get('wifi_direct_last_connect_result')}`, "
        f"wifiDirectConnectAttempts=`{len(device.get('wifi_direct_connect_attempts') or [])}`, "
        f"wifiDirectBoundEndpoints=`{len(device.get('wifi_direct_bound_endpoints') or [])}`, "
        f"wifiDirectVisibleDevices=`{len(device.get('wifi_direct_visible_devices') or [])}`, "
        f"wifiDirectTxtRecords=`{len(device.get('wifi_direct_txt_records') or [])}`, "
        f"debugWifiDirectPeerRequested=`{device.get('debug_wifi_direct_peer_requested')}`, "
        f"debugWifiDirectPeerStatus=`{device.get('debug_wifi_direct_peer_status')}`, "
        f"debugWifiDirectPeerAddress=`{device.get('debug_wifi_direct_peer_device_address')}`, "
        f"debugWifiDirectPeerPort=`{device.get('debug_wifi_direct_peer_port')}`, "
        f"wifiP2pStateBefore=`{((device.get('wifi_p2p_state_summary') or {}).get('before_broadcast') or {}).get('wifi_p2p_info')}`, "
        f"wifiP2pStateAfter=`{((device.get('wifi_p2p_state_summary') or {}).get('after_broadcast') or {}).get('wifi_p2p_info')}`, "
        f"wifiDirectPermissionWarning=`{device.get('wifi_direct_permission_warning')}`, "
        f"wifiDirectFineLocationGranted=`{(device.get('wifi_direct_permissions') or {}).get('fine_location_granted')}`, "
        f"malformedFramesDropped=`{device.get('malformed_frames_dropped')}`, "
        f"wifiDirectActive=`{(device.get('wifi_direct') or {}).get('active')}`, "
        f"evidenceMode=`{device.get('evidence_mode')}`, "
        f"unknownPeerRejected=`{device.get('unknown_peer_rejected')}`, "
        f"wrongRecipientRejected=`{device.get('wrong_recipient_rejected')}`, "
        f"duplicatesDropped=`{device.get('duplicates_dropped')}`"
    )
lines.extend(
    [
        "",
        "## Claim Boundary",
        "",
        manifest["claim_boundary"],
        "",
    ]
)
(output_dir / "debug_route_evidence_summary.md").write_text("\n".join(lines), encoding="utf-8")
PY

echo "Saved debug route evidence to: ${output_dir}"
echo "Manifest: ${output_dir}/manifest.json"
