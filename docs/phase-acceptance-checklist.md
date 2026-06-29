# Phase Acceptance Checklist

Use this checklist to decide whether each Kraken phase is ready, partial, scaffold-only or blocked. It is intentionally action-oriented and overlaps with the implementation audit.

## Phase 1 - Protocol Specifications

- Intended outcome: protocol specs for core Kraken models.
- Current status: complete implementation.
- Acceptance criteria:
  - `protocol-spec/README.md` exists.
  - all required schema docs exist.
  - JSON examples parse.
  - invite-only, no public discovery, QR handshake, new key means new user and bilateral unlink rules are documented.
- Manual verification:
  - read `protocol-spec/README.md`;
  - spot-check `identity.md`, `invite.md`, `relationship.md`, `packet.md`.
- Automated tests:
  - `tests/test_protocol_spec_docs.py`.
- Current gaps:
  - no JSON Schema files.
- Next action:
  - keep specs stable while Kotlin/C++ models evolve.

## Phase 1.1 - Protocol Specification Validation

- Intended outcome: prevent protocol spec drift.
- Current status: complete implementation.
- Acceptance criteria:
  - required Markdown sections are checked;
  - fenced JSON blocks parse;
  - forbidden JSON keys are rejected;
  - README links every schema.
- Manual verification:
  - confirm test failures are understandable if a schema is edited.
- Automated tests:
  - `tests/test_protocol_spec_docs.py`.
- Current gaps:
  - semantic validation is limited.
- Next action:
  - add JSON Schema only when protocol stabilizes.

## Phase 2 - Android Skeleton

- Intended outcome: buildable Android-first Kotlin/Compose app skeleton.
- Current status: complete implementation.
- Acceptance criteria:
  - `app-android/` exists;
  - required screens exist;
  - CMake/native boundary exists;
  - no account/public discovery/networking/crypto implementation was introduced.
- Manual verification:
  - install app and navigate all screens.
- Automated tests:
  - `./gradlew test`;
  - `./gradlew assembleDebug`.
- Current gaps:
  - early UI was placeholder quality, later improved.
- Next action:
  - continue device UI review.

## Phase 3 - Local Identity Creation

- Intended outcome: create and persist local identity.
- Current status: partial implementation.
- Acceptance criteria:
  - display name only;
  - identity key material generated with secure randomness placeholder;
  - fingerprint derived from public key;
  - identity reloads after app restart;
  - display-name edit does not change fingerprint.
- Manual verification:
  - create identity;
  - restart app;
  - edit display name;
  - verify fingerprint unchanged.
- Automated tests:
  - `FingerprintFormatterTest`;
  - Android policy guards.
- Current gaps:
  - no Android Keystore production storage;
  - no full key compromise flow.
- Next action:
  - plan Keystore migration separately.

## Phase 4 - QR Invite Payload Export And Import

- Intended outcome: show and scan one-time invite QR and create pending state.
- Current status: MVP implemented for QR-first local demo.
- Acceptance criteria:
  - My QR renders a scannable invite QR;
  - Import Invite routes scanner output through invite validation;
  - invalid/self/duplicate invite is rejected;
  - successful import creates pending state, not active membership.
- Manual verification:
  - show invite QR from one app state;
  - scan QR from a second app state/device;
  - verify pending state only.
- Automated tests:
  - `InviteImportServiceTest`.
  - `QrScanImportServiceTest`.
  - `OfflineHandshakeServiceTest`.
- Current gaps:
  - invite signatures are not implemented.
- Next action:
  - keep QR-first UX while adding message/P2P transport layers.

## Phase 5 - Relationship State Machine

- Intended outcome: local relationship lifecycle and unlink semantics.
- Current status: partial implementation.
- Acceptance criteria:
  - `PENDING_IMPORT -> PENDING_HANDSHAKE -> ACTIVE`;
  - send allowed only in `ACTIVE`;
  - unlink disables sending;
  - peer unlink moves to `BLOCKED_BY_PEER`;
  - negative unlink with realm context creates complaint.
- Manual verification:
  - use Contacts to start/accept handshake;
  - open Chat;
  - unlink relationship;
  - verify composer disabled.
- Automated tests:
  - `RelationshipServiceTest`.
- Current gaps:
  - no signed unlink notices;
  - no network exchange.
- Next action:
  - keep state machine local until crypto/network design is reviewed.

## Phase 6 - Realm And Membership Models

- Intended outcome: local realm/membership/capacity models.
- Current status: partial implementation.
- Acceptance criteria:
  - create demo realm;
  - capacity defaults to 500;
  - membership certificate placeholder exists;
  - invite edge and pending request models exist;
  - local state transitions work.
- Manual verification:
  - create demo realm;
  - pause/resume/archive/leave;
  - check capacity badge.
- Automated tests:
  - `RealmServiceTest`;
  - `ApprovalEvaluatorTest`.
- Current gaps:
  - no real signatures;
  - no distributed enforcement.
- Next action:
  - validate UI and protocol naming.

## Phase 6.1 - Android Build Infrastructure

- Intended outcome: command-line Android build/test.
- Current status: complete implementation.
- Acceptance criteria:
  - Gradle wrapper exists;
  - `./gradlew test` passes;
  - `./gradlew assembleDebug` passes.
- Manual verification:
  - run commands from `app-android`.
- Automated tests:
  - Gradle commands.
- Current gaps:
  - build warnings remain.
- Next action:
  - triage warnings after UI review.

## Phase 6.2 - Build Compatibility And UI Baseline

- Intended outcome: build compatibility and usable dark UI baseline.
- Current status: partial implementation.
- Acceptance criteria:
  - dark theme default;
  - MainActivity simplified;
  - reusable UI packages exist;
  - 16 KB native linker flag present.
- Manual verification:
  - inspect app on phone/emulator;
  - verify no text overflow.
- Automated tests:
  - Gradle test/assemble.
- Current gaps:
  - no screenshot evidence yet.
- Next action:
  - capture screenshots.

## Phase 6.3 - UI Reference Strategy

- Intended outcome: documented UI reference policy.
- Current status: docs only.
- Acceptance criteria:
  - `docs/android-ui-reference-strategy.md` exists;
  - reuse/copying policy documented;
  - screen targets and components listed.
- Manual verification:
  - read document before UI tasks.
- Automated tests:
  - none specific.
- Current gaps:
  - no runtime behavior.
- Next action:
  - keep using it for UI tasks.

## Phase 6.4 - Custom Icon Baseline

- Intended outcome: replace text-letter nav icons with original icons.
- Current status: partial implementation.
- Acceptance criteria:
  - production bottom nav uses icons;
  - custom icon baseline exists;
  - no copied messenger logos/assets.
- Manual verification:
  - inspect bottom nav on device.
- Automated tests:
  - Gradle build/test.
- Current gaps:
  - readability/accessibility review missing.
- Next action:
  - choose final icon direction after UI Lab review.

## Phase 6.5 - Messenger-Like UI Refactor

- Intended outcome: app feels more like a messenger prototype.
- Current status: partial implementation.
- Acceptance criteria:
  - Home, Contacts, Chat, Realms, Settings and Research are more coherent;
  - no public discovery/account UI appears;
  - bottom nav remains usable.
- Manual verification:
  - screenshot full app flow.
- Automated tests:
  - Gradle build/test.
- Current gaps:
  - visual quality still needs owner decision.
- Next action:
  - migrate selected UI Lab direction screen by screen.

## Phase 7 - Pending Approval Workflow

- Intended outcome: local approval policy and pending membership workflow.
- Current status: partial implementation.
- Acceptance criteria:
  - single-admin and threshold modes evaluate locally;
  - duplicate approvals count once;
  - eligible reject rejects;
  - approved request creates certificate placeholder.
- Manual verification:
  - create demo pending request;
  - approve/reject in Pending Approvals.
- Automated tests:
  - `ApprovalEvaluatorTest`.
- Current gaps:
  - local-only, no signed distributed workflow.
- Next action:
  - improve UI after realm review.

## Phase 8 - Local Delivery Simulator

- Intended outcome: local delivery simulation for dissertation reasoning.
- Current status: partial implementation.
- Acceptance criteria:
  - A-B-C-Y delivery scenario works;
  - receipt returns;
  - tombstone clears honest relay buffers;
  - expired/duplicate packets handled.
- Manual verification:
  - read simulator test and experiment plan.
- Automated tests:
  - `tests/test_local_delivery_simulator.py`.
- Current gaps:
  - no Android UI integration;
  - no experiment dataset.
- Next action:
  - run planned simulator experiments later.

## Phase 9 - Packet Model And Transit Buffer

- Intended outcome: reusable packet/buffer core.
- Current status: partial implementation.
- Acceptance criteria:
  - packet fields match protocol;
  - duplicate suppression works;
  - expiration and copy budget rules work;
  - reserve copy policy exists.
- Manual verification:
  - compare `packet_buffer.py` to `protocol-spec/schemas/packet.md`.
- Automated tests:
  - `tests/test_packet_buffer.py`.
- Current gaps:
  - Python-only, not Android/C++ shared.
- Next action:
  - decide common model ownership before migration.

## Phase 10 - Receipts And Tombstones

- Intended outcome: local status/control-message models.
- Current status: partial implementation.
- Acceptance criteria:
  - HopAck, DeliveryReceipt, ReadReceipt and Tombstone models exist;
  - status transitions are tested;
  - tombstone language remains best-effort.
- Manual verification:
  - inspect Chat status legend.
- Automated tests:
  - `tests/test_message_controls.py`.
- Current gaps:
  - no real network propagation or signing.
- Next action:
  - keep best-effort wording visible.

## Phase 11 - Safe Crypto Abstraction

- Intended outcome: crypto interfaces only.
- Current status: scaffold only.
- Acceptance criteria:
  - abstraction interfaces exist;
  - fake/test provider is test-only;
  - no production crypto claims.
- Manual verification:
  - inspect `crypto/CryptoAbstractions.kt`.
- Automated tests:
  - `CryptoAbstractionsTest`.
- Current gaps:
  - no real provider;
  - no security review.
- Next action:
  - design reviewed provider integration separately.

## Phase 12 - LAN Transport Abstraction

- Intended outcome: first serverless LAN transport path.
- Current status: prototype implementation.
- Acceptance criteria:
  - transport models exist;
  - Direct LAN NSD + TCP implementation exists;
  - Mesh diagnostics can start the transport and process the local message queue;
  - no public discovery;
  - no external server dependency.
- Manual verification:
  - inspect Mesh Status, transport models and `DirectLanTransport`;
  - rerun two-device LAN smoke after major transport/UI changes.
- Automated tests:
  - `LanTransportModelsTest`;
  - `LanFrameCodecTest`;
  - `LanPermissionGuardTest`;
  - `MeshServiceTest`;
  - policy guards.
- Current gaps:
  - manual Samsung/Xiaomi two-phone LAN/Wi-Fi prototype evidence is captured in
    `reports/out/two_device_delivery_evidence.md`;
  - repeatable capture helper exists at
    `scripts/capture_two_phone_smoke_evidence.sh`; fresh post-cleanup chat
    capture exists under
    `artifacts/two-phone-test/repeatable-20260606-195249-post-cleanup-chat-delivery/`;
  - no delivery latency/loss metrics yet;
  - no production encryption.
- Next action:
  - add mesh counter export or automate message-send/receipt orchestration if
    repeatable metrics are required.

## Phase 13 - Battery-Aware Relay Policy

- Intended outcome: battery/relay policy models and UI wiring.
- Current status: partial implementation.
- Acceptance criteria:
  - relay modes exist;
  - forwarding evaluator handles low battery, charging and Wi-Fi flags;
  - Settings persists relay mode.
- Manual verification:
  - change relay mode in Settings;
  - inspect Mesh Status summary.
- Automated tests:
  - `RelayPolicyTest`.
- Current gaps:
  - no background scanning/orchestration.
- Next action:
  - avoid implying active background behavior.

## Phase 14 - Courier Score

- Intended outcome: local privacy-preserving Courier Score model.
- Current status: partial implementation.
- Acceptance criteria:
  - daily aggregation model exists;
  - no GPS/route/recipient fields;
  - Courier Score separate from Relay Reliability.
- Manual verification:
  - inspect Mesh Status/Home summary wording.
- Automated tests:
  - `CourierScoreTest`.
- Current gaps:
  - no real relay telemetry inputs.
- Next action:
  - keep local/delayed wording.

## Phase 15 - Complaint And Moderation MVP

- Intended outcome: local complaint aggregates and moderation placeholders.
- Current status: partial implementation.
- Acceptance criteria:
  - negative unlink maps to complaint event;
  - complaint aggregation exists;
  - single complaint does not auto-ban;
  - root escalation is separate.
- Manual verification:
  - inspect Pending Approvals/Moderation surfaces.
- Automated tests:
  - `ComplaintModeratorTest`.
- Current gaps:
  - no full moderator workflow.
- Next action:
  - keep root governance limited.

## Phase 16 - Channels MVP

- Intended outcome: realm-scoped channels as wider-communication alternative.
- Current status: partial implementation.
- Acceptance criteria:
  - channel exists inside realm;
  - publisher role checked;
  - latest-N policy exists;
  - mute/leave states exist;
  - no public discovery.
- Manual verification:
  - create demo channel after demo realm.
- Automated tests:
  - `ChannelServiceTest`.
- Current gaps:
  - no real delivery or publish flow.
- Next action:
  - review channel UX after realm direction is selected.

## Phase 17 - Small Group MVP

- Intended outcome: strictly limited small groups.
- Current status: partial implementation.
- Acceptance criteria:
  - max members default exists;
  - cannot exceed limit;
  - backlog/TTL/slow mode policy exists;
  - no large public group UI.
- Manual verification:
  - inspect Channels/Groups section.
- Automated tests:
  - `SmallGroupServiceTest`.
- Current gaps:
  - no real messaging.
- Next action:
  - keep groups secondary to direct/channels.

## Phase 18 - Research Panel MVP

- Intended outcome: diagnostic research panel.
- Current status: partial implementation.
- Acceptance criteria:
  - diagnostic-only warning visible;
  - curve input/result/benchmark placeholders exist;
  - no production encryption claim.
- Manual verification:
  - inspect Research screen.
- Automated tests:
  - `ResearchDiagnosticServiceTest`.
- Current gaps:
  - Research Panel remains diagnostic-only and must not claim production crypto;
  - live UI/report export for the new profile-admission attack evidence still
    needs review.
- Next action:
  - align Research Mode wording with the product-admission boundary.

## Phase 19 - C++ Research Backend

- Intended outcome: native boundary for C++ research diagnostics.
- Current status: partial implementation.
- Acceptance criteria:
  - native library builds;
  - JNI/status boundary exists;
  - Adamova Stage A diagnostic backend is callable;
  - Kotlin-vs-C++ benchmark is available for research diagnostics.
- Manual verification:
  - inspect native core status in Research/Settings if exposed.
- Automated tests:
  - `NativeCoreBridgeTest`;
  - `./gradlew assembleDebug`.
- Current gaps:
  - native core is limited to status and Adamova diagnostics/admission support;
  - no production encryption, packet signatures or Keystore-backed crypto
    envelope.
- Next action:
  - keep native boundary small and limited to deterministic admission/diagnostic
    functions until production crypto architecture is reviewed separately.

## Phase 20 - Commission Demo Scenario

- Intended outcome: demo scenario and optional helper.
- Current status: docs only / partial UI.
- Acceptance criteria:
  - commission demo scenario doc exists;
  - demo checklist exists;
  - no fake server/public discovery claims.
- Manual verification:
  - run demo checklist on installed app.
- Automated tests:
  - build/test only.
- Current gaps:
  - no rehearsal screenshots;
  - no validated committee flow.
- Next action:
  - use manual review guide and screenshot script.
