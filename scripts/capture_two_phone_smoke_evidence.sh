#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package_name="${KRAKEN_PACKAGE_NAME:-com.disser.kraken}"
apk_path="${KRAKEN_APK_PATH:-${repo_root}/app-android/app/build/outputs/apk/debug/app-debug.apk}"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="manual-lan-wifi"
device_a=""
device_b=""
output_dir=""
route_evidence_json=""

usage() {
  cat <<'EOF'
Usage: scripts/capture_two_phone_smoke_evidence.sh [options]

Capture a repeatable two-phone Kraken smoke evidence snapshot.

Options:
  --device-a SERIAL    ADB serial for phone A. If omitted, exactly two connected devices are required.
  --device-b SERIAL    ADB serial for phone B. If omitted, exactly two connected devices are required.
  --label LABEL        Scenario label for the output folder. Default: manual-lan-wifi.
  --out-dir PATH       Output directory. Default: artifacts/two-phone-test/repeatable-<timestamp>-<label>.
  --package NAME       Android package. Default: com.disser.kraken or KRAKEN_PACKAGE_NAME.
  --apk PATH           APK path. Default: app-android/app/build/outputs/apk/debug/app-debug.apk.
  --route-evidence-json PATH
                       Optional JSON copied from the in-app route-specific evidence export.
                       If provided, the script stores it as route_specific_evidence.json
                       and writes route_specific_evidence_summary.md.
  -h, --help           Show this help.

Run this after completing the manual two-phone flow in the app:
  1. both phones have local identities;
  2. QR/offline handshake has produced ACTIVE contacts;
  3. A -> B and B -> A messages were sent;
  4. delivery/receipt UI states are visible on both phones.

The script does not install, clear data, grant permissions, send messages or
drop evidence. It captures the current state into ignored local artifacts.
EOF
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
    --apk)
      apk_path="${2:-}"
      shift 2
      ;;
    --route-evidence-json)
      route_evidence_json="${2:-}"
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

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="manual-lan-wifi"
fi

if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/two-phone-test/repeatable-${timestamp}-${safe_label}"
fi
mkdir -p "${output_dir}"

connected_devices=()
while IFS= read -r serial; do
  connected_devices+=("${serial}")
done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "${device_a}" && -z "${device_b}" ]]; then
  if [[ "${#connected_devices[@]}" -ne 2 ]]; then
    echo "Expected exactly two connected adb devices, got ${#connected_devices[@]}." >&2
    adb devices >&2
    exit 1
  fi
  device_a="${connected_devices[0]}"
  device_b="${connected_devices[1]}"
elif [[ -z "${device_a}" || -z "${device_b}" ]]; then
  echo "Pass both --device-a and --device-b, or omit both with exactly two devices connected." >&2
  exit 2
fi

if [[ "${device_a}" == "${device_b}" ]]; then
  echo "--device-a and --device-b must be different serials." >&2
  exit 2
fi

route_evidence_file=""
if [[ -n "${route_evidence_json}" ]]; then
  if [[ ! -f "${route_evidence_json}" ]]; then
    echo "Route-specific evidence JSON not found: ${route_evidence_json}" >&2
    exit 2
  fi
  route_evidence_file="route_specific_evidence.json"
  cp "${route_evidence_json}" "${output_dir}/${route_evidence_file}"
fi

run_capture() {
  local serial="$1"
  local role="$2"
  local role_dir="${output_dir}/${role}-${serial}"
  mkdir -p "${role_dir}"

  {
    echo "\$ adb -s ${serial} shell getprop ro.product.manufacturer"
    adb -s "${serial}" shell getprop ro.product.manufacturer || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.product.model"
    adb -s "${serial}" shell getprop ro.product.model || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.product.device"
    adb -s "${serial}" shell getprop ro.product.device || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.build.version.release"
    adb -s "${serial}" shell getprop ro.build.version.release || true
  } > "${role_dir}/device_identity.txt" 2>&1

  adb -s "${serial}" shell ip -f inet addr show > "${role_dir}/network_inet.txt" 2>&1 || true
  adb -s "${serial}" shell dumpsys window > "${role_dir}/window_dump.txt" 2>&1 || true
  adb -s "${serial}" shell dumpsys activity activities > "${role_dir}/activity_dump.txt" 2>&1 || true
  adb -s "${serial}" shell dumpsys package "${package_name}" > "${role_dir}/package_dump.txt" 2>&1 || true
  adb -s "${serial}" exec-out screencap -p > "${role_dir}/screen.png" || true

  local remote_xml="/sdcard/kraken-two-phone-${role}-${timestamp}.xml"
  if adb -s "${serial}" shell uiautomator dump "${remote_xml}" > "${role_dir}/uiautomator_dump.txt" 2>&1; then
    adb -s "${serial}" exec-out cat "${remote_xml}" > "${role_dir}/ui.xml" || true
    adb -s "${serial}" shell rm -f "${remote_xml}" >/dev/null 2>&1 || true
  fi
}

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"

adb devices -l > "${output_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${output_dir}/git_status.txt" 2>&1 || true

apk_size=""
apk_sha256=""
if [[ -f "${apk_path}" ]]; then
  apk_size="$(wc -c < "${apk_path}" | tr -d ' ')"
  apk_sha256="$(shasum -a 256 "${apk_path}" | awk '{ print $1 }')"
  printf '%s  %s\n' "${apk_sha256}" "${apk_path}" > "${output_dir}/apk_sha256.txt"
else
  echo "APK not found: ${apk_path}" > "${output_dir}/apk_sha256.txt"
fi

run_capture "${device_a}" "device-a"
run_capture "${device_b}" "device-b"

python3 - "${output_dir}" "${generated_at}" "${git_sha}" "${git_branch}" "${git_source_state}" "${package_name}" "${apk_path}" "${apk_size}" "${apk_sha256}" "${device_a}" "${device_b}" "${safe_label}" "${route_evidence_file}" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

output_dir = Path(sys.argv[1])
generated_at = sys.argv[2]
git_sha = sys.argv[3]
git_branch = sys.argv[4]
git_source_state = sys.argv[5]
package_name = sys.argv[6]
apk_path = sys.argv[7]
apk_size = sys.argv[8]
apk_sha256 = sys.argv[9]
device_a = sys.argv[10]
device_b = sys.argv[11]
label = sys.argv[12]
route_evidence_file = sys.argv[13]

def rel(path: Path) -> str:
    return str(path.relative_to(output_dir))

devices = []
for role, serial in [("device-a", device_a), ("device-b", device_b)]:
    role_dir = output_dir / f"{role}-{serial}"
    devices.append(
        {
            "role": role,
            "serial": serial,
            "screen_png": rel(role_dir / "screen.png"),
            "ui_xml": rel(role_dir / "ui.xml"),
            "device_identity": rel(role_dir / "device_identity.txt"),
            "network_inet": rel(role_dir / "network_inet.txt"),
            "window_dump": rel(role_dir / "window_dump.txt"),
            "activity_dump": rel(role_dir / "activity_dump.txt"),
            "package_dump": rel(role_dir / "package_dump.txt"),
        }
    )

route_specific_evidence = {
    "status": "not_provided",
    "expected_source": "in-app MeshEvidenceExporter route-specific JSON",
    "expected_fields": [
        "transport.selected_route",
        "transport.recent_route_attempts",
        "metrics.packets_sent",
        "metrics.packets_received",
        "metrics.receipts_received",
        "metrics.duplicates_dropped",
        "metrics.expired_dropped",
        "metrics.unknown_peer_rejected",
        "metrics.wrong_recipient_rejected",
        "metrics.last_delivery_latency_ms",
        "queue_size",
        "last_packet_status",
        "app_version_name",
        "git_sha",
        "app_build_type",
        "device_model",
        "claim_boundary",
    ],
}

if route_evidence_file:
    route_payload_path = output_dir / route_evidence_file
    route_payload = json.loads(route_payload_path.read_text(encoding="utf-8"))
    transport = route_payload.get("transport", {})
    metrics = route_payload.get("metrics", {})
    summary_file = "route_specific_evidence_summary.md"
    summary_lines = [
        "# Kraken Route-Specific Evidence Summary",
        "",
        f"- Source JSON: `{route_evidence_file}`",
        f"- Selected route: `{transport.get('selected_route', 'none')}`",
        f"- Recent route attempts: `{len(transport.get('recent_route_attempts', []))}`",
        f"- Queue size: `{route_payload.get('queue_size', route_payload.get('queued_packets', 'n/a'))}`",
        f"- Last packet status: `{route_payload.get('last_packet_status', 'n/a')}`",
        f"- App version: `{route_payload.get('app_version_name', 'n/a')}`",
        f"- Build type: `{route_payload.get('app_build_type', 'n/a')}`",
        f"- Git SHA: `{route_payload.get('git_sha', 'n/a')}`",
        f"- Device model: `{route_payload.get('device_model', 'n/a')}`",
        "",
        "## Counters",
        "",
        f"- packetsSent: `{metrics.get('packets_sent', 'n/a')}`",
        f"- packetsReceived: `{metrics.get('packets_received', 'n/a')}`",
        f"- receiptsReceived: `{metrics.get('receipts_received', 'n/a')}`",
        f"- duplicatesDropped: `{metrics.get('duplicates_dropped', 'n/a')}`",
        f"- expiredDropped: `{metrics.get('expired_dropped', 'n/a')}`",
        f"- unknownPeerRejected: `{metrics.get('unknown_peer_rejected', 'n/a')}`",
        f"- wrongRecipientRejected: `{metrics.get('wrong_recipient_rejected', 'n/a')}`",
        f"- lastDeliveryLatencyMs: `{metrics.get('last_delivery_latency_ms', 'n/a')}`",
        "",
        "## Claim Boundary",
        "",
        f"`{route_payload.get('claim_boundary', 'missing')}`",
        "",
        "Prototype evidence only. This is not production secure messaging.",
        "",
    ]
    (output_dir / summary_file).write_text("\n".join(summary_lines), encoding="utf-8")
    route_specific_evidence = {
        "status": "provided",
        "json_file": route_evidence_file,
        "markdown_summary_file": summary_file,
        "selected_route": transport.get("selected_route"),
        "recent_route_attempt_count": len(transport.get("recent_route_attempts", [])),
        "claim_boundary": route_payload.get("claim_boundary"),
    }

manifest = {
    "report_version": "kraken.two_phone_smoke_capture.v1",
    "generated_at": generated_at,
    "label": label,
    "package": package_name,
    "git": {
        "branch": git_branch,
        "sha": git_sha,
        "source_state": git_source_state,
        "status_file": "git_status.txt",
    },
    "apk": {
        "path": apk_path,
        "size_bytes": int(apk_size) if apk_size else None,
        "sha256": apk_sha256 or None,
        "sha256_file": "apk_sha256.txt",
    },
    "adb_devices_file": "adb_devices.txt",
    "devices": devices,
    "route_specific_evidence": route_specific_evidence,
    "manual_steps_expected_before_capture": [
        "both phones have local identities",
        "QR/offline handshake has produced ACTIVE contacts",
        "A -> B message is visible on B",
        "B -> A message is visible on A",
        "delivery/receipt UI state is visible where expected",
    ],
    "claim_boundary": (
        "Repeatable capture bundle for manual two-phone prototype smoke; "
        "not production reliability, production encryption or security proof."
    ),
}
(output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

cat > "${output_dir}/two_phone_smoke_summary.md" <<EOF
# Kraken Two-Phone Smoke Capture

Generated: \`${generated_at}\`
Label: \`${safe_label}\`
Package: \`${package_name}\`
Git: \`${git_branch}\` / \`${git_sha}\` / \`${git_source_state}\`
APK: \`${apk_path}\`
APK SHA-256: \`${apk_sha256:-missing}\`

## Devices

- Device A: \`${device_a}\`
- Device B: \`${device_b}\`

## Manual Preconditions

- Both phones have local identities.
- QR/offline handshake has produced ACTIVE contacts.
- A -> B and B -> A messages have been sent.
- Delivery/receipt UI states are visible before capture.

## Captured Files

- \`manifest.json\`
- \`adb_devices.txt\`
- \`git_status.txt\`
- \`apk_sha256.txt\`
- \`route_specific_evidence.json\` and \`route_specific_evidence_summary.md\` if \`--route-evidence-json\` was provided
- \`device-a-${device_a}/screen.png\`
- \`device-a-${device_a}/ui.xml\`
- \`device-a-${device_a}/network_inet.txt\`
- \`device-b-${device_b}/screen.png\`
- \`device-b-${device_b}/ui.xml\`
- \`device-b-${device_b}/network_inet.txt\`

## Claim Boundary

This bundle is repeatable capture evidence for a manual two-phone prototype
smoke. It does not prove production reliability, production encryption,
audited signatures, Android Keystore identity storage or security review.
EOF

echo "Saved two-phone smoke evidence to: ${output_dir}"
echo "Device A: ${device_a}"
echo "Device B: ${device_b}"
echo "Manifest: ${output_dir}/manifest.json"
