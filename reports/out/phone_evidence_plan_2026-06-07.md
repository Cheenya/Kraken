# Phone Evidence Plan

Date: 2026-06-07.

This is a separate plan for work that requires connected phones. The user
approved a fresh phone pass on 2026-06-07; the first execution captured ADB and
Kraken-opened provenance, then an open-chat UI bundle with Bluetooth-direct and
delivered/error states. It did not yet capture an orchestrated fresh send with
route counters.

## Execution Notes 2026-06-07

Current ADB status:

- Samsung `R5CY22X6MSB`: `device`, model `SM_S938B`;
- Xiaomi `d948ffd0`: `device`, model `2201122G`.

Captured bundles:

- `artifacts/two-phone-test/repeatable-20260607-200131-fresh-20260607-connected-phones/`
  - two ADB-visible devices and APK/Git metadata captured;
  - not Kraken evidence, because Samsung was on an unrelated screen and Xiaomi
    was on Developer Settings.
- `artifacts/two-phone-test/repeatable-20260607-200227-fresh-20260607-kraken-opened/`
  - both devices show the Kraken welcome screen;
  - APK SHA-256:
    `af1ab1e3bc333cbd93fc0fb0ec8336c7052bbbd30a4456af2195cd64442eb0c4`;
  - useful as connected-device and app-open provenance.
- `artifacts/two-phone-test/repeatable-20260607-201107-fresh-20260607-after-open/`
  - both devices show Overview with one active contact;
  - Xiaomi shows Bluetooth active and last error `ble-connection:19`.
- `artifacts/two-phone-test/repeatable-20260607-201221-fresh-20260607-chats-tab/`
  - both devices show chat lists;
  - Xiaomi shows `Вы: спвпрпо` with `доставлено`.
- `artifacts/two-phone-test/repeatable-20260607-201329-fresh-20260607-open-chat/`
  - both devices show the open chat with `Bluetooth напрямую`;
  - delivered and error UI states are visible;
  - useful as fresh manual UI evidence;
  - not enough for the remaining route-counter gate, because it does not
    orchestrate a fresh send during capture or export packet counters.

Open phone work after this pass: perform the LAN/Wi-Fi and BLE route-specific
smokes below with fresh sends and counters while the phones are ADB-visible.

## Preconditions

1. Both phones appear in `adb devices -l` as `device`, not `offline` or
   `unauthorized`. Met on 2026-06-07 for Samsung `R5CY22X6MSB` and Xiaomi
   `d948ffd0`.
2. The Android worktree state is captured before the run:
   - branch;
   - commit SHA;
   - `git status --short`;
   - APK path;
   - APK SHA-256.
3. The phones run the same APK hash recorded in the evidence manifest.
4. Capture output goes under ignored local artifacts, for example:

```text
artifacts/two-phone-test/repeatable-<timestamp>-<label>/
```

## LAN/Wi-Fi Fresh Smoke

Goal: refresh the already documented manual LAN/Wi-Fi two-phone evidence with
capture-time metadata and route-specific observations.

Collect:

- Samsung and Xiaomi screenshots;
- per-device UI XML;
- visible peer names and message state;
- route label or transport evidence;
- delivery receipt state;
- packet queue/counter snapshot if available;
- latency/loss/retry observations if available;
- manifest with device IDs, APK hash and Git state.

Safe claim after success:

> Kraken has fresh manual two-phone LAN/Wi-Fi prototype evidence for QR-gated
> local messaging under the captured device, APK and network conditions.

Still not allowed:

- production-secure messaging claim;
- broad reliability claim;
- latency/loss bounds without repeated measured runs;
- trust claim from LAN discovery alone.

## BLE Direct-Route Fresh Smoke

Goal: refresh the existing manual BLE direct-route evidence and add route-specific
negative cases where practical.

Collect:

- Samsung and Xiaomi screenshots;
- per-device UI XML;
- BLE route label or equivalent diagnostics;
- Bluetooth/GATT service evidence if available;
- delivered UI state;
- unknown peer rejection;
- wrong-recipient rejection;
- duplicate replay rejection;
- packet counter snapshot if available.

Safe claim after success:

> Kraken has fresh manual two-phone BLE direct-route prototype evidence with
> bounded rejection/counter observations for the captured APK and devices.

Still not allowed:

- broad BLE reliability claim;
- multi-hop BLE mesh claim;
- production transport security claim.

## Visual QA Pass

Goal: refresh screenshots that are likely to be shown to a supervisor or in the
dissertation evidence appendix.

Capture:

- start/overview screen;
- chats/contacts;
- My QR;
- scanner;
- mesh diagnostics;
- Research Panel;
- local realm screen;
- settings/prototype boundary screen.

Check:

- no stale `pending` wording for LAN/Wi-Fi or BLE evidence;
- no production-security claim;
- route labels do not overstate multi-hop behavior;
- Research Panel remains diagnostic/offline-report wording.

## Handoff

After the run, update:

- `reports/out/two_device_delivery_evidence.md`;
- `reports/out/ble_two_device_delivery_evidence_2026-06-06.md` or a new dated
  BLE evidence report if the capture date changes;
- `reports/out/current_project_readiness_2026-06-07.md` or a new dated readiness
  report;
- `docs/research-notes-index.md`;
- local dissertation `CURRENT.md`, `05`, `07` and `08` only if the fresh capture
  changes the claim boundary.
