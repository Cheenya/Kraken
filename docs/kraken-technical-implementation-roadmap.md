# Kraken Technical Implementation Roadmap

This document is the execution baseline for Kraken development and Codex tasks.

Related documents:

- `kraken-current-concept.md`
- `kraken-identity-and-unlink-policy.md`
- `kraken-key-generation-policy.md`
- `mobile-p2p-messenger-plan.md`
- `mobile-p2p-messenger-plan-addendum.md`

## 0. Development Principle

Do not build the whole messenger at once.

Correct order:

```text
1. Specify protocol models and states.
2. Build Android skeleton.
3. Add local identity and invite flow.
4. Add relationship state machine.
5. Add realm/membership models.
6. Simulate delivery locally.
7. Add packet/transit core.
8. Add receipts/tombstones.
9. Add safe crypto.
10. Add first Android transport.
11. Add battery/courier/moderation UX.
12. Add research panel.
13. Move deterministic hot paths to C++.
14. Prepare demo scenario.
```

Main rule:

```text
Small working increments with tests and demo value are better than one large incomplete messenger.
```

## 1. Target Repository Shape

Desired monorepo shape:

```text
disser-messenger-project/
  docs/
  protocol-spec/
  app-android/
  core-native/
  research-python/
  simulator/
```

Current Python research code may remain under `src/disser_messenger/` until a later cleanup.

## 2. Phase 1 — Protocol Specification

Goal: define model names, states and payload examples before implementation spreads across Android, C++ and tests.

Add:

```text
protocol-spec/README.md
protocol-spec/schemas/identity.md
protocol-spec/schemas/invite.md
protocol-spec/schemas/membership_certificate.md
protocol-spec/schemas/relationship.md
protocol-spec/schemas/unlink_notice.md
protocol-spec/schemas/packet.md
protocol-spec/schemas/receipt.md
protocol-spec/schemas/tombstone.md
protocol-spec/schemas/complaint.md
protocol-spec/schemas/realm_policy.md
```

Acceptance criteria:

- all core models are named consistently;
- example JSON payloads exist;
- spec says no IMEI/device-id key generation;
- spec says no public discovery;
- spec says QR starts handshake and does not grant membership directly;
- spec says new key means new user;
- spec says unlink is bilateral for honest clients.

Codex task seed:

```text
Create protocol-spec docs for Kraken core models.
Add Markdown specifications and JSON examples for identity, one-time invite,
membership certificate, invite edge, relationship state, unlink notice,
packet, receipt, tombstone, complaint, complaint aggregate, realm policy,
approval policy and capacity token.
Do not implement business logic yet.
```

## 3. Phase 2 — Android Skeleton

Goal: create an Android app that builds, opens and shows the product shell.

Stack:

```text
Kotlin
Jetpack Compose
Material 3
Navigation Compose
CMake/NDK placeholder
```

Screens:

- Welcome;
- Create Identity;
- Home;
- My QR;
- Scan / Import Invite;
- Contacts;
- Realms;
- Pending Approvals;
- Chat placeholder;
- Channels placeholder;
- Mesh Status;
- Settings;
- Research placeholder.

Acceptance criteria:

- Android project builds;
- app opens;
- navigation works;
- no phone/email/login/account fields exist;
- native C++ placeholder exists;
- research screen placeholder exists;
- settings include relay-mode placeholders.

## 4. Phase 3 — Local Identity

Goal: let the user create a local cryptographic identity.

Components:

- IdentityStore;
- IdentityState;
- IdentityKeyProvider;
- FingerprintFormatter.

User enters only:

```text
display_name
```

App generates:

```text
identity keypair
fingerprint
created_at
```

Forbidden as key material:

```text
IMEI
Android ID
phone number
SIM identifiers
MAC address
serial number
hardware fingerprint
display name
realm name
```

Acceptance criteria:

- user can create identity;
- display name can change without changing key;
- fingerprint is stable;
- identity is loaded after restart;
- no device-id dependency exists;
- duplicate public key handling is modeled.

## 5. Phase 4 — QR Invite Export And Import

Goal: implement QR-first payload exchange without granting membership automatically.

Components:

- contact/invite payload export;
- QR generation;
- QR scan/import;
- invite parser;
- payload validation.

Acceptance criteria:

- app generates QR payload;
- another client can import payload;
- imported invite enters pending state;
- QR import does not create active membership automatically.
- product UX does not present text payload copy/paste as the normal invite path.

## 6. Phase 5 — Relationship State Machine

Goal: implement user-to-user relationship lifecycle.

States:

```text
PENDING_IMPORT
PENDING_HANDSHAKE
ACTIVE
UNLINK_REQUESTED
UNLINKED
BLOCKED_BY_PEER
REJOIN_REQUIRED
```

Rules:

- sending is allowed only in ACTIVE;
- unlink disables sending;
- valid unlink notice from peer sets BLOCKED_BY_PEER;
- reactivation requires new invite/handshake;
- negative unlink reasons create complaint event model.

Acceptance criteria:

- cannot write in non-ACTIVE relationship;
- local unlink transitions state correctly;
- peer unlink notice transitions to BLOCKED_BY_PEER;
- rejoin requires new invite/handshake;
- current unlink reason codes are used;
- negative reason creates complaint event model when linked to a realm.

## 7. Phase 6 — Realm And Membership Models

Goal: represent invite-only mesh realms.

Models:

- Realm;
- RealmPolicy;
- MembershipCertificate;
- InviteEdge;
- PendingMembershipRequest;
- ApprovalPolicy;
- CapacityState.

Per-user realm states:

```text
ACTIVE
ONLY_DIRECT
PAUSED
ARCHIVED
LEFT
```

Acceptance criteria:

- can create local realm model;
- can list realms;
- can pause/archive realm locally;
- membership certificate model exists;
- invite edge model exists;
- pending approval model exists;
- no public discovery is introduced.

## 8. Phase 7 — Pending Approval And Approval Policy

Goal: if the inviter cannot approve directly, create pending moderation flow.

Features:

- pending approvals screen;
- admin/moderator approval action;
- single-admin policy;
- threshold policy model;
- warning when realm has one admin and grows.

Acceptance criteria:

- pending user cannot write first;
- pending user cannot receive backlog;
- admin sees who invited whom and where;
- admin can approve/reject;
- threshold policy is modeled;
- single-admin warning exists.

## 9. Phase 8 — Local Delivery Simulator

Goal: test routing logic without Android transport complexity.

Scenario:

```text
A creates packet for Y.
A meets B.
B meets C.
C meets Y.
Y receives packet.
Y creates delivery receipt.
Receipt returns to A.
A creates tombstone.
Tombstone deletes copies from honest buffers.
```

Acceptance criteria:

- A -> B -> C -> Y delivery works;
- relays cannot decrypt payload;
- copy_budget decreases/splits;
- ttl_hops decreases;
- delivery receipt returns to sender;
- tombstone deletes packet from honest relay buffers;
- expired packets are deleted.

## 10. Phase 9 — Packet Model And Transit Buffer

Goal: implement reusable packet and forwarding policy core.

Models:

- Packet;
- PacketScope;
- TransitBuffer;
- ForwardingPolicy;
- BatteryPolicy;
- CopyBudgetPolicy;
- ReserveCopyPolicy.

Rules:

- packet has ttl_hops;
- packet has expires_at;
- packet has copy_budget;
- buffer has size/time limits;
- expired packets are deleted;
- duplicate packet is ignored;
- forwarding respects copy budget;
- last reserve copy is not sent to low-score relay.

## 11. Phase 10 — Message Status, Receipts And Tombstones

Goal: implement WhatsApp-like message status adapted to no-server P2P.

Statuses:

```text
PENDING
SENT_TO_NETWORK
DELIVERED
READ
NOT_CONFIRMED
EXPIRED
FAILED
```

Control messages:

- HopAck;
- DeliveryReceipt;
- ReadReceipt;
- Tombstone.

Acceptance criteria:

- hop ACK moves message to SENT_TO_NETWORK;
- delivery receipt moves message to DELIVERED;
- read receipt is optional;
- no receipt before deadline moves to NOT_CONFIRMED;
- tombstone can be created after delivery;
- receipts are modeled as encrypted control messages.

## 12. Phase 11 — Safe Crypto Workflow

Goal: add real E2EE for direct messages using reviewed primitives.

Do not write custom cryptographic primitives.

Use abstractions:

```text
CryptoBox
KeyAgreementProvider
KdfProvider
AeadProvider
```

Candidate primitives:

```text
X25519 or equivalent for key agreement
HKDF
ChaCha20-Poly1305 or AES-GCM
platform secure randomness
```

Acceptance criteria:

- A encrypts message for Y;
- B/C cannot decrypt;
- Y decrypts;
- modified ciphertext fails;
- wrong recipient fails;
- key agreement/encryption metrics are collected.

## 13. Phase 12 — Android LAN Transport

Goal: implement first real transport using LAN before BLE/Wi-Fi Direct/Nearby complexity.

TransportAdapter operations:

```text
start
stop
discoverPeers
openSession
exchangeSummary
sendPacket
closeSession
```

Acceptance criteria:

- two Android devices can connect on one LAN or manual IP/port;
- they exchange summaries;
- they transfer packet;
- three-device relay scenario works;
- battery policy can disable forwarding.

## 14. Phase 13 — Battery-Aware Orchestration

Goal: avoid constant background transmission.

User modes:

```text
Only my messages
Help a little
Help on charging/Wi-Fi
Active courier
Research mode
```

Acceptance criteria:

- user can select relay mode;
- forwarding is disabled on low battery;
- charging/Wi-Fi mode works;
- modes are visible in UI;
- forwarding window metrics are collected.

## 15. Phase 14 — Courier Score

Goal: add engagement without leaking privacy.

Rules:

- local by default;
- no GPS;
- no realtime point increments;
- no global leaderboard in MVP;
- no recipient/route/timestamp details;
- daily delayed aggregation;
- Courier Score is separate from Relay Reliability.

## 16. Phase 15 — Complaint And Moderation MVP

Goal: implement minimal governance model without turning moderation into full-time support.

Models:

- ComplaintEvent;
- ComplaintAggregate;
- ModerationAction;
- RestrictionState;
- GovernanceEscalation.

Acceptance criteria:

- negative unlink creates complaint event;
- complaints aggregate into cards;
- moderator sees aggregates, not raw endless list;
- local complaint does not go to root;
- governance escalation model is separate.

## 17. Phase 16 — Channels MVP

Goal: add safer alternative to large group chats.

Models:

- Channel;
- Publisher role;
- Subscriber state;
- Channel packet;
- Latest-N policy;
- TTL policy.

Acceptance criteria:

- channel is created inside realm;
- only publisher can publish;
- subscriber receives latest-N;
- channel can be muted/left;
- complaint against channel creates governance signal.

## 18. Phase 17 — Small Group MVP

Goal: add strictly limited small group chats.

Limits:

- max members, for example 5-10;
- max backlog;
- TTL;
- slow mode.

Acceptance criteria:

- group is created inside realm;
- member limit cannot be exceeded;
- messages have TTL;
- backlog is limited;
- pending approval for invitees works.

## 19. Phase 18 — Research Panel

Goal: expose dissertation diagnostics inside the prototype.

Features:

- input curve coefficients a,b;
- nonsingularity check;
- 2-torsion count;
- 3-torsion indicator;
- A1..A6 classification;
- benchmark sample;
- export JSON/CSV/Markdown later.

Acceptance criteria:

- user can check E(a,b);
- results match fixtures;
- benchmark sample can run;
- UI marks it as research/diagnostic only;
- it is not used for production encryption.

## 20. Phase 19 — C++ Core Migration

Goal: move deterministic/performance-sensitive parts to C++.

First candidates:

- curve model;
- torsion classifier;
- packet serialization;
- policy evaluation;
- metrics collector.

Acceptance criteria:

- C++ core builds through NDK;
- Kotlin calls native functions;
- results match Python fixtures;
- unit tests exist where possible;
- benchmarks compare implementations where useful.

## 21. Phase 20 — Commission Demo Scenario

Goal: prepare an installable demo for dissertation committee.

Demo realm:

```text
Dissertation Demo
```

Scenario:

1. Committee installs APK.
2. Each user creates display name and local identity.
3. Project owner shows one-time QR for demo realm.
4. User scans invite and completes handshake.
5. Admin approves.
6. Users see realm.
7. A sends direct message to Y.
8. Message relays through B if possible.
9. B sees only transit event.
10. Y receives message.
11. A sees delivery status.
12. Research panel shows curve diagnostic.
13. Courier Score shows local/demo contribution.

## 22. Parallel Track — Delivery Simulator For Dissertation

Goal: generate dissertation-friendly delivery metrics before real network scale exists.

Simulate:

- N users;
- encounter probability;
- copy_budget;
- TTL;
- relay modes;
- battery constraints;
- social-aware on/off;
- reserve copy on/off;
- channel/group load.

Metrics:

- delivery rate;
- average latency;
- copy overhead;
- storage pressure;
- battery cost estimate;
- complaint/moderation load.

## 23. Priority Buckets

### Must-Have For First Full MVP

- Android skeleton
- local identity
- one-time QR/import
- relationship ACTIVE/UNLINKED
- basic realm
- membership certificate model
- direct encrypted message
- LAN transport
- delivery receipts
- transit buffer
- basic research panel

### Should-Have

- pending approval
- capacity model
- complaints
- channels
- courier score
- battery modes
- tombstones
- simulator

### Could-Have

- Wi-Fi Direct
- BLE discovery
- Nearby Connections
- threshold admin approval
- root capacity tokens
- social-aware routing
- relay tokens
- small groups

### Not MVP

- global discovery
- public city search
- large anonymous groups
- global leaderboard
- automatic content scanning
- transparent key rotation
- account recovery
- iOS app
- production security claim

## 24. Recommended Codex Task Order

```text
1. Protocol spec docs
2. Android skeleton
3. Identity store
4. QR/invite payload
5. Relationship state machine
6. Realm/membership models
7. Local delivery simulator
8. Packet/transit buffer
9. Receipts/tombstones
10. Safe crypto abstraction
11. LAN transport
12. Battery policy
13. Courier score
14. Complaint models
15. Research panel
16. C++ core migration
17. Demo flow polish
```

## 25. Manual Review Checklist

When reviewing Codex output, verify that it did not:

- add phone/email/login fields;
- use IMEI, Android ID, MAC, serial or device fingerprint for keys;
- add public realm discovery;
- transfer trust to a new key;
- implement transparent key rotation;
- mix relay sanctions with community sanctions;
- create large groups without limits;
- write custom cryptographic primitives;
- claim the dissertation algorithm already accelerates cryptography;
- claim tombstones guarantee deletion from malicious devices;
- remove invite-only constraints;
- make QR grant membership without handshake;
- introduce realtime Courier Score that leaks activity.

## 26. Immediate Next Five Steps

1. Create `protocol-spec/README.md` and model specs.
2. Create Android skeleton.
3. Implement local identity creation.
4. Implement QR invite export/import.
5. Implement relationship state machine and bilateral unlink.

After these five steps, Kraken will already have a visible foundation:

```text
create identity
show QR/import invite
create pending relationship
activate relationship through handshake placeholder
unlink relationship
require new QR for restoration
```

## 27. Roadmap Summary

```text
I. Specification
II. Android foundation
III. Identity and trust
IV. Realm governance
V. P2P delivery core
VI. Transport
VII. UX and engagement
VIII. Moderation
IX. Research layer
X. Optimization
XI. Demo
```

## 28. Current Planning Status

This roadmap is the execution baseline for Codex.

When in doubt:

1. update protocol docs first;
2. implement small models with tests;
3. avoid broad product scope;
4. preserve invite-only and privacy constraints;
5. keep dissertation claims research-oriented, not marketing-oriented.
