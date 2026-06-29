#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not available on PATH." >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${repo_root}/artifacts/screenshots/android"
mkdir -p "${output_dir}"

echo "Connected devices:"
adb devices

name="${1:-screenshot}"
safe_name="$(printf '%s' "${name}" | tr -c '[:alnum:]_.-' '-' | sed 's/^-*//; s/-*$//')"
if [[ -z "${safe_name}" ]]; then
  safe_name="screenshot"
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
output_path="${output_dir}/${safe_name}-${timestamp}.png"

adb exec-out screencap -p > "${output_path}"

echo "Saved screenshot: ${output_path}"
