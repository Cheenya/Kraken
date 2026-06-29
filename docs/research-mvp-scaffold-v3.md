# Kraken Research MVP Scaffold v3

Historical snapshot: this document is superseded as a current-state source by
`reports/out/current_project_readiness_2026-06-08.md`,
`reports/out/kraken_10_10_followup_audit_2026-06-10.md`,
`reports/out/kraken_10_10_completion_matrix_2026-06-10.md` and
`reports/out/kraken_10_10_completion_matrix_audit_2026-06-10.md`.

Keep the older commit hashes and dirty-tree notes below as historical context
only; they are not the current `10/10` or branch-state source of truth.

This document was the local truth for the Kraken research MVP after the
Android, QR, Research Panel, math-core and UI work done after
`chore/research-mvp-scaffold-v2`.

It exists because the GitHub `main` branch is still documentation-first, while
the local branch `codex/android-research-panel-report-viewer` contains the
active Android prototype work.

## Repository State

- Local branch: `codex/android-research-panel-report-viewer`.
- Remote default branch: `origin/main`.
- Active upstream:
  `origin/codex/android-research-panel-report-viewer`.
- Current local and upstream tip: `e542ea0 Refresh Kraken project readiness
  evidence`.
- Related active math worktree:
  `/Users/cheenya/Projects/disser-messenger-project` on
  `codex/math-experiment-evidence-pack`.
- Legacy remote branches `origin/chore/research-mvp-scaffold` and
  `origin/chore/research-mvp-scaffold-v2` still exist, but are no longer the
  Android truth source.
- Working tree currently contains cleanup/documentation/test/report changes;
  raw phone and benchmark artifacts remain ignored unless intentionally
  promoted into curated reports.

## Product Framing

Kraken is currently a local-first Android research prototype. The useful framing
is:

- messenger shell for QR-based trust establishment;
- Research Panel for curve diagnostics, evidence reports and benchmark results;
- no account registration;
- no phone/email login;
- no public discovery;
- no external server or cloud sync;
- no production crypto claim.

The dissertation math is a research/diagnostic layer. It must not be presented as
production message encryption or as a replacement for standard ECC libraries.

## What Is Implemented Locally

### Android Shell

- Kotlin/Compose Android app.
- Local identity creation with display name.
- Local persistence through SharedPreferences-backed stores.
- Russian-first UI in the current dirty layer.
- Quiet Graphite / Privacy Console direction as the main product UI.
- App icon and splash/start assets are finalized for the current Kraken Core
  direction and installed successfully on a physical phone.

### QR Invite Flow

- Real QR rendering for invite payloads.
- QR scanner using the camera.
- My QR lifecycle controls:
  - regenerate QR;
  - revoke QR;
  - state badge;
  - short invite metadata;
  - details disclosure.
- Import Invite can scan QR and route decoded payloads.
- JSON payloads may still exist internally for tests/debugging, but product UX is
  QR-only.

### Offline Mutual QR Handshake

- Versioned response payload:
  `kraken.handshake.response.v1`.
- Versioned final confirmation payload:
  `kraken.handshake.confirmation.v1`.
- Three-step offline handshake:
  1. A shows invite QR.
  2. B scans invite and becomes pending.
  3. B shows response QR.
  4. A scans response and becomes active with B.
  5. A shows final confirmation QR.
  6. B scans confirmation and becomes active with A.
- Invite import alone does not create `ACTIVE`.
- Self-handshake and wrong-recipient payloads are rejected.
- Proof/signature fields are placeholders and explicitly not production crypto.

### Contacts, Chats And Realms

- Contacts show active and pending relationships.
- Chat composer is enabled only for `ACTIVE` relationships.
- Chat UI is wired to local message state, packet queueing and prototype
  transport status transitions.
- Realm lifecycle UX has been reworked:
  - active/pending/left-archived grouping;
  - owner/admin/member action visibility;
  - technical IDs moved into details;
  - delete local record for left/archived realms.

### Research Panel

- Bundled Android-compatible math report contract:
  `kraken.math.curve_diagnostic.android.v1`.
- Research Panel reads offline JSON assets.
- Guided examples are tiered:
  - teaching;
  - validation;
  - research-scale.
- Large coefficient examples are bundled from the math evidence branch.
- Research-scale examples are marked as rational diagnostics over `Q`, not
  production finite-field ECC.

### Math Evidence

Implemented on the math/evidence branches, not all necessarily present in this
Android worktree:

- exact rational elliptic curve diagnostics;
- fixture corpus;
- SageMath comparison workflow;
- large-coefficient corpus;
- dissertation evidence reports;
- Android-compatible report exporter.

Important current evidence numbers:

- base SageMath comparison: 15 curves, 11 direct matches, 4 unsupported local,
  0 mismatches;
- large coefficient corpus: 20 curves, 20 direct matches, 0 mismatches;
- large coefficient benchmark: 5 runs, 20 curves per run, median total runtime
  22.8673 ms, p95 total runtime 24.1665 ms.

### Native Research Core

The Android branch contains a native C++ Adamova Stage A path and Android
benchmark wiring.

Measured on phone:

- Kotlin median total: 117.4933 ms;
- C++ median total: 6.1216 ms;
- C++ speedup by summed medians: 19.1931x;
- comparable exact rows: 4;
- comparable exact C++ speedup: 56.6869x;
- strongest stress case observed: 352.4328x.

This is a research diagnostic benchmark. It does not measure production message
encryption.

## What Is Not Implemented

### P2P Transport

Real P2P is partially implemented as Direct LAN NSD + TCP and BLE GATT
prototype transports, but it is not production-reliability proof.

Current implementation has:

- `TransportAdapter` interface;
- manual LAN peer/session/request models;
- `DebugLanTransportAdapter`;
- Direct LAN NSD + TCP transport;
- BLE GATT transport, chunk codec/reassembler and Android 12+ runtime
  permission flow;
- Mesh diagnostics action for sending/receiving the local message queue;
- local message store, packet envelope, outbox/inbox/seen/receipt stores,
  trust gate, duplicate/expiry/TTL checks and receipt handling.

Current implementation does not have:

- production encryption;
- repeatable automated two-device delivery harness;
- delivery latency and loss metrics;
- automated BLE route-specific send/rejection/counter evidence;
- Wi-Fi Direct or Nearby Connections implementation;
- real relay/store-carry-forward runtime;
- production-grade delivery reliability proof.

Therefore the accurate status is:

> QR trust establishment and Direct LAN prototype transport are implemented.
> Manual Samsung/Xiaomi two-phone LAN/Wi-Fi prototype smoke evidence and a fresh
> post-cleanup UI capture bundle exist. Manual Samsung/Xiaomi BLE direct-route
> evidence also exists. Repeatable automation, latency/loss metrics, BLE
> rejection/counter evidence, real relay runtime and production crypto remain
> missing.

### Production Crypto

Not implemented:

- audited identity key storage;
- Android Keystore production key handling;
- real E2EE session protocol;
- signed invites/relationships/control messages;
- production message encryption;
- production-grade curve/parameter selection logic.

### Messaging Runtime

Implemented at prototype level:

- SharedPreferences-backed local message store;
- persistent packet outbox/inbox/seen/receipt stores;
- packet serialization for the Android runtime;
- send/receive status transitions through the mesh runtime;
- duplicate/replay rejection for packet IDs.

Still not implemented:

- production-grade delivery reliability proof;
- latency/loss metric export across repeat runs;
- attachment handling;
- audited encrypted payload transport.

## GitHub Documentation Drift

GitHub `main` currently describes the project as documentation-first. That is no
longer true locally.

Recently corrected docs:

- `app-android/README.md` now describes QR, native research backend and current
  P2P limitations accurately.
- `docs/manual-review-guide.md` now treats QR as the primary user flow.
- `docs/test-coverage-map.md` now recognizes QR rendering/scanning coverage.
- `docs/morning-handoff-summary.md` now reflects the current Android branch.

Still treat older architecture docs as target plans unless they explicitly say a
feature is implemented.

## Current Cleanup Strategy

Cleanup is tracked as narrow changesets instead of one giant commit:

1. Documentation truth update.
2. QR-only UX cleanup.
3. Brand/splash/start screen asset cleanup.
4. Research attack and benchmark cleanup.
5. Native C++ core cleanup.
6. UI polish cleanup.
7. Final Android build/install validation.

Do not add more feature work before the current cleanup/docs/evidence state is
staged intentionally.

Each commit should have a narrow theme and should pass:

```bash
cd app-android
./gradlew test
./gradlew assembleDebug
```

When device validation matters:

```bash
PATH="$HOME/Library/Android/sdk/platform-tools:$PATH" ./gradlew installDebug
```

## Completed Cleanup Commits

- `6f448e5 Add current research MVP scaffold`
- `b1bef91 Update QR implementation audit status`
- `4229663 Move QR UX to QR-first copy`
- `0869377 Add Kraken branded launch experience`
- `8753754 Add native research backend benchmark`
- `c195a40 Remove b-zero guided curve examples`
- `bfd763a Polish Russian production UI copy`

Follow-up QA/docs cleanup now includes:

- artifact ignore rules;
- visual QA report;
- updated handoff docs;
- small start/splash copy fixes;
- branch/stash/tree audit;
- repeatable two-phone smoke capture helper;
- fresh post-cleanup two-phone chat capture bundle;
- generated cache cleanup.

## P2P MVP Scope

The narrow manual LAN MVP now exists at prototype level:

1. Message packet model in Android.
2. Outbox/inbox/seen/receipt stores.
3. Manual peer endpoint and discovered peer models.
4. Real `ServerSocket`/`Socket` LAN transport.
5. Length-prefixed packet framing.
6. Mesh diagnostics/exchange summary.
7. Encrypted-placeholder packet send path.
8. Delivery receipt processing.
9. `INTERNET` permission justified as local LAN only.
10. Manual Samsung/Xiaomi two-device LAN/Wi-Fi smoke.

Do not claim production P2P messaging readiness until repeatable automation,
latency/loss metrics, production crypto and route-specific BLE/routerless
reliability evidence exist.
