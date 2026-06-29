# Tree Cleanup Audit Log - 2026-06-15

Цель: разобрать текущие деревья Kraken, аккуратно смерджить нужные ветки/слои, решить конфликты и прийти к состоянию, где локально и на GitHub нет подозрительного грязного состояния.

Правила:
- Не откатывать пользовательские/предыдущие изменения без явной команды.
- Не делать push/deploy без явной команды.
- Не удалять evidence/artifacts физически без отдельного решения.
- Все cleanup-операции фиксировать здесь до/после выполнения.
- После сжатия контекста сначала читать этот файл, затем `git status --short --branch`, `git worktree list --porcelain`, `git branch -vv --all`.

## Как Вести Этот Журнал

Этот файл является рабочей памятью по cleanup/merge-аудиту. При любом сжатии контекста или сомнении сначала читать его, а не восстанавливать ход работ по чату.

Фиксировать здесь:

- снятые состояния дерева: ветка, upstream, ahead/behind, dirty/untracked группы;
- классификацию изменений: Android core, UI/UX, crypto, Wi-Fi Direct, macOS, docs/reports, artifacts;
- каждую манипуляцию с деревом: ignore/stage/merge/copy/move/delete, до и после;
- почему действие безопасно или почему оно отложено;
- какие проверки запущены и что они доказали;
- какие изменения нельзя трогать без отдельного согласования.

Не считать задачу завершенной, если журнал не обновлен после последнего существенного действия.

## Текущее Исходное Состояние

Снято в основном Android worktree:

- Repo root: `/Users/cheenya/Projects/kraken-android-research-panel`
- Это linked worktree от common repo `/Users/cheenya/Projects/disser-messenger-project/.git`
- Текущая ветка: `codex/android-research-panel-report-viewer`
- Upstream: `origin/codex/android-research-panel-report-viewer`
- Состояние ветки: ahead 17 относительно upstream
- HEAD: `797f9c4 Republish Wi-Fi Direct service after group owner prearm`
- `origin/codex/android-research-panel-report-viewer`: `1587cd3 Document production secure messenger scope`

Worktrees:

- `/Users/cheenya/Projects/disser-messenger-project`
  - branch: `codex/math-experiment-evidence-pack`
  - clean на момент аудита
- `/Users/cheenya/Projects/kraken-android-research-panel`
  - branch: `codex/android-research-panel-report-viewer`
  - dirty, ahead 17
- `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux`
  - branch: `codex/ui-ux-messenger-baseline`
  - dirty
  - HEAD также `797f9c4`, то есть UI/UX branch и Android branch сейчас указывают на один коммит, но имеют разные незакоммиченные слои

Remotes:

- `origin` -> `https://github.com/Cheenya/disser-messenger-project.git`
- `origin/main`: `e68ba27 Add archived author drafts and rights notice`

## Команды Уже Выполнены

```bash
git rev-parse --show-toplevel
git rev-parse --git-dir
git rev-parse --git-common-dir
git branch --show-current
git status --short --branch
git remote -v
git branch -vv --all --sort=-committerdate
git worktree list --porcelain
git stash list --date=local
git log --oneline --decorate --graph --all -n 80
git fetch --all --prune
git diff --stat -- . ':!artifacts' ':!reports/out'
git diff --name-only
git ls-files --others --exclude-standard
git diff --check
```

Результат `git diff --check`: чисто.

## Классификация Dirty State

### Основной Android worktree

Tracked modified, крупные группы:

- Android mesh/runtime/transport:
  - `app-android/app/src/main/java/com/disser/kraken/mesh/*`
  - `app-android/app/src/main/java/com/disser/kraken/navigation/KrakenAppState.kt`
  - `app-android/app/src/main/java/com/disser/kraken/qr/*`
  - `app-android/app/src/main/java/com/disser/kraken/invite/*`
- UI/UX:
  - `ChatScreen.kt` - главный merge hotspot, очень большой diff
  - `ContactsScreen.kt`
  - `MyQrScreen.kt`
  - `SettingsScreen.kt`
  - `ResearchScreen.kt`
  - navigation/components/icons
- Crypto / dissertation claim docs:
  - `crypto/*`
  - `docs/kraken-crypto-implementation-plan.md`
  - `docs/kraken-attack-scenarios-evidence.md`
  - `protocol-spec/README.md`
- Wi-Fi Direct/evidence scripts:
  - `scripts/run_directed_wifi_direct_route_trial.sh`
  - `scripts/capture_debug_route_evidence.sh`
  - `tests/test_android_policy_guards.py`

Untracked source/test files:

- `app-android/app/src/main/java/com/disser/kraken/crypto/AdamovaBoundCryptoEnvelope.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/AdamovaPacketCryptoBinding.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/QrHandshakeMessageSessionKeyProvider.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectConnectDiagnostics.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectEndpointResolver.kt`
- `app-android/app/src/main/java/com/disser/kraken/message/SavedMessageStore.kt`
- `app-android/app/src/main/java/com/disser/kraken/navigation/PendingInviteReconciliation.kt`
- `app-android/app/src/main/java/com/disser/kraken/qr/KrakenQrPayloadCodec.kt`
- `app-android/app/src/main/java/com/disser/kraken/realm/RealmRelayPolicy.kt`
- matching tests under `app-android/app/src/test/java/...`

Untracked large groups:

- `artifacts/`: 1657 files, about 278 MB
- `app-macos/`: 49 git-visible source/config files plus ignored build/dist/artifacts inside the directory
- `reports/out/`: 7 new report files
- `scripts/`: 7 new scripts
- `docs/`: 2 new runbook files
- `handoff.md`

### Existing tracked artifacts

`git ls-files artifacts` shows 41 tracked curated/evidence files, including:

- `artifacts/brand-crops/*`
- selected old evidence dirs under `artifacts/desktop-relay-inline`, `artifacts/desktop-relay-preflight`, `artifacts/device-screenshots`, `artifacts/phone-preflight`, `artifacts/ui-final-research-demo-*`

Important: do not blanket-ignore tracked files problematically; tracked files remain tracked even if `.gitignore` adds broader patterns.

## Known Recent Verification

Before this tree-cleanup goal, for the pairing blocker:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel/app-android
./gradlew :app:testDebugUnitTest --tests com.disser.kraken.mesh.MeshServiceTest --tests com.disser.kraken.navigation.PendingInviteReconciliationTest --tests com.disser.kraken.invite.InviteImportServiceTest --tests com.disser.kraken.qr.QrScanImportServiceTest
./gradlew assembleDebug
cd /Users/cheenya/Projects/kraken-android-research-panel
git diff --check
```

All passed at that time. APK was installed to Samsung and Xiaomi. User later confirmed pairing repeats stably in both directions after deleting contacts on both devices.

### 2026-06-15 - Post-cleanup phone sanity and one-scan QR confirmation

Context:

- Branch: `codex/kraken-android`
- Devices:
  - Samsung `R5CY22X6MSB`
  - Xiaomi `d948ffd0`
- APK: fresh `app-android` debug build from the current Android branch.
- Evidence directory from automated sanity capture:
  - `artifacts/post-cleanup-phone-sanity/20260615-101144/`

Automated sanity checks performed:

- `./gradlew assembleDebug` passed.
- APK installed successfully on both phones.
- Runtime permissions were granted on both phones, including:
  - `NEARBY_WIFI_DEVICES`
  - `ACCESS_FINE_LOCATION`
  - Bluetooth scan/connect permissions
  - notifications
- Kraken launched on both phones.
- The branded start screen was present.
- Main messenger UI opened on both phones.
- Bottom navigation was present: `Чаты`, `Контакты`, `Реалмы`, `Настройки`.
- Contacts screen opened on both phones and showed the existing active counterpart.
- `Мой QR` opened on both phones and showed a large one-time QR with status/timer/actions.
- Crash logs for launch/main/contacts/My QR captures were empty.
- Final logcat tail did not show a Kraken `FATAL EXCEPTION` or ANR.
- `git status --short --branch` stayed clean after the run.

Manual phone result confirmed by the user:

- `one-scan QR works on Samsung/Xiaomi after cleanup`.
- User manually checked the phone flow after the sanity build/install and reported: `one-scan QR сработал`.

What this proves:

- The current Android branch can be built, installed and opened on both phones.
- The current UI baseline has the start screen, messenger bottom navigation, Contacts and My QR paths intact.
- The post-cleanup one-scan QR pairing path works on the Samsung/Xiaomi phone pair in manual use.

What this does not prove:

- It is not Wi-Fi Direct route delivery evidence.
- It is not message-delivery evidence.
- It is not a cryptographic protocol proof.
- Direction-specific one-scan artifacts were not captured by an automated handshake harness in this run; the one-scan result is user-confirmed phone evidence plus the local sanity screenshots/logs above.

## Immediate Safe Next Steps

1. Add targeted `.gitignore` entries for newly generated evidence directories so `git status` stops being dominated by untracked artifacts, without deleting files.
2. Keep `app-macos/` visible for now; it is a real source tree and should become a separate commit/branch decision, not ignored as junk.
3. Compare Android and UI/UX dirty layers before staging:
   - main Android worktree vs `kraken-android-research-panel-ui-ux`
   - focus on `ChatScreen.kt`, `ContactsScreen.kt`, `MyQrScreen.kt`, `SettingsScreen.kt`, `KrakenAppState.kt`, `MeshService.kt`
4. Split likely commit slices:
   - evidence/artifact ignore hygiene
   - Android QR/handshake/pending cleanup
   - UI/UX messenger baseline
   - crypto-path/docs/tests
   - Wi-Fi Direct diagnostics/scripts
   - macOS app scaffold/transport, if intentionally kept in this repo
5. Run minimal verification per slice before commit.

## Open Questions / Risks

- Whether `app-macos/` should stay inside this repository or become a separate repo/worktree is still unresolved.
- `reports/out/*` are usually historical artifacts; earlier memory says do not rewrite archives unless explicitly requested. For new reports, decide whether to commit as dissertation evidence or ignore/move.
- `artifacts/` contains valuable raw evidence but is too large/noisy for GitHub by default. Prefer ignore + curated summaries in docs/reports.
- Main branch `codex/android-research-panel-report-viewer` is already ahead 17 and not pushed; pushing later will publish a lot of Wi-Fi Direct history, not only current cleanup.

## Cleanup Actions

### 2026-06-15 - Ignore generated artifacts noise

Action:

- Updated root `.gitignore` to ignore generated `artifacts/*` by default.
- Preserved `artifacts/brand-crops/**` as unignored because that directory contains curated tracked brand assets.
- No files were deleted.

Reason:

- `git ls-files --others --exclude-standard` showed 1657 untracked files under `artifacts/`, about 278 MB.
- These are raw evidence/device/test captures and should not dominate `git status` or be accidentally pushed to GitHub.
- Existing tracked files under `artifacts/` remain tracked by Git.

Verification after action:

```bash
git status --short --branch
git ls-files --others --exclude-standard
git diff --check
git check-ignore -v artifacts/phone-one-scan-proof/sentinel
git check-ignore -v artifacts/brand-crops/new-test.png
```

Observed:

- `git diff --check` stayed clean.
- `artifacts/phone-one-scan-proof/sentinel` is ignored by `.gitignore:17:artifacts/*`.
- `artifacts/brand-crops/new-test.png` is unignored by `.gitignore:20:!artifacts/brand-crops/**`.
- Untracked count dropped to 84.

## Cross-Worktree Audit

### 2026-06-15 - Android vs UI/UX dirty layers

Compared:

- Main Android worktree: `/Users/cheenya/Projects/kraken-android-research-panel`
- UI/UX worktree: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux`

Commands:

```bash
git status --short --branch
git diff --name-only -- \
  app-android/app/src/main/java/com/disser/kraken/ui/screens/ContactsScreen.kt \
  app-android/app/src/main/java/com/disser/kraken/navigation/KrakenAppState.kt \
  app-android/app/src/main/java/com/disser/kraken/mesh/MeshService.kt \
  app-android/app/src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt
```

Observed:

- Both worktrees point at the same base commit `797f9c4`.
- Therefore normal branch merge is not enough: the important divergence is in uncommitted tracked/untracked changes.
- Main Android worktree is ahead of upstream by 17 commits and contains the latest phone-tested pairing fixes.
- UI/UX worktree contains useful historical UI/UX artifacts and some older implementation slices, but it is not safe to copy wholesale.

Decision:

- Treat main Android worktree as the current canonical runtime state for QR/pairing/handshake behavior.
- Treat UI/UX worktree as a source for selective recovery only.
- Do not overwrite these main files from UI/UX wholesale without a file-level diff review:
  - `ContactsScreen.kt`
  - `KrakenAppState.kt`
  - `MeshService.kt`
  - `ChatScreen.kt`

Reason:

- Earlier phone work established that main Android has the newer pairing behavior after fixing repeatable one-scan pairing and deleting stale contacts on both devices.
- UI/UX has evidence/planning material worth preserving, but it also contains stale UX/runtime pieces that can reintroduce already-fixed regressions.

Safe candidates to import from UI/UX after review:

- `docs/ui_ux_messenger_baseline_plan.md`
- `docs/ui_ux_messenger_baseline_tracker.md`
- `scripts/capture_phone_to_phone_qr_evidence.sh`
- selected screenshot/evidence summaries, not raw artifact directories

Unsafe candidates for direct overwrite:

- screen/runtime files that participate in pairing, pending invites, mesh service startup, blocking overlays, contact deletion, or saved messages.

### 2026-06-15 - Imported UI/UX ledger files into main worktree

Action:

- Copied from `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux` into main Android worktree:
  - `docs/ui_ux_messenger_baseline_plan.md`
  - `docs/ui_ux_messenger_baseline_tracker.md`
  - `scripts/capture_phone_to_phone_qr_evidence.sh`
- Marked `scripts/capture_phone_to_phone_qr_evidence.sh` executable.

Reason:

- These files are planning/evidence harness artifacts, not runtime code.
- They preserve the UI/UX block history and evidence boundaries inside the main cleanup tree.
- This reduces the chance that future context compaction or branch cleanup loses the UI/UX baseline state.

Verification:

```bash
bash -n scripts/capture_phone_to_phone_qr_evidence.sh
git diff --check -- \
  .gitignore \
  docs/tree-cleanup-audit-log-2026-06-15.md \
  docs/ui_ux_messenger_baseline_plan.md \
  docs/ui_ux_messenger_baseline_tracker.md \
  scripts/capture_phone_to_phone_qr_evidence.sh
```

Observed:

- Shell syntax check passed.
- Diff whitespace check passed.

Boundary:

- No Android runtime files were copied from UI/UX in this action.
- No artifacts were copied.
- No commit/push was performed.

### 2026-06-15 - Staged first cleanup slice

Action:

- Staged only the hygiene/ledger/evidence-harness slice:
  - `.gitignore`
  - `docs/tree-cleanup-audit-log-2026-06-15.md`
  - `docs/ui_ux_messenger_baseline_plan.md`
  - `docs/ui_ux_messenger_baseline_tracker.md`
  - `scripts/capture_phone_to_phone_qr_evidence.sh`

Reason:

- This slice is safe and self-contained.
- It records the cleanup process, preserves UI/UX decision history, and reduces artifact noise.
- It intentionally does not stage Android runtime code, crypto code, Wi-Fi Direct code, macOS code, or reports.

Verification:

```bash
git diff --cached --name-status
git diff --cached --check
bash -n scripts/capture_phone_to_phone_qr_evidence.sh
git diff --cached --stat
```

Observed:

- Staged files:
  - `M .gitignore`
  - `A docs/tree-cleanup-audit-log-2026-06-15.md`
  - `A docs/ui_ux_messenger_baseline_plan.md`
  - `A docs/ui_ux_messenger_baseline_tracker.md`
  - `A scripts/capture_phone_to_phone_qr_evidence.sh`
- `git diff --cached --check` passed.
- `bash -n` passed.
- Staged diff size: 5 files, 1505 insertions.

Boundary:

- At staging time, no commit was made.
- No push was made.

Commit:

- First cleanup slice was committed locally with message `Document tree cleanup and UI UX ledger`.
- Push was not performed.

## Subagent Findings

### 2026-06-15 - Bernoulli / dirty-state commit-slice audit

Scope:

- Read-only classification of current dirty state in main Android worktree.

Findings:

- Current staged slice is the cleanest first commit candidate:
  - `.gitignore`
  - `docs/tree-cleanup-audit-log-2026-06-15.md`
  - `docs/ui_ux_messenger_baseline_plan.md`
  - `docs/ui_ux_messenger_baseline_tracker.md`
  - `scripts/capture_phone_to_phone_qr_evidence.sh`
- Remaining unstaged groups should be split into separate slices:
  - Android mesh/runtime/Wi-Fi Direct
  - QR / invite / pending pairing
  - crypto / Adamova-bound context
  - UI/UX messenger baseline
  - realm/store-and-forward/message persistence
  - docs/reports/policy guards
  - macOS scaffold/transport bridge

Do not mix:

- `app-macos/` with Android runtime, Wi-Fi Direct, or UI slices.
- `ChatScreen.kt` / `ContactsScreen.kt` / `SettingsScreen.kt` with `MeshService.kt`, `WifiDirectTransport.kt`, or `WifiDirectEndpointResolver.kt`.
- historical `reports/out/*` with feature code.
- crypto binding files with Wi-Fi Direct endpoint resolver.
- QR/pending invite reconciliation with UI baseline, except minimal compile-required screen adaptations.

Suggested checks:

- Hygiene slice:
  - `git diff --cached --check`
  - `bash -n scripts/capture_phone_to_phone_qr_evidence.sh`
- Wi-Fi Direct:
  - `./gradlew :app:testDebugUnitTest --tests com.disser.kraken.mesh.WifiDirectEndpointResolverTest --tests com.disser.kraken.mesh.WifiDirectConnectDiagnosticsTest --tests com.disser.kraken.mesh.WifiDirectPeerBindingTest`
  - `./gradlew assembleDebug`
- QR/pending:
  - `./gradlew :app:testDebugUnitTest --tests com.disser.kraken.navigation.PendingInviteReconciliationTest --tests com.disser.kraken.qr.KrakenQrPayloadCodecTest --tests com.disser.kraken.qr.QrScanImportServiceTest --tests com.disser.kraken.invite.InviteImportServiceTest`
- Crypto:
  - `./gradlew :app:testDebugUnitTest --tests com.disser.kraken.crypto.AdamovaBoundCryptoEnvelopeTest --tests com.disser.kraken.mesh.QrHandshakeMessageSessionKeyProviderTest --tests com.disser.kraken.crypto.CryptoAbstractionsTest`
- UI/UX:
  - `./gradlew :app:testDebugUnitTest --tests com.disser.kraken.ui.MainUiCopyGuardTest --tests com.disser.kraken.ui.WelcomeBrandingGuardTest --tests com.disser.kraken.ui.screens.ChatMeshStatusLabelTest`
  - `./gradlew assembleDebug`
- Docs/policy:
  - `python3 -m pytest tests/test_android_policy_guards.py`
  - `git diff --check`
- macOS:
  - SwiftPM build/test in `app-macos`
  - shell syntax checks for related scripts

### 2026-06-15 - Ptolemy / app-macos audit

Scope:

- Read-only classification of untracked `app-macos/`.

Findings:

- `app-macos/` is a real SwiftPM/macOS source tree, not junk.
- It contains:
  - `Package.swift`
  - `README.md`
  - `.gitignore`
  - `.codex/environments/environment.toml`
  - `Sources/**`
  - `script/build_and_run.sh`
- There is no nested `.git` repo inside `app-macos/`.
- Local build artifacts exist but are ignored by `app-macos/.gitignore`:
  - `.build/` about 385 MB
  - `dist/` about 13 MB

Decision:

- Do not delete or blanket-ignore `app-macos/`.
- Do not mix `app-macos/` into Android cleanup commits.
- Treat it as a separate macOS/desktop transport bridge slice or, later, a separate repo decision if it becomes an independent product.

Suggested checks:

```bash
cd app-macos
swift build --build-path /tmp/kraken-desktop-build
swift run --build-path /tmp/kraken-desktop-build KrakenDesktopCoreSmoke
```

Boundary:

- `./script/build_and_run.sh --verify` is not read-only: it writes `dist/`, uses `.build/`, signs, and launches the app.

## Next Slice Order

### 2026-06-15 - Remaining dirty tree decomposition

Current staged slice:

1. Hygiene / cleanup ledger / UI-UX ledger
   - Status: staged.
   - Files:
     - `.gitignore`
     - `docs/tree-cleanup-audit-log-2026-06-15.md`
     - `docs/ui_ux_messenger_baseline_plan.md`
     - `docs/ui_ux_messenger_baseline_tracker.md`
     - `scripts/capture_phone_to_phone_qr_evidence.sh`
   - Verification already run:
     - `git diff --cached --check`
     - `git diff --check`
     - `bash -n scripts/capture_phone_to_phone_qr_evidence.sh`

Recommended next slices:

2. Wi-Fi Direct endpoint/diagnostics/evidence
   - Includes:
     - `WifiDirectTransport.kt`
     - `WifiDirectPeerBinding.kt`
     - `WifiDirectEndpointResolver.kt`
     - `WifiDirectConnectDiagnostics.kt`
     - Wi-Fi Direct tests
     - directed Wi-Fi Direct scripts
     - `tests/test_android_policy_guards.py`
   - Keep separate from crypto and UI.

3. QR / invite / pending pairing / one-scan reconciliation
   - Includes:
     - `InviteImportService.kt`
     - `InviteQrCode.kt`
     - `IssuedInviteLifecyclePolicy.kt`
     - `PendingInviteStore.kt`
     - `PendingInviteReconciliation.kt`
     - `KrakenQrPayloadCodec.kt`
     - `QrScannerScreen.kt`
     - related tests
   - This is the phone-tested pairing behavior; do not overwrite from UI/UX worktree.

4. Crypto / Adamova-bound context path
   - Includes:
     - `AdamovaBoundCryptoEnvelope.kt`
     - `AdamovaPacketCryptoBinding.kt`
     - `QrHandshakeMessageSessionKeyProvider.kt`
     - `CryptoAbstractions.kt`
     - crypto and mesh crypto tests
     - active crypto docs/protocol spec
   - Keep claim wording simple but accurate: Adamova is policy/context binding around normal crypto, not a replacement for AEAD/key exchange.

5. Realm / relay / saved messages
   - Includes:
     - `RealmRelayPolicy.kt`
     - `SavedMessageStore.kt`
     - `SimulatedStoreAndForwardRelay.kt`
     - `PacketStores.kt`
     - `MessageModels.kt`
     - related tests
   - Needs careful boundary with mesh pipeline and UI saved messages.

6. UI/UX messenger baseline runtime
   - Includes:
     - `ChatScreen.kt`
     - `ContactsScreen.kt`
     - `SettingsScreen.kt`
     - `MyQrScreen.kt`
     - `ScreenContainer.kt`
     - bottom nav/components/icons
     - UI copy tests
   - Do not copy wholesale from UI/UX worktree; current main has newer pairing fixes.

7. Docs/reports/evidence
   - Includes active docs, new runbooks, handoff, selected reports.
   - Keep `reports/out/*` separate because these are historical/dissertation outputs.

8. macOS desktop transport bridge
   - Includes `app-macos/` and macOS-specific scripts/reports.
   - Treat as standalone branch/commit-slice decision.

Decision:

- Do not start by staging Android runtime code until the first hygiene slice is either committed or intentionally left staged.
- If committing is approved, commit the staged hygiene slice first, then work slice-by-slice.
- If committing is not approved yet, continue read-only classification and prepare path lists/check commands without staging more runtime files.

### 2026-06-15 - Android baseline verification before runtime slice

Action:

- Ran targeted Android runtime tests before staging the broad app slice:

```bash
cd app-android
./gradlew :app:testDebugUnitTest \
  --tests com.disser.kraken.mesh.MeshServiceTest \
  --tests com.disser.kraken.mesh.MeshDeliveryPipelineTest \
  --tests com.disser.kraken.navigation.PendingInviteReconciliationTest \
  --tests com.disser.kraken.qr.KrakenQrPayloadCodecTest \
  --tests com.disser.kraken.crypto.AdamovaBoundCryptoEnvelopeTest \
  --tests com.disser.kraken.mesh.WifiDirectEndpointResolverTest \
  --tests com.disser.kraken.mesh.WifiDirectConnectDiagnosticsTest
```

Observed:

- `BUILD SUCCESSFUL`.

Then ran:

```bash
cd app-android
./gradlew test assembleDebug
```

First result:

- Failed only `MainUiCopyGuardTest.contactManagementExposesLocalForgetPathForStalePairing`.
- Root cause: guard still expected old blocking-sheet copy from Block 41:
  - `Остальные действия временно заблокированы`
  - `Не сработало - показать резервные действия`
- Current main UI intentionally keeps fallback/cancel available after the user reported permanent blocking of `Отменить` / `Не получилось через Bluetooth?`.

Fix:

- Updated `MainUiCopyGuardTest` to assert current desired behavior:
  - visible nearby progress text stays;
  - `Не получилось через Bluetooth?` remains present;
  - `Остальные действия временно заблокированы` must not be present.

Verification after fix:

```bash
cd app-android
./gradlew :app:testDebugUnitTest --tests com.disser.kraken.ui.MainUiCopyGuardTest
./gradlew test assembleDebug
```

Observed:

- Focused UI copy guard passed.
- Full `test assembleDebug` passed.

Commit:

- Android baseline slice was committed locally with message `Integrate Android mesh pairing and messenger baseline`.
- Push was not performed.

### 2026-06-15 - Policy/docs verification before docs/scripts slice

Action:

- Ran script syntax/compile checks:

```bash
python3 -m py_compile \
  scripts/build_directed_wifi_direct_trial_manifest.py \
  scripts/extract_wifi_direct_peer_hint.py \
  scripts/audit_10_10_closure_suite_output.py

bash -n \
  scripts/capture_debug_route_evidence.sh \
  scripts/run_directed_wifi_direct_route_trial.sh \
  scripts/run_route_benchmark_trials.sh \
  scripts/capture_manual_nearby_freeze_smoke.sh \
  scripts/capture_wifi_p2p_appop_noise.sh \
  scripts/run_crypto_attack_unit_suite.sh \
  scripts/run_macos_android_lan_adb_bridge_trial.sh \
  scripts/run_macos_ble_probe.sh
```

Observed:

- `py_compile` passed.
- `bash -n` passed.

Then attempted:

```bash
python3 -m pytest tests/test_android_policy_guards.py tests/test_protocol_spec_docs.py
```

Observed:

- Failed because the current `python3` points to Python 3.14 without module `pytest`.
- Used brew pytest instead:

```bash
/opt/homebrew/bin/pytest tests/test_android_policy_guards.py tests/test_protocol_spec_docs.py
```

First result:

- Failed `test_wifi_direct_is_auto_transport_or_debug_profile_not_main_chat_mode`.
- Root cause: policy guard expected stale UI copy:
  - Settings mode labels `Авто` / `Проверка`;
  - old `routeSettingsMode.next()` behavior;
  - old long Wi-Fi Direct / hotspot explanation copy.
- Current UI uses segmented route mode labels `Обычно` / `Диагностика`, clickable mode selection, and compact MeshStatus actions `С точкой доступа` / `Wi‑Fi Direct`.

Fix:

- Updated `tests/test_android_policy_guards.py` to assert current UI behavior and to reject stale `Авто` / `Проверка` labels.

Verification after fix:

```bash
/opt/homebrew/bin/pytest tests/test_android_policy_guards.py tests/test_protocol_spec_docs.py
```

Observed:

- `70 passed`.

Commit:

- Docs/policy/scripts slice was committed locally with message `Update Kraken docs policy guards and evidence scripts`.
- Push was not performed.

### 2026-06-15 - macOS desktop transport bridge verification

Scope:

- `app-macos/`
- `scripts/run_macos_android_lan_adb_bridge_trial.sh`
- `scripts/run_macos_ble_probe.sh`

Verification:

```bash
cd app-macos
swift build --build-path /tmp/kraken-desktop-build
swift run --build-path /tmp/kraken-desktop-build KrakenDesktopCoreSmoke
```

```bash
bash -n scripts/run_macos_android_lan_adb_bridge_trial.sh scripts/run_macos_ble_probe.sh
```

Observed:

- SwiftPM build completed successfully.
- `KrakenDesktopCoreSmoke passed`.
- macOS shell scripts passed syntax check.

Boundary:

- Build output was written to `/tmp/kraken-desktop-build`, not to repo-local `.build`.
- `reports/out/*` are not part of this macOS source slice.

Commit:

- macOS desktop transport bridge slice was committed locally with message `Add macOS desktop transport bridge`.
- Push was not performed.

### 2026-06-15 - Reports/out evidence slice

Scope:

- Remaining dirty state after Android, docs/scripts, and macOS commits was limited to `reports/out/*`.
- Modified tracked reports:
  - `reports/out/adamova_admission_gate_attack_demo.md`
  - `reports/out/adamova_effectiveness_dissertation_table.md`
  - `reports/out/dissertation_final_insert_packet_2026-06-08.md`
- New reports:
  - `reports/out/crypto_mesh_goal_completion_audit_2026-06-15.json`
  - `reports/out/crypto_mesh_goal_completion_audit_2026-06-15.md`
  - `reports/out/crypto_mesh_technical_status_2026-06-15.json`
  - `reports/out/crypto_mesh_technical_status_2026-06-15.md`
  - `reports/out/macos_desktop_transport_bridge_trial_2026-06-14.md`
  - `reports/out/non_phone_transport_crypto_audit_2026-06-14.md`
  - `reports/out/non_phone_transport_crypto_commit_slices_2026-06-14.md`

Verification:

```bash
git diff --check -- reports/out
```

Observed:

- Diff whitespace check passed.

Commit:

- reports/evidence slice was committed locally with message `Refresh dissertation evidence reports`.
- Push was not performed.

## Final Tree Cleanup State

### 2026-06-15 - Local and GitHub state after cleanup

Actions completed after the report slice:

- UI/UX worktree `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux` was cleaned without reset/delete:
  - added broad generated-artifact ignore for `artifacts/*`;
  - committed tracked UI/UX WIP and selected untracked source/docs/scripts locally as `Archive UI UX messenger baseline worktree`;
  - raw UI/UX screenshots/artifacts were not deleted and were not committed.
- Main Android branch was pushed to GitHub.

Published branch:

- `codex/android-research-panel-report-viewer`
- Local HEAD after first push: `247db6a65f54c6e19f7ae80a26c20723c99fe261`
- Remote HEAD after first push: `247db6a65f54c6e19f7ae80a26c20723c99fe261`

Clean-state audit:

```bash
git fetch --all --prune
git status --short --branch
git rev-parse HEAD
git rev-parse origin/codex/android-research-panel-report-viewer
git stash list --date=local
```

Observed for `/Users/cheenya/Projects/kraken-android-research-panel`:

- clean worktree;
- local HEAD matched origin;
- stash list empty.

Observed for `/Users/cheenya/Projects/disser-messenger-project`:

- clean worktree;
- local `codex/math-experiment-evidence-pack` matched origin.

Observed for `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux`:

- clean worktree;
- local archive branch HEAD: `6ebb6b68d8e1dba7723453d465b134e06a91da2c`;
- no remote UI/UX branch exists, so the archive branch was not pushed.

Note:

- This final journal update itself requires a follow-up commit/push after the first clean-state audit.

### 2026-06-15 - Final four-branch GitHub layout

Target layout requested by the user:

- dissertation branch;
- Android branch;
- macOS branch;
- archive branch.

Final remote branches:

- `codex/kraken-dissertation`
- `codex/kraken-android`
- `codex/kraken-macos`
- `codex/kraken-archive`

Default GitHub branch:

- `codex/kraken-android`

Branch cleanup actions:

- Renamed/published Android work as `codex/kraken-android`.
- Renamed/published dissertation work as `codex/kraken-dissertation`.
- Created/published macOS work branch `codex/kraken-macos` from the verified Android/macOS HEAD.
- Renamed UI/UX WIP branch into `codex/kraken-archive`.
- Added `docs/archive-branch-manifest-2026-06-15.md` on the archive branch.
- Merged old `origin/chore/research-mvp-scaffold` into archive with `ours` strategy, preserving its history without overwriting archive contents.
- Old `origin/main` was already an ancestor of the archive branch.
- Changed GitHub default branch from `main` to `codex/kraken-android`.
- Deleted obsolete remote branches:
  - `main`
  - `codex/android-research-panel-report-viewer`
  - `codex/math-experiment-evidence-pack`
  - `chore/research-mvp-scaffold`
- Deleted obsolete local branch:
  - `main`

Verification:

```bash
git ls-remote --heads origin
gh repo view Cheenya/disser-messenger-project --json defaultBranchRef
git branch -vv --all --sort=-committerdate
git status --short --branch
```

Observed before this note commit:

- GitHub had exactly 4 heads:
  - `codex/kraken-android`
  - `codex/kraken-archive`
  - `codex/kraken-dissertation`
  - `codex/kraken-macos`
- default branch was `codex/kraken-android`.
- Android, dissertation, and archive worktrees were clean.
