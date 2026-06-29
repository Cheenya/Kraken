# Commit Slicing Handoff

Date: 2026-06-07.

This is a read-only staging/commit handoff. It does not run staging, commit or push commands.
Use it only after explicit approval to commit the current dirty tree.

- Read-only: `true`.
- Executed: `false`.
- Stage/commit/push executed: `false`.
- Source dirty-tree verification passed: `true`.

## Required Discipline

- Do not use `git add .`.
- Stage one slice at a time.
- Review `git diff --cached --stat` before each commit.
- Re-run the relevant tests after the final slice.
- Do not push until explicitly approved.

## Slices

### `android_mesh_ble_manual_evidence_docs`

- Repository: `android`.
- Root: `/Users/cheenya/Projects/kraken-android-research-panel`.
- Purpose: Update LAN/Wi-Fi and BLE manual evidence boundaries plus capture helper docs/tests.
- Dirty files: 1.
- Commit subject: `Refresh LAN and BLE manual evidence docs`.

Files:

- `docs/research-notes-index.md`

Commands after explicit commit approval:

- `git -C /Users/cheenya/Projects/kraken-android-research-panel add -- docs/research-notes-index.md`
- `git -C /Users/cheenya/Projects/kraken-android-research-panel diff --cached --stat`
- `git -C /Users/cheenya/Projects/kraken-android-research-panel commit -m 'Refresh LAN and BLE manual evidence docs'`

### `android_branch_tree_cleanup_and_retention`

- Repository: `android`.
- Root: `/Users/cheenya/Projects/kraken-android-research-panel`.
- Purpose: Document branch/stash/raw evidence cleanup and verify it read-only.
- Dirty files: 9.
- Commit subject: `Document branch cleanup and retention plan`.

Files:

- `reports/out/cleanup_execution_status_2026-06-07.json`
- `reports/out/cleanup_execution_status_2026-06-07.md`
- `reports/out/commit_slicing_handoff_2026-06-07.json`
- `reports/out/commit_slicing_handoff_2026-06-07.md`
- `reports/out/dirty_tree_commit_slices_2026-06-07.json`
- `reports/out/dirty_tree_commit_slices_2026-06-07.md`
- `reports/out/non_phone_cleanup_status_2026-06-07.json`
- `reports/out/non_phone_cleanup_status_2026-06-07.md`
- `scripts/audit_dirty_tree_slices.py`

Commands after explicit commit approval:

- `git -C /Users/cheenya/Projects/kraken-android-research-panel add -- reports/out/cleanup_execution_status_2026-06-07.json reports/out/cleanup_execution_status_2026-06-07.md reports/out/commit_slicing_handoff_2026-06-07.json reports/out/commit_slicing_handoff_2026-06-07.md reports/out/dirty_tree_commit_slices_2026-06-07.json reports/out/dirty_tree_commit_slices_2026-06-07.md reports/out/non_phone_cleanup_status_2026-06-07.json reports/out/non_phone_cleanup_status_2026-06-07.md scripts/audit_dirty_tree_slices.py`
- `git -C /Users/cheenya/Projects/kraken-android-research-panel diff --cached --stat`
- `git -C /Users/cheenya/Projects/kraken-android-research-panel commit -m 'Document branch cleanup and retention plan'`

### `android_june_7_readiness_and_policy_guards`

- Repository: `android`.
- Root: `/Users/cheenya/Projects/kraken-android-research-panel`.
- Purpose: Make June 7 readiness and 10/10 research-prototype planning the current entrypoint.
- Dirty files: 3.
- Commit subject: `Refresh June 7 Kraken readiness guards`.

Files:

- `README.md`
- `reports/out/current_project_readiness_2026-06-07.md`
- `tests/test_android_policy_guards.py`

Commands after explicit commit approval:

- `git -C /Users/cheenya/Projects/kraken-android-research-panel add -- README.md reports/out/current_project_readiness_2026-06-07.md tests/test_android_policy_guards.py`
- `git -C /Users/cheenya/Projects/kraken-android-research-panel diff --cached --stat`
- `git -C /Users/cheenya/Projects/kraken-android-research-panel commit -m 'Refresh June 7 Kraken readiness guards'`

## Post-Commit Checks

- `/Users/cheenya/Projects/disser-messenger-project/.venv/bin/python -m pytest`
- `./gradlew test`
- `./gradlew assembleDebug`
- `git diff --check`

## Boundary

This handoff does not decide branch deletion, stash cleanup, raw artifact
retention, or phone-dependent recapture. Those remain in the cleanup action
plan and non-phone cleanup status reports.
