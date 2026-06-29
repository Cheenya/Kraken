#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package_name="${KRAKEN_PACKAGE_NAME:-com.disser.kraken}"
device_id="${1:-${ANDROID_SERIAL:-}}"
timestamp="$(date +%Y%m%d-%H%M%S)"
output_dir="${repo_root}/artifacts/android-adamova-live/${timestamp}"
mkdir -p "${output_dir}"

adb_cmd=(adb)
if [[ -n "${device_id}" ]]; then
  adb_cmd+=( -s "${device_id}" )
fi

run_adb() {
  "${adb_cmd[@]}" "$@"
}

write_cmd_output() {
  local name="$1"
  shift
  {
    echo "\$ adb ${device_id:+-s ${device_id} }$*"
    run_adb "$@" || true
  } > "${output_dir}/${name}.txt" 2>&1
}

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
if [[ -z "${device_id}" ]]; then
  device_count="$(printf '%s\n' "${devices}" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "${device_count}" != "1" ]]; then
    echo "Expected exactly one connected adb device, got ${device_count}." >&2
    echo "Pass a serial as the first argument or set ANDROID_SERIAL." >&2
    adb devices >&2
    exit 1
  fi
  device_id="${devices}"
  adb_cmd=(adb -s "${device_id}")
fi

git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
app_dir="files/adamova_admission_gate"
json_name="admission_gate_attack_demo_latest.json"
md_name="admission_gate_attack_demo_latest.md"
manifest_name="live_evidence_manifest.json"

write_cmd_output "adb_devices" devices -l
write_cmd_output "device_props" shell getprop
write_cmd_output "package_dump" shell dumpsys package "${package_name}"
write_cmd_output "focused_activity" shell dumpsys activity activities

run_adb shell am start -n "${package_name}/.MainActivity" > "${output_dir}/launch.txt" 2>&1 || true
sleep 1
run_adb exec-out screencap -p > "${output_dir}/research_mode_screen.png" || true

pull_private_file() {
  local remote_path="$1"
  local local_path="$2"
  if run_adb shell run-as "${package_name}" ls "${remote_path}" >/dev/null 2>&1; then
    run_adb exec-out run-as "${package_name}" cat "${remote_path}" > "${local_path}"
    return 0
  fi
  return 1
}

json_status="missing"
md_status="missing"
if pull_private_file "${app_dir}/${json_name}" "${output_dir}/${json_name}"; then
  json_status="captured"
fi
if pull_private_file "${app_dir}/${md_name}" "${output_dir}/${md_name}"; then
  md_status="captured"
fi

python3 - "${output_dir}" "${json_name}" "${md_name}" "${manifest_name}" "${generated_at}" "${git_sha}" "${device_id}" "${package_name}" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

output_dir = Path(sys.argv[1])
json_name = sys.argv[2]
md_name = sys.argv[3]
manifest_name = sys.argv[4]
generated_at = sys.argv[5]
git_sha = sys.argv[6]
device_id = sys.argv[7]
package_name = sys.argv[8]

json_path = output_dir / json_name
md_path = output_dir / md_name
screenshot_path = output_dir / "research_mode_screen.png"

report_payload = None
report_metrics = None
if json_path.exists() and json_path.stat().st_size > 0:
    try:
        report_payload = json.loads(json_path.read_text(encoding="utf-8"))
        report_metrics = report_payload.get("metrics")
    except json.JSONDecodeError:
        report_payload = None
        report_metrics = None

manifest = {
    "generated_at": generated_at,
    "git_sha": git_sha,
    "device_serial": device_id,
    "package": package_name,
    "screenshot_present": screenshot_path.exists() and screenshot_path.stat().st_size > 0,
    "json_present": json_path.exists() and json_path.stat().st_size > 0,
    "markdown_present": md_path.exists() and md_path.stat().st_size > 0,
    "report_metrics": report_metrics,
    "claim_boundary": (
        "Live Android evidence for Research Mode Adamova admission demo; "
        "not production encryption or secure messenger proof."
    ),
}
(output_dir / manifest_name).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

cat > "${output_dir}/live_evidence_summary.md" <<EOF
# Adamova Android Live Evidence Capture

Generated: \`${generated_at}\`
Git SHA: \`${git_sha}\`
Device serial: \`${device_id}\`
Package: \`${package_name}\`

## Captured Files

- Screenshot: \`research_mode_screen.png\`
- JSON: \`${json_name}\` - \`${json_status}\`
- Markdown: \`${md_name}\` - \`${md_status}\`
- ADB device list: \`adb_devices.txt\`
- Device properties: \`device_props.txt\`
- Package dump: \`package_dump.txt\`
- Focused activity dump: \`focused_activity.txt\`
- Machine-readable manifest: \`${manifest_name}\`

## Interpretation Boundary

This capture is live Android evidence for the Research Mode Adamova admission
demo. It is evidence for experimental crypto-profile admission policy, not
production encryption, packet signatures, Android Keystore, or a secure
messenger proof.

## If JSON/Markdown Are Missing

Open Kraken on the device, go to Settings -> Research, run the Adamova admission
gate demo, wait until metrics appear, then run this script again.
EOF

echo "Saved Adamova Android live evidence to: ${output_dir}"
echo "JSON: ${json_status}"
echo "Markdown: ${md_status}"

if [[ "${json_status}" != "captured" || "${md_status}" != "captured" ]]; then
  exit 2
fi

python3 "${repo_root}/scripts/audit_adamova_effectiveness_evidence.py"
