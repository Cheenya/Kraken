#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="route-benchmark"
trials="10"
transport_profile="all"
sync_attempts="3"
sleep_between="2"
min_samples_per_route="10"
output_dir=""
debug_send_template="route benchmark trial {trial}"
start_mesh_before_export="true"
devices=()

usage() {
  cat <<'EOF'
Usage: scripts/run_route_benchmark_trials.sh [options]

Run repeated debug route-evidence captures and aggregate route latency samples.

Options:
  --device SERIAL       ADB serial. Pass exactly two or more times for benchmark evidence.
  --trials N           Trial count. Default: 10.
  --label LABEL         Label for output directory. Default: route-benchmark.
  --out-dir PATH        Output directory. Default: artifacts/route-benchmark/<timestamp>-<label>.
  --transport-profile PROFILE
                        Capture profile: all, hotspot-compatible, lan-only or wifi-direct-only.
                        Default: all.
  --debug-send-template TEXT
                        Message body template. {trial} is replaced with the trial number.
                        Default: route benchmark trial {trial}.
  --sync-attempts N     Mesh sync attempts after debug send. Default: 3.
  --sleep-between N     Seconds to sleep between trials. Default: 2.
  --min-samples-per-route N
                        Minimum delivered latency samples per route. Default: 10.
  --no-start-mesh-before-export
                        Do not ask the debug receiver to start mesh transports.
  -h, --help            Show this help.

This is a research/debug harness. It does not prove production reliability or
production security by itself. The generated summary may be used for a reliability
claim only when each claimed route passes the configured minimum sample gate with
fresh comparable two-device runs.
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
    --device)
      devices+=("${2:-}")
      shift 2
      ;;
    --trials)
      trials="${2:-}"
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
    --transport-profile)
      transport_profile="${2:-}"
      if [[ "${transport_profile}" != "all" && "${transport_profile}" != "hotspot-compatible" && "${transport_profile}" != "wifi-direct-only" && "${transport_profile}" != "lan-only" ]]; then
        echo "Unsupported --transport-profile: ${transport_profile}" >&2
        usage >&2
        exit 2
      fi
      shift 2
      ;;
    --debug-send-template)
      debug_send_template="${2:-}"
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
    --min-samples-per-route)
      min_samples_per_route="${2:-}"
      shift 2
      ;;
    --no-start-mesh-before-export)
      start_mesh_before_export="false"
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

positive_int "${trials}" "--trials"
positive_int "${sync_attempts}" "--sync-attempts"
positive_int "${sleep_between}" "--sleep-between"
positive_int "${min_samples_per_route}" "--min-samples-per-route"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

if [[ "${#devices[@]}" -eq 0 ]]; then
  while IFS= read -r serial; do
    devices+=("${serial}")
  done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
fi

if (( ${#devices[@]} < 2 )); then
  echo "Route benchmark requires at least two connected adb devices." >&2
  adb devices >&2
  exit 1
fi

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="route-benchmark"
fi

if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/route-benchmark/${timestamp}-${safe_label}"
fi
mkdir -p "${output_dir}"

git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"
generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

adb devices -l > "${output_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${output_dir}/git_status.txt" 2>&1 || true

capture_sources=()
for (( trial=1; trial<=trials; trial++ )); do
  trial_id="$(printf '%02d' "${trial}")"
  trial_dir="${output_dir}/trial-${trial_id}"
  body="${debug_send_template//\{trial\}/${trial_id}}"
  args=(
    "${repo_root}/scripts/capture_debug_route_evidence.sh"
    --label "${safe_label}-trial-${trial_id}"
    --out-dir "${trial_dir}"
    --transport-profile "${transport_profile}"
    --debug-send-body "${body}"
    --sync-after-debug-send
    --sync-attempts "${sync_attempts}"
  )
  if [[ "${start_mesh_before_export}" == "true" ]]; then
    args+=(--start-mesh-before-export)
  fi
  for serial in "${devices[@]}"; do
    args+=(--device "${serial}")
  done
  echo "Running trial ${trial_id}/${trials}: ${trial_dir}"
  "${args[@]}"
  while IFS= read -r route_json; do
    capture_sources+=("${route_json}")
  done < <(find "${trial_dir}" -mindepth 2 -maxdepth 2 -name route_specific_evidence_latest.json | sort)
  if (( trial < trials )); then
    sleep "${sleep_between}"
  fi
done

if (( ${#capture_sources[@]} == 0 )); then
  echo "No route evidence JSON files were captured." >&2
  exit 1
fi

summary_json="${output_dir}/route_benchmark_summary.json"
summary_md="${output_dir}/route_benchmark_summary.md"
summary_args=(
  python3 "${repo_root}/scripts/build_route_benchmark_summary.py"
  --out-json "${summary_json}"
  --out-md "${summary_md}"
  --date "$(date +%Y-%m-%d)"
  --min-samples-per-route "${min_samples_per_route}"
)
for source in "${capture_sources[@]}"; do
  summary_args+=(--source "${source}")
done
"${summary_args[@]}" > "${output_dir}/build_route_benchmark_summary.log"

python3 - "${output_dir}" "${generated_at}" "${git_branch}" "${git_sha}" "${git_source_state}" "${transport_profile}" "${trials}" "${min_samples_per_route}" "${start_mesh_before_export}" "${sync_attempts}" "${debug_send_template}" "${devices[@]}" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

output_dir = Path(sys.argv[1])
generated_at = sys.argv[2]
git_branch = sys.argv[3]
git_sha = sys.argv[4]
git_source_state = sys.argv[5]
transport_profile = sys.argv[6]
trials = int(sys.argv[7])
min_samples = int(sys.argv[8])
start_mesh_before_export = sys.argv[9] == "true"
sync_attempts = int(sys.argv[10])
debug_send_template = sys.argv[11]
devices = sys.argv[12:]
summary_path = output_dir / "route_benchmark_summary.json"
summary = json.loads(summary_path.read_text(encoding="utf-8"))
manifest = {
    "report_version": "kraken.route_benchmark_trials.v1",
    "generated_at": generated_at,
    "git": {
        "branch": git_branch,
        "sha": git_sha,
        "source_state": git_source_state,
        "status_file": "git_status.txt",
    },
    "devices": devices,
    "transport_profile": transport_profile,
    "trials": trials,
    "start_mesh_before_export": start_mesh_before_export,
    "sync_attempts": sync_attempts,
    "debug_send_template": debug_send_template,
    "min_samples_per_route": min_samples,
    "summary_json": "route_benchmark_summary.json",
    "summary_md": "route_benchmark_summary.md",
    "overall_status": summary.get("overall_status"),
    "routes": summary.get("routes", []),
    "claim_boundary": (
        "Research/debug route benchmark harness. Do not claim repeatable reliability "
        "unless each route passes the minimum sample gate with fresh comparable two-device runs."
    ),
}
(output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
lines = [
    "# Kraken Route Benchmark Trials",
    "",
    f"Generated: `{generated_at}`",
    f"Git: `{git_branch}` / `{git_sha}` / `{git_source_state}`",
    f"Devices: `{', '.join(devices)}`",
    f"Transport profile: `{transport_profile}`",
    f"Trials: `{trials}`",
    f"Minimum sample gate per route: `{min_samples}`",
    f"Overall status: `{summary.get('overall_status')}`",
    "",
    "## Claim Boundary",
    "",
    manifest["claim_boundary"],
    "",
    "## Summary",
    "",
    "- `route_benchmark_summary.json`",
    "- `route_benchmark_summary.md`",
    "",
]
(output_dir / "route_benchmark_trials.md").write_text("\n".join(lines), encoding="utf-8")
PY

echo "Saved route benchmark trials to: ${output_dir}"
echo "Manifest: ${output_dir}/manifest.json"
echo "Summary: ${summary_json}"
