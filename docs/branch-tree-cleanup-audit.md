# Branch And Tree Cleanup Audit

## 2026-06-15 UI/UX Recovery And Target Branch Model

Scope revalidated after the UI navigation regression was found.

Current Android worktree is still dirty and contains technical work that must
not be reverted wholesale:

- protected message payload path and crypto attack tests;
- Wi-Fi Direct diagnostics and honest negative directed-run evidence;
- LAN/BLE/hotspot-compatible transport profile changes;
- realm relay policy/runtime/evidence/simulation changes;
- UI/UX navigation recovery described below.

### UI/UX Baseline Now Protected

The messenger navigation baseline is:

- bottom navigation: `Чаты`, `Контакты`, `Реалмы`, `Настройки`;
- `Главная` must not appear in bottom navigation;
- `Home` remains an internal overview/entry route labelled `Обзор`;
- `Контакты` owns QR pairing actions; `Мой QR` and `Скан QR` must not return
  as separate bottom-navigation items;
- `Реалмы` is the accepted Russian label, not `Рилмы`;
- research, evidence and diagnostics screens stay available, but they are not
  primary messenger tabs.

Guard rails updated in code:

- `KrakenRoute.bottomRoutes == listOf(Chat, Contacts, Realms, Settings)`;
- `KrakenRoute.Home` is not a bottom route;
- `MainUiCopyGuardTest` rejects `Главная` in the branded entry copy and requires
  `Контакты`, `Реалмы`, `ОБЗОР` and `M E S S E N G E R`.

Historical source for this baseline:

- `7c32a31 Restore contacts as primary navigation` had the correct
  messenger-first bottom navigation;
- later commits `23135f2`, `f4a03cf` and `59eccb7` reintroduced `Home` into
  bottom navigation, removed `Contacts` from bottom navigation and then renamed
  `Home` back to `Главная`;
- `codex/ui-ux-messenger-baseline` was created after those changes and points
  at the same tip as the Android branch, so it is not a safe preserved UI branch.

### Target Branch Model

The intended cleanup target is three active workstreams:

| Workstream | Intended branch/worktree | Current action |
| --- | --- | --- |
| Android Kraken | `codex/android-kraken` or current `codex/android-research-panel-report-viewer` renamed after cleanup | Keep current technical work; split into reviewed commit slices before any push. |
| macOS Kraken | separate `codex/macos-kraken` branch/worktree | `app-macos/` is currently untracked in this Android worktree; do not merge or delete until it is promoted into its own branch/worktree. |
| Dissertation/math evidence | `codex/math-experiment-evidence-pack` in `/Users/cheenya/Projects/disser-messenger-project` | Keep separate; do not merge wholesale into Android. |

Recommended cleanup sequence:

1. Android first: make one reviewed commit slice for UI/UX baseline recovery and
   guard tests, then separate slices for crypto, Wi-Fi Direct diagnostics,
   realm relay, and reports.
2. macOS second: move or promote `app-macos/` into a dedicated macOS branch or
   repository/worktree, then test independently.
3. Dissertation third: update dissertation-facing docs from verified Android
   evidence only; keep math/dissertation branch separate unless a specific doc
   needs to be copied.
4. Only after slices are reviewed, archive misleading branch names such as
   `codex/ui-ux-messenger-baseline` or repoint/recreate them deliberately.

No branch deletion, reset, push or deploy was done during this recovery.

Date: 2026-06-06. Revalidated: 2026-06-07.

Scope: local repository `/Users/cheenya/Projects/kraken-android-research-panel`.

## Current Branch

Current branch:

```text
codex/android-research-panel-report-viewer
```

Tracking:

```text
origin/codex/android-research-panel-report-viewer
```

Status at audit time:

```text
in sync with upstream
dirty worktree
```

Continuation note: the worktree also contains local cleanup, documentation,
test and evidence-report changes. This audit does not unstage, revert, commit
or push them.

Current local and upstream tip:

```text
e542ea0 Refresh Kraken project readiness evidence
```

Do not commit, push or drop local state without an explicit user command.

## Local Branch Inventory

Branch comparison was done against current `HEAD` with `git merge-base
--is-ancestor`, `git cherry -v HEAD <branch>` and `git diff --name-only
HEAD...<branch>`.

Revalidation on 2026-06-07 confirmed:

- active Android `HEAD`: `e542ea0`;
- active math/evidence `HEAD`: `1b3baa5`;
- `main`, `codex/android-identity-invite-relationship-realm`,
  `codex/android-skeleton` and `codex/protocol-spec-core-models` are ancestors
  of Android `HEAD`;
- `codex/dissertation-research-alignment`,
  `codex/math-core-curve-diagnostics`,
  `codex/math-core-reference-validation` and
  `codex/math-research-panel-integration-plan` are ancestors of
  `codex/math-experiment-evidence-pack`;
- `codex/qr-one-time-invite` still has one patch-unique commit,
  `79e9006 Add one-time QR invite lifecycle`, and should not be merged
  wholesale;
- `origin/chore/research-mvp-scaffold-v2` has no `git cherry -v HEAD` output
  against Android `HEAD`, so it remains the safest remote cleanup candidate;
- `origin/chore/research-mvp-scaffold` still has patch-unique legacy commits and
  remains a keep/manual-review remote branch.

Machine-readable/read-only revalidation output:

- `reports/out/branch_tree_cleanup_verification_2026-06-07.md`;
- `reports/out/branch_tree_cleanup_verification_2026-06-07.json`;
- generator: `scripts/audit_branch_tree_cleanup_state.py`.

| Branch | Evidence | Disposition |
| --- | --- | --- |
| `codex/android-research-panel-report-viewer` | Active branch; tracks `origin/codex/android-research-panel-report-viewer`; local tip matches upstream, with dirty worktree changes. | keep active |
| `main` | ancestor of current `HEAD`; 0 patch-unique commits; 0 unique files | keep as repository baseline |
| `codex/android-identity-invite-relationship-realm` | ancestor of current `HEAD`; 0 patch-unique commits; 0 unique files | archive candidate after user-approved branch cleanup |
| `codex/android-skeleton` | ancestor of current `HEAD`; 0 patch-unique commits; 0 unique files | archive candidate after user-approved branch cleanup |
| `codex/protocol-spec-core-models` | ancestor of current `HEAD`; 0 patch-unique commits; 0 unique files | archive candidate after user-approved branch cleanup |
| `codex/math-experiment-evidence-pack` | active math/evidence branch in worktree `/Users/cheenya/Projects/disser-messenger-project`; not expected to merge wholesale into Android branch | keep; manage from math worktree |
| `codex/dissertation-research-alignment` | not ancestor of Android `HEAD`, but ancestor of `codex/math-experiment-evidence-pack`; 0 patch-unique commits vs math evidence | archive candidate after confirming math worktree remains active |
| `codex/math-core-curve-diagnostics` | not ancestor of Android `HEAD`, but ancestor of `codex/math-experiment-evidence-pack`; 0 patch-unique commits vs math evidence | archive candidate after confirming math worktree remains active |
| `codex/math-core-reference-validation` | not ancestor of Android `HEAD`, but ancestor of `codex/math-experiment-evidence-pack`; 0 patch-unique commits vs math evidence | archive candidate after confirming math worktree remains active |
| `codex/math-research-panel-integration-plan` | not ancestor of Android `HEAD`, but ancestor of `codex/math-experiment-evidence-pack`; 0 patch-unique commits vs math evidence | archive candidate after confirming math worktree remains active |
| `codex/qr-one-time-invite` | 1 patch-unique commit and 9 unique files vs Android `HEAD`; focused review below shows current branch supersedes most behavior, and its useful consumed timestamp/peer metadata has been salvaged into the current model | archive candidate after user-approved branch cleanup; do not merge the branch wholesale |

Remote branches:

| Remote branch | Initial disposition |
| --- | --- |
| `origin/main` | keep |
| `origin/codex/android-research-panel-report-viewer` | active upstream |
| `origin/codex/math-experiment-evidence-pack` | active math upstream |
| `origin/chore/research-mvp-scaffold-v2` | ancestor of current `HEAD`; legacy remote base; revalidated with empty `git cherry -v HEAD origin/chore/research-mvp-scaffold-v2` output on 2026-06-07 |
| `origin/chore/research-mvp-scaffold` | not ancestor of Android or math evidence; 22 commits ahead of current Android `HEAD`, 13 patch-unique legacy commits after cherry-equivalence; also 22 commits ahead of active math evidence with the same legacy patch set | leave remote untouched unless remote cleanup is explicitly requested |

## Stash Inventory

| Stash | Meaning | Disposition |
| --- | --- | --- |
| `stash@{0}` | two phone QA artifacts before clean rebuild | indexed; keep until user approves stash drop or selected raw files are extracted |
| `stash@{1}` | local Xiaomi UI audit artifacts before clean build | indexed; keep until UI audit summary is promoted or user approves stash drop |

## Linked Math Worktree

The repository also has an active linked worktree:

```text
/Users/cheenya/Projects/disser-messenger-project
```

Current branch:

```text
codex/math-experiment-evidence-pack
```

Tracking:

```text
origin/codex/math-experiment-evidence-pack
```

Current tip:

```text
1b3baa5 Prepare dissertation readiness packet
```

Status in that worktree:

```text
dirty documentation/supervisor packet/test guard changes
```

Tracked modified files there are limited to:

- `.gitignore`;
- `docs/current-project-summary.md`;
- `docs/documentation-index.md`;
- `docs/drafts/dissertation-results-chapter-draft.md`;
- `reports/out/supervisor_packet/README.md`;
- `reports/out/supervisor_packet/dissertation_data_readiness_2026-05-30.md`;
- `reports/out/supervisor_packet/handoff.md`;
- `reports/out/supervisor_packet/one_page_summary_ru.md`;
- `tests/test_supervisor_packet_consistency.py`.

Visible untracked file:

- `reports/out/supervisor_packet/android_dissertation_insert_prompt_ru_2026-05-30.md`
  was promoted out of ignored status through a narrow `.gitignore` exception,
  because active docs reference it as a supervisor-facing prompt.

Cleanup performed in the math worktree:

- removed `.DS_Store`;
- removed `.mypy_cache/`, `.pytest_cache/`, `.ruff_cache/`;
- removed Python `__pycache__` directories outside `.venv`;
- removed `src/disser_messenger_project.egg-info/`;
- removed Sage-generated `validate_torsion_reference.sage.py` intermediates.
- reconciled supervisor/current-state docs from stale `132 passed` and
  `two-device pending` wording to current math `138 passed`, Android
  `e542ea0`, June 6 two-phone evidence and June 7 readiness/10/10 planning.
- made the supervisor Android insert prompt visible to Git instead of leaving
  a referenced source-of-truth file hidden under the broad `reports/out/`
  ignore rule.

Cleanup deliberately not performed:

- `.venv/` was kept. It is 249 MiB and ignored, but the system Python currently
  lacks required project dependencies (`pytest`, `sympy`, `cryptography`,
  `pydantic`, `rich`, `typer`), so deleting it would remove the local test
  environment rather than just dead cache.
- ignored generated report output under `reports/out/*` was kept because it is
  small and may be raw/reproduction evidence rather than disposable cache.

Current math worktree size after cleanup:

| Path | Size | Disposition |
| --- | ---: | --- |
| worktree root | 304 MiB | dominated by local `.venv/` |
| `.venv/` | 249 MiB | ignored local test/dependency environment; keep unless user approves rebuilding it later |
| `reports/out/` | 3.6 MiB | report/evidence output; keep unless curated retention is decided |
| `docs/` | 680 KiB | tracked docs |
| `src/` | 124 KiB | tracked source |
| `tests/` | 160 KiB | tracked tests |

Conclusion: `codex/math-experiment-evidence-pack` is active and should stay.
Older local math branches remain archive candidates only because they are
ancestors of this active worktree; do not delete them without explicit user
approval.

## Focused QR Branch Review

Branch `codex/qr-one-time-invite` adds one patch-unique commit:

```text
79e9006 Add one-time QR invite lifecycle
```

Unique files/changes are limited to invite lifecycle code and My QR wiring:

- `OneTimeInviteService.kt`;
- `OneTimeInviteStore.kt`;
- `OneTimeInviteServiceTest.kt`;
- changes to `InviteModels.kt`, `InvitePayloadFactory.kt`,
  `KrakenAppState.kt`, `KrakenNavHost.kt`, `KrakenStorageKeys.kt` and
  `MyQrScreen.kt`.

Current Android `HEAD` already has the later model:

- `IssuedInviteRecord`;
- `IssuedInviteStore`;
- `IssuedInviteLifecyclePolicy`;
- `IssuedInviteRecord.isUsableAt(...)`;
- UI create/revoke wiring in `MyQrScreen`;
- response checks in `OfflineHandshakeService` for revoked, consumed and
  expired known invites.

What the old branch originally had that current `HEAD` did not keep exactly:

- `consumedAtEpochMillis`;
- `consumedByPublicKey`;
- explicit `OneTimeInviteUseResult` for local consume attempts.

Conclusion: the old branch should not be merged wholesale because its payload
and storage model is older than the current realm/direct issued-invite model.
The useful forensic/audit metadata for consumed invites has now been added as a
focused extension to `IssuedInviteRecord` and `IssuedInviteStore`, with tests,
instead of merging `OneTimeInviteService`.

## Stash Evidence Index

The stash entries were inspected read-only with `git stash show
--include-untracked` and `git ls-tree -lr stash@{n}^3`; no stash was applied or
dropped.

| Stash | Files | Size | Contents | Disposition |
| --- | ---: | ---: | --- | --- |
| `stash@{0}` | 18 | 5.34 MiB | raw two-phone QA screenshots/XML plus empty Samsung/Xiaomi app data dump XML files under `artifacts/two-phone-test/2026-06-01/` and one Xiaomi clean overview capture | keep for now; raw evidence only, already superseded at summary level by `reports/out/two_device_delivery_evidence.md` |
| `stash@{1}` | 12 | 11.01 MiB | Xiaomi UI audit screenshots/XML, device setting snapshots and `artifacts/ui-audit/2026-06-01-xiaomi/ui-audit-report.md` | keep until either the UI audit report is promoted/summarized or user approves dropping the stash |

Useful finding from `stash@{1}`:

> Do not use decorative Unicode glyphs as production icons on core screens. Use
> app vector icons or raster assets with fixed dimensions, and verify bounds via
> device screenshot plus `uiautomator dump`.

Both stash entries are artifact-only. They do not contain source-code changes
that need to be merged into the active branch.

## Tree Cleanup Rules

- Keep source, tests, protocol docs and curated reports in git.
- Keep raw phone screenshots, UI dumps, preflight folders and live Android
  evidence under ignored `artifacts/` directories unless a small curated report
  is promoted into `reports/out/`.
- Do not delete evidence directories without first writing the tracked summary
  that replaces them.
- Prefer tracked summaries over committing large PNG/XML/logcat dumps.
- Historical reports may stay if explicitly marked as historical or superseded.

## Tree Cleanup Findings

Repository tree size at audit time:

| Path | Size | Disposition |
| --- | ---: | --- |
| repository root | 365 MiB | after generated cache cleanup; dominated by ignored raw evidence and local APK output |
| `artifacts/` | 220 MiB | mostly ignored raw evidence; keep ignored by default |
| `app-android/app/build/` | 131 MiB | generated Gradle output; kept locally for debug APK rebuild/retest convenience; capture reports store capture-time APK hash separately |
| `reports/out/` | 2.4 MiB | curated reports plus generated Adamova outputs; select tracked reports deliberately |

Current raw/local evidence details are captured in:

- `reports/out/raw_evidence_retention_manifest_2026-06-06.md`;
- `reports/out/raw_evidence_retention_manifest_2026-06-06.json`;
- generator: `scripts/audit_raw_evidence_retention.py`.

Approval-only cleanup actions are consolidated in:

- `reports/out/cleanup_action_plan_2026-06-07.md`;
- `reports/out/cleanup_action_plan_2026-06-07.json`;
- generator: `scripts/build_cleanup_action_plan.py`.

Cross-tree closure for work that does not need phones is summarized in
`reports/out/non_phone_completion_audit_2026-06-07.md`. Fresh device work is
split into `reports/out/phone_evidence_plan_2026-06-07.md`.

The manifest was regenerated on 2026-06-07 after cache cleanup; no raw evidence
directory was deleted.

Generated local cleanup performed:

- removed root `build/`, which contained only generated Adamova host/test output;
- removed `app-android/.gradle/`, the local Gradle cache;
- removed `app-android/app/.cxx/`, the local native build cache;
- kept `app-android/app/build/outputs/apk/debug/app-debug.apk` as local debug
  APK output for rebuild/retest convenience. The latest two-phone capture
  records its own capture-time APK path/hash and should not be rewritten from a
  later rebuild without recapturing devices.

Tracked artifact groups are small enough to keep as curated evidence/assets:

| Tracked artifact group | Files | Size | Disposition |
| --- | ---: | ---: | --- |
| `artifacts/brand-crops/` | 9 | 5.5 MiB | keep tracked; source images for app icons/splash |
| `artifacts/device-screenshots/` | 7 | 3.1 MiB | keep tracked as current curated visual evidence |
| `artifacts/phone-preflight/20260603-193043/` | 6 | 24 KiB | keep tracked as first preflight record |
| `artifacts/desktop-relay-preflight/20260603-193209/` | 2 | 3 KiB | keep tracked as relay preflight summary |
| `artifacts/research_backend_benchmark/` | 1 | 1 KiB | keep tracked as curated device benchmark summary |

Ignored Python/pytest cache directories were removed during cleanup:

- `.pytest_cache/`;
- `benchmarks/__pycache__/`;
- `scripts/__pycache__/`;
- `src/**/__pycache__/`;
- `tests/__pycache__/`.

Current ignored raw evidence footprint:

| Ignored raw area | Size | Disposition |
| --- | ---: | --- |
| `artifacts/phone-preflight/` | 65.860 MiB | mixed tracked curated first preflight plus ignored raw device captures; keep ignored pending retention decision |
| `artifacts/ui-ux-device/` | 46.796 MiB | raw UI/UX device captures; keep ignored pending retention decision |
| `artifacts/device-screenshots/` | 33.787 MiB | mixed tracked curated screenshots plus ignored raw runs; tracked curated subset remains small |
| `artifacts/two-phone-test/` | 33.097 MiB | raw two-phone delivery evidence including fresh post-cleanup capture; keep ignored |
| `artifacts/live-screenshots/` | 12.286 MiB | raw live screenshot captures; keep ignored pending retention decision |
| `artifacts/screenshots/` | 10.232 MiB | raw screenshot output; keep ignored pending retention decision |
| `artifacts/android-adamova-live/` | 4.706 MiB | raw Adamova Android screenshots/UI/device dumps; tracked reports are portable evidence |
| `artifacts/ui-ux-concepts/` | 4.024 MiB | raw/generated UI concept assets; curate before pruning |
| `app-android/app/build/` | 126.154 MiB | generated Gradle output kept for local APK rebuild/retest convenience; two-phone evidence keeps capture-time APK hash |

Tracked tree audit:

- no tracked Gradle build cache, native `.cxx` output, APK/AAB, Python cache,
  temporary files or logs are present;
- the largest tracked files are app bitmap assets, source/crop assets, PDFs and
  curated dissertation screenshots;
- tracked-but-ignored exceptions are intentional curated evidence:
  `artifacts/device-screenshots/*` and
  `artifacts/phone-preflight/20260603-193043/*`.

No tracked large artifact was deleted in this pass. Removing historical PDFs,
curated screenshots or brand source crops would be a content-retention decision,
not safe automatic cleanup.

## Adamova Evidence Pack Disposition

The current Adamova files are not generic trash. Scripts, tests and portable
reports are tracked and modified as a small evidence pack; raw live Android
captures remain ignored:

| Path pattern | Role | Disposition |
| --- | --- | --- |
| `scripts/adamova_effectiveness_experiment.py` | reproducible desktop experiment runner | tracked source |
| `scripts/audit_adamova_effectiveness_evidence.py` | completion audit for reports plus live Android evidence | tracked source |
| `scripts/capture_adamova_android_evidence.sh` | local capture helper for ADB screenshots/device dumps | tracked helper; raw outputs stay ignored |
| `tests/test_adamova_effectiveness_experiment.py` | regression tests for experiment and audit behavior | tracked test |
| `reports/out/adamova_effectiveness_*.md` | curated dissertation-facing reports/handoff/table | tracked reports |
| `reports/out/adamova_effectiveness_*.json` | machine-readable evidence/audit payloads | tracked reports |
| `reports/out/adamova_effectiveness_*.csv` | compact scenario table | tracked report |
| `artifacts/android-adamova-live/` | raw ADB screenshots, UI dumps and device text files | keep ignored; do not bulk commit |

The Adamova native CLI is rebuilt on demand under root `build/adamova-host/`.
That path appears in the generated reports as the reproducible local build path,
but root `build/` is disposable output and should be removed after tests/report
generation.

No commit was made during this cleanup pass. If the user asks for commit/push
later, this pack should be staged as a separate changeset from general
documentation reconciliation.

## Two-Phone Smoke Capture Workflow

`scripts/capture_two_phone_smoke_evidence.sh` is the repeatable capture helper
for post-change Samsung/Xiaomi smoke runs. It writes ignored local output under
`artifacts/two-phone-test/repeatable-<timestamp>-<label>/` by default.

The script captures:

- connected ADB devices;
- git branch/SHA/source state;
- APK path, size and SHA-256;
- per-device screenshot;
- per-device `uiautomator` XML;
- per-device network, window, activity and package dumps;
- `manifest.json` with report version
  `kraken.two_phone_smoke_capture.v1`;
- `two_phone_smoke_summary.md`.

The script deliberately does not install APKs, clear app data, grant
permissions, send messages, delete stash entries or delete branches. It should
be run after the manual two-phone flow is visible on both devices.

Latest usable post-cleanup run:

```text
artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/
```

It captures Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0` in open Kraken chat
screens with peer names, visible messages and `доставлено` UI states. An
earlier `post-cleanup-lan-wifi` run from the same evening only proved device
connection/current app state and should not be used as delivery evidence.

## What Is Still Missing

For a dissertation/research prototype, the main missing work is no longer
"make any messenger UI exist". The remaining gaps are narrower:

- delivery metrics for the LAN prototype: latency, loss/retry counts and
  repeat-run pass/fail history;
- route-specific BLE automation, rejection smoke and exported counters beyond
  the current manual direct-route evidence;
- production crypto boundary work: Android Keystore-backed identity, reviewed
  signatures, signed QR payloads and packet encryption;
- executing branch archive/deletion only after an explicit user command;
- commit-slice decision for the tracked Adamova evidence pack and related
  documentation updates;
- user-approved stash drop/extraction after the stash evidence index above;
- final retention window for ignored raw evidence under
  `artifacts/phone-preflight/`, `artifacts/device-screenshots/`,
  `artifacts/two-phone-test/` and `artifacts/android-adamova-live/`.

## Current Dirty Tree Commit Slicing Plan

No staging, commit or push was performed during this cleanup pass. If the user
approves committing later, do not use `git add .`; stage the mixed tree in
separate reviewable slices.

Read-only slice coverage is machine-checked by:

- `reports/out/dirty_tree_commit_slices_2026-06-07.md`;
- `reports/out/dirty_tree_commit_slices_2026-06-07.json`;
- generator: `scripts/audit_dirty_tree_slices.py`.

Exact staging commands and suggested commit subjects are documented in
`reports/out/commit_slicing_handoff_2026-06-07.md`; the handoff is read-only and
does not stage, commit or push changes.

Latest verification result: `passed`; Android dirty files: 57; linked math dirty
files: 25; unassigned files: 0; duplicate assignments: 0.

### Slice 1: QR/issued-invite metadata salvage

Purpose: keep only the useful consumed invite audit metadata from the old
`codex/qr-one-time-invite` branch without merging its obsolete service model.

Files:

- `app-android/app/src/main/java/com/disser/kraken/invite/InviteModels.kt`;
- `app-android/app/src/main/java/com/disser/kraken/invite/IssuedInviteStore.kt`;
- `app-android/app/src/main/java/com/disser/kraken/navigation/KrakenAppState.kt`;
- `app-android/app/src/test/java/com/disser/kraken/invite/IssuedInviteRecordTest.kt`;
- `app-android/app/src/test/java/com/disser/kraken/demo/ResearchDemoReleaseDocTest.kt`.

### Slice 2: Mesh/BLE/manual evidence documentation

Purpose: replace stale "BLE missing" and "two-device pending" wording with the
current LAN/Wi-Fi and BLE direct-route evidence boundaries.

Files:

- `docs/codex-implementation-audit.md`;
- `docs/kraken-attack-scenarios-evidence.md`;
- `docs/kraken-research-demo-v1-release.md`;
- `docs/manual-review-guide.md`;
- `docs/mesh-transport-hardening.md`;
- `docs/multi-transport-mesh-roadmap.md`;
- `docs/phase-acceptance-checklist.md`;
- `docs/research-mvp-scaffold-v3.md`;
- `docs/research-notes-index.md`;
- `reports/out/dissertation_insert_prompt_ru_2026-05-30.md`;
- `reports/out/mesh_metrics_summary.json`;
- `reports/out/two_device_delivery_evidence.md`;
- `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`;
- `scripts/capture_two_phone_smoke_evidence.sh`;
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshEvidenceReportTest.kt`.

### Slice 3: Evidence pack эффективности контура допуска

Purpose: keep reproducible reports, scripts and tests for the profile admission
contour together while raw Android captures remain ignored.

Files:

- `scripts/adamova_effectiveness_experiment.py`;
- `scripts/audit_adamova_effectiveness_evidence.py`;
- `tests/test_adamova_effectiveness_experiment.py`;
- `reports/out/adamova_effectiveness_completion_audit.json`;
- `reports/out/adamova_effectiveness_completion_audit.md`;
- `reports/out/adamova_effectiveness_dissertation_table.md`;
- `reports/out/adamova_effectiveness_experiment.csv`;
- `reports/out/adamova_effectiveness_experiment.json`;
- `reports/out/adamova_effectiveness_experiment.md`;
- `reports/out/adamova_effectiveness_result_handoff.md`.

### Slice 4: Tree/branch cleanup audit and retention policy

Purpose: document branch, stash, raw evidence and generated-output cleanup
without deleting approval-only state.

Files:

- `docs/branch-tree-cleanup-audit.md`;
- `reports/out/branch_tree_cleanup_verification_2026-06-07.json`;
- `reports/out/branch_tree_cleanup_verification_2026-06-07.md`;
- `reports/out/cleanup_action_plan_2026-06-07.json`;
- `reports/out/cleanup_action_plan_2026-06-07.md`;
- `reports/out/dirty_tree_commit_slices_2026-06-07.json`;
- `reports/out/dirty_tree_commit_slices_2026-06-07.md`;
- `reports/out/non_phone_cleanup_status_2026-06-07.json`;
- `reports/out/non_phone_cleanup_status_2026-06-07.md`;
- `reports/out/non_phone_completion_audit_2026-06-07.md`;
- `reports/out/phone_evidence_plan_2026-06-07.md`;
- `scripts/audit_dirty_tree_slices.py`;
- `scripts/audit_branch_tree_cleanup_state.py`;
- `scripts/build_cleanup_action_plan.py`;
- `scripts/build_non_phone_cleanup_status.py`;
- `reports/out/raw_evidence_retention_manifest_2026-06-06.json`;
- `reports/out/raw_evidence_retention_manifest_2026-06-06.md`;
- `scripts/audit_raw_evidence_retention.py`.

### Slice 5: June 7 readiness and 10/10 research-prototype plan

Purpose: make 2026-06-07 the current entrypoint and separate dissertation-grade
research prototype readiness from production secure messenger readiness.

Files:

- `README.md`;
- `reports/out/current_project_readiness_2026-06-06.md`;
- `reports/out/current_project_readiness_2026-06-07.md`;
- `reports/out/kraken_10_10_readiness_plan_2026-06-07.json`;
- `reports/out/kraken_10_10_readiness_plan_2026-06-07.md`;
- `tests/test_android_policy_guards.py`.

### Linked math worktree slice

The linked worktree `/Users/cheenya/Projects/disser-messenger-project` should be
committed separately from Android:

- `.gitignore`;
- `docs/current-project-summary.md`;
- `docs/documentation-index.md`;
- `docs/drafts/dissertation-results-chapter-draft.md`;
- `reports/out/local_output_retention_manifest_2026-06-07.json`;
- `reports/out/local_output_retention_manifest_2026-06-07.md`;
- `reports/out/supervisor_packet/README.md`;
- `reports/out/supervisor_packet/android_dissertation_insert_prompt_ru_2026-05-30.md`;
- `reports/out/supervisor_packet/dissertation_data_readiness_2026-05-30.md`;
- `reports/out/supervisor_packet/handoff.md`;
- `reports/out/supervisor_packet/one_page_summary_ru.md`;
- `scripts/audit_local_output_retention.py`;
- `tests/test_supervisor_packet_consistency.py`.

Do not mix branch/stash deletion with any of these commit slices. Local branch
deletion, remote branch deletion, stash dropping and raw evidence pruning remain
approval-only actions documented below.

## Approval-Only Cleanup Commands

The commands below are intentionally documented, not executed. Run them only
after explicit user approval, because they delete local refs, stash entries or
raw evidence directories.

### Local branch archive/delete

Current local archive candidates that are ancestors of the active Android
branch can be removed with the non-forced branch delete command:

```bash
git branch -d \
  codex/android-identity-invite-relationship-realm \
  codex/android-skeleton \
  codex/protocol-spec-core-models
```

Expected behavior: `git branch -d` should refuse if Git does not consider a
branch safely merged into its configured upstream/current merge base. If it
refuses, do not switch to `-D` automatically; re-audit that branch first.

The following local candidates are not ancestors of the active Android branch,
but have already been audited as either ancestors of active math evidence or
salvaged/superseded QR work. They require a separate explicit approval because
cleanup would need forced local branch deletion:

```bash
git branch -D \
  codex/dissertation-research-alignment \
  codex/math-core-curve-diagnostics \
  codex/math-core-reference-validation \
  codex/math-research-panel-integration-plan \
  codex/qr-one-time-invite
```

Before running the forced command, re-check:

```bash
git merge-base --is-ancestor codex/dissertation-research-alignment codex/math-experiment-evidence-pack
git merge-base --is-ancestor codex/math-core-curve-diagnostics codex/math-experiment-evidence-pack
git merge-base --is-ancestor codex/math-core-reference-validation codex/math-experiment-evidence-pack
git merge-base --is-ancestor codex/math-research-panel-integration-plan codex/math-experiment-evidence-pack
git cherry -v HEAD codex/qr-one-time-invite
```

Do not delete:

- `codex/android-research-panel-report-viewer`;
- `codex/math-experiment-evidence-pack`;
- `main`.

### Remote branch cleanup

Remote cleanup needs a separate explicit approval because it changes shared
state. Current low-risk remote candidate:

```bash
git push origin --delete chore/research-mvp-scaffold-v2
```

Do not delete `origin/chore/research-mvp-scaffold` without a deeper remote
cleanup decision. It is not an ancestor of the active Android or math evidence
branches and still has patch-unique legacy commits.

### Stash cleanup

Both stash entries are artifact-only. If the current summaries are considered
sufficient and the user approves dropping the raw stash copies, drop in reverse
index order so indices do not shift under the command sequence:

```bash
git stash drop stash@{1}
git stash drop stash@{0}
```

Before dropping, the safer confirmation commands are:

```bash
git stash show --include-untracked --stat stash@{1}
git stash show --include-untracked --stat stash@{0}
```

### Raw evidence retention

Default policy remains keep ignored raw evidence. If a retention window is
approved later, remove only directories that already have tracked summaries or
are superseded by current reports. Never bulk-delete `artifacts/two-phone-test/`
while `reports/out/two_device_delivery_evidence.md` references local raw
captures.

Candidate review commands:

```bash
du -sh artifacts/phone-preflight/ artifacts/device-screenshots/ \
  artifacts/live-screenshots/ artifacts/screenshots/ artifacts/ui-ux-device/ \
  artifacts/android-adamova-live/ artifacts/two-phone-test/
find artifacts/phone-preflight -mindepth 1 -maxdepth 1 -type d | sort
find artifacts/device-screenshots -mindepth 1 -maxdepth 1 -type d | sort
```

Deletion commands should be written only after the retention window is chosen.

## Immediate Documentation Conflicts Found

The following files contained stale `two-device pending` wording while raw
two-phone evidence already exists:

- `app-android/README.md`;
- `reports/out/two_device_delivery_evidence.md`;
- `reports/out/android_p2p_smoke_report.md`;
- `reports/out/mesh_delivery_simulation.md`;
- `reports/out/p2p_research_prototype_readiness_2026-05-30.md`;
- `docs/android-dissertation-evidence-2026-05-30.md`;
- `reports/out/dissertation_insert_prompt_ru_2026-05-30.md`;
- `docs/codex-implementation-audit.md`.

This cleanup batch updates the active/current reports and marks dated May 30
dissertation artifacts as historical where they mention superseded two-device
status.

Additional current-state docs reconciled during the continuation:

- `docs/research-mvp-scaffold-v3.md`;
- `docs/multi-transport-mesh-roadmap.md`;
- `docs/mesh-transport-hardening.md`;
- `docs/manual-review-guide.md`;
- `docs/codex-implementation-audit.md`.

Linked math/evidence worktree files reconciled during the continuation:

- `.gitignore`;
- `docs/current-project-summary.md`;
- `docs/documentation-index.md`;
- `docs/drafts/dissertation-results-chapter-draft.md`;
- `reports/out/supervisor_packet/README.md`;
- `reports/out/supervisor_packet/android_dissertation_insert_prompt_ru_2026-05-30.md`;
- `reports/out/supervisor_packet/dissertation_data_readiness_2026-05-30.md`;
- `reports/out/supervisor_packet/handoff.md`;
- `reports/out/supervisor_packet/one_page_summary_ru.md`;
- `tests/test_supervisor_packet_consistency.py`.
