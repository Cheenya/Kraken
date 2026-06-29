# Prompt for dissertation writing chat

Скопируй этот prompt в соседний чат, где пишется диссертация.

Current-status note, 2026-06-06: prompt updated after manual Samsung/Xiaomi
two-phone LAN/Wi-Fi prototype smoke evidence was captured. It must still not be
used to claim production security, audited E2EE, repeatable delivery metrics or
production-ready messenger status.

```text
Ты помогаешь срочно вставить в диссертацию актуальные материалы проекта Kraken.

Контекст:
- Проект: Kraken dissertation research prototype.
- Не писать, что это production secure messenger.
- Не писать, что реализована production encryption, audited E2EE, production-ready delivery reliability или repeatable two-device P2P/mesh metrics.
- Писать честно: это research prototype + reproducible evidence pipeline.

Актуальные ветки и commits:
- Math/evidence branch: codex/math-experiment-evidence-pack, commit 1b3baa5 Prepare dissertation readiness packet.
- Android branch: codex/android-research-panel-report-viewer, current evidence commit e542ea0 Refresh Kraken project readiness evidence.
- Historical May 30 Android baseline before later evidence refresh: f4a03cf Merge contacts into chat navigation.

Что уже можно использовать в диссертации:
1. Math-core diagnostics над Q:
   - short Weierstrass curves y^2 = x^3 + ax + b;
   - exact rational arithmetic;
   - discriminant/singularity/j-invariant;
   - rational 2-torsion diagnostics;
   - Lutz-Nagell-oriented candidate diagnostics;
   - bounded torsion probing;
   - Android-compatible JSON reports.
2. SageMath reproducibility:
   - main corpus: 15 curves;
   - Sage direct matches: 11;
   - unsupported_local: 4;
   - mismatches: 0;
   - SageMath version: 10.8.
3. Large-coefficient corpus:
   - 20 curves;
   - 20 SageMath direct matches;
   - 0 mismatches;
   - benchmark 5 runs;
   - median total runtime 22.8673 ms;
   - p95 total runtime 24.1665 ms.
4. Random/injected risk simulation:
   - seed 20260524;
   - corpus 90 curves;
   - no_precheck false accepts: 50/50;
   - discriminant_only false accepts: 40/50;
   - kraken_precheck false accepts: 0/50;
   - kraken_precheck needs_reference_validation: 10.
5. Android research prototype:
   - local identity;
   - real invite QR;
   - camera QR scanner;
   - QR lifecycle controls;
   - offline mutual QR handshake;
   - invite QR import does not create ACTIVE directly;
   - unified Chats/Contacts screen;
   - local realms UX;
   - Mesh diagnostics UI;
   - Research Panel with offline reports;
   - controlled research attack/log display;
   - Kotlin vs C++ diagnostic benchmark display.
6. P2P/mesh current state:
   - packet envelope exists;
   - packet stores/limits/seen cache exist;
   - loopback/two-node delivery tests pass;
   - receipt generation and sender delivered-state update are tested;
   - queue/retry behavior is tested;
   - unknown/pending/blocked/wrong-recipient rejection is tested;
   - Direct LAN NSD + TCP implementation exists and builds;
   - manual Samsung/Xiaomi two-phone LAN/Wi-Fi prototype smoke evidence exists in `reports/out/two_device_delivery_evidence.md`;
   - manual Samsung/Xiaomi BLE direct-route evidence exists in `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`;
   - repeatable automated route-specific harness, latency metrics, BLE rejection/counter evidence and production reliability evidence are still missing;
   - production crypto/Keystore/signed QR proofs are roadmap, not implemented.

Key artifacts:
- docs/current-project-summary.md
- reports/out/supervisor_packet/dissertation_data_readiness_2026-05-30.md
- reports/out/supervisor_packet/one_page_summary_ru.md
- docs/drafts/dissertation-results-chapter-draft.md
- reports/out/sage_reproducibility_pack/README.md
- reports/out/random_risk_simulation.md
- reports/out/large_coefficient_curve_evidence.md
- docs/article-text-formulation-audit.md
- Android branch reports/out/p2p_research_prototype_readiness_2026-05-30.md
- Android branch reports/out/mesh_delivery_simulation.md
- Android branch reports/out/two_device_delivery_evidence.md
- Android branch reports/out/ble_two_device_delivery_evidence_2026-06-06.md
- Android branch reports/out/dissertation_screenshots_2026-05-30/README.md

Screenshots to reference:
- 01_start_screen_after_reinstall.png: branded start screen.
- 02_overview.png: overview with local identity and QR actions.
- 03_chats_contacts_merged.png: unified Chats/Contacts screen.
- 04_settings.png: settings and prototype boundary.
- 05_mesh_status.png: mesh diagnostics and local LAN endpoint.
- 06_research_panel_top.png: Research Panel and controlled research attack.
- 07_research_benchmark_examples.png: benchmark entry and large-coefficient corpus.
- 08_research_benchmark_result.png: Kotlin vs C++ diagnostic benchmark result.
- 09_my_qr.png: one-time invite QR and handshake requirement.
- 10_realms.png: local realms screen.

Задача:
1. Подготовь раздел диссертации “Android-прототип Kraken как исследовательский стенд”.
2. Подготовь подраздел “P2P/mesh transport: реализованный prototype layer и manual two-phone LAN/Wi-Fi evidence”.
3. Подготовь подписи к рисункам для всех screenshots.
4. Подготовь аккуратную формулировку ограничений:
   - no production encryption;
   - no audited secure messenger claim;
   - LAN discovery is not trust;
   - ACTIVE contact only after QR/offline handshake;
   - manual two-phone LAN/Wi-Fi evidence is prototype smoke, not production reliability proof;
   - rational diagnostics over Q are not production finite-field ECC.
5. Подготовь 1-2 абзаца вывода: приложения уже достаточно для демонстрационного research prototype и материалов диссертации, но не для заявления о production-ready messenger.

Тон:
- научный;
- осторожный;
- без рекламных claims;
- без “мы доказали криптостойкость”;
- с упором на воспроизводимость, evidence, traceability и честные limitations.
```
