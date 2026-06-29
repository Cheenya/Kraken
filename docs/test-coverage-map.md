# Test Coverage Map

This map connects current automated tests to Kraken features and lists the next useful tests. It does not claim security validation or production readiness.

## Summary

| Area | Tests Present | Current Coverage | Main Missing Tests |
| --- | --- | --- | --- |
| Protocol spec validation | yes | good for docs drift | semantic schema validation |
| Android policy guards | yes | good for forbidden active scope | docs allowlist strategy |
| Identity | yes | fingerprint/key-label invariants | Android Keystore integration |
| Invite/QR import | yes | payload validation, QR rendering/scanning import path and pending import | signatures and cross-device consumed-state sync |
| Relationship state machine | yes | local lifecycle and unlink | signed peer unlink exchange |
| Realm/membership | yes | local demo realm and approval evaluator | signed certificate validation |
| Pending approval | yes | evaluator rules | full UI interaction test |
| Simulator | yes | Python delivery scenario | experiment sweeps/results |
| Packet/transit buffer | yes | Python buffer policies | Android/C++ parity |
| Receipts/tombstones | yes | Python status transitions | signed tombstone propagation |
| Crypto abstraction | yes | fake/test provider behavior | real provider contract tests |
| LAN transport abstraction | yes | model/interface behavior | real LAN socket tests |
| Battery policy | yes | forwarding evaluator | Android battery/Wi-Fi integration |
| Courier Score | yes | privacy-preserving local aggregates | real relay event ingestion |
| Moderation | yes | local aggregates/escalation | moderator UI workflow |
| Channels | yes | local channel policy | delivery integration |
| Small groups | yes | local limits/policy | approval and delivery integration |
| Research panel | yes | Android-side diagnostic models, bundled reports and native C++ benchmark path | Python/Sage evidence parity display and broader native fixtures |
| Demo data | yes | demo relationship safety | store-level reset integration |

## Protocol Spec Validation

- Tests present:
  - `tests/test_protocol_spec_docs.py`
- Covered:
  - required sections in schema docs;
  - fenced JSON parsing;
  - forbidden keys in JSON examples;
  - README links to schema files;
  - core policy phrases in README.
- Missing:
  - JSON Schema validation;
  - cross-file semantic consistency;
  - enum consistency between protocol spec and Kotlin/Python models.
- Recommended next tests:
  - generate an enum inventory from protocol docs and compare with Kotlin/Python enums.

## Android Policy Guards

- Tests present:
  - `tests/test_android_policy_guards.py`
- Covered:
  - forbidden Android permissions;
  - forbidden account/device model fields;
  - forbidden networking/device identifier APIs;
  - forbidden account/public-discovery UI labels;
  - forbidden Firebase, Google Sign-In, analytics and ad SDK tokens in active Android/build files.
- Missing:
  - structured docs allowlist;
  - resource string scanning beyond current XML/Kotlin files if new resource types are added;
  - explicit check that any future `INTERNET` permission is documented as local/debug only.
- Recommended next tests:
  - fail if `INTERNET` is added without a reviewed transport note.

## Identity

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/identity/FingerprintFormatterTest.kt`
  - policy guards.
- Covered:
  - fingerprint stability;
  - display-name edit invariant;
  - forbidden identity fields.
- Missing:
  - persistence roundtrip with Android context;
  - Android Keystore provider tests;
  - key compromise/recreate warning flow.
- Recommended next tests:
  - add Robolectric or instrumentation test only if the project adopts that test stack.

## Invite And QR Import

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/invite/InviteImportServiceTest.kt`
  - `app-android/app/src/test/java/com/disser/kraken/invite/InviteQrCodeGeneratorTest.kt`
  - `app-android/app/src/test/java/com/disser/kraken/qr/QrScanImportServiceTest.kt`
  - `app-android/app/src/test/java/com/disser/kraken/handshake/OfflineHandshakeServiceTest.kt`
- Covered:
  - JSON roundtrip;
  - invalid JSON rejection;
  - missing required fields;
  - self-invite rejection;
  - duplicate invite/public key behavior;
  - pending import state;
  - QR payload rendering model;
  - camera QR decoded text routed through existing import validation;
  - invite QR import remains pending;
  - response QR and final confirmation QR complete the offline mutual handshake.
- Missing:
  - canonical signing payload;
  - production invite/handshake signatures;
  - consumed one-time invite enforcement across devices;
  - broader two-device instrumentation/UI test.
- Recommended next tests:
  - add a manual/instrumented two-device QR handshake smoke test once UI stabilizes.

## Relationship State Machine

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/relationship/RelationshipServiceTest.kt`
- Covered:
  - pending import to handshake to active;
  - send allowed only in active;
  - unlink disables sending;
  - peer unlink blocks relationship;
  - negative unlink creates complaint with realm context.
- Missing:
  - UI interaction tests;
  - signed unlink notice verification;
  - rejoin via new invite end-to-end flow.
- Recommended next tests:
  - add a pure test that old relationship IDs cannot be reused by import helpers.

## Realm And Membership

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/realm/RealmServiceTest.kt`
  - `app-android/app/src/test/java/com/disser/kraken/realm/ApprovalEvaluatorTest.kt`
- Covered:
  - demo realm creation;
  - default policy and capacity;
  - membership certificate placeholder;
  - invite edge shape;
  - local state transitions;
  - approval mode validation.
- Missing:
  - signed certificate validation;
  - distributed capacity statement validation;
  - UI workflow tests.
- Recommended next tests:
  - compare Kotlin membership certificate fields to protocol spec.

## Pending Approval

- Tests present:
  - `ApprovalEvaluatorTest`.
- Covered:
  - single-admin approval;
  - threshold approval;
  - duplicate approvals count once;
  - member approval does not approve;
  - eligible reject rejects;
  - pending user restrictions.
- Missing:
  - full Pending Approvals screen test;
  - persistence roundtrip.
- Recommended next tests:
  - add UI-level manual checklist before instrumentation.

## Local Delivery Simulator

- Tests present:
  - `tests/test_local_delivery_simulator.py`
- Covered:
  - A to B to C to Y delivery;
  - receipt return;
  - tombstone clears honest buffers;
  - expired packet deletion;
  - duplicate suppression;
  - relay cannot read encrypted payload placeholder.
- Missing:
  - parameter sweep experiments;
  - metrics export;
  - malicious relay scenarios beyond tombstone ignoring if modeled.
- Recommended next tests:
  - add experiment fixtures for copy budget and TTL comparisons.

## Packet Model And Transit Buffer

- Tests present:
  - `tests/test_packet_buffer.py`
- Covered:
  - add packet;
  - reject duplicate;
  - expiration;
  - copy budget split/reduction;
  - reserve copy rule.
- Missing:
  - Kotlin/C++ parity;
  - serialization compatibility;
  - social reserve scoring inputs.
- Recommended next tests:
  - create fixtures shared by Python and future Kotlin/C++ models.

## Receipts And Tombstones

- Tests present:
  - `tests/test_message_controls.py`
- Covered:
  - message status transitions;
  - read receipt disabled case;
  - tombstone target packet ID;
  - best-effort tombstone behavior.
- Missing:
  - signed tombstone verification;
  - relay buffer integration in Android;
  - UI snapshot for status legend.
- Recommended next tests:
  - connect tombstone model to transit buffer test cases.

## Crypto Abstraction

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/crypto/CryptoAbstractionsTest.kt`
- Covered:
  - fake provider roundtrip;
  - fake wrong-recipient/modified ciphertext behavior if implemented;
  - interface shape compiles.
- Missing:
  - production provider;
  - reviewed crypto library integration;
  - Android Keystore key reference behavior.
- Recommended next tests:
  - require production provider tests before any real encryption phase.

## LAN Transport Abstraction

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/transport/LanTransportModelsTest.kt`
- Covered:
  - transport session models;
  - summary exchange model;
  - packet send request model;
  - no public discovery field assumptions.
- Missing:
  - actual socket transport;
  - manual IP/port validation;
  - permission review if `INTERNET` is added.
- Recommended next tests:
  - keep interface tests pure until transport design is approved.

## Battery Policy

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/relay/RelayPolicyTest.kt`
- Covered:
  - low battery disables forwarding;
  - charging/Wi-Fi mode conditions;
  - only-my-messages disables transit;
  - research mode broader limits.
- Missing:
  - Android OS battery/Wi-Fi state integration;
  - background scheduling tests.
- Recommended next tests:
  - add pure tests for scan window calculations before OS integration.

## Courier Score

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/courier/CourierScoreTest.kt`
- Covered:
  - daily aggregation;
  - no GPS/location fields;
  - no recipient/route details;
  - separation from Relay Reliability.
- Missing:
  - ingestion from real delivery/receipt events;
  - privacy UI tests.
- Recommended next tests:
  - add fixture events for delayed daily summaries.

## Moderation

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/relationship/ComplaintModeratorTest.kt`
- Covered:
  - negative unlink to complaint event;
  - aggregation by target/reason/realm;
  - local complaint not root escalation;
  - single complaint does not auto-ban.
- Missing:
  - moderator UI workflow;
  - complaint abuse controls.
- Recommended next tests:
  - add threshold/repeated-pattern tests before enforcement actions.

## Channels

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/channel/ChannelServiceTest.kt`
- Covered:
  - channel created inside realm;
  - publisher role requirement;
  - latest-N policy;
  - mute/leave state;
  - no public discovery.
- Missing:
  - actual message delivery;
  - channel complaint UI.
- Recommended next tests:
  - add persistence snapshot tests if store testing stack is added.

## Small Groups

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/group/SmallGroupServiceTest.kt`
- Covered:
  - max member limit;
  - TTL/backlog policy;
  - pending approval requirement;
  - small-group-only constraints.
- Missing:
  - real messaging;
  - UI interaction tests.
- Recommended next tests:
  - add group invite approval integration tests.

## Research Panel

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/research/ResearchDiagnosticServiceTest.kt`
- Covered:
  - diagnostic-only wording/model;
  - basic curve input/result placeholder logic;
  - no production encryption claim.
- Missing:
  - native/Python classifier parity;
  - benchmark output validation;
  - export format tests.
- Recommended next tests:
  - add shared fixtures for Android, Python and future C++ research code.

## Demo Data

- Tests present:
  - `app-android/app/src/test/java/com/disser/kraken/demo/DemoDataSeederTest.kt`
- Covered:
  - demo relationship samples visibly marked as demo;
  - pending/active/blocked review states are present;
  - samples do not create realm/discovery context.
- Missing:
  - store-level reset integration;
  - UI Lab button interaction.
- Recommended next tests:
  - add instrumentation/Robolectric only if the project adopts Android UI testing.
