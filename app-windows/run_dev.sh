#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

python3 -m unittest discover -s tests
python3 -m compileall kraken_windows tests
python3 -m kraken_windows
