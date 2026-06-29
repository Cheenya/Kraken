# Adamova Effectiveness Result Handoff

Date: 2026-06-06.
Branch: `codex/android-research-panel-report-viewer`.
Git SHA at experiment generation: `e542ea0`.

## Result Status

The Adamova effectiveness evidence pack is complete in the current local
workspace. The completion audit reports `complete=true`.

Primary audit:

- `reports/out/adamova_effectiveness_completion_audit.md`
- `reports/out/adamova_effectiveness_completion_audit.json`

Raw Android live evidence is intentionally local-only under:

- `artifacts/android-adamova-live/20260606-185510/`

That directory is ignored by git because it contains device dumps and screenshots.
The portable dissertation-facing result is recorded in the reports below.

## Desktop / Report Experiment

Artifacts:

- `scripts/adamova_effectiveness_experiment.py`
- `reports/out/adamova_effectiveness_experiment.md`
- `reports/out/adamova_effectiveness_experiment.json`
- `reports/out/adamova_effectiveness_experiment.csv`
- `reports/out/adamova_effectiveness_dissertation_table.md`

Backend:

- `host_cpp_native_core_cli_from_android_source`
- Native CLI path: `build/adamova-host/adamova_native_cli`
- The CLI is compiled from `app-android/app/src/main/cpp/kraken_native_placeholder.cpp`
  using `KRAKEN_ADAMOVA_CLI`.
- The native CLI path is generated on demand under root `build/`. That
  directory is disposable build output and may be removed after report
  generation; the source of truth is the C++ source, script and generated
  reports.

Corpus:

- total profiles: 20;
- weak/invalid profiles: 10;
- accepted controls: 10;
- constructed/injected cases: 10;
- Sage-labeled fixture controls: 3;
- deterministic generated controls: 7.

Key metrics:

| Metric | Value |
| --- | ---: |
| Weak profiles accepted without precheck | 8 / 10 |
| Weak profiles accepted by discriminant-only | 6 / 10 |
| Weak profiles accepted by Adamova C++ gate | 0 / 10 |
| Rejected/blocked by Adamova C++ gate | 10 / 10 |
| Needs reference validation | 2 |
| Size-guarded | 1 |

## Live Android Evidence

Capture directory:

- `artifacts/android-adamova-live/20260606-185510/`

Captured files:

- `research_mode_screen.png`;
- `admission_gate_attack_demo_latest.json`;
- `admission_gate_attack_demo_latest.md`;
- `live_evidence_manifest.json`;
- `live_evidence_summary.md`;
- ADB/device/package/focus diagnostic text files.

Device:

- Samsung `SM-S938B`;
- serial `R5CY22X6MSB`;
- package `com.disser.kraken`.

Android Research Mode metrics:

| Metric | Value |
| --- | ---: |
| profiles_total | 7 |
| weak_profiles_total | 7 |
| accepted_without_precheck | 6 |
| accepted_by_discriminant_only | 5 |
| accepted_by_adamova_gate | 0 |
| rejected_by_adamova_gate | 7 |
| needs_reference_validation | 1 |
| size_guarded | 1 |
| native_unavailable | 0 |
| median_gate_latency_ms | 0.326406 |
| p95_gate_latency_ms | 0.362969 |

## Dissertation Thesis

In the controlled experimental-profile substitution model, Adamova C++ admission
gate reduced accepted weak/invalid profiles from `8/10` without precheck and
`6/10` with discriminant-only precheck to `0/10`, while routing size/unsupported
cases to reference validation before session or message use.

Safe Russian wording:

> В контролируемой модели подмены параметров экспериментального
> криптографического профиля Adamova C++ admission gate снижает число
> автоматически принятых слабых/некорректных профилей до нуля и переводит
> unsupported/size-guarded случаи в режим эталонной проверки до создания
> сессии и отправки сообщений.

Boundary:

- this is controlled evidence for profile admission policy;
- this is not a proof of production message cryptographic security;
- this does not replace reviewed primitives, packet signatures, encryption,
  Android Keystore, or external security review.

## Validation Snapshot

Last checks run:

- `/Users/cheenya/Projects/disser-messenger-project/.venv/bin/python -m pytest` -> `52 passed`;
- `python3 -m compileall -q scripts tests src` -> passed;
- `git diff --check` -> passed;
- earlier Android checks after C++ CLI entrypoint:
  - `./gradlew test` -> passed;
  - `./gradlew assembleDebug` -> passed;
  - `adb install -r app-android/app/build/outputs/apk/debug/app-debug.apk` -> success on Samsung.

## Tree Triage

Recommended commit slice:

1. Adamova C++ host CLI support:
   - `app-android/app/src/main/cpp/kraken_native_placeholder.cpp`.
2. Adamova effectiveness evidence scripts/tests/reports:
   - `scripts/adamova_effectiveness_experiment.py`;
   - `scripts/audit_adamova_effectiveness_evidence.py`;
   - `scripts/capture_adamova_android_evidence.sh`;
   - `tests/test_adamova_effectiveness_experiment.py`;
   - `reports/out/adamova_effectiveness_*`.
3. Documentation/index updates:
   - `docs/research-notes-index.md`;
   - `reports/out/adamova_admission_gate_dissertation_evidence_plan.md`;
   - this handoff file.
4. Guard/test maintenance:
   - `tests/test_android_policy_guards.py`;
   - `.gitignore`.

Do not commit raw local screenshot/device bundles unless explicitly needed.

## Next Actions

1. Commit the Adamova effectiveness evidence pack as a clean changeset.
2. Push the branch after reviewing the commit diff.
3. Update dissertation prompt/materials with:
   - the desktop experiment table;
   - Android Research Mode metrics;
   - the claim boundary.
4. Decide whether to add a curated screenshot from the live bundle into a
   tracked dissertation screenshot pack, separate from raw ADB artifacts.
5. Continue product work separately: production crypto roadmap, real security
   envelope, Keystore, signed profile binding, and P2P/BLE stability.
