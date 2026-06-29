#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="directed-wifi-direct-route-trial"
sender_device=""
target_device=""
out_dir=""
body=""
settle_ms="12000"
debug_send_wait_ms="15000"
debug_send_attempts="3"
debug_send_retry_delay_ms="2000"
sync_attempts="3"
prearm_target_group_owner="false"
hint_target_wifi_direct_peer="false"

usage() {
  cat <<'EOF'
Usage: scripts/run_directed_wifi_direct_route_trial.sh [options]

Run one directed Wi-Fi Direct sender -> target route trial with target-before,
sender-send and target-after captures.

Required:
  --sender-device SERIAL       Android serial that sends the debug message.
  --target-device SERIAL       Android serial observed before and after send.

Optional:
  --label LABEL                Output label. Default: directed-wifi-direct-route-trial.
  --out-dir PATH               Output dir. Default: artifacts/directed-wifi-direct/<timestamp>-<label>.
  --body BODY                  Debug message body. Default: timestamped label.
  --settle-ms N                Mesh settle time for Wi-Fi Direct captures. Default: 12000.
  --debug-send-wait-ms N       Wait for sendable relationship on sender. Default: 15000.
  --debug-send-attempts N      Sender debug-send attempts. Default: 3.
  --debug-send-retry-delay-ms N
                              Delay between sender attempts. Default: 2000.
  --sync-attempts N            Mesh sync attempts after sender debug-send. Default: 3.
  --prearm-target-group-owner  Debug-only: ask target to create a Wi-Fi Direct group owner
                              group during target-before capture.
  --hint-target-wifi-direct-peer
                              Debug-only: after target-before capture, add the target
                              Wi-Fi Direct device address/port as a sender peer hint.
  -h, --help                   Show this help.

This harness is debug evidence only. It reports transport counter deltas and,
when target-side evidence exports matching message_id and packet_id, separates
message-id-bound delivery from counter-only delivery.
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
    --label)
      label="${2:-}"
      shift 2
      ;;
    --out-dir)
      out_dir="${2:-}"
      shift 2
      ;;
    --body)
      body="${2:-}"
      shift 2
      ;;
    --settle-ms)
      settle_ms="${2:-}"
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
    --sync-attempts)
      sync_attempts="${2:-}"
      shift 2
      ;;
    --prearm-target-group-owner)
      prearm_target_group_owner="true"
      shift
      ;;
    --hint-target-wifi-direct-peer)
      hint_target_wifi_direct_peer="true"
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

if [[ -z "${sender_device}" || -z "${target_device}" ]]; then
  echo "--sender-device and --target-device are required." >&2
  usage >&2
  exit 2
fi

if [[ "${sender_device}" == "${target_device}" ]]; then
  echo "Sender and target devices must be different." >&2
  exit 2
fi

positive_int "${settle_ms}" "--settle-ms"
positive_int "${debug_send_wait_ms}" "--debug-send-wait-ms"
positive_int "${debug_send_attempts}" "--debug-send-attempts"
positive_int "${debug_send_retry_delay_ms}" "--debug-send-retry-delay-ms"
positive_int "${sync_attempts}" "--sync-attempts"

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
  safe_label="directed-wifi-direct-route-trial"
fi

if [[ -z "${out_dir}" ]]; then
  out_dir="${repo_root}/artifacts/directed-wifi-direct/${timestamp}-${safe_label}"
fi
mkdir -p "${out_dir}"

if [[ -z "${body}" ]]; then
  body="directed Wi-Fi Direct route trial ${safe_label} ${timestamp}"
fi

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"

adb devices -l > "${out_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${out_dir}/git_status.txt" 2>&1 || true

target_before_dir="${out_dir}/target-before"
sender_dir="${out_dir}/sender-send"
target_after_dir="${out_dir}/target-after"
target_hint_device_address=""
target_hint_device_name=""
target_hint_port=""
target_hint_status="not-requested"

target_before_args=()
if [[ "${prearm_target_group_owner}" == "true" ]]; then
  target_before_args+=(--ensure-wifi-direct-group-owner)
fi

target_before_command=(
  "${repo_root}/scripts/capture_debug_route_evidence.sh"
  --device "${target_device}" \
  --label "${safe_label}-target-before" \
  --out-dir "${target_before_dir}" \
  --force-stop-mesh-before-start \
  --start-foreground-wifi-direct \
  --start-mesh-before-export \
  --transport-profile wifi-direct-only \
  --start-mesh-settle-ms "${settle_ms}"
)
if [[ "${#target_before_args[@]}" -gt 0 ]]; then
  target_before_command+=("${target_before_args[@]}")
fi
"${target_before_command[@]}"

if [[ "${hint_target_wifi_direct_peer}" == "true" ]]; then
  target_hint_json="$(
    python3 "${repo_root}/scripts/extract_wifi_direct_peer_hint.py" \
      --capture-dir "${target_before_dir}" \
      --target-device "${target_device}"
  )"
  target_hint_device_address="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("device_address") or "")' "${target_hint_json}")"
  target_hint_device_name="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("device_name") or "")' "${target_hint_json}")"
  target_hint_port="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("port") or "")' "${target_hint_json}")"
  target_hint_status="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("status") or "missing-target-go-address-or-port")' "${target_hint_json}")"
fi

sender_debug_peer_args=()
if [[ "${hint_target_wifi_direct_peer}" == "true" && "${target_hint_status}" == "ready" ]]; then
  sender_debug_peer_args+=(
    --debug-wifi-direct-peer-device-address "${target_hint_device_address}"
    --debug-wifi-direct-peer-port "${target_hint_port}"
  )
  if [[ -n "${target_hint_device_name}" ]]; then
    sender_debug_peer_args+=(--debug-wifi-direct-peer-device-name "${target_hint_device_name}")
  fi
fi

sender_command=(
  "${repo_root}/scripts/capture_debug_route_evidence.sh"
  --device "${sender_device}" \
  --label "${safe_label}-sender-send" \
  --out-dir "${sender_dir}" \
  --force-stop-mesh-before-start \
  --start-foreground-wifi-direct \
  --start-mesh-before-export \
  --transport-profile wifi-direct-only \
  --start-mesh-settle-ms "${settle_ms}" \
  --debug-send-body "${body}" \
  --debug-send-wait-ms "${debug_send_wait_ms}" \
  --debug-send-attempts "${debug_send_attempts}" \
  --debug-send-retry-delay-ms "${debug_send_retry_delay_ms}" \
  --sync-after-debug-send \
  --sync-attempts "${sync_attempts}"
)
if [[ "${#sender_debug_peer_args[@]}" -gt 0 ]]; then
  sender_command+=("${sender_debug_peer_args[@]}")
fi
"${sender_command[@]}"

"${repo_root}/scripts/capture_debug_route_evidence.sh" \
  --device "${target_device}" \
  --label "${safe_label}-target-after" \
  --out-dir "${target_after_dir}" \
  --reuse-running-mesh \
  --transport-profile wifi-direct-only \
  --sync-after-debug-send \
  --sync-before-export \
  --sync-attempts "${sync_attempts}"

python3 "${repo_root}/scripts/build_directed_wifi_direct_trial_manifest.py" \
  --out-dir "${out_dir}" \
  --generated-at "${generated_at}" \
  --git-branch "${git_branch}" \
  --git-sha "${git_sha}" \
  --git-source-state "${git_source_state}" \
  --sender-device "${sender_device}" \
  --target-device "${target_device}" \
  --target-before-dir "${target_before_dir}" \
  --sender-dir "${sender_dir}" \
  --target-after-dir "${target_after_dir}" \
  --body "${body}" \
  --prearm-target-group-owner "${prearm_target_group_owner}" \
  --hint-target-wifi-direct-peer "${hint_target_wifi_direct_peer}" \
  --target-hint-device-address "${target_hint_device_address}" \
  --target-hint-device-name "${target_hint_device_name}" \
  --target-hint-port "${target_hint_port}" \
  --target-hint-status "${target_hint_status}"

echo "Saved directed Wi-Fi Direct route trial to: ${out_dir}"
echo "Manifest: ${out_dir}/manifest.json"
