# P2P / Mesh Research Prototype Readiness

Date: 2026-05-30

Current-status note, 2026-06-06: this file is a historical May 30 checkpoint.
For current manual Samsung/Xiaomi two-phone LAN/Wi-Fi prototype evidence, use
`reports/out/two_device_delivery_evidence.md`. The May 30 `pending` wording
below must not be treated as the current project status.

Implementation baseline before this report refresh: `f4a03cf`

## Verdict

At this May 30 checkpoint, Kraken Android had enough P2P/mesh implementation to
document a research prototype pipeline, but it did not yet have completed real
two-device LAN delivery evidence.

Current state:

- packet envelope exists;
- packet stores and pruning exist;
- loopback/two-node transport tests pass;
- receipt path is implemented and tested;
- queue/retry states are implemented and tested;
- Direct LAN NSD + TCP transport exists and builds;
- Mesh diagnostics UI exists;
- unknown/pending/blocked/wrong-recipient gates are tested;
- simulated store-and-forward relay is tested;
- real relay is disabled by default;
- production crypto is not implemented.

## What Is Proven By Tests

| Area | Evidence | Status |
| --- | --- | --- |
| A -> B prototype packet delivery | `MeshDeliveryPipelineTest.twoNodeDeliveryAcceptsOnlyActiveQrTrustedRelationships` | passed |
| Runtime service delivery | `MeshServiceTest.twoNodeServicesDeliverMessageAndReceiptThroughSharedTransport` | passed |
| Receipt returns B -> A | `MeshServiceTest.twoNodeServicesDeliverMessageAndReceiptThroughSharedTransport` | passed |
| Sender marks delivered | `MessageStatus.DELIVERED_TO_PEER` assertion | passed |
| Trusted peer absent queues packet | `MeshServiceTest.readyMessageCreatesPersistentQueuedPacketWhenTrustedPeerNotDiscovered` | passed |
| Queued receipt retry | `MeshServiceTest.inboundMessageQueuesReceiptWhenImmediateSendFailsAndRetriesLater` | passed |
| Stale sent retry | `MeshServiceTest.syncNowRetriesStaleSentMessageWhenReceiptDidNotArrive` | passed |
| Unknown sender rejection | `MeshDeliveryPipelineTest.unknownSenderRejectedInbound` | passed |
| Pending relationship rejection | `MeshDeliveryPipelineTest.pendingRelationshipCannotSendOutboundPacket` | passed |
| Wrong recipient rejection | `MeshDeliveryPipelineTest.wrongRecipientRejectedInbound` | passed |
| Duplicate rejection | `MeshDeliveryPipelineTest.duplicateInboundPacketRejected` | passed |
| Malformed payload rejection | `MeshDeliveryPipelineTest.messagePayloadTypeMustMatchPacketType` and id mismatch tests | passed |
| Simulated A -> C -> B relay | `SimulatedStoreAndForwardRelayTest.enabledRelayForwardsPacketAndDecrementsTtl` | passed |
| Relay disabled by default | `SimulatedStoreAndForwardRelayTest.relayDisabledBlocksTransitForwarding` | passed |
| Realm membership mesh gate | `MeshTrustGateAuditTest.*Realm*Rejected` | passed |

## One-Device Android Evidence

Fresh one-device screenshots were captured from:

```text
Samsung SM-S938B / R5CY22X6MSB
```

These screenshots support UI/dissertation illustration:

- start screen;
- Overview;
- merged Chats/Contacts;
- Settings;
- Mesh diagnostics;
- Research Panel;
- computational experiment / benchmark screens where available.

They do not prove two-device LAN delivery.

## May 30 Two-Device LAN Evidence Status

The May 30 ADB check saw one device only:

```text
R5CY22X6MSB device model=SM_S938B
```

Therefore, at the May 30 checkpoint, real two-device LAN smoke remained
pending. This was an explicit gate, not a hidden success. Current manual
two-phone LAN/Wi-Fi prototype evidence is now tracked in
`reports/out/two_device_delivery_evidence.md`.

To make future runs repeatable, collect:

1. `adb devices -l` with two devices;
2. app commit hash and APK path;
3. Device A and B Mesh status screenshots;
4. A -> B message screenshot;
5. B incoming message screenshot;
6. A delivered/receipt screenshot;
7. queue metrics before/after;
8. unknown peer rejection or blocked peer rejection evidence.

## Crypto / Keystore Status

Implemented:

- crypto envelope documentation;
- `PacketSigner`;
- `PacketVerifier`;
- `PacketEncryptor`;
- `PacketDecryptor`;
- `PrototypeNoSecurityPacketCrypto`;
- tests that keep prototype crypto visibly non-production.

Not implemented:

- Android Keystore-backed identity provider;
- signed QR payloads;
- signed mesh packets;
- encrypted message payloads;
- production E2EE;
- security review.

## Dissertation Wording

Safe:

> The Android prototype implements a QR-gated local message and mesh transport
> pipeline with automated loopback/two-node delivery tests and explicit
> trust-gating. Manual two-phone LAN/Wi-Fi prototype smoke evidence exists, but
> repeatable two-device automation, latency metrics and production crypto remain
> outside the current claim.

Unsafe:

> Kraken already provides complete production-secure P2P messaging.

## Files

- `reports/out/mesh_delivery_simulation.md`
- `reports/out/android_p2p_smoke_report.md`
- `reports/out/two_device_delivery_evidence.md`
- `reports/out/mesh_metrics_summary.json`
- `docs/prototype-mesh-threat-boundaries.md`
- `docs/mesh-trust-gating-audit.md`
- `protocol-spec/crypto-envelope.md`
- `docs/android-keystore-migration-plan.md`
