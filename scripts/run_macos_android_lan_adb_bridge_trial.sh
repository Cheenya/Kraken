#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
macos_root="${repo_root}/app-macos"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="macos-android-lan-adb-bridge"
serial=""
output_dir=""
package_name="${KRAKEN_PACKAGE_NAME:-com.disser.kraken}"
android_forward_port="54035"
android_reverse_port="43191"
mac_listen_port="43191"
mac_fingerprint=""
mac_peer_id="macos-desktop-adb-bridge"
mac_display_name="Kraken Desktop"
relationship_id=""
body="Kraken Desktop LAN/ADB bridge trial"
start_mesh_settle_ms="3000"
debug_send_wait_ms="8000"
debug_send_attempts="2"
debug_send_retry_delay_ms="1000"
listener_timeout_seconds="30"
keep_adb_tunnels="false"

usage() {
  cat <<'EOF'
Usage: scripts/run_macos_android_lan_adb_bridge_trial.sh [options]

Runs a reproducible macOS <-> Android LAN/TCP transport trial over ADB tunnels.

Options:
  --device SERIAL              ADB serial. If omitted, first connected device is used.
  --label LABEL                Artifact label. Default: macos-android-lan-adb-bridge.
  --out-dir PATH               Output dir. Default: artifacts/macos-android-lan-adb-bridge/<timestamp>-<label>.
  --package NAME               Android package. Default: com.disser.kraken.
  --android-forward-port PORT  Host port forwarded to Android LAN listener. Default: 54035.
  --android-reverse-port PORT  Android localhost port reversed to macOS listener. Default: 43191.
  --mac-listen-port PORT       macOS listener port. Default: 43191.
  --mac-fingerprint VALUE      Sender/manual peer fingerprint to use for the macOS endpoint.
                               If omitted, the Android first sendable relationship fingerprint is used.
  --mac-peer-id VALUE          Sender peer id. Default: macos-desktop-adb-bridge.
  --relationship-id VALUE      Packet relationship id. If omitted, derived from fingerprints.
  --body TEXT                  Message body for both directions.
  --keep-adb-tunnels           Do not remove adb forward/reverse tunnels on exit.
  -h, --help                   Show help.

Claim boundary:
  This proves Android-compatible LAN/TCP frame exchange through ADB tunnels.
  It does not prove native macOS Wi-Fi Direct, BLE GATT, production reliability,
  or that ADB endpoints are stable peer identity.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      serial="${2:-}"
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
    --android-forward-port)
      android_forward_port="${2:-}"
      shift 2
      ;;
    --android-reverse-port)
      android_reverse_port="${2:-}"
      shift 2
      ;;
    --mac-listen-port)
      mac_listen_port="${2:-}"
      shift 2
      ;;
    --mac-fingerprint)
      mac_fingerprint="${2:-}"
      shift 2
      ;;
    --mac-peer-id)
      mac_peer_id="${2:-}"
      shift 2
      ;;
    --relationship-id)
      relationship_id="${2:-}"
      shift 2
      ;;
    --body)
      body="${2:-}"
      shift 2
      ;;
    --keep-adb-tunnels)
      keep_adb_tunnels="true"
      shift
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

validate_port() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]] || (( value < 1 || value > 65535 )); then
    echo "Invalid ${name}: ${value}" >&2
    exit 2
  fi
}

validate_port "--android-forward-port" "${android_forward_port}"
validate_port "--android-reverse-port" "${android_reverse_port}"
validate_port "--mac-listen-port" "${mac_listen_port}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

if [[ -z "${serial}" ]]; then
  serial="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
if [[ -z "${serial}" ]]; then
  echo "No connected adb device." >&2
  adb devices >&2
  exit 1
fi

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="macos-android-lan-adb-bridge"
fi
if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/macos-android-lan-adb-bridge/${timestamp}-${safe_label}"
fi
mkdir -p "${output_dir}"

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git -C "${repo_root}" status --short --branch > "${output_dir}/git_status.txt" 2>&1 || true
adb devices -l > "${output_dir}/adb_devices.txt"

cleanup() {
  if [[ "${keep_adb_tunnels}" == "false" ]]; then
    adb -s "${serial}" forward --remove "tcp:${android_forward_port}" >/dev/null 2>&1 || true
    adb -s "${serial}" reverse --remove "tcp:${android_reverse_port}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "Building macOS LAN probes..."
(
  cd "${macos_root}"
  swift build --product KrakenDesktopLanProbe
  swift build --product KrakenDesktopLanListenProbe
  swift build --show-bin-path > "${output_dir}/swift_bin_path.txt"
) > "${output_dir}/swift_build.log" 2>&1
swift_bin_dir="$(cat "${output_dir}/swift_bin_path.txt")"
lan_probe_bin="${swift_bin_dir}/KrakenDesktopLanProbe"
lan_listen_bin="${swift_bin_dir}/KrakenDesktopLanListenProbe"

android_start_dir="${output_dir}/android_lan_start"
echo "Starting Android LAN-only transport on ${serial}..."
"${repo_root}/scripts/capture_debug_route_evidence.sh" \
  --device "${serial}" \
  --package "${package_name}" \
  --label "${safe_label}-android-lan-start" \
  --out-dir "${android_start_dir}" \
  --transport-profile lan-only \
  --start-mesh-before-export \
  --force-stop-mesh-before-start \
  --start-mesh-settle-ms "${start_mesh_settle_ms}" \
  > "${output_dir}/android_lan_start.log" 2>&1

android_endpoint_json="${output_dir}/android_endpoint.json"
python3 - "${android_start_dir}/manifest.json" "${serial}" "${mac_fingerprint}" "${relationship_id}" > "${android_endpoint_json}" <<'PY'
from __future__ import annotations

import json
import sys

manifest_path, serial, requested_mac_fingerprint, requested_relationship_id = sys.argv[1:5]
manifest = json.load(open(manifest_path, encoding="utf-8"))
device = next((item for item in manifest.get("devices", []) if item.get("serial") == serial), None)
if device is None:
    raise SystemExit(f"device {serial} not found in manifest")

local_port = device.get("local_port")
identity_fingerprint = device.get("identity_fingerprint")
first_peer_fingerprint = device.get("first_sendable_relationship_fingerprint")
first_relationship_id = device.get("first_sendable_relationship_id")
first_crypto_profile_id = device.get("first_sendable_relationship_crypto_profile_id")
first_session_profile_id = device.get("first_sendable_relationship_session_profile_id")
first_admission_decision_hash = device.get("first_sendable_relationship_admission_decision_hash")
first_profile_policy_version = device.get("first_sendable_relationship_profile_policy_version")
if not local_port:
    raise SystemExit("Android LAN local_port is missing")
if not identity_fingerprint:
    raise SystemExit("Android identity_fingerprint is missing; rebuild/install debug APK with updated receiver")

mac_fingerprint = requested_mac_fingerprint or first_peer_fingerprint
if not mac_fingerprint:
    raise SystemExit(
        "mac fingerprint is missing and Android has no first sendable relationship; pass --mac-fingerprint"
    )

def compact(value: str) -> str:
    return "".join(ch for ch in value if ch.isalnum()).upper()

relationship_id = requested_relationship_id or first_relationship_id
if not relationship_id:
    relationship_id = (
        "relationship-adb-bridge-"
        + compact(identity_fingerprint)[:12]
        + "-"
        + compact(mac_fingerprint)[:12]
    )

crypto_profile_id = first_crypto_profile_id or "standard-reviewed-primitives-v1"
session_profile_id = first_session_profile_id or f"session-{relationship_id}-{crypto_profile_id}"
admission_decision_hash = first_admission_decision_hash
profile_policy_version = first_profile_policy_version
if not admission_decision_hash:
    raise SystemExit(
        "Android first sendable relationship admission hash is missing; rebuild/install debug APK with updated receiver"
    )
if profile_policy_version is None:
    raise SystemExit(
        "Android first sendable relationship policy version is missing; rebuild/install debug APK with updated receiver"
    )

print(json.dumps({
    "serial": serial,
    "android_local_port": int(local_port),
    "android_identity_fingerprint": identity_fingerprint,
    "android_first_sendable_relationship_id": first_relationship_id,
    "android_first_sendable_relationship_fingerprint": first_peer_fingerprint,
    "mac_fingerprint": mac_fingerprint,
    "relationship_id": relationship_id,
    "crypto_profile_id": crypto_profile_id,
    "session_profile_id": session_profile_id,
    "admission_decision_hash": admission_decision_hash,
    "profile_policy_version": int(profile_policy_version),
    "selected_route": device.get("selected_route"),
    "enabled_transport_modes": device.get("enabled_transport_modes", []),
    "local_addresses": device.get("local_addresses", []),
}, ensure_ascii=False, indent=2))
PY

android_local_port="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["android_local_port"])' "${android_endpoint_json}")"
android_fingerprint="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["android_identity_fingerprint"])' "${android_endpoint_json}")"
mac_fingerprint="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["mac_fingerprint"])' "${android_endpoint_json}")"
relationship_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["relationship_id"])' "${android_endpoint_json}")"
crypto_profile_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["crypto_profile_id"])' "${android_endpoint_json}")"
session_profile_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["session_profile_id"])' "${android_endpoint_json}")"
admission_decision_hash="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["admission_decision_hash"])' "${android_endpoint_json}")"
profile_policy_version="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["profile_policy_version"])' "${android_endpoint_json}")"

echo "Configuring adb forward tcp:${android_forward_port} -> Android tcp:${android_local_port}..."
adb -s "${serial}" forward --remove "tcp:${android_forward_port}" >/dev/null 2>&1 || true
adb -s "${serial}" forward "tcp:${android_forward_port}" "tcp:${android_local_port}"
adb -s "${serial}" forward --list > "${output_dir}/adb_forward_list_after_setup.txt"

echo "Sending macOS -> Android LAN frame..."
set +e
"${lan_probe_bin}" \
  --host 127.0.0.1 \
  --port "${android_forward_port}" \
  --target-fingerprint "${android_fingerprint}" \
  --sender-fingerprint "${mac_fingerprint}" \
  --sender-peer-id "${mac_peer_id}" \
  --relationship-id "${relationship_id}" \
  --crypto-profile-id "${crypto_profile_id}" \
  --session-profile-id "${session_profile_id}" \
  --admission-decision-hash "${admission_decision_hash}" \
  --profile-policy-version "${profile_policy_version}" \
  --body "${body} :: macOS to Android" \
  > "${output_dir}/mac_to_android_probe.json" \
  2> "${output_dir}/mac_to_android_probe.stderr"
mac_to_android_status=$?
set -e

post_mac_to_android_dir="${output_dir}/android_after_mac_to_android"
"${repo_root}/scripts/capture_debug_route_evidence.sh" \
  --device "${serial}" \
  --package "${package_name}" \
  --label "${safe_label}-after-mac-to-android" \
  --out-dir "${post_mac_to_android_dir}" \
  --transport-profile lan-only \
  --reuse-running-mesh \
  --sync-before-export \
  --sync-attempts 1 \
  > "${output_dir}/android_after_mac_to_android.log" 2>&1 || true

echo "Starting macOS listener and configuring adb reverse..."
listener_json="${output_dir}/android_to_macos_listener.json"
"${lan_listen_bin}" \
  --port "${mac_listen_port}" \
  --timeout-seconds "${listener_timeout_seconds}" \
  --out "${listener_json}" \
  > "${output_dir}/android_to_macos_listener.stdout" \
  2> "${output_dir}/android_to_macos_listener.stderr" &
listener_pid=$!
sleep 1

adb -s "${serial}" reverse --remove "tcp:${android_reverse_port}" >/dev/null 2>&1 || true
adb -s "${serial}" reverse "tcp:${android_reverse_port}" "tcp:${mac_listen_port}"
adb -s "${serial}" reverse --list > "${output_dir}/adb_reverse_list_after_setup.txt"

echo "Sending Android -> macOS LAN frame..."
android_to_macos_dir="${output_dir}/android_to_macos_debug_send"
set +e
"${repo_root}/scripts/capture_debug_route_evidence.sh" \
  --device "${serial}" \
  --package "${package_name}" \
  --label "${safe_label}-android-to-macos" \
  --out-dir "${android_to_macos_dir}" \
  --transport-profile lan-only \
  --reuse-running-mesh \
  --manual-peer-fingerprint "${mac_fingerprint}" \
  --manual-peer-host 127.0.0.1 \
  --manual-peer-port "${android_reverse_port}" \
  --debug-send-body "${body} :: Android to macOS" \
  --debug-send-wait-ms "${debug_send_wait_ms}" \
  --debug-send-attempts "${debug_send_attempts}" \
  --debug-send-retry-delay-ms "${debug_send_retry_delay_ms}" \
  --sync-after-debug-send \
  --sync-attempts 1 \
  > "${output_dir}/android_to_macos_debug_send.log" 2>&1
android_to_macos_status=$?
wait "${listener_pid}"
listener_status=$?
set -e

python3 - \
  "${output_dir}" \
  "${generated_at}" \
  "${serial}" \
  "${android_forward_port}" \
  "${android_reverse_port}" \
  "${mac_listen_port}" \
  "${mac_to_android_status}" \
  "${android_to_macos_status}" \
  "${listener_status}" \
  <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

out = Path(sys.argv[1])
generated_at = sys.argv[2]
serial = sys.argv[3]
android_forward_port = int(sys.argv[4])
android_reverse_port = int(sys.argv[5])
mac_listen_port = int(sys.argv[6])
mac_to_android_status = int(sys.argv[7])
android_to_macos_status = int(sys.argv[8])
listener_status = int(sys.argv[9])

def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        return {"_read_error": str(exc), "_path": str(path)}

endpoint = read_json(out / "android_endpoint.json")
mac_to_android = read_json(out / "mac_to_android_probe.json")
listener = read_json(out / "android_to_macos_listener.json")
android_to_macos_manifest = read_json(out / "android_to_macos_debug_send" / "manifest.json")
after_mac_manifest = read_json(out / "android_after_mac_to_android" / "manifest.json")

def first_device(manifest: dict) -> dict:
    devices = manifest.get("devices")
    if isinstance(devices, list) and devices:
        return devices[0]
    return {}

android_to_macos_device = first_device(android_to_macos_manifest)
after_mac_device = first_device(after_mac_manifest)
android_debug_send = android_to_macos_manifest.get("debug_send", {})
android_command = android_to_macos_device.get("command_result", {})

mac_to_android_acked = mac_to_android_status == 0 and mac_to_android.get("status") == "acked"
mac_to_android_packet_id = mac_to_android.get("packetId")
mac_to_android_message_id = mac_to_android.get("messageId")
android_target_packet_ids = after_mac_device.get("target_received_packet_ids", [])
android_target_packet_message_ids = after_mac_device.get("target_received_packet_message_ids", [])
android_target_recent_message_ids = after_mac_device.get("target_recent_message_ids", [])
recent_rejected_inbound_packets = after_mac_device.get("recent_rejected_inbound_packets", [])
android_target_packet_found = (
    bool(mac_to_android_packet_id)
    and isinstance(android_target_packet_ids, list)
    and mac_to_android_packet_id in android_target_packet_ids
)
android_target_message_found = (
    bool(mac_to_android_message_id)
    and (
        (
            isinstance(android_target_packet_message_ids, list)
            and mac_to_android_message_id in android_target_packet_message_ids
        )
        or (
            isinstance(android_target_recent_message_ids, list)
            and mac_to_android_message_id in android_target_recent_message_ids
        )
    )
)
mac_to_android_delivery_observed = android_target_packet_found or android_target_message_found
android_to_macos_sent = (
    android_to_macos_status == 0
    and android_command.get("debug_send_success") is True
)
mac_listener_received = listener_status == 0 and listener.get("success") is True
transport_exchange_success = mac_to_android_acked and android_to_macos_sent and mac_listener_received
end_to_end_delivery_success = (
    mac_to_android_acked
    and mac_to_android_delivery_observed
    and android_to_macos_sent
    and mac_listener_received
)

summary = {
    "report_version": "kraken.macos_android_lan_adb_bridge_trial.v2",
    "generated_at": generated_at,
    "serial": serial,
    "android_forward": {
        "host_port": android_forward_port,
        "android_local_port": endpoint.get("android_local_port"),
    },
    "android_reverse": {
        "android_port": android_reverse_port,
        "mac_listen_port": mac_listen_port,
    },
    "identity_model": {
        "android_identity_fingerprint": endpoint.get("android_identity_fingerprint"),
        "mac_endpoint_fingerprint": endpoint.get("mac_fingerprint"),
        "relationship_id": endpoint.get("relationship_id"),
        "note": "ADB host/port are current transport endpoints, not stable peer identity.",
    },
    "mac_to_android": {
        "acked": mac_to_android_acked,
        "android_delivery_observed": mac_to_android_delivery_observed,
        "android_target_packet_found": android_target_packet_found,
        "android_target_message_found": android_target_message_found,
        "process_status": mac_to_android_status,
        "packet_id": mac_to_android_packet_id,
        "message_id": mac_to_android_message_id,
        "event": mac_to_android,
        "android_after_inbound": {
            "inbound_packets": after_mac_device.get("inbound_packets"),
            "accepted_connections": after_mac_device.get("accepted_connections"),
            "last_sync_summary": after_mac_device.get("command_result", {}).get("last_sync_summary"),
            "unknown_peer_rejected": after_mac_device.get("unknown_peer_rejected"),
            "wrong_recipient_rejected": after_mac_device.get("wrong_recipient_rejected"),
            "target_recent_message_ids": android_target_recent_message_ids,
            "target_received_packet_ids": android_target_packet_ids,
            "target_received_packet_message_ids": android_target_packet_message_ids,
            "recent_rejected_inbound_packets": recent_rejected_inbound_packets,
        },
    },
    "android_to_macos": {
        "debug_send_success": android_to_macos_sent,
        "debug_send_manifest": android_debug_send,
        "command_result": android_command,
        "process_status": android_to_macos_status,
    },
    "mac_listener": {
        "received": mac_listener_received,
        "process_status": listener_status,
        "payload": listener,
    },
    "transport_exchange_success": transport_exchange_success,
    "end_to_end_delivery_success": end_to_end_delivery_success,
    "overall_success": end_to_end_delivery_success,
    "files": {
        "android_endpoint": "android_endpoint.json",
        "mac_to_android_probe": "mac_to_android_probe.json",
        "android_lan_start_manifest": "android_lan_start/manifest.json",
        "android_after_mac_to_android_manifest": "android_after_mac_to_android/manifest.json",
        "android_to_macos_manifest": "android_to_macos_debug_send/manifest.json",
        "android_to_macos_listener": "android_to_macos_listener.json",
        "adb_devices": "adb_devices.txt",
        "git_status": "git_status.txt",
    },
    "claim_boundary": (
        "This is an Android-compatible LAN/TCP transport trial through adb forward/reverse. "
        "overall_success requires observed target delivery on Android, not only a TCP ACK. "
        "It does not prove native macOS Wi-Fi Direct, BLE GATT, production reliability, "
        "or endpoint stability across networks."
    ),
}
(out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [
    "# macOS Android LAN/ADB Bridge Trial",
    "",
    f"Generated: `{generated_at}`",
    f"Device: `{serial}`",
    f"Transport exchange success: `{str(transport_exchange_success).lower()}`",
    f"End-to-end delivery success: `{str(end_to_end_delivery_success).lower()}`",
    "",
    "## Results",
    "",
    f"- macOS -> Android ACK: `{str(mac_to_android_acked).lower()}`",
    f"- macOS -> Android delivery observed on Android: `{str(mac_to_android_delivery_observed).lower()}`",
    f"- Android target packet found: `{str(android_target_packet_found).lower()}`",
    f"- Android target message found: `{str(android_target_message_found).lower()}`",
    f"- Android debug send success: `{str(android_to_macos_sent).lower()}`",
    f"- macOS listener received frame: `{str(mac_listener_received).lower()}`",
    "",
    "## Identity vs endpoint",
    "",
    f"- Android identity: `{endpoint.get('android_identity_fingerprint')}`",
    f"- macOS endpoint fingerprint used: `{endpoint.get('mac_fingerprint')}`",
    f"- Relationship id: `{endpoint.get('relationship_id')}`",
    "- ADB ports are transport endpoints for this run, not peer identity.",
    "",
    "## Files",
    "",
]
for key, value in summary["files"].items():
    lines.append(f"- `{key}`: `{value}`")
lines.extend(["", "## Claim Boundary", "", summary["claim_boundary"], ""])
(out / "README.md").write_text("\n".join(lines), encoding="utf-8")

print(json.dumps({
    "overall_success": summary["overall_success"],
    "transport_exchange_success": transport_exchange_success,
    "end_to_end_delivery_success": end_to_end_delivery_success,
    "mac_to_android_acked": mac_to_android_acked,
    "mac_to_android_delivery_observed": mac_to_android_delivery_observed,
    "android_to_macos_sent": android_to_macos_sent,
    "mac_listener_received": mac_listener_received,
    "summary": str(out / "summary.json"),
}, ensure_ascii=False))
PY

echo "Saved macOS Android LAN/ADB bridge trial to: ${output_dir}"
echo "Summary: ${output_dir}/summary.json"
