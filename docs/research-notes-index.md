# Research Notes Index

This file records what was transferred from the local dissertation folder and
what should be consulted before implementation.

## Local Dissertation Folder

```text
/Users/cheenya/Documents/Диссертация
```

Important notes there:

- `00_ПРОТОКОЛ_НАВИГАЦИИ.md`
- `01_АУДИТ_БАЗИСА.md`
- `02_ОЦЕНКА_УДАЛЕНИЯ_КУРСОВОЙ_И_ПЕРВОЙ_НИР.md`

## Active Source Pool

Keep active:

- [`adamova-admission-gate-architecture.md`](adamova-admission-gate-architecture.md)
  - source-of-truth архитектура C++ контура допуска экспериментального
    криптографического профиля на основе диагностики кручения рациональных
    кривых, включая claim boundary, packet/session policy, attack demo и
    dissertation metrics.
- [`../reports/out/adamova_admission_gate_attack_demo.md`](../reports/out/adamova_admission_gate_attack_demo.md)
  - текущий implementation evidence report по unit-backed attack scenarios:
    weak profile rejection, packet profile mismatch и claim boundary.
- [`../reports/out/adamova_effectiveness_experiment.md`](../reports/out/adamova_effectiveness_experiment.md)
  - воспроизводимый controlled profile-substitution experiment: corpus,
    injected weak profiles, accepted controls, malformed/size-guarded,
    downgrade/mismatch, baselines `no_precheck`/`discriminant_only` и
    политика допуска по результату диагностики кручения.
- [`../reports/out/adamova_effectiveness_dissertation_table.md`](../reports/out/adamova_effectiveness_dissertation_table.md)
  - краткая таблица для вставки в диссертацию по эффективности диагностики
    кручения рациональных кривых в контуре допуска профиля.
- [`../reports/out/adamova_effectiveness_completion_audit.md`](../reports/out/adamova_effectiveness_completion_audit.md)
  - completion-аудит evidence pack: отчёты, C++ backend, corpus coverage,
    Sage/reference controls и live Android evidence.
- [`../reports/out/adamova_effectiveness_result_handoff.md`](../reports/out/adamova_effectiveness_result_handoff.md)
  - итоговый handoff по результатам проверки эффективности контура допуска:
    где лежат артефакты, какие метрики использовать в диссертации и как резать
    коммит.
- [`adamova-product-crypto-integration-consultation.md`](adamova-product-crypto-integration-consultation.md)
  - консультационный документ по C++ диагностике кручения рациональных кривых и
    вариантам интеграции в продуктовую криптографическую политику Kraken.
- [`kraken-attack-scenarios-evidence.md`](kraken-attack-scenarios-evidence.md)
  - текущая матрица defensive attack scenarios для QR trust, LAN/BLE, relay,
    packet validation, route-aware model и boundary исследовательского контура
    допуска профиля.
- [`../reports/out/current_project_readiness_2026-06-06.md`](../reports/out/current_project_readiness_2026-06-06.md)
  - historical June 6 readiness checkpoint; superseded by the June 8 current
    readiness entrypoint below.
- [`../reports/out/current_project_readiness_2026-06-07.md`](../reports/out/current_project_readiness_2026-06-07.md)
  - historical June 7 readiness snapshot; superseded by the June 8 source of
    truth below.
- [`../reports/out/kraken_10_10_readiness_plan_2026-06-07.md`](../reports/out/kraken_10_10_readiness_plan_2026-06-07.md)
  - historical June 7 plan; current blockers are tracked in the June 8 gap audit.
- [`../reports/out/current_project_readiness_2026-06-08.md`](../reports/out/current_project_readiness_2026-06-08.md)
  - базовый 2026-06-08 readiness-срез: 8/10 dissertation research prototype,
    7/10 route/attack evidence, production secure messenger not scored.
- [`../reports/out/kraken_10_10_gap_audit_2026-06-08.md`](../reports/out/kraken_10_10_gap_audit_2026-06-08.md)
  - базовый список blockers к честному `10/10`: Wi-Fi Direct, Mac inline MITM,
    physical hostile injection и reliability benchmark.
- [`../reports/out/kraken_10_10_followup_audit_2026-06-10.md`](../reports/out/kraken_10_10_followup_audit_2026-06-10.md)
  - актуальный follow-up слой: release hard gate, route benchmark runner,
    physical inline relay runner and single-device partial evidence; still not
    `10/10`.
- [`../reports/out/kraken_10_10_current_blocker_audit_2026-06-11.md`](../reports/out/kraken_10_10_current_blocker_audit_2026-06-11.md)
  - текущий blocker-audit после friend-test package: lint policy закрыта,
    Samsung install/launch прошёл, Samsung and Xiaomi are now both visible, but
    Wi-Fi Direct, physical attack and benchmark gates still prevent any honest
    `10/10` claim.
- [`../reports/out/production_secure_messenger_scope_2026-06-12.md`](../reports/out/production_secure_messenger_scope_2026-06-12.md)
  - scope note по вопросу production secure messenger со своим алгоритмом:
    production security является отдельным roadmap, а Adamova остаётся
    experimental admission/policy layer до formal crypto review и external
    security audit.
- [`../reports/out/kraken_10_10_live_audit_2026-06-11.md`](../reports/out/kraken_10_10_live_audit_2026-06-11.md)
  - machine-generated current-state audit from `scripts/audit_current_10_10_state.py`;
    fails closure while fewer than two devices are visible or open physical
    route/attack/benchmark gates remain.
- [`../reports/out/kraken_10_10_two_device_closure_attempt_2026-06-11.md`](../reports/out/kraken_10_10_two_device_closure_attempt_2026-06-11.md)
  - актуальный двухтелефонный closure attempt: Samsung and Xiaomi are both
    visible, Wi-Fi Direct service is active, but no bidirectional
    message-capable Wi-Fi Direct path is proven; route benchmark has zero
    delivered latency samples; physical Mac relay captured frames but normal
    forwarding to Xiaomi failed.
  - network boundary to preserve: LAN route assumes the same local network;
    different nearby networks require BLE/Wi-Fi Direct, and Wi-Fi Direct is
    still the principal open route blocker.
- [`../scripts/run_10_10_closure_suite.sh`](../scripts/run_10_10_closure_suite.sh)
  - one-command harness for the remaining physical gates when Samsung, Xiaomi
    and a reachable Mac LAN relay are available: bidirectional Wi-Fi Direct
    capture, route benchmark and physical inline relay modes.
- [`../scripts/audit_10_10_closure_suite_output.py`](../scripts/audit_10_10_closure_suite_output.py)
  - machine verifier for a generated closure-suite output directory; requires
    bidirectional Wi-Fi Direct delivery, benchmark sample gate success and
    physical inline relay evidence for `normal/drop/duplicate/tamper`.
- [`wifi-direct-next-phone-runbook.md`](wifi-direct-next-phone-runbook.md)
  - следующий физический Wi-Fi Direct runbook: build/install/grants,
    bidirectional directed runs, hint mode and manifest decision tree.
- [`../reports/out/non_phone_transport_crypto_audit_2026-06-14.md`](../reports/out/non_phone_transport_crypto_audit_2026-06-14.md)
  - локальный non-phone audit: что доказано transport/crypto тестами, что не
    доказано без телефонов, какие manifest поля решают следующий шаг.
- [`../reports/out/non_phone_transport_crypto_commit_slices_2026-06-14.md`](../reports/out/non_phone_transport_crypto_commit_slices_2026-06-14.md)
  - scoped commit-slice карта для transport diagnostics, directed harness,
    crypto/profile tests и terminology/runbook docs; commit не выполнен.
- [`../reports/out/kraken_release_package_2026-06-11.md`](../reports/out/kraken_release_package_2026-06-11.md)
  - актуальный `Kraken_release` для friend testing на `05ba28d`; debug
    research demo APK, не production secure messenger release.
- [`../reports/out/kraken_10_10_completion_matrix_2026-06-10.md`](../reports/out/kraken_10_10_completion_matrix_2026-06-10.md)
  - completion-control матрица по open/closed gates before any `10/10` claim;
    use the 2026-06-11 blocker audit, not this older matrix, for current
    ADB device-state.
- [`../reports/out/kraken_10_10_completion_matrix_audit_2026-06-10.md`](../reports/out/kraken_10_10_completion_matrix_audit_2026-06-10.md)
  - machine-checkable audit of the completion matrix references, statuses and
    entrypoints; expected result is `complete=false` while open gates remain.
- [`../reports/out/two_device_route_specific_smoke_2026-06-08.md`](../reports/out/two_device_route_specific_smoke_2026-06-08.md)
  - свежий tracked summary по route-specific LAN/BLE smoke и debug rejection
    counters; не является reliability benchmark.
- [`../reports/out/route_evidence_consistency_audit_2026-06-08.md`](../reports/out/route_evidence_consistency_audit_2026-06-08.md)
  - validator/report для stale raw JSON-vs-markdown summaries.
- [`../reports/out/two_device_delivery_evidence.md`](../reports/out/two_device_delivery_evidence.md)
  - актуальный tracked summary по ручному Samsung/Xiaomi two-phone LAN/Wi-Fi
    prototype smoke; supersedes older May 30 `pending` wording.
- [`../reports/out/ble_two_device_delivery_evidence_2026-06-06.md`](../reports/out/ble_two_device_delivery_evidence_2026-06-06.md)
  - актуальный tracked summary по ручному Samsung/Xiaomi BLE direct-route
    evidence; не доказывает BLE reliability/latency или production security.
- [`../scripts/capture_two_phone_smoke_evidence.sh`](../scripts/capture_two_phone_smoke_evidence.sh)
  - repeatable ADB capture helper для свежего two-phone smoke: APK hash, git
    state, device IDs, screenshots, UI XML и manifest; latest usable local run:
    `artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/`.
  - June 7 connected-phone UI evidence:
    `artifacts/two-phone-test/repeatable-20260607-201329-fresh-20260607-open-chat/`;
    both phones show the Bluetooth-direct chat for the current APK hash, but it
    is not automated fresh-send/counter evidence.
- [`branch-tree-cleanup-audit.md`](branch-tree-cleanup-audit.md)
  - рабочая карта веток, stash, raw artifacts и правил cleanup.
- [`../reports/out/branch_tree_cleanup_verification_2026-06-07.md`](../reports/out/branch_tree_cleanup_verification_2026-06-07.md)
  - read-only verification report для branch/tree cleanup audit: worktrees,
    branch ancestry, remote cleanup candidates, stash inventory and
    approval-only boundaries.
- [`../reports/out/cleanup_action_plan_2026-06-07.md`](../reports/out/cleanup_action_plan_2026-06-07.md)
  - consolidated approval-only cleanup plan для local branch deletion, remote
    branch deletion, stash drop и raw/local evidence retention review.
- [`../reports/out/dirty_tree_commit_slices_2026-06-07.md`](../reports/out/dirty_tree_commit_slices_2026-06-07.md)
  - read-only map of every current modified/untracked Android and linked math
    worktree file to an intended commit slice; used to avoid `git add .`.
- [`../reports/out/commit_slicing_handoff_2026-06-07.md`](../reports/out/commit_slicing_handoff_2026-06-07.md)
  - exact staged-commit handoff with per-slice `git add -- ...`,
    `diff --cached --stat` and suggested commit subjects; no commit/push was
    executed when that handoff snapshot was generated.
- [`../reports/out/cleanup_execution_status_2026-06-07.md`](../reports/out/cleanup_execution_status_2026-06-07.md)
  - post-approval execution report: commit slices, pushes, local branch deletion,
    remote stale branch deletion, stash drops and raw-evidence retention
    boundary.
- [`../reports/out/non_phone_cleanup_status_2026-06-07.md`](../reports/out/non_phone_cleanup_status_2026-06-07.md)
  - current closure report for cleanup work that does not require connected
    phones; separates completed read-only work from approval-only and
    phone-dependent remainder.
- [`../reports/out/non_phone_completion_audit_2026-06-07.md`](../reports/out/non_phone_completion_audit_2026-06-07.md)
  - cross-tree completion audit for non-phone work across Android, linked
    math/evidence and local dissertation folders.
- [`../reports/out/phone_evidence_plan_2026-06-07.md`](../reports/out/phone_evidence_plan_2026-06-07.md)
  - separate plan for fresh connected-phone work; 2026-06-07 execution captured
    ADB/open-chat UI evidence, while orchestrated LAN/Wi-Fi smoke, BLE
    direct-route smoke, route counters and visual QA remain route-specific work.
- [`../reports/out/raw_evidence_retention_manifest_2026-06-06.md`](../reports/out/raw_evidence_retention_manifest_2026-06-06.md)
  - manifest для ignored/local evidence и generated APK output: размеры,
    Git-классификация, disposition и approval-only deletion boundary.
- [`../scripts/audit_raw_evidence_retention.py`](../scripts/audit_raw_evidence_retention.py)
  - воспроизводимый генератор raw evidence retention manifest; не удаляет
    файлы и не выполняет cleanup.
- [`Статья Чистяков v2.pdf`](../sources/pdfs/%D0%A1%D1%82%D0%B0%D1%82%D1%8C%D1%8F%20%D0%A7%D0%B8%D1%81%D1%82%D1%8F%D0%BA%D0%BE%D0%B2%20v2.pdf)
- [`НИР_предзащита_Чистяков Н.А.pdf`](../sources/pdfs/%D0%9D%D0%98%D0%A0_%D0%BF%D1%80%D0%B5%D0%B4%D0%B7%D0%B0%D1%89%D0%B8%D1%82%D0%B0_%D0%A7%D0%B8%D1%81%D1%82%D1%8F%D0%BA%D0%BE%D0%B2%20%D0%9D.%D0%90.pdf)
- [`НИР_4 семестр_Чистяков Н.А.pdf`](../sources/pdfs/%D0%9D%D0%98%D0%A0_4%20%D1%81%D0%B5%D0%BC%D0%B5%D1%81%D1%82%D1%80_%D0%A7%D0%B8%D1%81%D1%82%D1%8F%D0%BA%D0%BE%D0%B2%20%D0%9D.%D0%90.pdf)
- [`НИР_лето. Чистяков Н.А.pdf`](../sources/pdfs/%D0%9D%D0%98%D0%A0_%D0%BB%D0%B5%D1%82%D0%BE.%20%D0%A7%D0%B8%D1%81%D1%82%D1%8F%D0%BA%D0%BE%D0%B2%20%D0%9D.%D0%90.pdf)
- `Материал для курсовой_2.pdf` - local theory reference in
  `/Users/cheenya/Documents/Диссертация`; not copied into this public repository.

Excluded from active pool:

- [`Курсовая Чистяков Н.А.pdf`](../sources/pdfs/archive/%D0%9A%D1%83%D1%80%D1%81%D0%BE%D0%B2%D0%B0%D1%8F%20%D0%A7%D0%B8%D1%81%D1%82%D1%8F%D0%BA%D0%BE%D0%B2%20%D0%9D.%D0%90.pdf)
- [`НИР Чистяков Н.А..pdf`](../sources/pdfs/archive/%D0%9D%D0%98%D0%A0%20%D0%A7%D0%B8%D1%81%D1%82%D1%8F%D0%BA%D0%BE%D0%B2%20%D0%9D.%D0%90..pdf)
- [`Бета.pdf`](../sources/pdfs/archive/%D0%91%D0%B5%D1%82%D0%B0.pdf)

Reason: these files duplicate later work, contain basic theory issues, or are
draft/provenance material rather than the current implementation baseline. The
local audit file documents the details.

External references not redistributed here:

- `Материал для курсовой.pdf`
- `Материал для курсовой_2.pdf`

## Theory Corrections To Preserve

1. Mazur's theorem over `Q` allows `Z/nZ` for `n = 1,...,10,12`, not `n=11`.
2. Full `E(R)_tors` is not just points with `y=0`; those are only 2-torsion.
3. `y^2 = x^3 - x` has torsion `Z/2Z x Z/2Z`, not `Z/2Z x Z/8Z`.
4. The rational-curve torsion work and finite-field ECDH work must be connected
   carefully. The ECDH branch is a motivation and metrics scenario, not a direct
   theorem-level consequence of the rational torsion pipeline.

## Open Implementation Questions

1. Which language/runtime will the messenger use?
2. Should the first prototype be CLI-only, desktop, mobile, or web?
3. Which production-safe key agreement primitive should be used in safe mode?
4. How should research-mode results be displayed?
5. Where should SageMath fixtures be generated and stored?
6. Should benchmark artifacts be committed, generated on demand, or stored as
   release artifacts?
