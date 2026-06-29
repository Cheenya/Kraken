#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="physical-inline-relay"
sender_device=""
target_device=""
listen_host="0.0.0.0"
listen_port="43199"
mac_host=""
target_host=""
target_port=""
target_fingerprint=""
target_host_arg=""
target_port_arg=""
target_fingerprint_arg=""
transport_profile="lan-only"
sync_attempts="3"
relay_timeout_seconds="90"
output_dir=""
modes=(normal drop duplicate tamper)

usage() {
  cat <<'EOF'
Usage: scripts/run_physical_inline_relay_trials.sh [options]

Run repeatable physical Android A -> Mac -> Android B inline LAN relay trials.

Required:
  --sender-device SERIAL       Android A serial used to send through the Mac relay.
  --target-device SERIAL       Android B serial used as the relay target.
  --mac-host HOST              Mac LAN address reachable from Android A.

Optional:
  --listen-host HOST           Relay listen host. Default: 0.0.0.0.
  --listen-port PORT           Relay listen port exposed to Android A. Default: 43199.
  --target-host HOST           Android B LAN address. If omitted, inferred from target capture.
  --target-port PORT           Android B LAN TCP port. If omitted, inferred from target capture.
  --target-fingerprint FP      Android B fingerprint. If omitted, inferred from target capture.
  --mode MODE                  Repeatable; one of normal, drop, duplicate, tamper.
                              Default: all four modes.
  --label LABEL                Output label. Default: physical-inline-relay.
  --out-dir PATH               Output dir. Default: artifacts/physical-inline-relay/<timestamp>-<label>.
  --sync-attempts N            Mesh sync attempts after debug send. Default: 3.
  --relay-timeout-seconds N    Per-mode relay timeout. Default: 90.
  -h, --help                   Show this help.

This is a research/debug harness. A successful run can support physical inline
relay evidence only together with the generated before/send/relay/after
artifacts. It does not prove production security or cryptographic MITM
resistance.
EOF
}

positive_int() {
  local value="$1"
  local name="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]] || (( value < 1 )); then
    echo "Invalid ${name}: ${value}" >&2
    exit 2
  fi
}

is_supported_mode() {
  case "$1" in
    normal|drop|duplicate|tamper) return 0 ;;
    *) return 1 ;;
  esac
}

mode_args_seen="false"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --sender-device)
      sender_device="${2:-}"
      shift 2
      ;;
    --target-device)
      target_device="${2:-}"
      shift 2
      ;;
    --mac-host)
      mac_host="${2:-}"
      shift 2
      ;;
    --listen-host)
      listen_host="${2:-}"
      shift 2
      ;;
    --listen-port)
      listen_port="${2:-}"
      shift 2
      ;;
    --target-host)
      target_host_arg="${2:-}"
      shift 2
      ;;
    --target-port)
      target_port_arg="${2:-}"
      shift 2
      ;;
    --target-fingerprint)
      target_fingerprint_arg="${2:-}"
      shift 2
      ;;
    --mode)
      if ! is_supported_mode "${2:-}"; then
        echo "Unsupported --mode: ${2:-}" >&2
        usage >&2
        exit 2
      fi
      if [[ "${mode_args_seen}" == "false" ]]; then
        modes=()
        mode_args_seen="true"
      fi
      modes+=("${2:-}")
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
    --sync-attempts)
      sync_attempts="${2:-}"
      shift 2
      ;;
    --relay-timeout-seconds)
      relay_timeout_seconds="${2:-}"
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

if [[ -z "${sender_device}" || -z "${target_device}" || -z "${mac_host}" ]]; then
  echo "--sender-device, --target-device and --mac-host are required." >&2
  usage >&2
  exit 2
fi

if [[ "${sender_device}" == "${target_device}" ]]; then
  echo "Sender and target devices must be different." >&2
  exit 2
fi

positive_int "${listen_port}" "--listen-port"
positive_int "${sync_attempts}" "--sync-attempts"
positive_int "${relay_timeout_seconds}" "--relay-timeout-seconds"
if [[ -n "${target_port_arg}" ]]; then
  positive_int "${target_port_arg}" "--target-port"
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

connected_devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
if ! grep -qx "${sender_device}" <<<"${connected_devices}"; then
  echo "Sender device is not connected: ${sender_device}" >&2
  adb devices >&2
  exit 1
fi
if ! grep -qx "${target_device}" <<<"${connected_devices}"; then
  echo "Target device is not connected: ${target_device}" >&2
  adb devices >&2
  exit 1
fi

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="physical-inline-relay"
fi

if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/physical-inline-relay/${timestamp}-${safe_label}"
fi
mkdir -p "${output_dir}"

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"
adb devices -l > "${output_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${output_dir}/git_status.txt" 2>&1 || true

infer_target_endpoint() {
  local manifest_path="$1"
  python3 - "${manifest_path}" "${target_host_arg}" "${target_port_arg}" "${target_fingerprint_arg}" <<'PY'
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
host_arg, port_arg, fingerprint_arg = sys.argv[2], sys.argv[3], sys.argv[4]
device = manifest["devices"][0]
addresses = [
    address
    for address in device.get("local_addresses", [])
    if address and not address.startswith("127.") and address != "0.0.0.0"
]
lan_addresses = [address for address in addresses if not address.startswith("192.168.49.")]
host = host_arg or (lan_addresses[0] if lan_addresses else (addresses[0] if addresses else ""))
port = port_arg or str(device.get("local_port") or "")
fingerprint = fingerprint_arg or str(
    (device.get("command_result", {}).get("identity", {}) or {}).get("fingerprint")
    or ""
)
if not fingerprint:
    match = re.search(r"Kraken-([0-9A-Fa-f]+)", str(device.get("registration_state", "")))
    if match:
        clean = match.group(1).upper()
        fingerprint = " ".join(clean[index : index + 4] for index in range(0, len(clean), 4))
print(host)
print(port)
print(fingerprint)
PY
}

mode_dirs=()
for mode in "${modes[@]}"; do
  mode_dir="${output_dir}/mode-${mode}"
  mkdir -p "${mode_dir}"

  before_dir="${mode_dir}/target-before"
  "${repo_root}/scripts/capture_debug_route_evidence.sh" \
    --device "${target_device}" \
    --label "${safe_label}-${mode}-target-before" \
    --out-dir "${before_dir}" \
    --transport-profile "${transport_profile}" \
    --start-mesh-before-export

  inferred="$(infer_target_endpoint "${before_dir}/manifest.json")"
  target_host="$(printf '%s\n' "${inferred}" | sed -n '1p')"
  target_port="$(printf '%s\n' "${inferred}" | sed -n '2p')"
  target_fingerprint="$(printf '%s\n' "${inferred}" | sed -n '3p')"

  if [[ -z "${target_host}" || -z "${target_port}" || -z "${target_fingerprint}" ]]; then
    echo "Could not infer target host/port/fingerprint from ${before_dir}/manifest.json." >&2
    echo "Pass --target-host, --target-port and --target-fingerprint explicitly." >&2
    exit 1
  fi

  relay_log="${mode_dir}/relay.log"
  python3 "${repo_root}/scripts/run_android_inline_lan_relay.py" \
    --listen-host "${listen_host}" \
    --listen-port "${listen_port}" \
    --target-host "${target_host}" \
    --target-port "${target_port}" \
    --target-fingerprint "${target_fingerprint}" \
    --mode "${mode}" \
    --max-frames 1 \
    --timeout-seconds "${relay_timeout_seconds}" \
    --report-dir "${mode_dir#${repo_root}/}" \
    > "${relay_log}" 2>&1 &
  relay_pid="$!"
  sleep 2

  send_dir="${mode_dir}/sender-send"
  body="physical inline relay ${mode} ${timestamp}"
  "${repo_root}/scripts/capture_debug_route_evidence.sh" \
    --device "${sender_device}" \
    --label "${safe_label}-${mode}-send" \
    --out-dir "${send_dir}" \
    --transport-profile "${transport_profile}" \
    --start-mesh-before-export \
    --manual-peer-fingerprint "${target_fingerprint}" \
    --manual-peer-host "${mac_host}" \
    --manual-peer-port "${listen_port}" \
    --debug-send-body "${body}" \
    --sync-after-debug-send \
    --sync-attempts "${sync_attempts}"

  wait "${relay_pid}" || true

  after_dir="${mode_dir}/target-after"
  "${repo_root}/scripts/capture_debug_route_evidence.sh" \
    --device "${target_device}" \
    --label "${safe_label}-${mode}-target-after" \
    --out-dir "${after_dir}" \
    --transport-profile "${transport_profile}"
  mode_dirs+=("${mode_dir}")
done

python3 - "${output_dir}" "${generated_at}" "${git_branch}" "${git_sha}" "${git_source_state}" "${sender_device}" "${target_device}" "${mac_host}" "${listen_host}" "${listen_port}" "${mode_dirs[@]}" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

output_dir = Path(sys.argv[1])
generated_at = sys.argv[2]
git_branch = sys.argv[3]
git_sha = sys.argv[4]
git_source_state = sys.argv[5]
sender_device = sys.argv[6]
target_device = sys.argv[7]
mac_host = sys.argv[8]
listen_host = sys.argv[9]
listen_port = int(sys.argv[10])
mode_dirs = [Path(item) for item in sys.argv[11:]]


def load_json(path: Path) -> dict:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def first_device(manifest_path: Path) -> dict:
    payload = load_json(manifest_path)
    devices = payload.get("devices") or []
    return devices[0] if devices else {}


def find_relay_report(mode_dir: Path) -> Path | None:
    reports = sorted(mode_dir.glob("*/android_inline_lan_relay.json"))
    return reports[-1] if reports else None


modes = []
for mode_dir in mode_dirs:
    mode = mode_dir.name.removeprefix("mode-")
    before_manifest = mode_dir / "target-before" / "manifest.json"
    send_manifest = mode_dir / "sender-send" / "manifest.json"
    after_manifest = mode_dir / "target-after" / "manifest.json"
    relay_report_path = find_relay_report(mode_dir)
    relay_report = load_json(relay_report_path) if relay_report_path else {}
    sender = first_device(send_manifest)
    target_before = first_device(before_manifest)
    target_after = first_device(after_manifest)
    frames = relay_report.get("frames", [])
    first_frame = frames[0] if frames else {}
    forward_attempts = first_frame.get("forward_attempts", [])
    modes.append(
        {
            "mode": mode,
            "target_before_manifest": str(before_manifest.relative_to(output_dir)),
            "sender_manifest": str(send_manifest.relative_to(output_dir)),
            "target_after_manifest": str(after_manifest.relative_to(output_dir)),
            "relay_report": str(relay_report_path.relative_to(output_dir)) if relay_report_path else None,
            "frames_received": relay_report.get("frames_received", 0),
            "relay_stats": relay_report.get("stats", {}),
            "relay_decision": first_frame.get("decision"),
            "forward_attempts": forward_attempts,
            "relay_target_host": (forward_attempts[0] or {}).get("host") if forward_attempts else None,
            "relay_target_port": (forward_attempts[0] or {}).get("port") if forward_attempts else None,
            "sender_selected_route": sender.get("selected_route"),
            "sender_command_result": sender.get("command_result", {}),
            "target_before_local_port": target_before.get("local_port"),
            "target_after_local_port": target_after.get("local_port"),
            "target_before_accepted_connections": target_before.get("accepted_connections"),
            "target_after_accepted_connections": target_after.get("accepted_connections"),
            "target_before_inbound_packets": target_before.get("inbound_packets"),
            "target_after_inbound_packets": target_after.get("inbound_packets"),
            "target_before_malformed_frames_dropped": target_before.get("malformed_frames_dropped"),
            "target_after_malformed_frames_dropped": target_after.get("malformed_frames_dropped"),
        }
    )

manifest = {
    "report_version": "kraken.physical_inline_relay_trials.v1",
    "generated_at": generated_at,
    "git": {
        "branch": git_branch,
        "sha": git_sha,
        "source_state": git_source_state,
        "status_file": "git_status.txt",
    },
    "devices": {
        "sender": sender_device,
        "target": target_device,
    },
    "relay": {
        "mac_host_for_sender": mac_host,
        "listen_host": listen_host,
        "listen_port": listen_port,
    },
    "modes": modes,
    "claim_boundary": (
        "Research/debug physical inline relay harness. A 10/10 attack claim requires "
        "successful before/send/relay/after evidence for normal, drop, duplicate and tamper "
        "on two connected phones. This does not prove production security or cryptographic "
        "MITM resistance."
    ),
}
(output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [
    "# Kraken Physical Inline Relay Trials",
    "",
    f"Generated: `{generated_at}`",
    f"Git: `{git_branch}` / `{git_sha}` / `{git_source_state}`",
    f"Sender: `{sender_device}`",
    f"Target: `{target_device}`",
    f"Relay listen: `{mac_host}:{listen_port}`",
    "",
    "## Modes",
    "",
]
for item in modes:
    lines.append(
        f"- `{item['mode']}`: framesReceived=`{item['frames_received']}`, "
        f"stats=`{item['relay_stats']}`, targetPort=`{item['target_before_local_port']}`, "
        f"targetPortAfter=`{item['target_after_local_port']}`, "
        f"targetInboundAfter=`{item['target_after_inbound_packets']}`"
    )
lines.extend(["", "## Claim Boundary", "", manifest["claim_boundary"], ""])
(output_dir / "physical_inline_relay_trials.md").write_text("\n".join(lines), encoding="utf-8")
PY

echo "Saved physical inline relay trials to: ${output_dir}"
echo "Manifest: ${output_dir}/manifest.json"
