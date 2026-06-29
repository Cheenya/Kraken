#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="10-10-closure-suite"
sender_device=""
target_device=""
mac_host=""
out_dir=""
wifi_direct_settle_ms="12000"
benchmark_trials="10"
benchmark_min_samples="10"
benchmark_sync_attempts="3"
relay_sync_attempts="3"
relay_timeout_seconds="90"

usage() {
  cat <<'EOF'
Usage: scripts/run_10_10_closure_suite.sh [options]

Run the remaining Kraken 10/10 closure gates in one reproducible sequence.

Required:
  --sender-device SERIAL    Android A serial.
  --target-device SERIAL    Android B serial.
  --mac-host HOST           Mac LAN address reachable from Android A.

Optional:
  --label LABEL             Output label. Default: 10-10-closure-suite.
  --out-dir PATH            Output directory. Default: artifacts/10-10-closure-suite/<timestamp>-<label>.
  --wifi-direct-settle-ms N Mesh settle time for Wi-Fi Direct capture. Default: 12000.
  --benchmark-trials N      Route benchmark trial count. Default: 10.
  --benchmark-min-samples N Minimum samples per route. Default: 10.
  --benchmark-sync-attempts N
                             Sync attempts per benchmark trial. Default: 3.
  --relay-sync-attempts N   Sync attempts per relay mode. Default: 3.
  --relay-timeout-seconds N Relay timeout per mode. Default: 90.
  -h, --help                Show this help.

This is a research/debug closure harness. It is not production security evidence
by itself; it only becomes 10/10 evidence if the generated Wi-Fi Direct,
benchmark and physical relay artifacts satisfy their gates.
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
    --mac-host)
      mac_host="${2:-}"
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
    --wifi-direct-settle-ms)
      wifi_direct_settle_ms="${2:-}"
      shift 2
      ;;
    --benchmark-trials)
      benchmark_trials="${2:-}"
      shift 2
      ;;
    --benchmark-min-samples)
      benchmark_min_samples="${2:-}"
      shift 2
      ;;
    --benchmark-sync-attempts)
      benchmark_sync_attempts="${2:-}"
      shift 2
      ;;
    --relay-sync-attempts)
      relay_sync_attempts="${2:-}"
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

positive_int "${wifi_direct_settle_ms}" "--wifi-direct-settle-ms"
positive_int "${benchmark_trials}" "--benchmark-trials"
positive_int "${benchmark_min_samples}" "--benchmark-min-samples"
positive_int "${benchmark_sync_attempts}" "--benchmark-sync-attempts"
positive_int "${relay_sync_attempts}" "--relay-sync-attempts"
positive_int "${relay_timeout_seconds}" "--relay-timeout-seconds"

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
  safe_label="10-10-closure-suite"
fi

if [[ -z "${out_dir}" ]]; then
  out_dir="${repo_root}/artifacts/10-10-closure-suite/${timestamp}-${safe_label}"
fi
mkdir -p "${out_dir}"

git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"
generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

adb devices -l > "${out_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${out_dir}/git_status.txt" 2>&1 || true

wifi_direct_forward_dir="${out_dir}/wifi-direct-sender-to-target"
wifi_direct_reverse_dir="${out_dir}/wifi-direct-target-to-sender"
route_benchmark_dir="${out_dir}/route-benchmark"
physical_relay_dir="${out_dir}/physical-inline-relay"

write_suite_manifest() {
  local suite_status="$1"
  python3 - "${out_dir}" "${generated_at}" "${git_branch}" "${git_sha}" "${git_source_state}" "${sender_device}" "${target_device}" "${mac_host}" "${wifi_direct_forward_dir}" "${wifi_direct_reverse_dir}" "${route_benchmark_dir}" "${physical_relay_dir}" "${suite_status}" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

out_dir = Path(sys.argv[1])
generated_at = sys.argv[2]
git_branch = sys.argv[3]
git_sha = sys.argv[4]
git_source_state = sys.argv[5]
sender_device = sys.argv[6]
target_device = sys.argv[7]
mac_host = sys.argv[8]
wifi_direct_forward_dir = Path(sys.argv[9])
wifi_direct_reverse_dir = Path(sys.argv[10])
route_benchmark_dir = Path(sys.argv[11])
physical_relay_dir = Path(sys.argv[12])
suite_status = sys.argv[13]

manifest = {
    "report_version": "kraken.10_10_closure_suite.v1",
    "generated_at": generated_at,
    "suite_status": suite_status,
    "git": {
        "branch": git_branch,
        "sha": git_sha,
        "source_state": git_source_state,
        "status_file": "git_status.txt",
    },
    "sender_device": sender_device,
    "target_device": target_device,
    "mac_host": mac_host,
    "wifi_direct_forward_dir": str(wifi_direct_forward_dir.relative_to(out_dir)),
    "wifi_direct_reverse_dir": str(wifi_direct_reverse_dir.relative_to(out_dir)),
    "route_benchmark_dir": str(route_benchmark_dir.relative_to(out_dir)),
    "physical_relay_dir": str(physical_relay_dir.relative_to(out_dir)),
    "claim_boundary": (
        "Research/debug closure harness. Do not claim 10/10 unless the Wi-Fi Direct, "
        "route benchmark and physical inline relay outputs satisfy their evidence gates."
    ),
}
(out_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
lines = [
    "# Kraken 10/10 Closure Suite",
    "",
    f"Generated: `{generated_at}`",
    f"Suite status: `{suite_status}`",
    f"Git: `{git_branch}` / `{git_sha}` / `{git_source_state}`",
    f"Sender device: `{sender_device}`",
    f"Target device: `{target_device}`",
    f"Mac host: `{mac_host}`",
    "",
    "## Outputs",
    "",
    f"- Wi-Fi Direct sender-to-target capture: `{wifi_direct_forward_dir.relative_to(out_dir)}`",
    f"- Wi-Fi Direct target-to-sender capture: `{wifi_direct_reverse_dir.relative_to(out_dir)}`",
    f"- Route benchmark: `{route_benchmark_dir.relative_to(out_dir)}`",
    f"- Physical inline relay: `{physical_relay_dir.relative_to(out_dir)}`",
    "",
    "## Boundary",
    "",
    manifest["claim_boundary"],
    "",
]
(out_dir / "closure_suite_summary.md").write_text("\n".join(lines), encoding="utf-8")
PY
}

write_suite_manifest started

"${repo_root}/scripts/run_directed_wifi_direct_route_trial.sh" \
  --sender-device "${sender_device}" \
  --target-device "${target_device}" \
  --label "${safe_label}-wifi-direct-sender-to-target" \
  --out-dir "${wifi_direct_forward_dir}" \
  --settle-ms "${wifi_direct_settle_ms}" \
  --body "10/10 Wi-Fi Direct closure sender-to-target ${timestamp}" \
  --debug-send-wait-ms "${wifi_direct_settle_ms}" \
  --debug-send-attempts "${benchmark_sync_attempts}" \
  --sync-attempts "${benchmark_sync_attempts}"

"${repo_root}/scripts/run_directed_wifi_direct_route_trial.sh" \
  --sender-device "${target_device}" \
  --target-device "${sender_device}" \
  --label "${safe_label}-wifi-direct-target-to-sender" \
  --out-dir "${wifi_direct_reverse_dir}" \
  --settle-ms "${wifi_direct_settle_ms}" \
  --body "10/10 Wi-Fi Direct closure target-to-sender ${timestamp}" \
  --debug-send-wait-ms "${wifi_direct_settle_ms}" \
  --debug-send-attempts "${benchmark_sync_attempts}" \
  --sync-attempts "${benchmark_sync_attempts}"

"${repo_root}/scripts/run_route_benchmark_trials.sh" \
  --device "${sender_device}" \
  --device "${target_device}" \
  --label "${safe_label}-route-benchmark" \
  --out-dir "${route_benchmark_dir}" \
  --transport-profile all \
  --trials "${benchmark_trials}" \
  --min-samples-per-route "${benchmark_min_samples}" \
  --sync-attempts "${benchmark_sync_attempts}"

"${repo_root}/scripts/run_physical_inline_relay_trials.sh" \
  --sender-device "${sender_device}" \
  --target-device "${target_device}" \
  --mac-host "${mac_host}" \
  --label "${safe_label}-physical-inline-relay" \
  --out-dir "${physical_relay_dir}" \
  --sync-attempts "${relay_sync_attempts}" \
  --relay-timeout-seconds "${relay_timeout_seconds}"

write_suite_manifest finished

python3 "${repo_root}/scripts/audit_10_10_closure_suite_output.py" \
  --suite-dir "${out_dir}" \
  --out-json "${out_dir}/closure_suite_output_audit.json" \
  --out-md "${out_dir}/closure_suite_output_audit.md"

echo "Wrote ${out_dir}/closure_suite_summary.md"
