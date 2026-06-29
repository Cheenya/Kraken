#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-"$ROOT_DIR/artifacts/crypto-attack-unit/$(date -u +%Y%m%d-%H%M%S)"}"

mkdir -p "$OUT_DIR"

cd "$ROOT_DIR/app-android"
./gradlew :app:testDebugUnitTest \
  --tests com.disser.kraken.crypto.AdamovaBoundCryptoEnvelopeTest \
  --tests com.disser.kraken.crypto.CryptoAbstractionsTest \
  --tests com.disser.kraken.mesh.QrHandshakeMessageSessionKeyProviderTest \
  --tests com.disser.kraken.mesh.MeshDeliveryPipelineTest \
  --tests com.disser.kraken.mesh.MeshServiceTest \
  | tee "$OUT_DIR/gradle_crypto_attack_tests.log"

cd "$ROOT_DIR"
/opt/homebrew/bin/pytest tests/test_android_policy_guards.py tests/test_protocol_spec_docs.py \
  | tee "$OUT_DIR/policy_docs_tests.log"

cat > "$OUT_DIR/crypto_attack_unit_summary.md" <<EOF
# Kraken Crypto Attack Unit Suite

Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)

## Covered checks

- plaintext message injection is rejected by default runtime;
- Adamova admission hash tamper is rejected before plaintext creation;
- session/profile binding tamper is rejected;
- unknown sender and wrong recipient are rejected;
- duplicate/replay packet is rejected when already seen;
- QR-handshake session key is shared by both relationship sides;
- missing QR invite secret cannot silently produce a runtime message key.

## Boundary

This is local unit/policy evidence. It does not replace physical MacBook packet
injection over LAN/BLE/Wi-Fi Direct.
EOF

echo "$OUT_DIR"
