# UI/UX Messenger Baseline Tracker

Worktree: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux`
Branch: `codex/ui-ux-messenger-baseline`
Plan source: `docs/ui_ux_messenger_baseline_plan.md`

## Operating Rules

- Treat this tracker as the live execution ledger after context compaction.
- Re-read this tracker before resuming work, after compaction, and before answering status questions.
- Do not commit, push, or deploy unless explicitly requested.
- Do not touch the main worktree `/Users/cheenya/Projects/kraken-android-research-panel` for this UI/UX branch.
- Every implementation block needs:
  - code changes listed;
  - `./gradlew test assembleDebug` or a reason it was not run;
  - Samsung screenshot evidence path;
  - tracker update.

## Current Status

Active objective: implement the tasks in `docs/ui_ux_messenger_baseline_plan.md`.

Last completed implementation block:

- Block 41: pending pairing interaction lock and reduced duplicate UI sync pressure.
- Evidence: `artifacts/ui-ux-block-41-pairing-lock-and-sync-throttle/pairing_lock_and_sync_throttle.md`
- Verification: focused copy/welcome guards passed; full `./gradlew test assembleDebug` passed; `git diff --check` passed; APK installed on Xiaomi and Samsung; Samsung post-install quick evidence captured.
- Decision: Android Samsung -> Xiaomi one-scan QR flow remains proven from Block 40. Block 41 improves the pending UX and removes duplicate UI-driven sync polling.

Latest main Android phone confirmation:

- 2026-06-15, branch `codex/kraken-android`: fresh APK was built, installed on Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0`, permissions were granted, start/main/contacts/My QR screens were captured without Kraken crash/ANR, and the user manually confirmed: `one-scan QR works on Samsung/Xiaomi after cleanup`.
- Sanity evidence: `artifacts/post-cleanup-phone-sanity/20260615-101144/`.
- Boundary: this confirms the phone QR pairing flow and UI sanity after cleanup; it is not Wi-Fi Direct route delivery or message-delivery evidence.

Latest cross-platform UI correction:

- 2026-06-29: added a planned Slice 6 to `docs/ui_ux_messenger_baseline_plan.md`.
- Scope: Android keeps the messenger IA source of truth `Чаты / Контакты / Реалмы / Настройки`; Android bottom nav gets the liked iOS-style floating capsule treatment without restoring `Главная`; Android composer replaces the inline emoji strip with a left-smile-button bottom emoji panel; iOS/macOS screens are aligned to Android screen responsibilities.
- Current evidence basis: Android/iOS screenshot comparison showed Android `Чаты` as a real chat list, while iOS `Чаты` was still effectively a dashboard/home surface under the wrong label.

## Completed Blocks

| Block | Scope | Evidence |
| --- | --- | --- |
| 1 | Baseline navigation/copy/QR/settings/actions | `artifacts/ui-ux-block-1-samsung/20260614-045239/` |
| 2 | QR size attempt, chat header, selection/favorites | `artifacts/ui-ux-block-2-samsung/20260614-051743/` |
| 3 | Compact message menu | `artifacts/ui-ux-block-3-samsung/20260614-052410/` |
| 4 | Anchored message menu | `artifacts/ui-ux-block-4-samsung/20260614-052955/` |
| 5 | Denser menu positioning | `artifacts/ui-ux-block-5-samsung/20260614-053445/` |
| 6 | Reaction strip and menu icons | `artifacts/ui-ux-block-6-samsung/20260614-053848/` |
| 7 | Settings route segmented control | `artifacts/ui-ux-block-7-samsung/20260614-054341/` |
| 8 | Chat row/status/menu-left/selection fixes | `artifacts/ui-ux-block-8-samsung/20260614-054749/` |
| 9 | Bottom nav compact selected state | `artifacts/ui-ux-block-9-samsung/20260614-055032/` |
| 10 | Larger QR rendering | `artifacts/ui-ux-block-10-samsung/20260614-160549/` |
| 11 | Normal settings mesh control and research entry placement | `artifacts/ui-ux-block-11-samsung/20260614-161146/` |
| 12 | Message long-press selection, no-ripple menu dismiss, directed reply swipe | `artifacts/ui-ux-block-12-samsung/20260614-161419/` |
| 13 | Research/diagnostics localization and prohibited visible terminology cleanup | `artifacts/ui-ux-block-13-samsung/20260614-162237/` |
| 14 | QR fallback flow terminology and response-QR blocker investigation | `artifacts/ui-ux-block-14-samsung/20260614-qr-flow-labels/` |
| 15 | Chat list search | `artifacts/ui-ux-block-15-samsung/20260614-chat-search/` |
| 16 | Failed-message inline retry and debug payload presentation | `artifacts/ui-ux-block-16-samsung/20260614-failed-debug-message/` |
| 17 | `Реалмы` bottom navigation decision | `artifacts/ui-ux-block-17-samsung/20260614-realms-nav-decision/` |
| 18 | QR deep-link and density pass | `artifacts/ui-ux-block-18-samsung/20260614-qr-deeplink-density/` |
| 19 | Completion screenshot audit and root title consistency | `artifacts/ui-ux-block-19-samsung/20260614-completion-audit/` |
| 20 | Contact actions bottom sheet and local pairing cancellation | `artifacts/ui-ux-block-20-samsung/20260614-contact-actions-cancel-pairing/` |
| 21 | QR happy-path fallback gating | `artifacts/ui-ux-block-21-samsung/20260614-qr-happy-path-fallback-gating/` |
| 22 | Plan sync, completion audit and remaining localization cleanup | `artifacts/ui-ux-block-22-samsung/20260614-plan-sync-completion-audit/` |
| 23 | Incoming-message right-swipe evidence | `artifacts/ui-ux-block-23-samsung/20260614-incoming-reply-swipe-evidence/` |
| 24 | Active/stale relationship local forget path | `artifacts/ui-ux-block-24-samsung/20260614-forget-relationship/` |
| 25 | Local Android final audit | `artifacts/ui-ux-final-audit/20260614-local-android-baseline/` |
| 26 | Phone-to-phone QR manual evidence harness | `scripts/capture_phone_to_phone_qr_evidence.sh` |
| 27 | Physical phone-to-phone QR scan evidence | `artifacts/ui-ux-phone-to-phone-qr/20260614-193923-samsung-to-xiaomi-physical-qr/` |
| 28 | Remaining gap handoff | `artifacts/ui-ux-final-audit/20260614-local-android-baseline/remaining_gap_handoff.md` |
| 29 | Xiaomi external camera QR handling | `artifacts/ui-ux-phone-to-phone-qr/20260614-xiaomi-camera-qr-issue/` |
| 30 | Navigation API cleanup | `artifacts/ui-ux-block-30-navigation-api-cleanup/navigation_api_cleanup.md` |
| 31 | Stale pending invite response fix | `artifacts/ui-ux-phone-to-phone-qr/20260614-samsung-invalid-xiaomi-qr/stale_pending_invite_response_fix.md` |
| 32 | Physical retry success after stale pending fix | `artifacts/ui-ux-phone-to-phone-qr/20260614-samsung-current-after-xiaomi-scan/samsung_scan_success_after_stale_pending_fix.md` |
| 33 | Fallback QR copy cleanup | `artifacts/ui-ux-block-33-fallback-qr-copy-cleanup/fallback_qr_copy_cleanup.md` |
| 34 | Evidence harness manifest integrity | `artifacts/ui-ux-block-34-evidence-harness-manifest-integrity/evidence_harness_manifest_integrity.md` |
| 35 | Nearby handshake confirmation packet | `artifacts/ui-ux-block-35-nearby-handshake-confirmation/nearby_handshake_confirmation.md` |
| 36 | Startup/welcome/splash regression fix | `artifacts/ui-ux-block-36-startup-launch-regression/startup_launch_regression_fix.md` |
| 37 | Live QR regression fix: generated QR no longer uses `intent://` | `artifacts/ui-ux-block-37-qr-regression-live/qr_regression_fix.md` |
| 38 | One-scan handshake response packet | `artifacts/ui-ux-block-38-one-scan-handshake-response/one_scan_handshake_response.md` |
| 39 | Local pause handoff at phone-required boundary | `artifacts/ui-ux-block-39-local-pause-handoff/local_pause_handoff.md` |
| 40 | Phone one-scan evidence, bottom-menu fix, pending progress UI | `artifacts/ui-ux-block-40-phone-one-scan-and-progress/phone_one_scan_and_progress.md` |
| 41 | Pairing lock and duplicate sync throttle | `artifacts/ui-ux-block-41-pairing-lock-and-sync-throttle/pairing_lock_and_sync_throttle.md` |

## Open Implementation Queue

P0 UI/UX gaps from plan:

- [ ] Implement Slice 6 Android bottom navigation polish: floating dark capsule, compact active pill, same destinations, no `Главная` tab.
- [ ] Implement Slice 6 Android composer emoji panel: left smile icon, bottom panel, emoji insertion at cursor, no inline emoji strip.
- [ ] Implement Slice 6 iOS `Чаты`: make the tab a real chat list, not dashboard/home content.
- [ ] Implement Slice 6 iOS `Контакты`: QR actions plus contact list are primary; manual/debug import details are secondary.
- [ ] Implement Slice 6 iOS `Реалмы`: realm list first; selected realm details/actions deeper in the flow.
- [ ] Implement Slice 6 iOS `Настройки`: user settings first; diagnostics/evidence lower or behind research/diagnostics.
- [ ] Implement Slice 6 macOS section alignment: expose `Чаты / Контакты / Реалмы` in the main desktop navigation while leaving Settings in a desktop-appropriate place.
- [x] Enlarge `Мой QR` on the real phone; reduce unused white field while preserving scan-safe quiet zone. Current note: QR is now visually larger and uses a smaller QR generator margin, but the code remains dense because the payload carries public key and invite metadata.
- [x] Make QR camera-friendlier outside the app. Current status: Block 37 supersedes the older Block 29 `intent://` attempt. Generated QR now uses compact `https://kraken.local/qr?...`; Kraken keeps legacy `intent://`, `kraken://`, and raw JSON decode support. Fully guaranteed stock-camera auto-open still needs verified HTTPS Android App Links with a real domain.
- [ ] Cross-platform QR scanner bug / scan evidence gap: the user-reported macOS scanner error came from `app-macos` in the main worktree. Current Android code has response/confirmation branches and QR URI normalization. Android physical camera scan is now proven, so this item is specifically macOS/main-worktree follow-up only and intentionally not edited in this Android UI/UX worktree.
- [x] Add normal-UI mesh start/restart control outside diagnostics.
- [x] Move `Исследовательский режим` out of network diagnostics.
- [x] Localize visible diagnostics/reports/labels; keep raw IDs only where unavoidable.
- [x] Remove `алгоритм Адамовой` from visible UI/report copy.
- [x] Long press selects messages; short tap opens single-message menu.
- [x] Remove full-screen flash/abrupt flicker when dismissing message action menu.
- [x] Reply swipe direction: incoming -> right, outgoing -> left; smooth animation. Current status: outgoing left-swipe and incoming right-swipe are both evidenced on Samsung.
- [x] Verify real QR scan happy path. Current status: Android QR generation, scanner copy, external URI routing, and post-decode pending confirmation UX are proven. Block 27 captured a real phone camera scan from Samsung scanner to Xiaomi QR, resulting in Samsung Contacts showing `Xiaomi` under `Ждут подтверждения`; Block 32 then captured the retry after stale-pending cleanup reaching an `ACTIVE` Samsung/Xiaomi relationship with a delivered outgoing message.
- [x] Decide or defer chat search. Implemented compact chat search in the chat root.
- [x] Audit failed-message inline retry and debug payload presentation.
- [x] Decide whether `Реалмы` stays in bottom nav. Decision: keep in bottom nav for this baseline.
- [x] Full screenshot audit across root tabs, QR flow, settings diagnostics, chat thread, message menu, message selection, and contact actions.
- [x] Keep `MESSENGER / PRIVATE / OFFLINE / SECURE` background text as a brand exception. Do not remove it during localization cleanup.
- [x] Remove `ответный QR` / `финальный QR` from Android user-facing source UI. Current status: Block 33 replaced those terms with neutral fallback wording and added guard coverage.
- [x] Implement messenger-style contact actions bottom sheet. Current status: profile exposes `Действия`; bottom sheet contains open chat, notification toggle, conversation cleanup, pending pairing cancellation, and active contact deletion through the reason flow.
- [x] Add local cancellation/removal for stale pending pairing. Current status: `Отменить` is visible for pending imports/handshakes in contacts; confirmation removes the local relationship and linked pending invite.
- [x] Add local forgetting/removal for stale active pairing. Current status: contact cards expose `Профиль`; profile actions expose `Забыть устройство`; confirmation removes the local relationship and linked pending invite without confirming the research/moderation unlink path.
- [x] Separate Back/Home semantics in the shared screen container. Current status: Block 30 removed stale `showHome`; root screens opt out of back via `showBack = false`, deep screens use `navController.popBackStack()`.
- [x] Allow retrying QR pairing after stale pending invite mismatch. Current status: Block 31 allows a newer valid response QR from the same peer to replace an older pending relationship instead of showing `This responder is already linked to another invite.` Block 32 physically verified the retry on Samsung/Xiaomi.
- [x] Keep QR evidence harness honest. Current status: Block 34 makes `captured_files` reflect only actual non-empty capture outputs.
- [x] Implement Android-side nearby auto-confirmation packet path. Current status: Block 35 adds `HANDSHAKE_CONFIRMATION` packets and proves pending responder -> active with in-memory two-node transport.
- [x] Restore startup/welcome/splash semantics. Current status: Block 36 removes timeout-based splash dismissal, starts the app at the branded welcome screen instead of chats, and hides the splash only after first-run readiness or mesh launch request/running state.
- [x] Stop generating Xiaomi-hostile `intent://` QR payloads. Current status: Block 37 switches generated QR to compact `https://kraken.local/qr?...`, keeps legacy decode support, installs the fresh APK on Samsung/Xiaomi, and records the remaining verified-domain boundary for universal external Camera auto-open.
- [x] Implement the missing first half of one-scan nearby completion. Current status: Block 38 adds `HANDSHAKE_RESPONSE` packets after scanning an invite and proves response -> confirmation -> active over in-memory two-node transport.
- [x] Capture physical two-phone one-scan nearby confirmation evidence over real transports. Current status: Block 40 proved Samsung -> Xiaomi physical QR scan advancing to `АКТИВЕН` on both phones without manual completion QR.
- [x] Restore bottom menu after startup/root navigation. Current status: Block 40 makes `ОТКРЫТЬ KRAKEN` open `Чаты` and renders the messenger bottom menu on the legacy `Home` root surface.
- [x] Add visible progress/status for nearby confirmation wait. Current status: Block 40 adds pending handshake status text plus a progress bar in contact cards.
- [x] Block conflicting UI interaction while nearby confirmation is running. Current status: Block 41 adds a blocking pending-pairing sheet with controlled fallback/cancel exits.
- [x] Reduce duplicate mesh sync pressure from the UI loop. Current status: Block 41 changes the Compose loop from `syncMeshIfRunning()` to `refreshMeshSnapshot()`; foreground service remains responsible for periodic sync.

## Subagent Audit Notes

- Prior standing subagents from the initial plan: Russell, Hypatia, Linnaeus.
- 2026-06-14 completion audit subagents:
  - Gauss / QR-navigation audit: PASS for QR generation, QR size, no chat QR duplication, auto-navigation, fallback gating; WEAK for Bluetooth/nearby active completion, which remains a transport follow-up.
  - Einstein / messenger interaction audit: PASS for unframed chat rows, Kraken title, route dot, anchored menu, long-press selection, group actions, failed retry, reply swipe direction; WEAK for dismiss flash because static evidence cannot prove absence of a transient flash.
  - Boole / localization audit was shut down after the user redirected to macOS handoff; local grep/tests cover the same acceptance area for this pass.
- 2026-06-14 final continuation subagents:
  - Laplace / plan-tracker audit: BLOCKED for claiming full goal completion because Bluetooth/nearby auto-confirmation and macOS scanner evidence remain external/open; stale plan status was fixed in Block 33.
  - Herschel / diff audit: PASS for Android QR/import/handshake/navigation/message changes; found P2 manifest-overclaim risk in `scripts/capture_phone_to_phone_qr_evidence.sh`, fixed in Block 34.

## Current Boundary

Current local block:

- Block 41 is complete locally for interaction locking and duplicate sync throttling.
- Xiaomi and Samsung have the Block 41 APK installed.

Next candidates after Block 41:

1. Slice 6 planning follow-up: implement Android capsule bottom nav and normal emoji panel, then align iOS/macOS screen responsibilities to the Android messenger model.
2. Optional fresh pending-pairing visual proof for the blocking progress sheet. This requires a new pairing attempt or forgetting the current active relationship first.
3. Optional reverse Xiaomi -> Samsung fresh-pairing proof, if the user wants symmetric physical evidence.
4. Optional Xiaomi stock Camera check against the new QR format. Guaranteed auto-open still needs a verified real Android App Link domain, not `kraken.local`.
5. macOS scanner fix only with explicit approval to touch the main worktree.
6. Final report/commit preparation once the macOS gap is intentionally closed or deferred by the user.

Do not mark the active goal complete until the open macOS boundary is either implemented with evidence or explicitly deferred by the user.

## Verification Commands

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel-ui-ux/app-android
./gradlew test assembleDebug
```

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel-ui-ux
git diff --check
adb -s R5CY22X6MSB install -r app-android/app/build/outputs/apk/debug/app-debug.apk
```

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel-ui-ux
bash -n scripts/capture_phone_to_phone_qr_evidence.sh
scripts/capture_phone_to_phone_qr_evidence.sh \
  --source-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --label samsung-to-xiaomi-physical-qr
```
