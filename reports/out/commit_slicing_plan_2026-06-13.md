# Kraken Commit Slicing Plan

Дата: 2026-06-13.
Repo: `/Users/cheenya/Projects/kraken-android-research-panel`.
Branch observed: `codex/android-research-panel-report-viewer`.

Цель: разрезать текущий dirty tree на логические группы без `git add .`.
Commit/push/deploy этим планом не выполнялись.

## Current Tree Boundary

Tracked modified files:

- `.gitignore`
- `app-android/app/src/debug/java/com/disser/kraken/debug/DebugEvidenceReceiver.kt`
- `app-android/app/src/main/AndroidManifest.xml`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshEvidenceExport.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshRuntime.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshService.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshTransportSelection.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectPermissions.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectTransport.kt`
- `app-android/app/src/main/java/com/disser/kraken/navigation/KrakenAppState.kt`
- `app-android/app/src/main/java/com/disser/kraken/navigation/KrakenNavHost.kt`
- `app-android/app/src/main/java/com/disser/kraken/ui/screens/SettingsScreen.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/LanPermissionGuardTest.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshEvidenceExportTest.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshServiceTest.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshTransportSelectionTest.kt`
- `scripts/audit_10_10_closure_suite_output.py`
- `scripts/capture_debug_route_evidence.sh`
- `scripts/kraken_phone_preflight.py`
- `scripts/run_10_10_closure_suite.sh`
- `tests/test_android_policy_guards.py`

Untracked files to classify:

- `app-android/app/src/test/java/com/disser/kraken/mesh/WifiDirectPermissionsTest.kt`
- `scripts/run_directed_wifi_direct_route_trial.sh`
- `reports/out/code_regression_audit_2026-06-12.md`
- `reports/out/phone_transport_audit_2026-06-12.md`
- `reports/out/transport_architecture_suspicion_plan_2026-06-12.md`
- `reports/out/transport_architecture_suspicion_plan_2026-06-12.ru.md`
- `handoff.md`
- raw evidence folders under `artifacts/phone-audit/` and `artifacts/ui-device-verification/`

Raw phone/UI evidence folders should remain uncommitted unless explicitly
requested. They are source evidence for reports, not normal source artifacts.

## Slice 1: Android 13+ Wi-Fi Direct Permission Model

Commit message:

```text
Fix Wi-Fi Direct fine-location permission diagnostics
```

Files:

- `app-android/app/src/main/AndroidManifest.xml`
- `app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectPermissions.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/WifiDirectPermissionsTest.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/LanPermissionGuardTest.kt`
- `scripts/kraken_phone_preflight.py`
- selected hunks from `tests/test_android_policy_guards.py`

Why:

- uncaps `ACCESS_FINE_LOCATION` for modern Android foreground Wi-Fi Direct;
- keeps `ACCESS_COARSE_LOCATION` legacy-capped;
- keeps `ACCESS_BACKGROUND_LOCATION` forbidden;
- adds declared/granted/app-op diagnostics for fine location.

Staging note:

- `tests/test_android_policy_guards.py` is mixed. Stage only the permission
  allowlist, manifest, `WifiDirectPermissions`, and preflight assertions here.

## Slice 2: Wi-Fi Direct Transport / Routing Fixes

Commit message:

```text
Stabilize Wi-Fi Direct endpoint resolution for directed sends
```

Files:

- `app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectTransport.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshService.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshServiceTest.kt`
- selected hunks from `tests/test_android_policy_guards.py`

Why:

- retries Wi-Fi Direct endpoint resolution before declaring send failure;
- requests connection/group information when a peer has no cached host;
- uses group/client IP fallback where Android exposes it;
- allows debug direct send to exercise transport fallback for a relationship
  peer that is not currently observed by `observePeers()`.

Staging note:

- `tests/test_android_policy_guards.py` is mixed. Stage only Wi-Fi Direct
  transport/endpoint assertions here.

## Slice 3: UX Boundary, Auto Mode And Diagnostics Toggle

Commit message:

```text
Hide Wi-Fi Direct from main Settings behind diagnostics mode
```

Files:

- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshTransportSelection.kt`
- selected hunks from `app-android/app/src/main/java/com/disser/kraken/mesh/MeshRuntime.kt`
- `app-android/app/src/main/java/com/disser/kraken/navigation/KrakenAppState.kt`
- `app-android/app/src/main/java/com/disser/kraken/navigation/KrakenNavHost.kt`
- `app-android/app/src/main/java/com/disser/kraken/ui/screens/SettingsScreen.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshTransportSelectionTest.kt`
- selected hunks from `tests/test_android_policy_guards.py`

Why:

- normal runtime starts with `MeshTransportSelection.AUTO`;
- removes the standalone user-facing Wi-Fi Direct switch;
- Settings shows `Маршруты связи` as `Авто`;
- tapping the row switches to `Проверка` and reveals diagnostic routes.

Staging note:

- `MeshRuntime.kt` is mixed. Stage only the `start()` / `MeshTransportSelection.AUTO`
  changes here. Leave packet inbox/received-packet accessors for Slice 4.

## Slice 4: Message-Id-Bound Evidence And Directed Harness

Commit message:

```text
Prove directed Wi-Fi Direct delivery with target message ids
```

Files:

- selected hunks from `app-android/app/src/main/java/com/disser/kraken/mesh/MeshRuntime.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshEvidenceExport.kt`
- `app-android/app/src/debug/java/com/disser/kraken/debug/DebugEvidenceReceiver.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshEvidenceExportTest.kt`
- `scripts/capture_debug_route_evidence.sh`
- `scripts/run_directed_wifi_direct_route_trial.sh`
- `.gitignore`
- selected hunks from `tests/test_android_policy_guards.py`

Why:

- exports bounded `target_delivery` with recent message ids and received packet
  ids;
- adds debug command fields for target-observed ids;
- adds `--sync-before-export` for target-after evidence;
- adds directed harness with `target-before`, `sender-send`, `target-after`;
- sets `message_delivery_proven=true` only when target newly observes matching
  sender `message_id` and `packet_id`.

Staging note:

- `DebugEvidenceReceiver.kt` is mixed with permission diagnostics from Slice 1.
  If strict atomic commits are required, use patch staging: permission fields
  in Slice 1, target delivery/debug-send retry fields in Slice 4.
- `scripts/capture_debug_route_evidence.sh` also includes several harness
  support changes (`launch_app_before_broadcast`, retry args). Keep them in
  Slice 4 because they support directed evidence collection.

## Slice 5: Closure Suite, Reports And Policy Guards

Commit message:

```text
Document phone audit results and update closure gates
```

Files:

- `scripts/run_10_10_closure_suite.sh`
- `scripts/audit_10_10_closure_suite_output.py`
- remaining selected hunks from `tests/test_android_policy_guards.py`
- `reports/out/code_regression_audit_2026-06-12.md`
- `reports/out/phone_transport_audit_2026-06-12.md`
- `reports/out/transport_architecture_suspicion_plan_2026-06-12.md`
- `reports/out/transport_architecture_suspicion_plan_2026-06-12.ru.md`
- optionally `handoff.md` after manual review

Why:

- closure suite uses the directed Wi-Fi Direct harness instead of a sequential
  multi-device capture for forward/reverse proof;
- audit script keeps `message_delivery_proven` as an explicit gate;
- reports preserve the initial blocker evidence and final phone rerun outcome;
- policy guards ensure future code cannot silently reintroduce overclaiming.

Staging note:

- `handoff.md` is untracked and may be pre-existing/user-facing. Review before
  inclusion. Do not stage raw `artifacts/phone-audit/` or
  `artifacts/ui-device-verification/` by default.

## Suggested Manual Staging Workflow

Use explicit paths and patch mode for mixed files:

```bash
git add app-android/app/src/main/AndroidManifest.xml
git add app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectPermissions.kt
git add app-android/app/src/test/java/com/disser/kraken/mesh/WifiDirectPermissionsTest.kt
git add app-android/app/src/test/java/com/disser/kraken/mesh/LanPermissionGuardTest.kt
git add scripts/kraken_phone_preflight.py
git add -p tests/test_android_policy_guards.py
```

For mixed files, prefer `git add -p` and verify with:

```bash
git diff --cached --stat
git diff --cached --check
git diff --cached --name-only
```

Never use `git add .` for this tree.

## Validation Already Observed Before This Plan

Recent checks from the current working state:

- `./gradlew test` passed.
- `./gradlew assembleDebug` passed.
- `/opt/homebrew/bin/pytest tests/test_android_policy_guards.py` passed.
- `python3 -m py_compile scripts/kraken_phone_preflight.py scripts/audit_10_10_closure_suite_output.py` passed.
- `bash -n scripts/capture_debug_route_evidence.sh scripts/run_directed_wifi_direct_route_trial.sh scripts/run_10_10_closure_suite.sh` passed.
- `git diff --check` passed.

Before committing each slice, rerun at least:

```bash
git diff --cached --check
/opt/homebrew/bin/pytest tests/test_android_policy_guards.py
cd app-android && ./gradlew test
```
