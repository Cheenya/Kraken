# Non-phone transport/crypto commit slices, 2026-06-14

Статус: карта для ручного staged commit. Commit/push в этом проходе не выполнять без отдельной команды.

## Slice 1: Wi-Fi Direct transport diagnostics and endpoint binding

Назначение: зафиксировать debug peer hint, endpoint binding states и evidence diagnostics без заявления о реальной доставке.

```bash
git add -- \
  app-android/README.md \
  app-android/app/src/debug/java/com/disser/kraken/debug/DebugEvidenceReceiver.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/CompositePeerTransport.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/MeshRuntime.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/MeshService.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/PeerTransport.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectEndpointResolver.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectPeerBinding.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectTransport.kt \
  app-android/app/src/test/java/com/disser/kraken/mesh/WifiDirectEndpointResolverTest.kt \
  app-android/app/src/test/java/com/disser/kraken/mesh/WifiDirectPeerBindingTest.kt
```

Suggested subject: `Instrument Wi-Fi Direct endpoint binding`

## Slice 2: Directed Wi-Fi Direct harness

Назначение: сделать directed harness честным и тестируемым без ADB: parsing hint, manifest verdict, counter delivery отдельно от message delivery.

```bash
git add -- \
  scripts/capture_debug_route_evidence.sh \
  scripts/run_directed_wifi_direct_route_trial.sh \
  scripts/build_directed_wifi_direct_trial_manifest.py \
  scripts/extract_wifi_direct_peer_hint.py \
  tests/test_android_policy_guards.py
```

Suggested subject: `Harden directed Wi-Fi Direct evidence harness`

## Slice 3: Crypto/profile admission tests

Назначение: закрепить, что слабый, ограниченный по размеру/сложности или mismatched profile не становится message-capable, а receipt/retry сохраняют metadata.

```bash
git add -- \
  app-android/app/src/test/java/com/disser/kraken/crypto/CryptoPlanDocTest.kt \
  app-android/app/src/test/java/com/disser/kraken/mesh/MeshDeliveryPipelineTest.kt \
  app-android/app/src/test/java/com/disser/kraken/mesh/MeshServiceTest.kt
```

Suggested subject: `Cover crypto profile admission across retry paths`

## Slice 4: Terminology and runbook docs

Назначение: русская терминология для контура допуска, next phone-run runbook и локальный audit boundary.

```bash
git add -- \
  docs/adamova-admission-gate-architecture.md \
  docs/adamova-product-crypto-integration-consultation.md \
  docs/branch-tree-cleanup-audit.md \
  docs/kraken-attack-scenarios-evidence.md \
  docs/kraken-crypto-implementation-plan.md \
  docs/kraken-native-core-boundary.md \
  docs/kraken-production-readiness-roadmap.md \
  docs/mesh-trust-gating-audit.md \
  docs/phase-acceptance-checklist.md \
  docs/research-notes-index.md \
  docs/wifi-direct-next-phone-runbook.md \
  reports/out/adamova_admission_gate_attack_demo.md \
  reports/out/adamova_effectiveness_dissertation_table.md \
  reports/out/dissertation_final_insert_packet_2026-06-08.md \
  reports/out/non_phone_transport_crypto_audit_2026-06-14.md \
  reports/out/non_phone_transport_crypto_commit_slices_2026-06-14.md
```

Suggested subject: `Refresh transport crypto wording and phone runbook`

## Do not stage by default

Оставить вне этих slices без отдельного решения:

- `app-macos/`
- `artifacts/desktop-relay-preflight/20260613-214443/`
- `artifacts/macos-lan-listen-probe/`
- `artifacts/phone-audit/`
- `artifacts/ui-device-verification/`
- `artifacts/ui-ux-audit-samsung/`
- `artifacts/ui-ux-telegram-reference-samsung/`
- `handoff.md`
