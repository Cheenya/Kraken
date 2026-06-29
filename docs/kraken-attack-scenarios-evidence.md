# Kraken Attack Scenarios Evidence

Статус: текущий audit-документ для research prototype.
Дата: 2026-06-06.
Scope: Android Kraken P2P/mesh prototype, QR trust, LAN/BLE transports, relay,
packet validation, route-aware model, Adamova experimental-profile admission
boundary.

Этот документ фиксирует, какие сценарии атак/злоупотреблений уже покрыты
кодом, тестами или ручным evidence, а какие пока являются roadmap. Это не
инструкция по эксплуатации уязвимостей и не production security review.

## Claim Boundary

Разрешённая формулировка:

> Kraken демонстрирует защитные проверки research-прототипа: QR-established
> trust gate, packet validation, duplicate/expiry/TTL rejection, unknown peer
> rejection, bounded stores, route-aware diagnostics, LAN/BLE transport
> separation, controlled relay/attack simulations, and product-path Adamova
> admission checks for an experimental crypto profile.

Запрещённая формулировка:

- `Kraken является production secure messenger`.
- `Bluetooth/BLE доставка промышленно надёжна и полностью проверена`.
- `Wi-Fi Direct transport реализован и проверен`.
- `Mesh защищён промышленной криптографией`.
- `Диагностика кручения рациональных кривых доказывает production crypto security`.
- `prototype-placeholder` является цифровой подписью.

## Evidence Levels

| Level | Meaning |
| --- | --- |
| `implemented` | Есть runtime-код. |
| `unit-tested` | Есть unit/source tests, но не физический two-phone smoke. |
| `desktop-preflight` | Проверено локально на Mac через desktop relay/preflight, без Android radios. |
| `manual-two-phone-lan` | Есть ручной двухтелефонный LAN NSD/TCP over local Wi-Fi smoke evidence. |
| `manual-two-phone-ble` | Есть ручной двухтелефонный BLE direct-route smoke evidence. |
| `pending-physical` | Нужна проверка на телефонах. |
| `roadmap` | Сценарий запланирован, но реализации нет. |

## Current Transport Reality

| Transport / route | Current state | Evidence |
| --- | --- | --- |
| LAN NSD + TCP | implemented; есть ручной LAN NSD/TCP over local Wi-Fi two-phone smoke | `DirectLanTransport`, `LanFrameCodec`, `LanEndpointPayload`, `reports/out/two_device_delivery_evidence.md`, raw `artifacts/two-phone-test/2026-06-01/` |
| BLE GATT | implemented; unit-tested; есть ручной Samsung/Xiaomi BLE direct-route smoke | `BleGattTransport`, `BleFrameCodec`, `BlePermissions`, `BleFrameCodecTest`, `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`, raw `artifacts/two-phone-test/ble-live-20260606-204431/` |
| Wi-Fi Direct | implemented prototype; phone route/negative evidence pending | `WifiDirectTransport`, `KrakenTransportCatalog.WIFI_DIRECT`, `docs/route-aware-capability-model.md` |
| Composite route | implemented; source/unit coverage for route aggregation | `CompositePeerTransport`, `PeerRouteAggregatorTest`, `MeshServiceTest.snapshotExposesPeerRouteEvidenceFromTransportDiagnostics` |
| Routed mesh / relay | simulated + desktop relay; real always-on relay not production default | `SimulatedStoreAndForwardRelay`, `scripts/kraken_desktop_relay_preflight.py`, `artifacts/desktop-relay-preflight/...` |

## Scenario Matrix

| Scenario | Scope | What is covered now | Evidence | Current status |
| --- | --- | --- | --- | --- |
| Unknown peer injects message packet | Any transport | Inbound packet is rejected when sender fingerprint has no ACTIVE relationship. | `MeshDeliveryPipelineTest.unknownSenderRejectedInbound`, `MeshTrustGate` | unit-tested |
| Pending QR relationship tries to send | QR / mesh pipeline | Outbound packet is rejected for `PENDING_HANDSHAKE`. | `MeshDeliveryPipelineTest.pendingRelationshipCannotSendOutboundPacket` | unit-tested |
| Packet addressed to wrong recipient | Any transport | Inbound packet is rejected as `WRONG_RECIPIENT`. | `MeshDeliveryPipelineTest.wrongRecipientRejectedInbound`, `MeshTrustGate` | unit-tested |
| Duplicate/replay packet | Any transport / relay | Duplicate packet IDs are rejected and counted. | `MeshDeliveryPipelineTest.duplicateInboundPacketRejected`, `MeshPacketCodecTest.duplicatePacketRejected`, `PacketValidator` | unit-tested |
| Expired packet replay | Any transport / relay | Expired packets are rejected before storage/forwarding. | `MeshPacketCodecTest.expiredPacketRejected`, `PacketValidator` | unit-tested |
| TTL exhausted transit packet | Relay / routed mesh | Forwarding rejects `ttlHops <= 0`. | `MeshPacketCodecTest.exhaustedTtlRejectedWhenForwarding`, `SimulatedStoreAndForwardRelayTest.duplicateExpiredAndTtlExhaustedPacketsAreDropped` | unit-tested |
| Malformed packet JSON/frame | LAN/BLE/packet codec | Invalid packet JSON, payload mismatch and oversize frame cases fail safely. | `MeshPacketCodecTest.invalidPacketFailsSafely`, `MeshDeliveryPipelineTest.messagePayloadTypeMustMatchPacketType`, `LanFrameCodecTest`, `BleFrameCodecTest.rejectsOversizedPacket` | unit-tested |
| Message payload/envelope mismatch | Any transport | Message/receipt payload IDs must match envelope message IDs. | `MeshDeliveryPipelineTest.messagePayloadIdMustMatchEnvelopeMessageId`, `MeshDeliveryPipelineTest.receiptPayloadIdMustMatchEnvelopeMessageId` | unit-tested |
| Blocked/unlinked peer attempts delivery | Trust gate | Trust gate contains `BLOCKED_OR_UNLINKED` rejection path. | `MeshTrustGate`, `MeshTrustGateAuditTest`, `MeshTransportHardeningTest` | unit-tested |
| Realm membership removed while message queued | Realm-scoped message | Outbound realm message is rejected when membership evidence is absent/blocked. | `MeshServiceTest.syncNowRejectsQueuedRealmMessageWhenPeerMembershipRemoved`, `MeshRejectionReason.REALM_MEMBERSHIP_BLOCKED` | unit-tested |
| Relay disabled but transit packet appears | Routed mesh | Relay refuses transit forwarding unless prototype relay mode is explicit. | `SimulatedStoreAndForwardRelayTest.relayDisabledBlocksTransitForwarding` | unit-tested |
| Relay forwards A -> C -> B | Simulated mesh | Relay decrements TTL and forwards in in-memory transport. | `SimulatedStoreAndForwardRelayTest.enabledRelayForwardsPacketAndDecrementsTtl` | unit-tested |
| Relay drop/duplicate/tamper behavior | Desktop relay | Mac relay can simulate `drop`, `duplicate`, `tamper` and record decisions; executable tests pin the local decision semantics. | `tests/test_desktop_lan_relay.py`, `scripts/kraken_desktop_relay_preflight.py`, `artifacts/desktop-relay-preflight/20260603-193209/desktop_relay_preflight.md` | unit-tested; desktop-preflight |
| LAN direct delivery under QR trust | LAN NSD/TCP over local Wi-Fi | Two phones exchanged messages after LAN discovery/fanout fixes. | `reports/out/two_device_delivery_evidence.md`, raw `artifacts/two-phone-test/2026-06-01/after-peer-fail-fanout-fix/` | manual-two-phone-lan |
| BLE chunk tampering/partial transfer | BLE GATT codec | Oversized packets and expired partial BLE transfers fail safely; checksum mismatch is represented in codec. | `BleFrameCodec`, `BleFrameCodecTest.rejectsOversizedPacket`, `BleFrameCodecTest.rejectsExpiredPartialTransfer` | unit-tested |
| BLE physical nearby attack | BLE GATT radio | Captured manual two-phone BLE direct-route evidence; attack/rejection scenarios over BLE still need route-specific smoke. | `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`, `BleGattTransport`, route diagnostics | manual-two-phone-ble; attack cases pending |
| Wi-Fi Direct route attack | Wi-Fi Direct | Transport code exists, but route attack cases still require two-phone evidence. | `WifiDirectTransport`, `KrakenTransportCatalog.WIFI_DIRECT` | pending-physical |
| Route label confusion | UI / route model | Direct BLE is not allowed to be labelled as generic mesh in tested formatter. | `PeerRouteAggregatorTest.directBleSubtitleDoesNotClaimGenericMeshAvailability`, `docs/route-aware-capability-model.md` | unit-tested |
| QR screenshot abuse | QR trust model | QR import alone should not be treated as production authentication; trust still requires relationship state. Nearby handshake/proximity is a mitigation direction, not proof. | QR/relationship code; threat model docs | partially implemented; needs physical UX test |
| Production crypto context tamper resistance | Crypto envelope | Adamova admission context is now bound into AEAD associated data; protected message payloads use `ENCRYPTED_MESSAGE_JSON`; production payload policy rejects plaintext outbox/inbox without the Adamova-bound protector; tampered admission/session context is rejected before payload open. Signatures, Keystore-backed key material, production key agreement/KDF and replay protection are still open. | `AdamovaBoundCryptoEnvelope`, `AdamovaPacketCryptoBinding`, `AdamovaBoundCryptoEnvelopeTest`, `MeshDeliveryPipelineTest` | unit-tested; partial crypto-path |
| Adamova weak curve profile attack | Experimental crypto admission policy | Current tree has a product-path admission gate for the experimental Adamova crypto profile: QR/relationship/packet metadata carry profile binding, outbox rejects unapproved profiles, inbox rejects profile mismatches, and accepted admission context is bound into AEAD associated data for encrypted message payloads. This is not a complete secure messenger protocol proof. | `ProductCryptoAdmissionGate`, `OfflineHandshakeServiceTest`, `MeshDeliveryPipelineTest`, `AdamovaAdmissionAttackDemoRunnerTest`, `reports/out/adamova_admission_gate_attack_demo.md` | unit-tested; partial crypto-path |

## LAN / Wi-Fi Attack Coverage

Covered:

- malformed LAN frame rejection;
- manual peer fallback without creating trust;
- LAN delivery only after QR-active relationship;
- unknown peer rejection;
- duplicate/expired packet rejection;
- local two-phone LAN smoke evidence.

Important limitation:

- `INTERNET` permission exists for local sockets only. It is not evidence of
  cloud/server relay.
- LAN discovery is not trust. It only supplies a possible route.
- The LAN smoke does not prove Wi-Fi Direct or full multi-hop mesh. BLE is
  tracked separately in `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`.

## BLE Attack Coverage

Covered by code/tests:

- Android 12+ permission checks for BLE;
- BLE frame chunking/reassembly;
- GATT write-size bounded chunks;
- oversized packet rejection;
- partial transfer expiration;
- checksum/metadata validation inside `BleFrameCodec`;
- route diagnostics can represent direct BLE separately from routed mesh.

Covered by manual evidence:

- Samsung/Xiaomi two-phone BLE direct-route UI state;
- app route label `Bluetooth напрямую`;
- delivered chat messages in the same captured chat;
- Android BLE stack evidence for the Kraken GATT service UUID.

Not yet covered:

- automated route-specific BLE send/receipt smoke with exported counters;
- BLE-only attack simulation with one phone offline from Wi-Fi;
- malicious BLE advertiser spoofing beyond fingerprint/trust gate checks.

Safe wording:

> BLE GATT transport exists, has codec/permission/unit coverage, and now has
> manual Samsung/Xiaomi direct-route evidence. BLE attack/rejection coverage
> still requires route-specific two-device testing with exported counters.

## Wi-Fi Direct Attack Coverage

Current state:

- Wi-Fi Direct now has prototype transport code in the Android tree.
- `WifiDirectTransport` uses `WifiP2pManager`, DNS-SD TXT identity and the
  shared socket frame codec.
- Attack scenarios over Wi-Fi Direct must not be claimed as covered until
  physical two-phone route/negative evidence exists.

Needed before attack evidence:

1. Run two-device smoke without common router.
2. Export route evidence proving `wifi-direct` selection.
3. Repeat unknown peer / wrong recipient / duplicate / malformed / TTL tests
   over Wi-Fi Direct.
4. Record latency/loss/retry samples for the Wi-Fi Direct route.

Safe wording:

> Wi-Fi Direct exists as prototype transport code, but is not current attack
> evidence until two-phone Wi-Fi Direct route and rejection tests are captured.

## Routed Mesh / Relay Attack Coverage

Covered:

- simulated A -> C -> B relay with TTL decrement;
- relay disabled by default;
- duplicate/expired/TTL exhausted transit packet drops;
- desktop relay attack modes: normal, drop, duplicate, tamper, pinned by
  `tests/test_desktop_lan_relay.py`;
- local desktop preflight artifact.

Limitations:

- desktop relay preflight proves local relay decision logic only;
- Mac relay is LAN-compatible, not BLE/Wi-Fi Direct;
- current local tamper mode proves relay decision logic; production crypto
  context tamper is covered separately by Adamova-bound AEAD unit tests;
- real Android multi-hop routed mesh is not production-ready.

Safe wording:

> Kraken has controlled relay simulations and a desktop relay/attacker harness
> for research evidence. This is not a production relay network.

## Packet And Trust Gate Attack Coverage

Strongest implemented checks:

- ACTIVE relationship required for message delivery;
- unknown sender rejected;
- wrong recipient rejected;
- pending relationship cannot send;
- duplicate packet ID rejected;
- expired packet rejected;
- TTL exhausted rejected for forwarding;
- payload/envelope mismatch rejected;
- malformed packet rejected;
- blocked/unlinked relationship rejected;
- bounded packet stores and seen cache.

Weak or not production-grade:

- `proofMode = "prototype-placeholder"` is not a signature;
- `PrototypeNoSecurityPacketCrypto` remains a debug/prototype guard and must not
  be used as the production packet crypto implementation;
- protected message packets support `ENCRYPTED_MESSAGE_JSON`; the default
  runtime and lower-level message processors reject plaintext outbox/inbox
  without Adamova-bound protection, while legacy `LOCAL_MESSAGE_JSON` remains
  only for explicitly selected debug/compatibility fixtures;
- no Android Keystore identity;
- no production key agreement;
- no complete production E2EE protocol;
- no formal replay-proof signed envelope.

## Adamova / Research Attack Boundary

What is present now:

- native Adamova Stage A diagnostic backend is callable through
  `NativeCoreBridge`;
- Research models can evaluate diagnostic cases;
- mesh evidence export carries Adamova diagnostic metadata;
- `ProductCryptoAdmissionGate` evaluates admission for the experimental
  Adamova crypto profile;
- QR invite/response/confirmation and `Relationship`/`KrakenPacket` metadata
  carry `cryptoProfileId`, `admissionDecisionHash` and policy version binding;
- outbox/inbox enforcement rejects unknown, unapproved or mismatched
  experimental crypto profile packets;
- `AdamovaBoundCryptoEnvelope` validates local admission and binds
  `cryptoProfileId`, `profileHash`, `admissionDecisionHash`,
  `profilePolicyVersion`, `nativeBackendVersion`, `sessionProfileId` and
  `relationshipId` into AEAD associated data;
- `AdamovaPacketCryptoBinding` derives the crypto context from
  `KrakenPacket + Relationship`;
- `AdamovaMessagePayloadProtector` seals and opens protected message payloads as
  `ENCRYPTED_MESSAGE_JSON`;
- production payload policy can require `AdamovaMessagePayloadProtector` and
  reject plaintext message packets before message creation;
- `AdamovaAdmissionAttackDemoRunner` provides a backend for controlled weak
  profile attack scenarios;
- this supports dissertation traceability, diagnostics and product-path
  crypto-path evidence for the experimental profile.

What is not present now:

- no production cryptographic proof that Adamova diagnostics alone improve
  message security;
- no complete production signature/Keystore-backed session protocol;
- no production session key provider wired through all runtime app paths;
- legacy plaintext message payload path still exists for compatibility;
- no reviewed secure messenger protocol or production security proof;
- no claim that rational diagnostics over `Q` validate finite-field production
  ECC.

Safe wording:

> Adamova diagnostics are included as research/evidence tooling and as a
> product-path admission and crypto-context binding layer for Kraken's
> experimental crypto profile. They block weak/unknown/mismatched experimental
> profiles before session/packet use and bind accepted admission context into
> AEAD associated data for encrypted message payloads, but do not replace
> signatures, Keystore-backed key storage, key agreement, replay protection or
> external cryptographic review.

## Evidence Gaps To Close Next

P0:

1. Add route-specific BLE send/rejection evidence with exported counters.
2. Capture Wi-Fi Direct route evidence and route/negative tests before any
   `10/10` claim.
3. Produce a current mesh evidence JSON from the installed APK with git SHA,
   source state and route diagnostics.
4. Run desktop relay `drop`, `duplicate`, `tamper` against Android devices, not
   only local preflight.
5. Replace `PrototypeNoSecurityPacketCrypto` in message delivery with the
   Adamova-bound AEAD envelope plus real session key derivation.

P1:

1. Add route-specific attack screenshots: `нет маршрута`, `Bluetooth напрямую`,
   `LAN NSD/TCP over local Wi-Fi напрямую`, `через mesh`.
2. Add physical evidence that LAN discovery does not create contact/trust.
3. Add malicious BLE advertiser spoofing test fixture.
4. Add signed packet/envelope design before making any crypto-strength claim.
5. Add Android Keystore migration evidence separately from research diagnostics.

## Dissertation-Ready Summary

Короткая формулировка:

> В прототипе Kraken реализован набор защитных проверок для P2P/mesh-доставки:
> допуск сообщений только для QR-активных контактов, отклонение неизвестных,
> ошибочно адресованных, дублирующихся, истёкших и некорректных пакетов,
> ограничение TTL для transit forwarding, bounded storage и контролируемые
> сценарии relay drop/duplicate/tamper. Для experimental crypto profile добавлен
> контур допуска экспериментального криптографического профиля с
> QR/relationship/packet binding и outbox/inbox rejection для
> unknown/rejected/mismatched profile. LAN NSD/TCP over local Wi-Fi
> доставка подтверждена ручным двухтелефонным smoke. BLE GATT имеет реализацию,
> manual direct-route evidence и route-specific counters; физические
> BLE attack/rejection cases ещё открыты. Wi-Fi Direct
> имеет prototype transport implementation, но ещё не имеет двухтелефонного
> route/negative evidence.

Обязательная оговорка:

> Эти сценарии относятся к research prototype и не являются доказательством
> промышленной криптографической безопасности, поскольку production E2EE,
> подписанные envelopes и Android Keystore ещё не реализованы.
