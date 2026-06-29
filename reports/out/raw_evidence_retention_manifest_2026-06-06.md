# Raw Evidence Retention Manifest

Date: 2026-06-07.

Purpose: classify ignored/local evidence and generated APK output without
deleting anything. Every listed area requires explicit user approval before
deletion or pruning.

| Path | Git class | Size MiB | Files | Child dirs | Tracked files | Disposition |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `artifacts/phone-preflight` | `mixed_tracked_curated_and_ignored` | 65.860 | 618 | 38 | 6 | keep ignored until a retention window is approved |
| `artifacts/device-screenshots` | `mixed_tracked_curated_and_ignored` | 33.787 | 193 | 11 | 7 | keep tracked curated subset; keep ignored raw runs until retention decision |
| `artifacts/live-screenshots` | `ignored` | 12.286 | 18 | 0 | 0 | keep ignored until retention decision |
| `artifacts/screenshots` | `ignored` | 10.232 | 6 | 1 | 0 | keep ignored until retention decision |
| `artifacts/ui-ux-device` | `ignored` | 46.796 | 282 | 10 | 0 | keep ignored until retention decision |
| `artifacts/ui-ux-concepts` | `ignored` | 4.024 | 49 | 1 | 0 | keep ignored until retention decision |
| `artifacts/android-adamova-live` | `ignored` | 4.706 | 27 | 3 | 0 | keep ignored; tracked Adamova reports are the portable evidence pack |
| `artifacts/two-phone-test` | `ignored` | 33.097 | 158 | 7 | 0 | keep ignored; do not bulk-delete while current reports reference raw captures |
| `app-android/app/build` | `ignored` | 126.154 | 2591 | 7 | 0 | keep local debug APK output for rebuild/retest convenience until retention is approved |

## Retention Rules

- Do not bulk-delete raw evidence directories while tracked reports reference them.
- Prefer promoting small curated summaries/screenshots over committing raw PNG/XML/device dumps.
- `app-android/app/build` is generated output. Two-phone reports record
  capture-time APK path/hash; later local rebuilds can produce a different
  hash, so do not overwrite capture metadata without a fresh recapture.
- Stash entries and branches remain separate approval-only cleanup items in
  `docs/branch-tree-cleanup-audit.md`.

## Area Notes

### `artifacts/phone-preflight`

- Retention class: `ignored_raw_device_preflight`.
- Evidence summary: summarized by docs/branch-tree-cleanup-audit.md; includes many timestamped ADB/device state captures.
- Approval required before delete: `true`.

### `artifacts/device-screenshots`

- Retention class: `mixed_curated_tracked_and_ignored_raw_screenshots`.
- Evidence summary: tracked curated visual evidence exists, while ignored timestamped QA runs remain local.
- Approval required before delete: `true`.

### `artifacts/live-screenshots`

- Retention class: `ignored_raw_live_screenshots`.
- Evidence summary: raw live screenshots only; no bulk delete without review.
- Approval required before delete: `true`.

### `artifacts/screenshots`

- Retention class: `ignored_raw_screenshot_output`.
- Evidence summary: manual/raw screenshot output; curate selected images before deletion.
- Approval required before delete: `true`.

### `artifacts/ui-ux-device`

- Retention class: `ignored_raw_ui_ux_device_evidence`.
- Evidence summary: device UI/UX verification runs; summarize before pruning.
- Approval required before delete: `true`.

### `artifacts/ui-ux-concepts`

- Retention class: `ignored_raw_ui_concepts`.
- Evidence summary: generated/local UI concept assets; curate before pruning.
- Approval required before delete: `true`.

### `artifacts/android-adamova-live`

- Retention class: `ignored_raw_adamova_android_capture`.
- Evidence summary: referenced by reports/out/adamova_effectiveness_result_handoff.md and completion audit.
- Approval required before delete: `true`.

### `artifacts/two-phone-test`

- Retention class: `ignored_raw_two_phone_delivery_evidence`.
- Evidence summary: referenced by reports/out/two_device_delivery_evidence.md and current readiness report.
- Approval required before delete: `true`.

### `app-android/app/build`

- Retention class: `generated_gradle_output_local_apk_rebuild_output`.
- Evidence summary: two-phone reports record capture-time APK path/hash; later local rebuilds can produce a different hash.
- Approval required before delete: `true`.
