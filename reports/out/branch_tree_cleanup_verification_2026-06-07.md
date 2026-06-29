# Branch Tree Cleanup Verification

Date: 2026-06-07.

This is a read-only verification of the branch/tree cleanup audit. It
runs Git inspection commands only and does not delete branches, drop
stash entries, stage files, commit, push or prune raw evidence.

Overall status: `passed`.

| Check | Status | Detail |
| --- | --- | --- |
| `active_android_branch` | `passed` | codex/android-research-panel-report-viewer@e542ea0 |
| `android_upstream_synced` | `passed` | origin/codex/android-research-panel-report-viewer@e542ea0 |
| `linked_math_worktree_present` | `passed` | math worktree listed by git worktree list --porcelain |
| `android_ancestor_main` | `passed` | main ancestor of Android HEAD |
| `android_ancestor_codex/android-identity-invite-relationship-realm` | `passed` | codex/android-identity-invite-relationship-realm ancestor of Android HEAD |
| `android_ancestor_codex/android-skeleton` | `passed` | codex/android-skeleton ancestor of Android HEAD |
| `android_ancestor_codex/protocol-spec-core-models` | `passed` | codex/protocol-spec-core-models ancestor of Android HEAD |
| `math_ancestor_codex/dissertation-research-alignment` | `passed` | codex/dissertation-research-alignment ancestor of codex/math-experiment-evidence-pack |
| `math_ancestor_codex/math-core-curve-diagnostics` | `passed` | codex/math-core-curve-diagnostics ancestor of codex/math-experiment-evidence-pack |
| `math_ancestor_codex/math-core-reference-validation` | `passed` | codex/math-core-reference-validation ancestor of codex/math-experiment-evidence-pack |
| `math_ancestor_codex/math-research-panel-integration-plan` | `passed` | codex/math-research-panel-integration-plan ancestor of codex/math-experiment-evidence-pack |
| `qr_branch_patch_unique_but_not_wholesale_merge_candidate` | `passed` | + 79e9006e49717276b6f0e0a93a87b0a52201b5cf Add one-time QR invite lifecycle |
| `remote_scaffold_v2_has_no_patch_unique_commits` | `passed` | patch_unique_count=0 |
| `remote_scaffold_still_needs_manual_review` | `passed` | patch_unique_count=13 |
| `stash_inventory_still_indexed` | `passed` | stash@{0}: On codex/android-research-panel-report-viewer: two phone qa artifacts before clean rebuild \| stash@{1}: On codex/android-research-panel-report-viewer: local xiaomi ui audit artifacts before clean build |
| `cleanup_audit_keeps_destructive_actions_approval_only` | `passed` | branch, stash, remote and raw evidence cleanup remain documented as approval-only |
