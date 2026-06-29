# Codex Implementation Audit

Date: 2026-05-18

Historical snapshot: this audit is superseded for current `10/10` and branch
state by `reports/out/current_project_readiness_2026-06-08.md`,
`reports/out/kraken_10_10_followup_audit_2026-06-10.md`,
`reports/out/kraken_10_10_completion_matrix_2026-06-10.md` and
`reports/out/kraken_10_10_completion_matrix_audit_2026-06-10.md`.

The older validation table below is retained as implementation history. It must
not be used to override the later release hard gate, route benchmark runner,
physical inline relay runner or completion matrix.

Current local update: 2026-06-06. The Android branch has advanced beyond the
original audit. QR rendering/scanning, QR lifecycle controls, offline mutual QR
handshake, branded launch assets, Research Panel guided examples, native C++
research benchmark support, Direct LAN NSD + TCP transport and manual
Samsung/Xiaomi two-phone LAN/Wi-Fi prototype smoke evidence now exist locally.
Production cryptography, Android Keystore-backed identity, signed QR proofs and
repeatable two-device delivery metrics are still not implemented.

Original audited branch: `codex/android-identity-invite-relationship-realm`

Current Android branch: `codex/android-research-panel-report-viewer`

Purpose: capture the real implementation depth of the current branch before more feature work. This audit separates complete implementation, partial implementation, scaffold-only work, docs-only work and tasks that still need manual review.

Current P2P/mesh safety boundary: `docs/prototype-mesh-threat-boundaries.md`
defines that LAN discovery is not trust, QR/offline handshake is the only source
of `ACTIVE` relationship, and prototype packet proofs are not production
signatures.

## Status Legend

- complete implementation: the requested local behavior exists, has tests, and builds.
- partial implementation: meaningful behavior exists, but important acceptance criteria are missing.
- scaffold only: interfaces, models, placeholders or UI shells exist, but the real behavior is not implemented.
- docs only: documentation was added without runtime behavior.
- skipped: planned but not implemented.
- unknown / needs manual review: implementation exists but requires device, emulator, screenshot or security review before calling it complete.

## Current Validation Snapshot

| Area | Current Result |
| --- | --- |
| Android build | `./gradlew assembleDebug` passes |
| Android unit tests | `./gradlew test` passes |
| Python compile | `python3 -m compileall .` passes |
| Python tests | `pytest -q` passes, 44 tests |
| Working tree | dirty with local cleanup/docs/tests/report changes |
| Install/run | prior Android device validation exists; no final release install was performed by this audit |
| Screenshots/evidence | manual two-phone LAN/Wi-Fi evidence and fresh post-cleanup chat capture exist |

## Top-Level Summary

| Item | Status | Related Commits | Main Risk |
| --- | --- | --- | --- |
| Phase 1 - Protocol specs | complete implementation | `38c4ffc` | Specs are early and may change |
| Phase 1.1 - Protocol spec validation | complete implementation | `97be277` | Markdown guard scope is intentionally lightweight |
| Phase 2 - Android skeleton | complete implementation | `8b9b6de` | Skeleton UX only |
| Phase 3 - Local identity | partial implementation | `4a8739f` | Placeholder key storage, no Android Keystore |
| Phase 4 - QR invite | partial implementation | `c4386fd`, `7a18d88`, `c2fedea`, `e2b4484`, `4e6da19` | QR UX exists; production signatures/proofs absent |
| Phase 5 - Relationship state machine | partial implementation | `12e0c1d` | Local-only unlink and handshake |
| Phase 6 - Realm/membership models | partial implementation | `9244f6a` | Local-only certificates and capacity |
| Phase 6.1 - Gradle wrapper/build | complete implementation | `ee9bf73` | Build warnings remain |
| Phase 6.2 - Build compatibility/UI polish | partial implementation | `3807735`, `dfc826f` | Needs visual manual review |
| Phase 6.3 - UI reference strategy | docs only | `cdcb1a5` | Not runtime behavior |
| Phase 6.4 - UI design/icons | partial implementation | `6c59670` | Icon readability needs device review |
| Phase 6.5 - Messenger UI refactor | partial implementation | `36b1e1c` | Visual quality still needs review |
| Phase 7 - Pending approval | partial implementation | `2154bd2` | Local-only workflow |
| Phase 8 - Delivery simulator | partial implementation | `a252f52` | Python simulator only, no Android transport |
| Phase 9 - Packet/transit buffer | partial implementation | `198888b` | Python core only, not integrated into app |
| Phase 10 - Receipts/tombstones | partial implementation | `94acfe7` | Local models only, no network propagation |
| Phase 11 - Crypto abstraction | scaffold only | `1078355` | Interfaces/test fake only, no production crypto |
| Phase 12 - LAN transport | prototype implementation | `49ced82`, `d1535fc`, `ac30559` | Direct LAN exists with manual two-phone prototype evidence; repeatable metrics and production security still missing |
| Phase 13 - Battery policy | partial implementation | `d159da8` | Policy evaluator only, no background orchestration |
| Phase 14 - Courier Score | partial implementation | `5478b65` | Local model only, no real relay telemetry |
| Phase 15 - Complaint/moderation | partial implementation | `7ef5ce3` | Local aggregate/model only |
| Phase 16 - Channels | partial implementation | `0149c79` | Local demo model only |
| Phase 17 - Small groups | partial implementation | `55532d5` | Local demo model only |
| Phase 18 - Research panel | partial implementation | `d0e5595`, `d5ce17e`, `8753754` | Diagnostic-only; not production crypto |
| Phase 19 - C++ research backend | partial implementation | `a47dc5c`, `8753754` | Native diagnostics/benchmark only; no protocol/crypto/network runtime |
| Phase 20 - Commission demo | docs only / partial UI | `b66d4be` | Demo guide exists, runtime demo remains limited |
| Improvement A - Screenshot readiness | partial implementation | `0ad75d4` | Demo state needs manual review |
| Improvement B - Design QA | partial implementation | `2cdf838` | Visual QA not performed with screenshots |
| Improvement C - UI architecture cleanup | partial implementation | `a528196` | Coupling may remain in app state/screen wiring |
| Improvement D - Persistence hardening | partial implementation | `26330d9` | SharedPreferences remain MVP storage |
| Improvement E - Policy guards | complete implementation | `38a6639` | Guard allowlist may need expansion |
| Improvement F - Build warnings | docs/partial build logic | `1bdf3e9` | Warnings remain by design |
| Improvement G - Android workflow README | docs only | `0a84220` | Needs user validation on local machine |
| Improvement H - Demo checklist | docs only | `26bee24` | Checklist not executed |
| Improvement I - Engineering backlog | docs only | `147a041` | Planning artifact only |
| Improvement J - Threat model | docs only | `a3c9819` | Draft, not formal security review |
| Improvement K - Simulator plan | docs only | `792957c` | Plan only |
| Improvement L - QR implementation plan | docs only | `cf54ad5` | Plan only |
| Design A - Icon concepts | partial implementation | `6fb7e32` | Experimental only |
| Design B - Home variants | partial implementation | `7d958e1` | Experimental only |
| Design C - Onboarding variants | skipped | none | Optional lower priority |
| Design D - Chat variants | partial implementation | `9bc3e28` | Experimental only |
| Design E - Realm variants | skipped | none | Optional lower priority |
| Design F - Settings variants | skipped | none | Optional lower priority |
| Design G - Copywriting options | skipped | none | Optional lower priority |
| Design H - Preview catalog | skipped | none | Optional lower priority |
| Design I - Demo data helper | partial implementation | `44c2528` | Needs safety review on device |
| Design J - UX decision matrix | docs only | `4c4aa02` | Recommendation only |
| Design K - UI implementation plan | docs only | `54bb144` | Plan only |

## Detailed Phase Audit

### Phase 1 - Protocol Specifications

- Status: complete implementation.
- Related commits: `38c4ffc Add Kraken protocol model specs`.
- Files changed: `protocol-spec/README.md`, `protocol-spec/schemas/*.md`.
- Actually implemented: Markdown protocol specifications and JSON examples for identity, invite, membership certificate, invite edge, relationship, unlink notice, packet, receipt, tombstone, complaint, complaint aggregate, realm policy, approval policy and capacity token.
- Placeholder/scaffold: all specs are design documents, not runtime protocol enforcement.
- Acceptance criteria satisfied: invite-only, no public discovery, QR handshake semantics, new key means new user, bilateral unlink, certificate-based membership and best-effort tombstone language are documented.
- Missing: formal schema files such as JSON Schema are not present.
- Tests: covered by `tests/test_protocol_spec_docs.py`.
- Manual review needed: terminology review before Kotlin/C++ model generation.
- Build impact: none.
- Risk notes: specs are intentionally early.

### Phase 1.1 - Protocol Specification Validation

- Status: complete implementation.
- Related commits: `97be277 Add protocol spec documentation validation`.
- Files changed: `tests/test_protocol_spec_docs.py`.
- Actually implemented: required section checks, JSON fenced block parsing, forbidden JSON key checks and README link coverage.
- Placeholder/scaffold: no runtime validation.
- Acceptance criteria satisfied: protocol spec docs are guarded against common drift.
- Missing: semantic validation beyond key names and section headings.
- Tests: self-contained pytest tests.
- Manual review needed: policy wording still needs human review.
- Build impact: Python tests only.
- Risk notes: intentionally lightweight.

### Phase 2 - Android Skeleton

- Status: complete implementation.
- Related commits: `8b9b6de Add Android skeleton for Kraken`.
- Files changed: `app-android/`, initial Compose app, CMake placeholder and README.
- Actually implemented: Android project skeleton, Kotlin/Compose app, navigation placeholders, native placeholder.
- Placeholder/scaffold: all screens were placeholder quality at this stage.
- Acceptance criteria satisfied: Android-first project exists with required screens and no account/public discovery flows.
- Missing: real product behavior, added in later phases.
- Tests: build and later Android unit tests.
- Manual review needed: app launch on device.
- Build impact: introduced Android build.
- Risk notes: none beyond skeleton maturity.

### Phase 3 - Local Identity Creation

- Status: partial implementation.
- Related commits: `4a8739f Add local identity creation`.
- Files changed: `identity/*`, app state, Create Identity, Home and Settings UI.
- Actually implemented: local identity model, secure-random placeholder key provider, SHA-256 fingerprint formatting, SharedPreferences persistence, display-name edit behavior and tests.
- Placeholder/scaffold: private key storage is a placeholder reference, not Android Keystore.
- Acceptance criteria satisfied: display name only, no device identifiers, identity reload after local storage, stable fingerprint on display-name change.
- Missing: Android Keystore non-exportable key integration and full key compromise lifecycle.
- Tests: `FingerprintFormatterTest`, policy guard tests.
- Manual review needed: create identity, restart app and verify identity persists.
- Build impact: Android app logic.
- Risk notes: should be labeled MVP storage until Keystore migration.

### Phase 4 - QR Invite Payload Export And Import

- Status: implemented for MVP QR UX; production signatures still missing.
- Related commits: `c4386fd Add QR invite payload import and export`, `7a18d88 Add real QR invite rendering`, `c2fedea Add QR invite scanner`, `e2b4484 Add QR lifecycle controls to My QR`, `4e6da19 Add offline mutual QR handshake`.
- Files changed: `invite/*`, `qr/*`, `handshake/*`, My QR screen, Import Invite screen, pending invite storage and relationship stores.
- Actually implemented: one-time invite payloads, real QR rendering, camera QR scanning, regenerate/revoke lifecycle controls, self-invite rejection, duplicate checks, pending import storage and three-step offline mutual QR handshake.
- Placeholder/scaffold: cryptographic invite signatures and handshake proofs are placeholders.
- Acceptance criteria satisfied: imported invite creates pending state and does not create active membership; active relationship requires reciprocal QR handshake.
- Missing: production signatures, Android Keystore integration and distributed consumed-state protocol.
- Tests: `InviteImportServiceTest`, `InviteQrCodeGeneratorTest`, `QrScanImportServiceTest`, `OfflineHandshakeServiceTest`.
- Manual review needed: two-device QR flow: invite QR -> response QR -> confirmation QR.
- Build impact: Android app logic.
- Risk notes: UX must not imply membership is granted.

### Phase 5 - Relationship State Machine

- Status: partial implementation.
- Related commits: `12e0c1d Add relationship state machine`.
- Files changed: `relationship/*`, Contacts and Chat UI.
- Actually implemented: relationship states, offline QR handshake state transitions, `canSendMessage`, unlink notices, blocked-by-peer behavior and complaint event creation for negative unlink with realm context.
- Placeholder/scaffold: unlink signatures and network delivery of unlink notices are placeholders.
- Acceptance criteria satisfied: send eligibility only for `ACTIVE`, rejoin semantics are modeled.
- Missing: real peer notice exchange and production signing.
- Tests: `RelationshipServiceTest`.
- Manual review needed: Contacts to Chat flow with pending/active/unlinked states.
- Build impact: Android app logic.
- Risk notes: local-only lifecycle should not be mistaken for distributed protocol behavior.

### Phase 6 - Realm And Membership Models

- Status: partial implementation.
- Related commits: `9244f6a Add realm and membership models`.
- Files changed: `realm/*`, Realms and Pending Approvals UI.
- Actually implemented: local realm model, realm policy, membership certificate placeholder, invite edge, pending membership request, approval policy and capacity state.
- Placeholder/scaffold: signatures, distributed capacity enforcement and networked approval workflow are absent.
- Acceptance criteria satisfied: demo realm creates default policy, capacity 500 and local membership certificate.
- Missing: signed certificate validation, distributed realm state and real invite acceptance edges.
- Tests: `RealmServiceTest`, `ApprovalEvaluatorTest`.
- Manual review needed: create demo realm and state transitions.
- Build impact: Android app logic.
- Risk notes: local realm ownership is demo-only until crypto/network phases mature.

### Phase 6.1 - Android Build Infrastructure

- Status: complete implementation.
- Related commits: `ee9bf73 Add Android Gradle wrapper and build validation`.
- Files changed: Gradle wrapper files and build config adjustments.
- Actually implemented: Gradle wrapper and command-line build path.
- Placeholder/scaffold: none.
- Acceptance criteria satisfied: `./gradlew test` and `./gradlew assembleDebug` are available.
- Missing: none for wrapper availability.
- Tests: Gradle commands.
- Manual review needed: Android Studio import if desired.
- Build impact: positive.
- Risk notes: wrapper should be reviewed before pushing if repository policy requires binary review.

### Phase 6.2 - Build Compatibility And UI Baseline Polish

- Status: partial implementation.
- Related commits: `3807735 Fix Android build compatibility and polish baseline UI`, `dfc826f Refactor Android UI and add dark theme baseline`.
- Files changed: CMake/build files, UI theme/components/screens/navigation.
- Actually implemented: 16 KB native linker setting, dark theme baseline, split UI files and reusable components.
- Placeholder/scaffold: visual quality still requires screenshots and manual review.
- Acceptance criteria satisfied: app builds and UI is no longer monolithic.
- Missing: verified screenshot QA on device.
- Tests: Gradle build/test.
- Manual review needed: phone/emulator visual pass.
- Build impact: positive, warnings remain.
- Risk notes: UI may still be inconsistent despite compilation.

### Phase 6.3 - UI Reference Strategy

- Status: docs only.
- Related commits: `cdcb1a5 Add Android UI reference strategy and design system`.
- Files changed: `docs/android-ui-reference-strategy.md` and UI architecture refinements.
- Actually implemented: reference strategy and component inventory.
- Placeholder/scaffold: no selected production UI direction enforced.
- Acceptance criteria satisfied: future UI tasks have a documented reference policy.
- Missing: screenshot evidence and final design choice.
- Tests: build/test after commit.
- Manual review needed: owner chooses direction.
- Build impact: minimal.
- Risk notes: references must remain inspiration only.

### Phase 6.4 - Kraken Custom Icon Baseline

- Status: partial implementation.
- Related commits: `6c59670 Add Kraken custom icon baseline`.
- Files changed: `ui/icons/*`, bottom navigation and action usage.
- Actually implemented: original Compose icon baseline and production nav icon replacement.
- Placeholder/scaffold: icons need readability QA at real screen size.
- Acceptance criteria satisfied: bottom nav no longer uses text letters.
- Missing: visual QA and possible icon refinement.
- Tests: Gradle build/test.
- Manual review needed: inspect bottom nav on phone.
- Build impact: UI compile only.
- Risk notes: abstract icons can reduce usability.

### Phase 6.5 - Messenger-Like UI Refactor

- Status: partial implementation.
- Related commits: `36b1e1c Refactor Android UI toward messenger baseline`.
- Files changed: production UI screens and components.
- Actually implemented: production screens moved toward messenger-like structure.
- Placeholder/scaffold: still not final design; many flows remain placeholders.
- Acceptance criteria satisfied: no public discovery/account UI introduced, build passes.
- Missing: screenshots, usability testing and final screen polish.
- Tests: Gradle build/test.
- Manual review needed: full app walkthrough.
- Build impact: UI logic.
- Risk notes: visual direction should be validated before more feature work.

### Phase 7 - Pending Approval Workflow

- Status: partial implementation.
- Related commits: `2154bd2 Add pending approval workflow`.
- Files changed: `realm/ApprovalEvaluator.kt`, realm models/store/UI.
- Actually implemented: local approval decisions, single-admin and threshold evaluation, pending user right restrictions and placeholder certificate issuance.
- Placeholder/scaffold: no distributed moderation or real signed approval exchange.
- Acceptance criteria satisfied: approval rules are locally testable.
- Missing: full UX and real membership certificate signing.
- Tests: `ApprovalEvaluatorTest`.
- Manual review needed: Pending Approvals screen behavior.
- Build impact: Android logic.
- Risk notes: local workflow only.

### Phase 8 - Local Delivery Simulator

- Status: partial implementation.
- Related commits: `a252f52 Add local delivery simulator`.
- Files changed: `src/disser_messenger/mesh/delivery_simulator.py`, `tests/test_local_delivery_simulator.py`.
- Actually implemented: Python store-carry-forward simulation with receipt/tombstone behavior.
- Placeholder/scaffold: not integrated into Android runtime.
- Acceptance criteria satisfied: A-B-C-Y simulation and buffer cleanup are testable.
- Missing: Android visualization and broader experiment suite.
- Tests: `tests/test_local_delivery_simulator.py`.
- Manual review needed: dissertation metric expectations.
- Build impact: Python only.
- Risk notes: simulator assumptions need documentation before research use.

### Phase 9 - Packet Model And Transit Buffer

- Status: partial implementation.
- Related commits: `198888b Add packet model and transit buffer`.
- Files changed: `src/disser_messenger/mesh/packet_buffer.py`, tests.
- Actually implemented: packet model, scope, TTL/expiration, duplicate suppression, copy budget and reserve policy.
- Placeholder/scaffold: Android app does not use this buffer.
- Acceptance criteria satisfied: core buffer rules are covered in Python tests.
- Missing: Kotlin/C++ shared model integration.
- Tests: `tests/test_packet_buffer.py`.
- Manual review needed: compare fields to protocol spec.
- Build impact: Python only.
- Risk notes: duplicated model risk with Android code.

### Phase 10 - Message Status, Receipts And Tombstones

- Status: partial implementation.
- Related commits: `94acfe7 Add message receipts and tombstone models`.
- Files changed: `src/disser_messenger/mesh/message_controls.py`, tests and Chat status UI references.
- Actually implemented: local message status transitions, HopAck, delivery/read receipt and tombstone target model.
- Placeholder/scaffold: no real packet propagation or signed tombstone exchange.
- Acceptance criteria satisfied: status transitions and best-effort deletion semantics are covered.
- Missing: Android integration beyond UI legend.
- Tests: `tests/test_message_controls.py`.
- Manual review needed: wording in UI and docs.
- Build impact: Python and UI references.
- Risk notes: tombstones must not be described as guaranteed deletion.

### Phase 11 - Safe Crypto Abstraction

- Status: scaffold only.
- Related commits: `1078355 Add safe crypto abstraction`.
- Files changed: `crypto/CryptoAbstractions.kt`, tests.
- Actually implemented: interfaces, key material references, encrypted payload model and test-only fake provider.
- Placeholder/scaffold: no production crypto provider.
- Acceptance criteria satisfied: no custom production primitives were added.
- Missing: reviewed library integration, Android Keystore integration and threat review.
- Tests: `CryptoAbstractionsTest`.
- Manual review needed: ensure fake provider cannot be used as production crypto.
- Build impact: Kotlin model/test only.
- Risk notes: must remain abstraction-only until security design is reviewed.

### Phase 12 - Android LAN Transport

Status: prototype implementation.
Related commits: `49ced82 Add LAN transport abstraction`, `d1535fc Add direct LAN NSD TCP transport`, `ac30559 Wire mesh queue sync and realm admin actions`.
Files changed: `transport/LanTransportModels.kt`, `mesh/DirectLanTransport.kt`, `mesh/LanFrameCodec.kt`, Mesh Status/Settings UI references and tests.
Actually implemented: transport adapter/session/request/summary models, Direct LAN NSD + TCP transport, 4-byte length-prefixed packet frames, local-LAN `INTERNET` permission, and Mesh diagnostics queue sync.
Placeholder/scaffold: production encryption, signed packet proofs, repeatable two-device harness and delivery latency metrics are still missing.
Acceptance criteria satisfied: no public discovery or external server dependency; LAN discovery remains separate from QR trust.
Manual evidence: `reports/out/two_device_delivery_evidence.md` tracks Samsung/Xiaomi LAN/Wi-Fi prototype smoke with message and receipt-level UI evidence; `reports/out/ble_two_device_delivery_evidence_2026-06-06.md` tracks manual Samsung/Xiaomi BLE direct-route evidence.
Missing: repeatable route-specific automation, latency/packet-loss metrics, BLE rejection/counter smoke and production security review.
Tests: `LanTransportModelsTest`, `LanFrameCodecTest`, `LanPermissionGuardTest`, `MeshServiceTest`, policy guards.
Manual review needed: rerun two-device LAN smoke after major transport/UI changes and capture fresh screenshots.
- Build impact: Kotlin model/test only.
- Risk notes: avoid accidental INTERNET/server assumptions.

### Phase 13 - Battery-Aware Orchestration

- Status: partial implementation.
- Related commits: `d159da8 Add battery-aware relay policy`.
- Files changed: `relay/*`, Settings and Mesh Status.
- Actually implemented: relay modes, battery policy model, forwarding evaluator and UI persistence.
- Placeholder/scaffold: no Android background scheduling, scan windows or OS battery integration.
- Acceptance criteria satisfied: evaluator tests cover low battery, charging/Wi-Fi modes and relay mode restrictions.
- Missing: real orchestration.
- Tests: `RelayPolicyTest`.
- Manual review needed: Settings behavior.
- Build impact: Android logic.
- Risk notes: must not imply actual scanning is running.

### Phase 14 - Courier Score

- Status: partial implementation.
- Related commits: `5478b65 Add local Courier Score model`.
- Files changed: `courier/*`, Mesh Status/Home/Settings summaries.
- Actually implemented: local score snapshots, daily summary and separation from relay reliability.
- Placeholder/scaffold: no real relay telemetry inputs.
- Acceptance criteria satisfied: privacy constraints such as no GPS/route/recipient fields are testable.
- Missing: real event ingestion and delayed aggregation UI review.
- Tests: `CourierScoreTest`.
- Manual review needed: ensure UI does not create leaderboard expectations.
- Build impact: Android model/UI.
- Risk notes: privacy claims must stay conservative.

### Phase 15 - Complaint And Moderation MVP

- Status: partial implementation.
- Related commits: `7ef5ce3 Add complaint and moderation MVP models`.
- Files changed: `relationship/ModerationModels.kt`, `ComplaintStore.kt`, Pending/Moderation UI references and tests.
- Actually implemented: complaint aggregate model, moderation action placeholders and governance escalation distinction.
- Placeholder/scaffold: no real moderator workflow or distributed complaint sync.
- Acceptance criteria satisfied: single complaint does not auto-ban; root escalation is separate.
- Missing: full moderation UX and abuse controls.
- Tests: `ComplaintModeratorTest`.
- Manual review needed: complaint wording and UI placement.
- Build impact: Android model/UI.
- Risk notes: avoid turning root governance into ordinary support desk.

### Phase 16 - Channels MVP

- Status: partial implementation.
- Related commits: `0149c79 Add channels MVP`.
- Files changed: `channel/*`, Channels screen and tests.
- Actually implemented: local channel model, roles, subscriber state, latest-N policy and demo channel behavior.
- Placeholder/scaffold: no real channel delivery.
- Acceptance criteria satisfied: no public discovery, realm-scoped channel model.
- Missing: real publish/subscribe and membership integration.
- Tests: `ChannelServiceTest`.
- Manual review needed: Channels screen.
- Build impact: Android model/UI.
- Risk notes: must not become public channel discovery.

### Phase 17 - Small Group MVP

- Status: partial implementation.
- Related commits: `55532d5 Add small group MVP models`.
- Files changed: `group/*`, Channels/Groups UI references and tests.
- Actually implemented: local small group model, max member limit, backlog/TTL/slow mode policy.
- Placeholder/scaffold: no real group messaging.
- Acceptance criteria satisfied: large groups are not introduced.
- Missing: approval integration and message delivery.
- Tests: `SmallGroupServiceTest`.
- Manual review needed: UI copy should emphasize small groups only.
- Build impact: Android model/UI.
- Risk notes: avoid feature creep into large group chat.

### Phase 18 - Research Panel MVP

- Status: partial implementation.
- Related commits: `d0e5595 Add research panel MVP`.
- Files changed: `research/*`, Research screen and tests.
- Actually implemented: Android-side research models and diagnostic-only UI placeholders.
- Placeholder/scaffold: no direct Python/C++ algorithm integration for all research routines.
- Acceptance criteria satisfied: production encryption claims are avoided.
- Missing: real benchmark integration and export workflow.
- Tests: `ResearchDiagnosticServiceTest`.
- Manual review needed: dissertation wording.
- Build impact: Android model/UI.
- Risk notes: do not imply algorithm improves production crypto.

### Phase 19 - C++ Core Migration Scaffold

- Status: scaffold only.
- Related commits: `a47dc5c Add C++ core migration scaffold`.
- Files changed: native C++ placeholder, `nativecore/*`, native boundary docs.
- Actually implemented: native core status/JNI boundary scaffold.
- Placeholder/scaffold: no real packet/policy/research migration.
- Acceptance criteria satisfied: native library still builds.
- Missing: meaningful C++ core logic.
- Tests: `NativeCoreBridgeTest`, Gradle assemble.
- Manual review needed: native ABI/package inspection.
- Build impact: native build path.
- Risk notes: watch 16 KB page-size compatibility.

### Phase 20 - Commission Demo Scenario

- Status: docs only / partial UI.
- Related commits: `b66d4be Add commission demo scenario`.
- Files changed: `docs/kraken-commission-demo-scenario.md`, optional demo checklist/UI references.
- Actually implemented: demo scenario document and limited in-app demo helper hooks.
- Placeholder/scaffold: no full scripted demo mode.
- Acceptance criteria satisfied: committee walkthrough is documented.
- Missing: real device rehearsal and screenshots.
- Tests: build/test only.
- Manual review needed: execute demo checklist.
- Build impact: minimal.
- Risk notes: demo must disclose placeholders honestly.

## Improvement Task Audit

### Improvement A - UI Screenshot Readiness

- Status: partial implementation.
- Related commits: `0ad75d4 Improve UI screenshot readiness`.
- Files changed: UI screens and demo/sample state hooks.
- Actually implemented: clearer empty states and sample/demo affordances.
- Placeholder/scaffold: no screenshots captured.
- Acceptance criteria satisfied: app is easier to review visually.
- Missing: actual screenshot artifact set.
- Tests: Gradle build/test.
- Manual review needed: full screenshot checklist.
- Build impact: UI only.
- Risk notes: demo data must stay clearly labeled.

### Improvement B - Design QA Pass

- Status: partial implementation.
- Related commits: `2cdf838 Polish Android design consistency`.
- Files changed: UI components/theme/screens.
- Actually implemented: spacing, badges, disabled states and visual consistency improvements.
- Placeholder/scaffold: no device screenshot proof.
- Acceptance criteria satisfied: build remains green.
- Missing: Pixel portrait overflow inspection.
- Tests: Gradle build/test.
- Manual review needed: visual QA.
- Build impact: UI only.
- Risk notes: subjective visual quality remains open.

### Improvement C - App Architecture Cleanup

- Status: partial implementation.
- Related commits: `a528196 Clean up Android UI architecture`.
- Files changed: `MainActivity.kt`, navigation, screens, components.
- Actually implemented: smaller activity and clearer package split.
- Placeholder/scaffold: no DI, and app state still centralizes much wiring.
- Acceptance criteria satisfied: no giant all-screen MainActivity.
- Missing: deeper architecture review.
- Tests: Gradle build/test.
- Manual review needed: code review for coupling.
- Build impact: UI structure.
- Risk notes: avoid over-abstracting before product flow stabilizes.

### Improvement D - Local Persistence Hardening

- Status: partial implementation.
- Related commits: `26330d9 Harden local persistence structure`.
- Files changed: `storage/KrakenStorageKeys.kt`, store classes.
- Actually implemented: centralized storage keys and clearer store boundaries.
- Placeholder/scaffold: still SharedPreferences, no encryption/Keystore migration.
- Acceptance criteria satisfied: fewer scattered magic keys.
- Missing: migration tests and sensitive storage design.
- Tests: existing model/store tests.
- Manual review needed: stored data inspection.
- Build impact: Android storage logic.
- Risk notes: private key placeholder must not be treated as production storage.

### Improvement E - Android Policy Guard Expansion

- Status: complete implementation.
- Related commits: `38a6639 Expand Android policy guard tests`.
- Files changed: `tests/test_android_policy_guards.py`.
- Actually implemented: manifest, source and UI label checks for forbidden permissions, fields and flows.
- Placeholder/scaffold: allowlist is limited.
- Acceptance criteria satisfied: major forbidden concepts are guarded.
- Missing: docs-aware allowlist can be improved.
- Tests: self-contained pytest test.
- Manual review needed: read false positives/false negatives when adding new docs.
- Build impact: Python tests only.
- Risk notes: guard must not block legitimate forbidden-word documentation.

### Improvement F - Gradle And Build Hygiene

- Status: docs/partial build logic.
- Related commits: `1bdf3e9 Clean up Android build warnings`.
- Files changed: Gradle config/docs.
- Actually implemented: safe build-warning cleanup where possible.
- Placeholder/scaffold: deprecated AGP warnings remain.
- Acceptance criteria satisfied: build still passes.
- Missing: AGP legacy variant root cause cleanup.
- Tests: Gradle build/test.
- Manual review needed: revisit with AGP/Kotlin version strategy.
- Build impact: build config.
- Risk notes: previous safe choice was to leave some warnings rather than break build.

### Improvement G - Android Workflow README

- Status: docs only.
- Related commits: `0a84220 Document Android build and run workflow`.
- Files changed: `app-android/README.md`.
- Actually implemented: build/run workflow documentation.
- Placeholder/scaffold: not runtime behavior.
- Acceptance criteria satisfied: commands and limitations are documented.
- Missing: user-specific Android Studio screenshot instructions may evolve.
- Tests: docs build not applicable.
- Manual review needed: execute workflow on project owner's machine.
- Build impact: none.
- Risk notes: docs can drift.

### Improvement H - Demo Checklist

- Status: docs only.
- Related commits: `26bee24 Add Kraken demo checklist`.
- Files changed: `docs/kraken-demo-checklist.md`.
- Actually implemented: demo checklist document.
- Placeholder/scaffold: checklist not executed.
- Acceptance criteria satisfied: pre-demo steps are captured.
- Missing: real rehearsal results.
- Tests: none beyond docs presence.
- Manual review needed: run checklist.
- Build impact: none.
- Risk notes: demo flow may expose placeholder rough edges.

### Improvement I - Engineering Backlog

- Status: docs only.
- Related commits: `147a041 Add Kraken engineering backlog`.
- Files changed: `docs/kraken-engineering-backlog.md`.
- Actually implemented: structured backlog.
- Placeholder/scaffold: planning artifact only.
- Acceptance criteria satisfied: future work is organized.
- Missing: owner prioritization.
- Tests: none.
- Manual review needed: adjust priorities.
- Build impact: none.
- Risk notes: backlog should not be treated as committed scope.

### Improvement J - Threat Model Draft

- Status: docs only.
- Related commits: `a3c9819 Add Kraken threat model draft`.
- Files changed: `docs/kraken-threat-model.md`.
- Actually implemented: initial threat model draft.
- Placeholder/scaffold: not formal security review.
- Acceptance criteria satisfied: main threat categories are documented.
- Missing: expert crypto/security review.
- Tests: none.
- Manual review needed: formal threat model review.
- Build impact: none.
- Risk notes: avoid claiming mitigations are complete.

### Improvement K - Simulator Experiment Plan

- Status: docs only.
- Related commits: `792957c Add simulator experiment plan`.
- Files changed: `docs/kraken-simulator-experiment-plan.md`.
- Actually implemented: dissertation-friendly experiment plan.
- Placeholder/scaffold: experiments not run.
- Acceptance criteria satisfied: parameters and metrics are listed.
- Missing: experiment harness and result collection.
- Tests: none.
- Manual review needed: align with dissertation methodology.
- Build impact: none.
- Risk notes: model assumptions need validation.

### Improvement L - QR Implementation Follow-Up

- Status: plan superseded by MVP implementation.
- Related commits: `cf54ad5 Add QR implementation plan`, `7a18d88 Add real QR invite rendering`, `c2fedea Add QR invite scanner`, `4e6da19 Add offline mutual QR handshake`.
- Files changed: `docs/kraken-qr-implementation-plan.md`, `invite/*`, `qr/*`, `handshake/*`, My QR and Import Invite screens.
- Actually implemented: QR generation, QR scanning, lifecycle controls and offline mutual QR handshake.
- Placeholder/scaffold: production signatures/proofs and distributed consumed-state synchronization are not implemented.
- Acceptance criteria satisfied: QR payload size is viable, QR import does not create active relationship directly, invalid/self/duplicate cases fail safely.
- Missing: production proof/signature validation and real P2P transport after handshake.
- Tests: invite, QR scanner and offline handshake tests.
- Manual review needed: two-device QR flow and copy removal from primary UX.
- Build impact: none.
- Risk notes: QR UX must preserve pending handshake semantics.

## Design Exploration Audit

### Design Exploration A - Icon Concept Sets

- Status: partial implementation.
- Related commits: `6fb7e32 Add experimental Kraken icon concepts`.
- Files changed: `ui/icons/experimental/ExperimentalKrakenIcons.kt`, `IconLabScreen.kt`, `docs/android-icon-concepts.md`.
- Actually implemented: Minimal Line, Glass Glyph and Abyss Geometry icon concepts with UI Lab preview.
- Placeholder/scaffold: no selected production replacement from these experimental sets.
- Acceptance criteria satisfied: variants are isolated and original.
- Missing: screenshot review and accessibility/readability pass.
- Tests: Gradle build/test.
- Manual review needed: inspect icons in UI Lab.
- Build impact: UI only.
- Risk notes: icon similarity/copyright risk should still be visually reviewed.

### Design Exploration B - Home Screen UX Variants

- Status: partial implementation.
- Related commits: `7d958e1 Add Home screen UX variants`.
- Files changed: `HomeUxVariants.kt`, `UiLabScreen.kt`, navigation, docs.
- Actually implemented: Messenger Hub, Privacy Onboarding Dashboard, Mesh Operations Dashboard and Liquid Glass Inspired variants.
- Placeholder/scaffold: production Home not replaced.
- Acceptance criteria satisfied: variants are isolated in UI Lab.
- Missing: screenshots and owner selection.
- Tests: Gradle build/test.
- Manual review needed: compare variants on device.
- Build impact: UI only.
- Risk notes: experimental UI should not become production by accident.

### Design Exploration C - Onboarding UX Variants

- Status: skipped.
- Related commits: none.
- Files changed: none.
- Actually implemented: none.
- Placeholder/scaffold: none.
- Acceptance criteria satisfied: not applicable.
- Missing: all onboarding variants.
- Tests: none.
- Manual review needed: decide whether still needed.
- Build impact: none.
- Risk notes: lower priority than Home/Chat/Icon exploration.

### Design Exploration D - Chat Screen UX Variants

- Status: partial implementation.
- Related commits: `9bc3e28 Add Chat screen UX variants`.
- Files changed: `ChatUxVariants.kt`, `UiLabScreen.kt`, `docs/android-chat-ux-variants.md`.
- Actually implemented: Classic Messenger, Privacy State First and Mesh Debug Hybrid variants.
- Placeholder/scaffold: no real sending and production Chat not replaced.
- Acceptance criteria satisfied: composer semantics are represented as variants.
- Missing: screenshot review and owner selection.
- Tests: Gradle build/test.
- Manual review needed: inspect active and blocked relationship states.
- Build impact: UI only.
- Risk notes: debug hybrid should not become default messenger UI.

### Design Exploration E - Realm And Mesh UX Variants

- Status: skipped.
- Related commits: none.
- Files changed: none.
- Actually implemented: none.
- Placeholder/scaffold: none.
- Acceptance criteria satisfied: not applicable.
- Missing: all realm variant preview composables and docs.
- Tests: none.
- Manual review needed: decide whether needed after Home/Chat choice.
- Build impact: none.
- Risk notes: current Realm UI may remain too administrative.

### Design Exploration F - Settings And Privacy UX Variants

- Status: skipped.
- Related commits: none.
- Files changed: none.
- Actually implemented: none.
- Placeholder/scaffold: none.
- Acceptance criteria satisfied: not applicable.
- Missing: all settings variants.
- Tests: none.
- Manual review needed: decide after privacy control direction is chosen.
- Build impact: none.
- Risk notes: Settings may still mix user and developer concepts.

### Design Exploration G - Copywriting Alternatives

- Status: skipped.
- Related commits: none.
- Files changed: none.
- Actually implemented: none.
- Placeholder/scaffold: none.
- Acceptance criteria satisfied: not applicable.
- Missing: copywriting options document.
- Tests: none.
- Manual review needed: useful before screenshot polish.
- Build impact: none.
- Risk notes: UI text may remain uneven.

### Design Exploration H - Compose UI Preview Catalog

- Status: skipped.
- Related commits: none.
- Files changed: none.
- Actually implemented: none.
- Placeholder/scaffold: none.
- Acceptance criteria satisfied: not applicable.
- Missing: preview catalog.
- Tests: none.
- Manual review needed: decide if Android Studio previews are worth maintaining.
- Build impact: none.
- Risk notes: previews can become maintenance burden.

### Design Exploration I - Demo Data Mode

- Status: partial implementation.
- Related commits: `44c2528 Add local demo data helper`.
- Files changed: `DemoDataSeeder.kt`, stores, `KrakenAppState.kt`, `UiLabScreen.kt`, `HomeScreen.kt`.
- Actually implemented: UI Lab load/reset demo data helper with sample identity-related objects, relationships, realm/channel/group/courier states.
- Placeholder/scaffold: demo data does not simulate full protocol.
- Acceptance criteria satisfied: local-only and not wired into normal Home.
- Missing: dedicated safety tests and manual reset verification.
- Tests: Gradle build/test and existing policy guards.
- Manual review needed: load/reset demo data on device.
- Build impact: Android logic/UI.
- Risk notes: demo data must remain clearly labeled.

### Design Exploration J - UX Decision Matrix

- Status: docs only.
- Related commits: `4c4aa02 Add Android UX decision matrix`.
- Files changed: `docs/android-ux-decision-matrix.md`.
- Actually implemented: decision matrix and recommendations.
- Placeholder/scaffold: no runtime behavior.
- Acceptance criteria satisfied: project owner can compare directions.
- Missing: screenshot evidence.
- Tests: build/test after commit.
- Manual review needed: select a direction.
- Build impact: none.
- Risk notes: matrix includes conceptual variants that were not implemented as composables.

### Design Exploration K - UI Implementation Plan

- Status: docs only.
- Related commits: `54bb144 Add Android UI implementation plan`.
- Files changed: `docs/android-ui-implementation-plan.md`.
- Actually implemented: migration plan, risks and screenshot checklist.
- Placeholder/scaffold: no runtime behavior.
- Acceptance criteria satisfied: controlled path from UI Lab to production UI.
- Missing: actual migration of selected design.
- Tests: build/test after commit.
- Manual review needed: approve plan.
- Build impact: none.
- Risk notes: plan should be revised after screenshots.

## UI Status

The app has a production UI and an experimental UI Lab. The UI Lab is intentionally isolated and contains icon, Home and Chat variants. The current production UI is buildable and functional for local prototype flows, but it still needs manual visual review and screenshots before claiming design quality.

## Security And Privacy Constraints Status

Current automated guards cover:

- forbidden Android permissions;
- forbidden active model fields;
- forbidden networking/device identifier APIs;
- forbidden account/public discovery UI labels.

Current implementation does not knowingly introduce phone/email/login/account registration, public discovery, device-id usage, external server/cloud dependencies or production crypto claims.

## Forbidden Scope Check

No known active implementation of:

- phone registration;
- email registration;
- login/password account registration;
- public discovery;
- nearby/global realm search;
- public user directory;
- IMEI, Android ID, MAC, serial or hardware fingerprint usage;
- production cryptography;
- cloud sync;
- analytics or ad SDKs.

## Remaining Warnings

- Gradle warns that `android.builtInKotlin=false` is deprecated.
- Gradle warns that `android.newDsl=false` is deprecated.
- Gradle warns that legacy variant APIs are obsolete.
- Configuration cache is suggested but not enabled.
- System `python3 -m pytest` fails because system Python lacks pytest.
- Android install/run and screenshots have not been performed in the latest checkpoint.

## Recommended Next Fixes

1. Keep raw phone evidence ignored and promote only curated summaries/reports.
2. Add repeatable message-send/receipt orchestration if delivery evidence must
   be reproducible without manual screen setup.
3. Export authoritative latency/loss/packet counters for LAN runs.
4. Add route-specific BLE send/rejection smoke with exported counters before
   claiming BLE reliability.
5. Decide whether Gradle legacy API warnings should be fixed now or deferred.
6. Keep primary QR UX QR-first; keep payload JSON only for technical/debug
   flows if needed.
7. Plan Android Keystore migration separately from UI work.
8. Keep production crypto and production reliability claims blocked until real
   implementation and review exist.
