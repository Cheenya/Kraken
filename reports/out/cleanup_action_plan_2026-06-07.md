# Cleanup Action Plan

Date: 2026-06-07.

This is a read-only approval plan. It does not execute any cleanup.
Every action below requires explicit user approval before running.

- Read-only: `true`.
- Executed: `false`.
- Approval required before any action: `true`.

## Actions

### `delete_safe_merged_android_archive_branches`

- Category: `local_branch_cleanup`.
- Repository: `android`.
- Status: `ready_after_user_approval`.
- Approval required: `true`.
- Rationale: These local branches are verified ancestors of the active Android branch.

Verification before approval/run:

- `git merge-base --is-ancestor main HEAD`
- `git merge-base --is-ancestor codex/android-identity-invite-relationship-realm HEAD`
- `git merge-base --is-ancestor codex/android-skeleton HEAD`
- `git merge-base --is-ancestor codex/protocol-spec-core-models HEAD`

Commands after explicit approval:

- `git branch -d codex/android-identity-invite-relationship-realm codex/android-skeleton codex/protocol-spec-core-models`

Do not touch:

- `codex/android-research-panel-report-viewer`
- `codex/math-experiment-evidence-pack`
- `main`

### `delete_audited_math_and_qr_local_archive_branches`

- Category: `local_branch_cleanup`.
- Repository: `android`.
- Status: `requires_separate_user_approval`.
- Approval required: `true`.
- Rationale: Math branches are ancestors of the active math worktree branch; the QR branch has one patch-unique commit whose useful metadata was salvaged without wholesale merging the old service model.

Verification before approval/run:

- `git merge-base --is-ancestor codex/dissertation-research-alignment codex/math-experiment-evidence-pack`
- `git merge-base --is-ancestor codex/math-core-curve-diagnostics codex/math-experiment-evidence-pack`
- `git merge-base --is-ancestor codex/math-core-reference-validation codex/math-experiment-evidence-pack`
- `git merge-base --is-ancestor codex/math-research-panel-integration-plan codex/math-experiment-evidence-pack`
- `git cherry -v HEAD codex/qr-one-time-invite`

Commands after explicit approval:

- `git branch -D codex/dissertation-research-alignment codex/math-core-curve-diagnostics codex/math-core-reference-validation codex/math-research-panel-integration-plan codex/qr-one-time-invite`

Do not touch:

- `codex/android-research-panel-report-viewer`
- `codex/math-experiment-evidence-pack`
- `main`

### `delete_low_risk_remote_scaffold_v2`

- Category: `remote_branch_cleanup`.
- Repository: `origin`.
- Status: `requires_separate_user_approval`.
- Approval required: `true`.
- Rationale: Remote scaffold v2 has no patch-unique commits relative to active Android HEAD.

Verification before approval/run:

- `git cherry -v HEAD origin/chore/research-mvp-scaffold-v2`

Commands after explicit approval:

- `git push origin --delete chore/research-mvp-scaffold-v2`

Do not touch:

- `origin/chore/research-mvp-scaffold`

### `drop_raw_artifact_stashes`

- Category: `stash_cleanup`.
- Repository: `android`.
- Status: `requires_user_confirmation_after_stat_review`.
- Approval required: `true`.
- Rationale: Both stash entries contain raw UI/two-phone artifacts whose summaries now exist in tracked reports.

Verification before approval/run:

- `git stash show --include-untracked --stat stash@{1}`
- `git stash show --include-untracked --stat stash@{0}`

Commands after explicit approval:

- `git stash drop stash@{1}`
- `git stash drop stash@{0}`

### `review_android_raw_evidence_retention_window`

- Category: `raw_evidence_retention`.
- Repository: `android`.
- Status: `retention_window_not_chosen`.
- Approval required: `true`.
- Rationale: Android raw evidence directories are ignored/local and classified by reports/out/raw_evidence_retention_manifest_2026-06-06.md.

Verification before approval/run:

- `du -sh artifacts/phone-preflight/ artifacts/device-screenshots/ artifacts/live-screenshots/ artifacts/screenshots/ artifacts/ui-ux-device/ artifacts/android-adamova-live/ artifacts/two-phone-test/ app-android/app/build/`

Commands after explicit approval: not written yet; choose retention window first.

Do not touch:

- `artifacts/two-phone-test/ while current reports reference local raw captures`
- `tracked curated screenshots/assets`

### `review_math_local_output_retention_window`

- Category: `local_output_retention`.
- Repository: `math`.
- Status: `retention_window_not_chosen`.
- Approval required: `true`.
- Rationale: Linked math local outputs and .venv are classified by reports/out/local_output_retention_manifest_2026-06-07.md.

Verification before approval/run:

- `du -sh .venv reports/out/benchmark-runs reports/out/large-coefficient-benchmark-runs reports/out/math-core reports/out/math-core-extended reports/out/math-core-repeated-smoke reports/out/torsion-reproduction`

Commands after explicit approval: not written yet; choose retention window first.

Do not touch:

- `tracked aggregate reports`
- `Sage reproducibility packs`
- `supervisor packet files`
