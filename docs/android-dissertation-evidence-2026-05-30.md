# Android Dissertation Evidence 2026-05-30

Current-status note, 2026-06-06: this file is a historical May 30 checkpoint.
For current manual Samsung/Xiaomi two-phone LAN/Wi-Fi prototype evidence, use
`reports/out/two_device_delivery_evidence.md`. The May 30 `pending` wording
below must not be treated as the current project status.

## Summary

Kraken Android is ready to be used as a dissertation research prototype
demonstration, with a clear boundary: it is not a production secure messenger.

Current Android branch baseline before this report refresh:

```text
codex/android-research-panel-report-viewer
f4a03cf Merge contacts into chat navigation
```

## Implemented Prototype Capabilities

- local identity;
- real invite QR rendering;
- camera QR scanner;
- QR lifecycle controls;
- offline mutual QR handshake;
- invite QR import creates pending relationship, not `ACTIVE`;
- `ACTIVE` relationship requires reciprocal QR flow;
- unified `Чаты` screen for active contacts, pending QR handshakes and inactive relationships;
- local chat composer gated by `ACTIVE`;
- local realms UX;
- Mesh diagnostics UI;
- Direct LAN NSD + TCP implementation;
- packet envelope, outbox/inbox/seen/receipt stores;
- queue/retry and receipt processing;
- Research Panel with bundled math reports and large-coefficient examples;
- controlled research attack display;
- Kotlin vs C++ diagnostic benchmark display.

## Current Evidence

### Automated

- `./gradlew test`: passed;
- `./gradlew assembleDebug`: passed;
- `./gradlew installDebug`: passed on Samsung `SM-S938B / R5CY22X6MSB`;
- `git diff --check`: passed;
- loopback/two-node delivery tests: passed;
- receipt path tests: passed;
- queue/retry tests: passed;
- unknown/pending/blocked/wrong-recipient rejection tests: passed;
- simulated relay tests: passed with relay disabled by default.

### Device screenshots

Curated screenshots:

```text
reports/out/dissertation_screenshots_2026-05-30/
```

The screenshot pack includes:

- start screen;
- overview;
- unified chats/contacts;
- settings and prototype boundary;
- mesh diagnostics;
- research attack and validation;
- Kotlin vs C++ benchmark result;
- My QR;
- realms.

### P2P / Mesh

May 30 status:

- prototype mesh delivery is validated by automated loopback/two-node tests;
- real Direct LAN NSD + TCP implementation builds and is exposed through Mesh diagnostics;
- real two-device LAN smoke was not marked complete at this checkpoint.

May 30 device availability:

```text
Visible ADB devices: 1
Device: Samsung SM-S938B / R5CY22X6MSB
```

Therefore, at the May 30 checkpoint, two-device LAN evidence remained a pending
gate, not a claimed result. Current manual two-phone LAN/Wi-Fi prototype
evidence is now tracked in `reports/out/two_device_delivery_evidence.md`.

## Dissertation Use

Use the Android app as:

- research prototype UI;
- demonstration of local/offline-first identity;
- demonstration of QR-established trust;
- demonstration of local message/realm workflow;
- display layer for mathematical evidence;
- prototype P2P/mesh implementation with automated simulation evidence.

Do not use it as:

- proof of production cryptographic security;
- proof of completed audited E2EE;
- proof of production-grade or repeatable automated two-device delivery
  reliability;
- proof that rational diagnostics over `Q` secure production finite-field ECC.

## Suggested Figure Captions

1. **Kraken branded start screen.** The prototype starts with local/offline/privacy positioning without account or server onboarding.
2. **Messenger-first overview.** The overview shows local identity, active contacts, pending QR handshakes, realms and primary QR actions.
3. **Unified chats and contacts.** Active contacts, pending QR handshakes and inactive relationships are shown in one messenger-first screen.
4. **Prototype settings boundary.** Settings expose local privacy, mesh diagnostics and Research mode while stating the prototype boundary.
5. **Mesh diagnostics.** LAN endpoint and peer discovery are diagnostic transport hints only; they do not establish trust.
6. **Research Panel.** Offline mathematical reports and controlled research attack evidence are displayed without running Python inside Android.
7. **Large-coefficient and benchmark evidence.** The Research Panel shows SageMath-validated large coefficient examples and diagnostic benchmark entry points.
8. **Kotlin vs C++ benchmark result.** Native C++ diagnostic backend is compared with Kotlin BigInteger for research diagnostics, not production encryption.
9. **One-time invite QR.** The QR creates an invite/pending relationship; activation requires reciprocal QR handshake.
10. **Local realms.** Realms are local invite-only governance records with technical identifiers hidden from the primary UI.

## Safe Wording

Use:

> Android-прототип Kraken демонстрирует local identity, QR-first trust
> establishment, local chat/realm UX, Research Panel и prototype mesh transport
> pipeline. Автоматические тесты подтверждают loopback/two-node delivery,
> receipt path, queue/retry и trust-gating; manual two-phone LAN/Wi-Fi prototype
> smoke evidence существует, но repeatable two-device automation, latency
> metrics и production crypto остаются за пределами текущего claim.

Avoid:

> Kraken уже является production secure P2P messenger.

## Related Files

- `reports/out/p2p_research_prototype_readiness_2026-05-30.md`
- `reports/out/mesh_delivery_simulation.md`
- `reports/out/android_p2p_smoke_report.md`
- `reports/out/two_device_delivery_evidence.md`
- `reports/out/mesh_metrics_summary.json`
- `reports/out/dissertation_screenshots_2026-05-30/README.md`
- `reports/out/dissertation_insert_prompt_ru_2026-05-30.md`
- `docs/prototype-mesh-threat-boundaries.md`
- `docs/mesh-trust-gating-audit.md`
- `protocol-spec/crypto-envelope.md`
- `docs/android-keystore-migration-plan.md`
