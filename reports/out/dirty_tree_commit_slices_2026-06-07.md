# Dirty Tree Commit Slices

Date: 2026-06-07.

This read-only report maps every current modified or untracked file in
the Android and linked math worktrees to an intended commit slice. It
does not stage, commit, push, delete branches, drop stash entries or
prune raw evidence.

Overall status: `passed`.

| Repository | Dirty files | Unassigned | Missing expected slice files |
| --- | ---: | ---: | ---: |
| `android` | 13 | 0 | 0 |
| `math` | 0 | 0 | 0 |

## Slices

### `android_qr_issued_invite_metadata`

- Repository: `android`.
- Purpose: Salvage useful consumed invite audit metadata without merging the old one-time invite service branch wholesale.
- Dirty files: 0.


### `android_mesh_ble_manual_evidence_docs`

- Repository: `android`.
- Purpose: Update LAN/Wi-Fi and BLE manual evidence boundaries plus capture helper docs/tests.
- Dirty files: 1.

  - `docs/research-notes-index.md`

### `android_adamova_effectiveness_evidence_pack`

- Repository: `android`.
- Purpose: Keep Adamova admission-gate scripts, tests and portable reports together.
- Dirty files: 0.


### `android_branch_tree_cleanup_and_retention`

- Repository: `android`.
- Purpose: Document branch/stash/raw evidence cleanup and verify it read-only.
- Dirty files: 9.

  - `reports/out/cleanup_execution_status_2026-06-07.json`
  - `reports/out/cleanup_execution_status_2026-06-07.md`
  - `reports/out/commit_slicing_handoff_2026-06-07.json`
  - `reports/out/commit_slicing_handoff_2026-06-07.md`
  - `reports/out/dirty_tree_commit_slices_2026-06-07.json`
  - `reports/out/dirty_tree_commit_slices_2026-06-07.md`
  - `reports/out/non_phone_cleanup_status_2026-06-07.json`
  - `reports/out/non_phone_cleanup_status_2026-06-07.md`
  - `scripts/audit_dirty_tree_slices.py`

### `android_june_7_readiness_and_policy_guards`

- Repository: `android`.
- Purpose: Make June 7 readiness and 10/10 research-prototype planning the current entrypoint.
- Dirty files: 3.

  - `README.md`
  - `reports/out/current_project_readiness_2026-06-07.md`
  - `tests/test_android_policy_guards.py`

### `math_supervisor_packet_june_7_sync`

- Repository: `math`.
- Purpose: Synchronize linked math/supervisor packet with June 7 Android readiness and current test counts.
- Dirty files: 0.
