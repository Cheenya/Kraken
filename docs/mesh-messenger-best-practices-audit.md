# Mesh Messenger Best Practices Audit

Status: research note.
Date: 2026-06-05.
Scope: compare current Kraken Android transport/messenger implementation with
observable practices from other offline, mesh and delay-tolerant messengers.

## Executive Summary

Kraken is directionally aligned with the right product principles: no cloud
account, QR-based trust, local-first storage, explicit foreground service,
separate BLE/LAN transports, receipts, retry/delete UX, notification channels
and diagnostics. The strongest current gap is not "lack of features"; it is the
absence of a real per-contact route model. The app still has places where direct
Bluetooth can be shown as generic mesh availability, while other systems make a
clear distinction between direct nearby links, routed mesh, offline storage and
high-bandwidth paths.

The next practical development vector is:

1. implement route-aware state (`none`, `direct BLE`, `direct LAN/Wi-Fi`,
   `routed mesh`);
2. attach feature capabilities to that state;
3. only then add rich features such as short voice notes, stickers, media and
   calls;
4. keep routed mesh traffic small, TTL-limited and budgeted.

## Sources Reviewed

Primary or near-primary sources consulted:

- Briar:
  - https://briarproject.org/
  - https://briarproject.org/how-it-works/
  - https://briarproject.org/manual/
- Berty Protocol:
  - https://berty.tech/ar/docs/protocol
- Bridgefy:
  - https://docs.bridgefy.me/sdk/android/overview
  - https://bridgefy.notion.site/Bridgefy-Mesh-Network-Basics-002ecd4c7bb64986bf00beb6d43d3036
  - https://www.usenix.org/system/files/sec22fall_albrecht.pdf
- Meshtastic:
  - https://meshtastic.org/
  - https://meshtastic.org/docs/overview/
- Bluetooth Mesh references:
  - https://www.bluetooth.com/learn-about-bluetooth/feature-enhancements/mesh/mesh-glossary/
  - https://www.bluetooth.com/bluetooth-mesh-primer/
- Reticulum / LXMF / Columba:
  - https://reticulum.network/
  - https://github.com/markqvist/LXMF
  - https://columba.network/

## Current Kraken Snapshot

Current tree evidence:

- `KrakenTransportCatalog` marks `lan-nsd-tcp` and `ble-gatt` as implemented.
- `TransportManager.startLan(...)` starts `BleGattTransport` and
  `DirectLanTransport` in `CompositePeerTransport`.
- `CompositePeerTransport` merges discovered peers by fingerprint and records
  recent route attempts, but merged `DiscoveredPeer` does not preserve which
  transport found the peer.
- `MeshServiceSnapshot` has global `discoveredPeers`, `transportDiagnostics`,
  queue metrics and foreground-service flags, but not per-contact route state.
- Chat UI still has `meshAwareContactSubtitle(...)`, which can show
  `dostupen cherez mesh` from a direct peer-visible condition.
- Route-aware design docs already exist:
  - `docs/route-aware-capability-model.md`
  - `docs/route-aware-implementation-plan.md`
- Foreground service, local notifications, notification inbox, mute, message
  delete/clear and local retry mechanics exist in the current dirty tree.

## Cross-Project Patterns

### 1. Direct Path And Routed Mesh Are Different Product States

Briar presents direct sync over Bluetooth/Wi-Fi and Internet/Tor as different
transport options. Berty separates direct transports from online rendezvous and
notes that direct transports are synchronous: peers need to be connected at the
same time. Bridgefy distinguishes direct one-to-one delivery from mesh relay in
its product model. Meshtastic explicitly exposes hop limits and packet relay
behavior.

Implication for Kraken:

- direct BLE must not be labelled as generic mesh;
- direct LAN/Wi-Fi must be a separate high-bandwidth state;
- routed mesh must be shown only when there is relay/path evidence;
- `no route` is a first-class state, not an error.

### 2. Mesh Needs Traffic Budgets, TTLs And Duplicate Suppression

Meshtastic uses rebroadcasting with duplicate detection and a hop limit.
Bluetooth Mesh uses managed flooding, TTL and a message cache. These mechanisms
are not optional polish; they are what keeps a mesh from eating itself.

Kraken already has packet IDs, seen-store and packet TTL concepts, but the UI
and per-contact route layer do not yet expose or enforce a user-facing budget.
Reactions, stickers and pings must therefore be denied or heavily budgeted on
routed mesh until route cost and relay policy exist.

### 3. Contact Trust Must Be Explicit And Verifiable

Briar nearby contact exchange uses mutual QR scanning. Berty's contact handshake
assumes the requester already obtained the responder identity through a trusted
channel such as QR, and it documents device-availability limits. Bridgefy is the
negative lesson: using a known crypto protocol is not enough if key binding,
verification and protocol integration are wrong.

Kraken is strong here compared with many prototypes:

- QR trust is already central;
- active chats are gated by relationship state;
- fingerprints exist in contact/profile flows;
- decorative public discovery is intentionally avoided.

Missing:

- production crypto provider;
- hardened key verification UX;
- audited session/key agreement;
- explicit "this prototype is not production-secure" boundaries must remain.

### 4. Offline Messaging Is Usually Delay-Tolerant, Not Online Presence

Briar tells users offline messages are delivered next time both sides are online
or via a mailbox. Berty discusses replication devices/servers that can store
content without decrypting it. LXMF has propagation nodes for store-and-forward
delivery when endpoints are not directly reachable.

Kraken has local queues and a foreground service, but not yet a mature
delay-tolerant routing model:

- no explicit relay/courier role for phones;
- no route cost or hop-count based scheduler;
- no per-contact last-seen/route expiry model;
- no user-facing distinction between "queued locally" and "known routed path".

### 5. Rich Features Are Route-Dependent

Bridgefy claims support for text, images, locations, game moves and database
sync over Bluetooth/Wi-Fi mesh. Columba claims multiple transports and voice
calls, while relying on broader Reticulum/LXMF concepts. Berty notes Wi-Fi
direct-style links are faster and more reliable than BLE. Meshtastic's low
payload constraints show the opposite extreme: very small messages, position
and telemetry are natural; heavy media is not.

Kraken should not expose rich actions statically. A practical policy:

- text: any relationship route, queued if no route;
- reactions: direct BLE or direct LAN only;
- built-in sticker IDs: direct BLE or better;
- custom stickers/GIF/photo/video: direct LAN/Wi-Fi only;
- short voice note: direct BLE or better, strict duration and size limits;
- voice/video calls: direct high-bandwidth only;
- routed mesh: text/control/receipts only until traffic budgets are proven.

### 6. Logs, CRDTs And Replication Matter For Groups And Multi-Device

Berty uses immutable logs and CRDT/eventual consistency to merge offline
conversation branches. LXMF treats messages as signed, self-contained objects and
supports propagation nodes. These are important if Kraken grows beyond simple
one-device one-to-one text.

Kraken currently has simpler local message lists, relationship state and realm
models. That is acceptable for P0/P1, but group sync, multi-device identity and
offline merge conflicts will require a more explicit log model.

## Comparison Matrix

| Area | Briar | Berty | Bridgefy | Meshtastic | Reticulum/LXMF/Columba | Kraken now |
| --- | --- | --- | --- | --- | --- | --- |
| Identity | local account, no cloud recovery | account/group keys, no personal data required | app UUID/display name model in public docs/papers | node/channel identity | cryptographic destinations | local identity, QR trust, prototype crypto warning |
| Contact trust | QR nearby and distance link flows | rendezvous point + QR/URL, authenticated handshake | historically weak verification lessons | shared channels/keys | destination keys | QR flow and relationship gating are good |
| Transports | Bluetooth, Wi-Fi, Tor, memory cards | BLE, Android Nearby, Multipeer, libp2p | BLE/Wi-Fi Direct SDK claims | LoRa via companion app transport | many Reticulum media | BLE GATT + LAN NSD/TCP + Wi-Fi Direct prototype; Wi-Fi Direct phone evidence pending |
| Route semantics | direct sync vs Tor/mailbox | direct vs rendezvous/replication | direct vs multi-hop/broadcast | hop limit/rebroadcast | path lookup/propagation | global peers only; no per-contact route kind yet |
| Offline model | direct when both online; mailbox optional | async logs; replication devices | mesh relay/product SDK | store small packets, rebroadcast | propagation nodes | local queue + foreground service; no relay role policy |
| Traffic control | transport-specific sync | CRDT/log sync | SDK opaque; security papers warn | duplicate cache, hop limit, small payloads | routing/queues/receipts | seen store and TTL exist; no route budget/capability UX |
| Rich media | conservative messenger/forum model | attachments in protocol model | claims images/location/db sync | tiny payloads, position/telemetry | extensible fields; Columba claims calls | text only; rich feature policy documented, not implemented |
| Security posture | audited/privacy-first positioning | detailed crypto/protocol docs | cautionary tale: Signal integration not enough | channel encryption, metadata visible | E2E/default encryption claims | prototype crypto, not production-secure |

## Kraken Strengths

- Strong product boundary: no cloud, no account/phone/email identity, no public
  discovery by default.
- QR-based trust and relationship states are already aligned with Briar/Berty
  style trust establishment.
- BLE, LAN and Wi-Fi Direct are implemented as separate prototype transports and routed via
  `CompositePeerTransport`.
- Foreground-service background operation and local notifications are moving in
  the right Android direction.
- Outbox/inbox/seen/receipt stores give a base for delay-tolerant messaging.
- Diagnostics and smoke scripts make the prototype testable on real phones.
- The new route-aware design docs correctly anticipate the biggest UX gap.

## Kraken Weaknesses And Omissions

### P0/P1 Gaps

- No per-contact route state. UI can still show `dostupen cherez mesh` for a
  direct peer-visible condition.
- `DiscoveredPeer` loses transport evidence after `CompositePeerTransport`
  merges peers by fingerprint.
- No route TTL/cache/last-seen model.
- No real routed mesh evidence model: no hop count, relay path, path cost or
  relay budget.
- No capability policy in the composer yet.
- No production crypto provider/session protocol; current prototype warnings are
  correct and must remain.
- Notification and foreground behavior are still being actively stabilized on
  real devices.

### P2/P3 Gaps

- No attachment framework with chunking, resume, checksums and storage quotas.
- No route-specific rich-feature policy enforced in code.
- Wi-Fi Direct still needs two-phone route/negative evidence before any `10/10`
  claim.
- No explicit courier/relay role selection or battery-aware relay scheduler.
- No CRDT/event log model for offline group/multi-device conflict resolution.
- No replication/mailbox node concept for high availability.
- No formal metadata-leakage budget for direct transport discovery.

## Recommended Development Vectors

### Vector 1 - Route-Aware UX Foundation

Implement `PeerRouteSnapshot` and route aggregation first.

Minimum acceptance:

- `Bluetooth напрямую` for direct BLE;
- `Wi-Fi/LAN напрямую` or `Быстрый прямой канал` for LAN/Wi-Fi;
- `Через mesh` only with relay/path evidence;
- `нет маршрута` or `последний контакт: HH:mm` otherwise;
- route state expires.

This is the most important next step because it prevents the UI from promising a
network condition that does not exist.

### Vector 2 - Capability-Based Composer

Before adding features, add `RouteCapabilityPolicy`.

Initial decisions:

- text remains available;
- reactions and built-in stickers are direct-route only;
- short voice notes are direct BLE or better;
- photo/video/GIF/calls are direct LAN/Wi-Fi only;
- routed mesh denies rich actions until relay budgets are implemented.

### Vector 3 - Mesh Traffic Budget And Relay Policy

Do not enable routed reactions/presence pings without:

- packet TTL;
- duplicate cache;
- per-route/hop cost;
- retry budget;
- relay role and battery policy;
- backoff and queue pressure metrics.

Meshtastic and Bluetooth Mesh both show that uncontrolled rebroadcasting is the
central scalability risk.

### Vector 4 - Crypto Hardening

Bridgefy is the warning case: using a famous protocol or encryption library does
not automatically produce a secure messenger. Kraken needs:

- selected reviewed primitives;
- explicit session/key agreement;
- key verification UX;
- tamper/wrong-recipient tests;
- no production-security claims until this exists.

### Vector 5 - Delay-Tolerant Store-And-Forward

Kraken should eventually distinguish:

- local queue on sender;
- direct delivery;
- routed mesh relay;
- optional mailbox/replication node that cannot read content;
- expired/failed packets.

This maps well to Briar Mailbox, Berty replication devices and LXMF propagation
nodes, but should remain optional and local-first.

### Vector 6 - Rich Media In The Right Order

Recommended order after route-aware foundation:

1. built-in sticker IDs on direct routes;
2. reactions on direct routes only;
3. short voice notes with hard duration/size limits;
4. attachment framework;
5. photo/GIF/video on direct LAN/Wi-Fi only;
6. voice/video calls on direct high-speed only.

Do not start with video/calls. They need stable direct high-bandwidth route
detection, session lifecycle, battery policy, and failure UX.

## Near-Term Backlog

1. Implement `PeerRouteModels.kt`.
2. Preserve `fingerprint + transportId + observedAt` evidence in diagnostics.
3. Add `PeerRouteAggregatorTest`.
4. Replace `meshAwareContactSubtitle(...)`.
5. Add `RouteCapabilityPolicyTest`.
6. Add a Mesh Status section for per-contact route evidence.
7. Update screenshot/evidence scripts to capture route evidence.

## Bottom Line

Kraken is not behind because it lacks GIFs, calls or stickers. It is behind if it
does not know how a contact is reachable. Other projects repeatedly show the
same lesson: route truth, traffic budget, explicit trust and delay-tolerant
semantics must come before rich messenger features.
