#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package_name="${KRAKEN_PACKAGE_NAME:-com.disser.kraken}"
timestamp="$(date +%Y%m%d-%H%M%S)"
label="phone-to-phone-qr"
source_device=""
target_device=""
output_dir=""
non_interactive=0

usage() {
  cat <<'EOF'
Usage: scripts/capture_phone_to_phone_qr_evidence.sh [options]

Capture manual phone-to-phone QR scan evidence for the Kraken UI/UX baseline.

Options:
  --source-device SERIAL  ADB serial for the phone showing My QR.
  --target-device SERIAL  ADB serial for the phone scanning QR.
  --label LABEL           Scenario label. Default: phone-to-phone-qr.
  --out-dir PATH          Output directory. Default:
                          artifacts/ui-ux-phone-to-phone-qr/<timestamp>-<label>.
  --package NAME          Android package. Default: com.disser.kraken.
  --non-interactive       Do not wait for Enter between capture steps.
                          Use only if screens are already prepared.
  -h, --help              Show this help.

Manual flow captured by this script:
  1. Source phone is on My QR with the QR visible.
  2. Target phone is on Scan QR before physical scan.
  3. User physically points target camera at source QR.
  4. Target phone reaches the post-scan result/pending contact screen.
  5. Optional final source screen is captured for context.

The script does not delete data, confirm destructive actions, grant trust, or
claim scan success by itself. It only captures screens, UI XML, device metadata
and a manifest. The reviewer must inspect the after-scan screenshot/result.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-device)
      source_device="${2:-}"
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
      output_dir="${2:-}"
      shift 2
      ;;
    --package)
      package_name="${2:-}"
      shift 2
      ;;
    --non-interactive)
      non_interactive=1
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

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

safe_label="$(printf '%s' "${label}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_label}" ]]; then
  safe_label="phone-to-phone-qr"
fi

connected_devices=()
while IFS= read -r serial; do
  connected_devices+=("${serial}")
done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')

if [[ -z "${source_device}" && -z "${target_device}" ]]; then
  if [[ "${#connected_devices[@]}" -ne 2 ]]; then
    echo "Expected exactly two connected adb devices, got ${#connected_devices[@]}." >&2
    adb devices >&2
    exit 1
  fi
  source_device="${connected_devices[0]}"
  target_device="${connected_devices[1]}"
elif [[ -z "${source_device}" || -z "${target_device}" ]]; then
  echo "Pass both --source-device and --target-device, or omit both with exactly two devices connected." >&2
  exit 2
fi

if [[ "${source_device}" == "${target_device}" ]]; then
  echo "--source-device and --target-device must be different serials." >&2
  exit 2
fi

if [[ -z "${output_dir}" ]]; then
  output_dir="${repo_root}/artifacts/ui-ux-phone-to-phone-qr/${timestamp}-${safe_label}"
fi
mkdir -p "${output_dir}"
captured_files=()

record_captured_file() {
  local path="$1"
  local relative_path="${path#"${output_dir}/"}"
  if [[ -s "${path}" ]]; then
    captured_files+=("${relative_path}")
  fi
}

write_captured_files_json() {
  local file
  local index
  for index in "${!captured_files[@]}"; do
    file="${captured_files[${index}]}"
    if [[ "${index}" -gt 0 ]]; then
      printf ',\n'
    fi
    printf '    "%s"' "${file//\"/\\\"}"
  done
  if [[ "${#captured_files[@]}" -gt 0 ]]; then
    printf '\n'
  fi
}

wait_for_user() {
  local message="$1"
  if [[ "${non_interactive}" -eq 1 ]]; then
    echo "${message}"
    return
  fi
  echo
  echo "${message}"
  read -r -p "Press Enter to capture..." _
}

capture_device_stage() {
  local serial="$1"
  local role="$2"
  local stage="$3"
  local prefix="${output_dir}/${stage}-${role}-${serial}"
  local remote_xml="/sdcard/kraken-qr-${stage}-${role}-${timestamp}.xml"

  {
    echo "\$ adb -s ${serial} shell getprop ro.product.manufacturer"
    adb -s "${serial}" shell getprop ro.product.manufacturer || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.product.model"
    adb -s "${serial}" shell getprop ro.product.model || true
    echo
    echo "\$ adb -s ${serial} shell getprop ro.build.version.release"
    adb -s "${serial}" shell getprop ro.build.version.release || true
  } > "${prefix}-device.txt" 2>&1
  record_captured_file "${prefix}-device.txt"

  if adb -s "${serial}" exec-out screencap -p > "${prefix}.png" 2> "${prefix}-screencap.txt"; then
    record_captured_file "${prefix}.png"
  else
    rm -f "${prefix}.png"
    record_captured_file "${prefix}-screencap.txt"
  fi
  if adb -s "${serial}" shell uiautomator dump "${remote_xml}" > "${prefix}-uiautomator.txt" 2>&1; then
    record_captured_file "${prefix}-uiautomator.txt"
    if adb -s "${serial}" exec-out cat "${remote_xml}" > "${prefix}.xml" 2>> "${prefix}-uiautomator.txt"; then
      record_captured_file "${prefix}.xml"
    else
      rm -f "${prefix}.xml"
    fi
    adb -s "${serial}" shell rm -f "${remote_xml}" >/dev/null 2>&1 || true
  else
    record_captured_file "${prefix}-uiautomator.txt"
  fi
}

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
git_branch="$(git -C "${repo_root}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
git_source_state="$(git -C "${repo_root}" diff --quiet --ignore-submodules -- && git -C "${repo_root}" diff --cached --quiet --ignore-submodules -- && echo clean || echo dirty)"

adb devices -l > "${output_dir}/adb_devices.txt"
git -C "${repo_root}" status --short --branch > "${output_dir}/git_status.txt" 2>&1 || true

wait_for_user "Step 1: On source ${source_device}, open Kraken -> Контакты -> Мой QR and keep the QR visible."
capture_device_stage "${source_device}" "source" "01-source-my-qr-before-scan"

wait_for_user "Step 2: On target ${target_device}, open Kraken -> Контакты -> Скан and keep the scanner visible."
capture_device_stage "${target_device}" "target" "02-target-scanner-before-scan"

wait_for_user "Step 3: Physically point target ${target_device} camera at source ${source_device} QR. Wait until Kraken shows the post-scan result/pending contact screen."
capture_device_stage "${target_device}" "target" "03-target-after-physical-scan"

wait_for_user "Step 4: Optional context capture. Leave source ${source_device} on the current Kraken screen."
capture_device_stage "${source_device}" "source" "04-source-after-scan-context"

cat > "${output_dir}/manifest.json" <<EOF
{
  "report_version": "kraken.ui_ux.phone_to_phone_qr_capture.v1",
  "generated_at": "${generated_at}",
  "label": "${safe_label}",
  "package": "${package_name}",
  "git": {
    "branch": "${git_branch}",
    "sha": "${git_sha}",
    "source_state": "${git_source_state}",
    "status_file": "git_status.txt"
  },
  "devices": {
    "source": "${source_device}",
    "target": "${target_device}"
  },
  "captured_files": [
$(write_captured_files_json)
  ],
  "claim_boundary": "Manual physical QR evidence capture. Success is proven only if the after-scan screenshot/UI shows a valid Kraken post-scan result; this script does not infer success."
}
EOF

cat > "${output_dir}/phone_to_phone_qr_capture.md" <<EOF
# Kraken Phone-To-Phone QR Capture

Generated: \`${generated_at}\`
Label: \`${safe_label}\`
Package: \`${package_name}\`
Git: \`${git_branch}\` / \`${git_sha}\` / \`${git_source_state}\`

## Devices

- Source / QR display: \`${source_device}\`
- Target / scanner: \`${target_device}\`

## Captured Flow

1. Source shows \`Мой QR\`.
2. Target shows \`Скан\`.
3. User physically scans source QR with target camera.
4. Target after-scan screen is captured.
5. Source context screen is captured.

## Success Criteria For Review

- \`03-target-after-physical-scan-target-${target_device}.png\` shows Kraken accepting the scanned QR and moving forward to the expected pending/contact confirmation state.
- If the screenshot shows an error, blank scanner, or unchanged scanner state, this artifact is evidence of a failed or inconclusive physical scan, not a success.

## Claim Boundary

This is manual physical QR evidence for the Android UI/UX baseline. It does not prove production cryptography, production security, long-term reliability, or macOS scanner behavior.
EOF

echo "Saved phone-to-phone QR evidence to: ${output_dir}"
echo "Summary: ${output_dir}/phone_to_phone_qr_capture.md"
echo "Manifest: ${output_dir}/manifest.json"
