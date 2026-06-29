# Kraken Current Project Readiness

Date: 2026-06-07.

Scope: current local checkout of
`/Users/cheenya/Projects/kraken-android-research-panel` on branch
`codex/android-research-panel-report-viewer`.

## Short Status

Kraken is now a working Android research/demo messenger prototype with local
identity, QR-established relationships, chats, realms, Research Panel, Adamova
C++ admission-gate evidence, LAN/Wi-Fi delivery evidence and BLE direct-route
evidence.

It is not a production secure messenger. Production encryption, Android
Keystore-backed identity, signed packet proofs and a security review remain open
work.

## Current Evidence

| Area | Status | Evidence |
| --- | --- | --- |
| Python syntax | passing | `python3 -m compileall -q scripts tests src`: passed on 2026-06-07 |
| Python support code | passing | `/Users/cheenya/Projects/disser-messenger-project/.venv/bin/python -m pytest`: 52 passed on 2026-06-07 |
| Android unit tests | passing | `./gradlew test`: BUILD SUCCESSFUL on 2026-06-07 |
| Android debug APK | passing | `./gradlew assembleDebug`: BUILD SUCCESSFUL on 2026-06-07; current local SHA-256 `af1ab1e3bc333cbd93fc0fb0ec8336c7052bbbd30a4456af2195cd64442eb0c4` |
| JSON artifacts | passing | `python3 -m json.tool` for current readiness-linked JSON payloads on 2026-06-07 |
| Whitespace diff check | passing | `git diff --check`: no output on 2026-06-07 |
| Dirty tree slice coverage | executed | dirty-tree slices were committed and pushed after user approval; see `reports/out/cleanup_execution_status_2026-06-07.md` |
| Non-phone cleanup status | passing | `reports/out/non_phone_cleanup_status_2026-06-07.md`: non-phone verification passed; phone-dependent capture split out |
| Cleanup execution | completed | Android/math commit slices pushed; stale local branches/stashes and remote `chore/research-mvp-scaffold-v2` removed; raw evidence retained |
| ADB capture readiness | two devices ADB-visible from current shell | `adb devices -l`: Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0` listed as `device` on 2026-06-07 |
| Fresh connected-phone UI capture | completed but bounded | `artifacts/two-phone-test/repeatable-20260607-201329-fresh-20260607-open-chat/`: both phones show the same chat with `Bluetooth напрямую`; delivered/error UI states are visible for APK SHA-256 `af1ab1e3bc333cbd93fc0fb0ec8336c7052bbbd30a4456af2195cd64442eb0c4`; this is not automated fresh-send/counter evidence |
| Two-phone LAN/Wi-Fi smoke | completed manually | `reports/out/two_device_delivery_evidence.md` |
| Two-phone BLE direct route | completed manually | `reports/out/ble_two_device_delivery_evidence_2026-06-06.md` |
| Adamova effectiveness pack | complete in current dirty evidence layer | `reports/out/adamova_effectiveness_completion_audit.md` |
| 10/10 research plan | added | `reports/out/kraken_10_10_readiness_plan_2026-06-07.md` |

## Research-Prototype Score

Current score: **7.5/10**.

This is enough for a serious dissertation prototype demo, but not enough for a
`10/10` evidence packet until route-specific counters, fresh two-phone
LAN/BLE smoke, final UI screenshots and commit slicing are complete. Fresh
device visibility is no longer the blocker: `adb devices -l` now shows both
phones as ADB-visible, and a fresh open-chat UI capture exists. That capture is
useful for device/APK provenance and manual chat-state evidence because it shows
both devices in the same Bluetooth-direct chat with delivered/error UI states.
It still does not close the route-specific counter gate because it does not
orchestrate a fresh send during the capture, export authoritative counters, or
record repeated latency/loss/retry history.

Use the detailed plan:

```text
reports/out/kraken_10_10_readiness_plan_2026-06-07.md
```

## What Changed Since 2026-06-06

- BLE direct-route evidence was found and tracked.
- Stale `BLE physical pending` wording was narrowed: BLE direct route is now
  manually evidenced; BLE route-specific rejection/counter automation remains
  pending.
- A `10/10` readiness definition was split from production readiness.
- After phones were reconnected, two fresh ADB bundles were collected:
  - `artifacts/two-phone-test/repeatable-20260607-200131-fresh-20260607-connected-phones/`
    proved ADB/device capture only; Samsung was not on Kraken UI and Xiaomi was
    on Developer Settings, so it is not Kraken delivery evidence.
  - `artifacts/two-phone-test/repeatable-20260607-200227-fresh-20260607-kraken-opened/`
    shows both devices on the Kraken welcome screen with APK/Git metadata.
  - `artifacts/two-phone-test/repeatable-20260607-201107-fresh-20260607-after-open/`
    shows both devices on Overview with one active contact; Xiaomi reports
    Bluetooth active and last error `ble-connection:19`.
  - `artifacts/two-phone-test/repeatable-20260607-201221-fresh-20260607-chats-tab/`
    shows both chat lists; Xiaomi shows `Вы: спвпрпо` with `доставлено`.
  - `artifacts/two-phone-test/repeatable-20260607-201329-fresh-20260607-open-chat/`
    shows the open chat on both phones with route label `Bluetooth напрямую`,
    delivered states and some error states.
- Current project status now distinguishes:
  - research-prototype readiness;
  - dissertation evidence readiness;
  - production secure messenger readiness.
- Post-approval cleanup was executed: Android and math slices were committed and
  pushed, audited local branches/stashes were removed, stale remote
  `chore/research-mvp-scaffold-v2` was deleted, and raw evidence was retained.

## Current Risks

- Raw evidence directories must not be bulk committed or deleted while reports
  reference them.
- BLE evidence currently proves manual direct-route UI state, not repeated
  reliability/latency.
- Adamova admission-gate evidence is for experimental profiles. It does not
  prove production crypto security.
- The fresh June 7 open-chat capture does not yet prove an automated fresh-send
  route run, rejection cases, latency/loss/retry history or authoritative packet
  counters.

## Immediate Next Actions

1. Add route-specific evidence export.
2. With both phones ADB-visible, run fresh two-phone LAN and BLE smoke with
   orchestrated sends, route labels, receipt UI and counters if a stronger demo
   packet is needed.
3. Keep the June 7 open-chat capture bounded as manual UI evidence, not
   automated reliability/counter proof.
4. Keep production crypto claims blocked.
