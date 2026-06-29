# Mobile P2P Messenger Plan

This document is the current living plan for the dissertation messenger prototype.
It should be updated whenever product, protocol, research or implementation decisions change.

## 1. Current Product Definition

We are building an Android-first decentralized P2P messenger prototype for dissertation research.

The messenger is not a Telegram/WhatsApp clone and is not a production security claim. It is a research-oriented mobile prototype that demonstrates:

- accountless local cryptographic identity;
- QR/manual key exchange instead of phone/email registration;
- decentralized P2P delivery without a central message-history server;
- end-to-end encrypted direct messages;
- opportunistic mesh forwarding through available peers;
- battery-aware delivery policy;
- metrics and research panels connected to elliptic-curve dissertation work.

Short formulation:

```text
Android-first accountless P2P messenger with local key identity, QR trust establishment,
opportunistic encrypted mesh delivery, bounded transit buffers and dissertation research metrics.
```

## 2. What The Project Is Not

For the first stages, the project is not:

- a production messenger;
- a centralized account system;
- a phone-number messenger;
- an email/login based messenger;
- a permanent cloud history service;
- a full Tor/I2P replacement;
- a full Matrix/Signal/OpenMLS clone;
- a guaranteed anonymous network;
- a guarantee that malicious relay devices delete packets.

## 3. Platform Strategy

### First Target

Android-first.

Rationale:

- the primary developer has Android;
- Kotlin is the natural Android application language;
- Jetpack Compose gives a fast UI path;
- Android is more realistic than iOS for early P2P/background experiments;
- optimized research/protocol code can be moved into a C++ core through NDK/JNI.

### Later Targets

Possible later paths:

- iOS native app through Swift/SwiftUI and a shared C++ core;
- Kotlin Multiplatform for shared business logic where useful;
- desktop/debug tooling only as support, not as the primary target.

## 4. Language And Layering Decisions

### Kotlin / Android Layer

Responsible for:

- Jetpack Compose UI;
- Android lifecycle;
- permissions;
- QR scan/generation;
- local settings;
- local encrypted storage policy;
- WorkManager/foreground service orchestration;
- Bluetooth/Wi-Fi/LAN/Nearby transport adapters;
- notifications;
- battery-aware scheduling.

### C++ Core Layer

Responsible for deterministic and performance-sensitive logic:

- packet model;
- routing decisions;
- transit buffer policy;
- metrics;
- cryptographic workflow orchestration;
- research algorithms;
- elliptic-curve/torsion diagnostics;
- serialization that needs to be shared across platforms later.

Important rule:

```text
Kotlin orchestrates the Android platform.
C++ implements deterministic protocol/research core.
```

C++ must not know about Android UI, permissions, Activity lifecycle, Compose or notifications.

## 5. Identity Model

There is no registration by phone, email, login or account server.

Each device/user creates a local identity:

```text
display_name + identity_keypair + fingerprint
```

The display name is only a local/user-facing label. It is not trusted identity.

The actual identity is the public key/fingerprint.

## 6. Trust Establishment

Contacts are added through out-of-band trust establishment:

- QR code;
- manual public key exchange;
- JSON/file invite;
- in-person verification.

QR/key exchange establishes trust. It does not deliver messages by itself.

### Contact Payload v1

Draft shape:

```json
{
  "app": "disser-messenger",
  "type": "contact",
  "version": 1,
  "display_name": "Alice",
  "identity_public_key": "...",
  "fingerprint": "...",
  "capabilities": ["p2p-v1", "safe-x25519"]
}
```

Future fields:

- `created_at`;
- `expires_at`;
- `signature`;
- device capabilities;
- relay-helper capabilities.

## 7. Transport Model

The first real mobile prototype should be transport-abstracted.

Transport adapter interface should allow:

- LAN transport first for development/demo;
- BLE discovery later;
- Wi-Fi Direct later;
- Nearby Connections later;
- libp2p or other overlay experiments later.

Preferred first practical path:

```text
Android app + LAN transport + QR identity/contact exchange + local P2P forwarding demo.
```

## 8. Delivery Model

The network must not assume a static route.

Instead of precomputing a stable path, the project uses opportunistic store-carry-forward delivery.

Baseline delivery approach:

```text
bounded gossip / Spray-and-Wait inspired routing
```

A message encrypted for final recipient may be copied to a limited number of relay peers. Relays temporarily store only encrypted transit packets and forward them when they meet other peers.

## 9. Packet Model

Draft packet fields:

```json
{
  "packet_id": "...",
  "message_id": "...",
  "sender_hint": "...",
  "recipient_hint": "...",
  "ttl_hops": 8,
  "expires_at": "...",
  "copy_budget": 8,
  "min_reserve": 1,
  "social_reserve": 1,
  "priority": "normal",
  "encrypted_payload": "..."
}
```

Notes:

- `encrypted_payload` is readable only by the final recipient.
- `recipient_hint` may initially be a simple hash/fingerprint hint for MVP.
- Advanced privacy versions may replace this with blinded route tags/onion-like routing.
- The packet must expire even if no tombstone is received.

## 10. Transit Buffer

There is no centralized message history.

Relays may temporarily keep encrypted packets in a bounded transit buffer.

Transit buffer is not user-readable history.

Policy fields:

```text
max_buffer_size_mb
max_packet_age_minutes
max_packets_per_peer
max_forwarding_time_per_window
max_bytes_per_window
battery_threshold
wifi_only_mode
charging_only_relay_mode
```

Default behavior:

- store only encrypted packets;
- enforce TTL and expiration;
- delete on valid tombstone;
- delete on expiration;
- never persist plaintext message history by default.

## 11. Message Status And Receipts

The UI should support WhatsApp-like indicators adapted to no-server P2P delivery.

Statuses:

```text
PENDING             message is local and waiting for an opportunity to send
SENT_TO_NETWORK     at least one neighbor accepted the packet
DELIVERED           final recipient sent encrypted delivery receipt
READ                final recipient sent optional encrypted read receipt
NOT_CONFIRMED       no receipt before deadline/TTL
EXPIRED             packet expired locally
FAILED              local error or policy prevented sending
```

UI mapping:

```text
clock / pending       waiting for send window
one gray check        accepted by first hop or direct recipient
two gray checks       final recipient confirmed delivery
two blue checks       final recipient confirmed read
warning icon          not confirmed / expired / failed
```

Important distinction:

```text
There is no server acknowledgment.
One gray check means first-hop acceptance, not server delivery.
```

Receipts must be encrypted control messages routed back through the same P2P network.

Read receipts must be optional because they leak privacy information.

## 12. Tombstone / Delete Signal

After delivery, the sender and/or final recipient may emit a signed tombstone packet.

Tombstone purpose:

```text
Tell honest relay nodes to delete a transit packet and stop forwarding it.
```

Draft tombstone fields:

```json
{
  "type": "tombstone",
  "packet_id": "...",
  "message_id": "...",
  "reason": "delivered",
  "issuer": "sender_or_recipient_key_id",
  "created_at": "...",
  "signature": "..."
}
```

Deletion is best-effort.

The protocol can require honest clients to delete, but it cannot physically force malicious devices to delete stored bytes.

Therefore every packet must also have TTL/expiration.

## 13. Routing Policy

### Baseline Policy

Use bounded Spray-and-Wait style forwarding:

- each packet has limited copy budget;
- forwarding decrements or splits budget;
- TTL prevents infinite spread;
- peer summaries prevent sending duplicates;
- battery policy may block forwarding;
- tombstone deletes delivered packets from honest buffers.

### Reserved Copy Policy

To avoid wasting all copies on low-value relays, packets should support reserve budgets.

Fields:

```text
copy_budget
min_reserve
social_reserve
random_budget
```

Rule:

```text
Do not give the last reserve copy to a low-score relay.
Keep at least one copy for direct delivery or a high-score relay.
```

Example:

```text
B receives a packet for E.
B meets C, D and F, but they are low-score relays.
B may forward some copies but keeps a reserve copy.
Later B meets G, a high-score relay or relay-token holder.
B gives G a reserve copy.
```

## 14. Social-Aware Routing And Privacy

Social-aware routing can improve delivery, but it risks leaking the social graph.

Dangerous naive approach:

```text
Peers exchange contact lists or announce "I know E".
```

This reveals social links and should not be default.

Recommended staged approach:

### Level 0: Privacy-Preserving Baseline

- Spray-and-Wait;
- TTL;
- copy budget;
- battery policy;
- no contact-list exchange.

### Level 1: Local Relay Scoring

Use local-only relay quality metrics:

- successful forward count;
- failed forward count;
- last seen;
- trust level;
- battery availability;
- overload penalty.

No peer reveals its contact list.

### Level 2: Optional Trusted Social Hints

Only among trusted contacts and only with explicit user consent.

Possible hints:

- coarse score bucket;
- route tag support;
- recent relay usefulness.

Avoid exact contact lists.

### Level 3: Research Relay Tokens

Future research idea:

- recipient gives selected contacts relay-helper tokens;
- packets use blinded routing tags;
- relay can prove usefulness without broadly revealing the social edge.

This is not MVP.

## 15. Battery-Aware Policy

The app must not constantly transmit in the background.

States:

```text
OFFLINE
IDLE
PASSIVE_SCAN
ACTIVE_HANDSHAKE
ONLINE_SESSION
TRANSIT_FORWARDING
LOW_POWER
```

Recommended behavior:

- active chat: behave like a normal messenger;
- app open: more frequent scans/handshakes;
- background: short scan windows only;
- low battery: disable transit forwarding;
- charging/Wi-Fi: allow more relay work if user opted in.

User-facing relay modes:

```text
Only my messages
Help the network a little
Help the network only on charging/Wi-Fi
Active mesh relay
Research mode
```

## 16. UI Scope

Mobile UI is required.

First Android screens:

- create local profile;
- my QR;
- scan/import contact;
- contacts;
- mesh status;
- chat;
- outgoing message status;
- relay/battery mode settings;
- research/diagnostics panel later.

No phone/email/account fields should exist.

## 17. Dissertation Research Connection

The messenger is the applied scenario.

The dissertation research layer includes:

- rational elliptic curve diagnostics;
- torsion indicators;
- A1..A6 classification;
- SageMath reference fixtures offline;
- finite-field/cryptographic parameter analysis later;
- metrics comparing validation cost, key formation and routing behavior.

Careful wording:

```text
The rational-curve torsion pipeline is a research/diagnostic layer.
It is not used as unaudited production encryption.
```

## 18. Roadmap

### Stage 1: Current Research MVP

- Python package scaffold;
- torsion classifier;
- fixtures;
- tests;
- JSONL benchmark;
- local mesh key graph.

### Stage 2: Android-First Planning/Skeleton

- Android Gradle skeleton;
- Kotlin + Jetpack Compose;
- C++ native core placeholder;
- docs updated for Android-first architecture;
- interfaces: IdentityStore, TrustStore, TransportAdapter, PacketBuffer, BatteryPolicy.

### Stage 3: Identity And QR

- local identity keypair;
- display name;
- fingerprint;
- contact payload export/import;
- QR generation/scanning;
- trust store.

### Stage 4: Opportunistic Delivery Model

- packet model;
- transit buffer;
- peer summaries;
- bounded forwarding;
- copy budget;
- reserve copy policy;
- expiration.

### Stage 5: Receipts And Tombstones

- hop ack;
- delivery receipt;
- optional read receipt;
- signed tombstone;
- local message status.

### Stage 6: Android Transport

- LAN transport first;
- short handshake windows;
- forwarding demo across devices/processes;
- battery-aware limits.

### Stage 7: Safe Crypto Workflow

- standard primitives for real message encryption;
- no custom production crypto;
- metrics for key formation and encryption/decryption.

### Stage 8: Social-Aware Experiments

- local relay scoring;
- reserve copy policy;
- optional trusted hints;
- relay tokens as research extension.

### Stage 9: Research Panel And Reports

- torsion benchmark summary;
- mesh delivery metrics;
- routing/battery metrics;
- dissertation-ready tables and figures.

## 19. Codex Backlog Seeds

### Task A: Android-First Skeleton

Implement Android-first project skeleton with Kotlin/Compose and native C++ placeholder.

Acceptance criteria:

- Android module exists;
- Jetpack Compose enabled;
- placeholder screens exist;
- CMake/NDK placeholder exists;
- no phone/email/account model;
- docs mention Android-first strategy.

### Task B: Local Identity And Contact Payload

Implement local identity and QR/JSON contact exchange.

Acceptance criteria:

- user can create display name;
- identity keypair generated locally;
- public contact payload exported;
- contact payload imported;
- fingerprint displayed;
- display name is documented as non-trusted label.

### Task C: Opportunistic Packet Model

Implement packet, transit buffer and bounded forwarding model.

Acceptance criteria:

- packet has packet_id, ttl_hops, expires_at, copy_budget, encrypted_payload;
- buffer enforces size/time limits;
- forwarding respects copy budget and reserve policy;
- expired packets are deleted.

### Task D: Delivery Receipts And Tombstones

Implement message statuses, receipts and tombstones.

Acceptance criteria:

- one-check status after first-hop acceptance;
- two-check status after final delivery receipt;
- optional read receipt;
- tombstone deletes transit packet on honest nodes;
- docs state deletion is best-effort.

### Task E: Relay Scoring Without Contact Graph Leak

Implement relay scoring using local-only metrics.

Acceptance criteria:

- no contact-list exchange;
- scoring uses local reliability metrics;
- last reserve copy is not forwarded to low-score relay;
- tests cover reserve copy behavior.

## 20. Open Questions

- Which safe crypto library should be used first on Android?
- Should the first Android transport be LAN, Nearby Connections, or BLE discovery plus LAN transfer?
- How much local encrypted history should be optional?
- How much metadata leakage is acceptable in MVP recipient hints?
- Should read receipts be disabled by default?
- How should relay-helper tokens be designed if selected for research?
- Which parts of the C++ core must be shared with iOS later?
