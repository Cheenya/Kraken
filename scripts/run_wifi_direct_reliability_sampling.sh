#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="wifi-direct-reliability-sampling"
device_a=""
device_b=""
device_a_label="device-a"
device_b_label="device-b"
iterations="3"
out_dir=""
settle_ms="12000"
debug_send_wait_ms="15000"
debug_send_attempts="3"
debug_send_retry_delay_ms="2000"
sync_attempts="3"
sleep_between="2"

usage() {
  cat <<'EOF'
Usage: scripts/run_wifi_direct_reliability_sampling.sh [options]

Run repeated directed Wi-Fi Direct trials in both directions and aggregate
transport-counter and message-id-bound delivery stability.

Required:
  --device-a SERIAL            First Android serial.
  --device-b SERIAL            Second Android serial.

Optional:
  --device-a-label LABEL       Human label for device A. Default: device-a.
  --device-b-label LABEL       Human label for device B. Default: device-b.
  --iterations N               Paired forward/reverse iterations. Default: 3.
  --label LABEL                Output label. Default: wifi-direct-reliability-sampling.
  --out-dir PATH               Output dir. Default: artifacts/wifi-direct-reliability/<timestamp>-<label>.
  --settle-ms N                Directed harness settle time. Default: 12000.
  --debug-send-wait-ms N       Directed harness sendable-peer wait. Default: 15000.
  --debug-send-attempts N      Directed harness sender attempts. Default: 3.
  --debug-send-retry-delay-ms N
                              Directed harness retry delay. Default: 2000.
  --sync-attempts N            Directed harness sync attempts. Default: 3.
  --sleep-between N            Seconds between directed trials. Default: 2.
  -h, --help                   Show this help.

This is research/debug reliability sampling only. It does not prove production
network reliability or production security.
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
    --device-a)
      device_a="${2:-}"
      shift 2
      ;;
    --device-b)
      device_b="${2:-}"
      shift 2
      ;;
    --device-a-label)
      device_a_label="${2:-}"
      shift 2
      ;;
    --device-b-label)
      device_b_label="${2:-}"
      shift 2
      ;;
    --iterations)
      iterations="${2:-}"
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
    --sleep-between)
      sleep_between="${2:-}"
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

if [[ -z "${device_a}" || -z "${device_b}" ]]; then
  echo "--device-a and --device-b are required." >&2
  usage >&2
  exit 2
fi
if [[ "${device_a}" == "${device_b}" ]]; then
  echo "Devices must be different." >&2
  exit 2
fi

positive_int "${iterations}" "--iterations"
positive_int "${settle_ms}" "--settle-ms"
positive_int "${debug_send_wait_ms}" "--debug-send-wait-ms"
positive_int "${debug_send_attempts}" "--debug-send-attempts"
positive_int "${debug_send_retry_delay_ms}" "--debug-send-retry-delay-ms"
positive_int "${sync_attempts}" "--sync-attempts"
positive_int "${sleep_between}" "--sleep-between"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

connected_devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
if ! grep -qx "${device_a}" <<<"${connected_devices}"; then
  echo "Device A is not connected: ${device_a}" >&2
  adb devices >&2
  exit 1
fi
if ! grep -qx "${device_b}" <<<"${connected_devices}"; then
  echo "Device B is not connected: ${device_b}" >&2
  adb devices >&2
  exit 1
fi

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="wifi-direct-reliability-sampling"
fi
if [[ -z "${out_dir}" ]]; then
  out_dir="${repo_root}/artifacts/wifi-direct-reliability/${timestamp}-${safe_label}"
fi
mkdir -p "${out_dir}"

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"

adb devices -l > "${out_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${out_dir}/git_status.txt" 2>&1 || true

run_trial() {
  local iteration="$1"
  local sender="$2"
  local target="$3"
  local sender_label="$4"
  local target_label="$5"
  local direction_label="${sender_label}-to-${target_label}"
  local trial_id
  trial_id="$(printf '%02d' "${iteration}")"
  local trial_dir="${out_dir}/${direction_label}/trial-${trial_id}"
  local body="Wi-Fi Direct reliability ${safe_label} ${direction_label} trial ${trial_id} ${timestamp}"

  echo "Running ${direction_label} trial ${trial_id}/${iterations}: ${trial_dir}"
  "${repo_root}/scripts/run_directed_wifi_direct_route_trial.sh" \
    --sender-device "${sender}" \
    --target-device "${target}" \
    --label "${safe_label}-${direction_label}-trial-${trial_id}" \
    --out-dir "${trial_dir}" \
    --body "${body}" \
    --settle-ms "${settle_ms}" \
    --debug-send-wait-ms "${debug_send_wait_ms}" \
    --debug-send-attempts "${debug_send_attempts}" \
    --debug-send-retry-delay-ms "${debug_send_retry_delay_ms}" \
    --sync-attempts "${sync_attempts}"
}

for (( iteration=1; iteration<=iterations; iteration++ )); do
  run_trial "${iteration}" "${device_a}" "${device_b}" "${device_a_label}" "${device_b_label}"
  sleep "${sleep_between}"
  run_trial "${iteration}" "${device_b}" "${device_a}" "${device_b_label}" "${device_a_label}"
  if (( iteration < iterations )); then
    sleep "${sleep_between}"
  fi
done

python3 - "${out_dir}" "${generated_at}" "${git_branch}" "${git_sha}" "${git_source_state}" "${device_a}" "${device_b}" "${device_a_label}" "${device_b_label}" "${iterations}" <<'PY'
from __future__ import annotations

import json
import statistics
import sys
from pathlib import Path
from typing import Any

out_dir = Path(sys.argv[1])
generated_at = sys.argv[2]
git_branch = sys.argv[3]
git_sha = sys.argv[4]
git_source_state = sys.argv[5]
device_a = sys.argv[6]
device_b = sys.argv[7]
device_a_label = sys.argv[8]
device_b_label = sys.argv[9]
iterations = int(sys.argv[10])


def load_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def percent(numerator: int, denominator: int) -> float:
    return round((numerator / denominator) * 100, 2) if denominator else 0.0


def int_value(payload: dict[str, Any], key: str) -> int:
    try:
        return int(payload.get(key))
    except (TypeError, ValueError):
        return 0


trials: list[dict[str, Any]] = []
for manifest_path in sorted(out_dir.glob("*-to-*/trial-*/manifest.json")):
    manifest = load_json(manifest_path)
    verdict = manifest.get("verdict") or {}
    target = manifest.get("target") or {}
    deltas = target.get("deltas") or {}
    newly_observed = target.get("newly_observed_after") or {}
    sender = manifest.get("sender") or {}
    direction = f"{manifest.get('sender_device')}->{manifest.get('target_device')}"
    trial = {
        "path": str(manifest_path.relative_to(out_dir)),
        "direction": direction,
        "sender_device": manifest.get("sender_device"),
        "target_device": manifest.get("target_device"),
        "sender_debug_send_success": sender.get("debug_send_success") is True,
        "permissions_ready": verdict.get("permissions_ready") is True,
        "relationship_ready": verdict.get("relationship_ready") is True,
        "wifi_direct_discovery_ready": verdict.get("wifi_direct_discovery_ready") is True,
        "endpoint_bound": verdict.get("endpoint_bound") is True,
        "send_attempted": verdict.get("send_attempted") is True,
        "transport_counter_delivery_observed": verdict.get("transport_counter_delivery_observed") is True,
        "message_delivery_proven": verdict.get("message_delivery_proven") is True,
        "status": verdict.get("status"),
        "accepted_connections_delta": int_value(deltas, "accepted_connections"),
        "inbound_packets_delta": int_value(deltas, "inbound_packets"),
        "malformed_frames_dropped_delta": int_value(deltas, "malformed_frames_dropped"),
        "matching_message_ids": newly_observed.get("matching_message_ids") or [],
        "matching_packet_ids": newly_observed.get("matching_packet_ids") or [],
        "sender_permission_warning": sender.get("wifi_direct_permission_warning"),
        "target_before_permission_warning": (target.get("before") or {}).get("wifi_direct_permission_warning"),
        "target_after_permission_warning": (target.get("after") or {}).get("wifi_direct_permission_warning"),
    }
    trials.append(trial)

directions: dict[str, dict[str, Any]] = {}
for trial in trials:
    entry = directions.setdefault(
        trial["direction"],
        {
            "direction": trial["direction"],
            "sender_device": trial["sender_device"],
            "target_device": trial["target_device"],
            "trial_count": 0,
            "sender_success_count": 0,
            "transport_counter_delivery_count": 0,
            "message_delivery_proven_count": 0,
            "permission_warning_count": 0,
            "permissions_ready_count": 0,
            "relationship_ready_count": 0,
            "wifi_direct_discovery_ready_count": 0,
            "endpoint_bound_count": 0,
            "send_attempted_count": 0,
            "accepted_connection_deltas": [],
            "inbound_packet_deltas": [],
            "failures": [],
        },
    )
    entry["trial_count"] += 1
    if trial["sender_debug_send_success"]:
        entry["sender_success_count"] += 1
    if trial["permissions_ready"]:
        entry["permissions_ready_count"] += 1
    if trial["relationship_ready"]:
        entry["relationship_ready_count"] += 1
    if trial["wifi_direct_discovery_ready"]:
        entry["wifi_direct_discovery_ready_count"] += 1
    if trial["endpoint_bound"]:
        entry["endpoint_bound_count"] += 1
    if trial["send_attempted"]:
        entry["send_attempted_count"] += 1
    if trial["transport_counter_delivery_observed"]:
        entry["transport_counter_delivery_count"] += 1
    if trial["message_delivery_proven"]:
        entry["message_delivery_proven_count"] += 1
    if trial["sender_permission_warning"] or trial["target_before_permission_warning"] or trial["target_after_permission_warning"]:
        entry["permission_warning_count"] += 1
    entry["accepted_connection_deltas"].append(trial["accepted_connections_delta"])
    entry["inbound_packet_deltas"].append(trial["inbound_packets_delta"])
    if not trial["message_delivery_proven"]:
        entry["failures"].append(trial)

for entry in directions.values():
    count = entry["trial_count"]
    entry["sender_success_rate_percent"] = percent(entry["sender_success_count"], count)
    entry["endpoint_bound_rate_percent"] = percent(entry["endpoint_bound_count"], count)
    entry["transport_counter_delivery_rate_percent"] = percent(entry["transport_counter_delivery_count"], count)
    entry["message_delivery_proven_rate_percent"] = percent(entry["message_delivery_proven_count"], count)
    entry["accepted_connections_delta_median"] = statistics.median(entry["accepted_connection_deltas"]) if count else 0
    entry["inbound_packets_delta_median"] = statistics.median(entry["inbound_packet_deltas"]) if count else 0

expected_trials = iterations * 2
message_successes = sum(1 for trial in trials if trial["message_delivery_proven"])
transport_successes = sum(1 for trial in trials if trial["transport_counter_delivery_observed"])
sender_successes = sum(1 for trial in trials if trial["sender_debug_send_success"])
endpoint_bound_successes = sum(1 for trial in trials if trial["endpoint_bound"])
discovery_ready_successes = sum(1 for trial in trials if trial["wifi_direct_discovery_ready"])
permission_warnings = sum(
    1 for trial in trials
    if trial["sender_permission_warning"] or trial["target_before_permission_warning"] or trial["target_after_permission_warning"]
)
overall_status = (
    "passed_all_sampled_trials"
    if len(trials) == expected_trials and message_successes == expected_trials and permission_warnings == 0
    else "completed_with_failures"
    if len(trials) == expected_trials
    else "incomplete_sampling"
)

manifest = {
    "report_version": "kraken.wifi_direct_reliability_sampling.v1",
    "generated_at": generated_at,
    "git": {
        "branch": git_branch,
        "sha": git_sha,
        "source_state": git_source_state,
        "status_file": "git_status.txt",
    },
    "device_a": {"serial": device_a, "label": device_a_label},
    "device_b": {"serial": device_b, "label": device_b_label},
    "iterations": iterations,
    "expected_trial_count": expected_trials,
    "observed_trial_count": len(trials),
    "overall_status": overall_status,
    "sender_debug_send_success_count": sender_successes,
    "wifi_direct_discovery_ready_count": discovery_ready_successes,
    "endpoint_bound_count": endpoint_bound_successes,
    "message_delivery_proven_count": message_successes,
    "transport_counter_delivery_count": transport_successes,
    "permission_warning_count": permission_warnings,
    "directions": list(directions.values()),
    "trials": trials,
    "claim_boundary": (
        "Repeated directed Wi-Fi Direct debug sampling. This can support a sampled "
        "prototype stability statement for these devices and this run only; it does "
        "not prove production network reliability or production security."
    ),
}
(out_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [
    "# Kraken Wi-Fi Direct Reliability Sampling",
    "",
    f"Generated: `{generated_at}`",
    f"Git: `{git_branch}` / `{git_sha}` / `{git_source_state}`",
    f"Device A: `{device_a_label}` / `{device_a}`",
    f"Device B: `{device_b_label}` / `{device_b}`",
    f"Iterations: `{iterations}`",
    f"Observed trials: `{len(trials)}/{expected_trials}`",
    f"Overall status: `{overall_status}`",
    f"Wi-Fi Direct discovery ready: `{discovery_ready_successes}/{len(trials)}`",
    f"Endpoint bound: `{endpoint_bound_successes}/{len(trials)}`",
    f"Message delivery proven: `{message_successes}/{len(trials)}`",
    f"Transport counter delivery observed: `{transport_successes}/{len(trials)}`",
    f"Permission warning trials: `{permission_warnings}`",
    "",
    "## Directions",
    "",
    "| Direction | Trials | Discovery ready | Endpoint bound | Message delivery | Transport counters | Sender success | Permission warnings | Median inbound delta |",
    "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
]
for entry in directions.values():
    lines.append(
        "| {direction} | {trial_count} "
        "| {wifi_direct_discovery_ready_count}/{trial_count} "
        "| {endpoint_bound_count}/{trial_count} ({endpoint_bound_rate_percent}%) "
        "| {message_delivery_proven_count}/{trial_count} ({message_delivery_proven_rate_percent}%) "
        "| {transport_counter_delivery_count}/{trial_count} ({transport_counter_delivery_rate_percent}%) "
        "| {sender_success_count}/{trial_count} ({sender_success_rate_percent}%) "
        "| {permission_warning_count} | {inbound_packets_delta_median} |".format(**entry)
    )
lines.extend(
    [
        "",
        "## Failed Trials",
        "",
    ]
)
failures = [trial for trial in trials if not trial["message_delivery_proven"]]
if failures:
    for trial in failures:
        lines.append(
            f"- `{trial['path']}` direction=`{trial['direction']}` status=`{trial['status']}` "
            f"inboundDelta=`{trial['inbound_packets_delta']}` senderSuccess=`{trial['sender_debug_send_success']}`"
        )
else:
    lines.append("- none")
lines.extend(
    [
        "",
        "## Claim Boundary",
        "",
        manifest["claim_boundary"],
        "",
    ]
)
(out_dir / "wifi_direct_reliability_sampling.md").write_text("\n".join(lines), encoding="utf-8")
PY

echo "Saved Wi-Fi Direct reliability sampling to: ${out_dir}"
echo "Manifest: ${out_dir}/manifest.json"
