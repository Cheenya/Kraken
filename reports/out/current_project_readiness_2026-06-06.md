# Kraken Current Project Readiness

Date: 2026-06-06.

Superseded by `reports/out/current_project_readiness_2026-06-07.md` as the
latest current-status entrypoint. Keep this file as the June 6 readiness
checkpoint.

Scope at checkpoint time: local checkout of
`/Users/cheenya/Projects/kraken-android-research-panel` on branch
`codex/android-research-panel-report-viewer`.

## Short Status

Kraken is now a working Android research/demo messenger prototype, not only a
screen mock. It has Android UI, local identity, QR-gated relationships,
local-first chats, realms, Research Panel, LAN/BLE transport code, queue/retry
logic, receipts, route diagnostics and Adamova experimental-profile admission
metadata.

It is still not a production secure messenger. Production encryption, Android
Keystore-backed identity, reviewed signatures and a full security review remain
open work.

## Current Evidence

| Area | Status | Evidence |
| --- | --- | --- |
| Python support code | passing | `pytest -q`: 44 passed on 2026-06-06 |
| Python syntax | passing | `python3 -m compileall .` on 2026-06-06 |
| Android unit tests | passing | `./gradlew test`: BUILD SUCCESSFUL on 2026-06-06 |
| Android debug APK | passing | `./gradlew assembleDebug`: BUILD SUCCESSFUL on 2026-06-06 |
| Whitespace diff check | passing | `git diff --check`: no output on 2026-06-06 |
| Two-phone LAN/Wi-Fi smoke | completed manually | `reports/out/two_device_delivery_evidence.md` |
| Two-phone BLE direct route | completed manually | `reports/out/ble_two_device_delivery_evidence_2026-06-06.md` |
| Repeatable two-phone capture helper | completed for post-cleanup chat state | `artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/` |
| Adamova effectiveness pack | complete, including live Android capture | `reports/out/adamova_effectiveness_completion_audit.md` |

Current debug APK path:

```text
app-android/app/build/outputs/apk/debug/app-debug.apk
```

Current local debug APK SHA-256 after the latest `./gradlew assembleDebug`
verification:

```text
af1ab1e3bc333cbd93fc0fb0ec8336c7052bbbd30a4456af2195cd64442eb0c4
```

The LAN/Wi-Fi two-phone report keeps the capture-time APK hash from the
evidence run; do not replace that historical hash without a fresh device
recapture.

## Two-Phone State

The older one-device wording in some May 30 reports is superseded. Raw local
evidence under `artifacts/two-phone-test/2026-06-01/` shows Samsung
`R5CY22X6MSB` and Xiaomi `d948ffd0` in a manual two-phone flow.

Authoritative tracked summary:

```text
reports/out/two_device_delivery_evidence.md
```

Important limitation: the raw screenshots and UI XML prove a manual prototype
delivery/receipt-level flow at the UI state level. A fresh post-cleanup capture
bundle now exists at
`artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/`
with APK/hash, git, device, screenshot and UI XML evidence. Authoritative in-app
packet counters are still needed. Separate manual BLE direct-route evidence
exists in `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`, but
route-specific BLE automation, rejection smoke and counters are still needed.
The evidence still does not provide production crypto proof, latency statistics
or malicious-device testing.

## Current Branch / Tree Risks

- Current branch matches `origin/codex/android-research-panel-report-viewer`;
  the working tree still has local modified files.
- The worktree has local modified files for cleanup documentation, tests,
  Adamova report regeneration and QR invite metadata salvage.
- `artifacts/phone-preflight/`, `artifacts/device-screenshots/`,
  `artifacts/two-phone-test/` and `artifacts/android-adamova-live/` are
  evidence/raw-output areas. They should stay ignored by default and should not
  be bulk committed.
- Generated root `build/`, `app-android/.gradle/` and `app-android/app/.cxx/`
  were removed as local cache/output cleanup. The debug APK build output is
  kept for local rebuild/retest convenience, while two-phone reports preserve
  their own capture-time APK hashes.
- Existing stash entries contain prior phone QA artifacts. Do not drop them
  unless the user explicitly approves it; they are now indexed in
  `docs/branch-tree-cleanup-audit.md`.
- Raw/local evidence retention is now indexed in
  `reports/out/raw_evidence_retention_manifest_2026-06-06.md`; deleting or
  pruning any listed area still requires explicit approval.
- Linked math/evidence worktree
  `/Users/cheenya/Projects/disser-messenger-project` remains active on
  `codex/math-experiment-evidence-pack`; cache cleanup was limited to generated
  Python/Sage intermediates while keeping its ignored `.venv/` test environment.

## Next Cleanup Priorities

1. Keep documentation reconciliation current: active overviews should point to
   this readiness report or `two_device_delivery_evidence.md`; historical May
   30 artifacts may remain if clearly marked as superseded.
2. If committing later, stage Adamova scripts/tests/reports as a dedicated
   evidence-pack changeset; keep raw Android live capture under ignored
   `artifacts/android-adamova-live/`.
3. For branch cleanup, use `docs/branch-tree-cleanup-audit.md`: several older
   Android/protocol branches are archive candidates, while old math branches are
   already ancestors of the active math/evidence worktree.
4. Add in-app mesh counter export to the capture flow if delivery latency/loss
   metrics are required.
5. Keep production-security claims blocked until Keystore, real crypto and
   review work are present.
