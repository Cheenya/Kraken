# Mesh Delivery Simulation

Implementation baseline before this report refresh: `f4a03cf`

Status: automated unit-test evidence for loopback and simulated store-and-forward mesh.

## Covered Scenarios

| Scenario | Evidence | Result |
| --- | --- | --- |
| A -> B loopback delivery | `MeshDeliveryPipelineTest.twoNodeDeliveryAcceptsOnlyActiveQrTrustedRelationships` | passed |
| B -> A receipt | `MeshDeliveryPipelineTest.twoNodeDeliveryAcceptsOnlyActiveQrTrustedRelationships` | passed |
| runtime queue sync sends ready message | `MeshServiceTest.syncNowSendsReadyMessagesToDiscoveredActivePeer` | passed |
| runtime receipt updates message status | `MeshServiceTest.syncNowAppliesReceiptToOutgoingMessage` | passed |
| two service nodes deliver message + receipt | `MeshServiceTest.twoNodeServicesDeliverMessageAndReceiptThroughSharedTransport` | passed |
| trusted peer absent creates persistent queued packet | `MeshServiceTest.readyMessageCreatesPersistentQueuedPacketWhenTrustedPeerNotDiscovered` | passed |
| queued receipt retries after peer becomes available | `MeshServiceTest.inboundMessageQueuesReceiptWhenImmediateSendFailsAndRetriesLater` | passed |
| stale sent message retries when receipt does not arrive | `MeshServiceTest.syncNowRetriesStaleSentMessageWhenReceiptDidNotArrive` | passed |
| pending outbound rejected | `MeshDeliveryPipelineTest.pendingRelationshipCannotSendOutboundPacket` | passed |
| unknown sender rejected | `MeshDeliveryPipelineTest.unknownSenderRejectedInbound` | passed |
| wrong recipient rejected | `MeshDeliveryPipelineTest.wrongRecipientRejectedInbound` | passed |
| duplicate packet rejected | `MeshDeliveryPipelineTest.duplicateInboundPacketRejected` | passed |
| malformed message payload rejected | `MeshDeliveryPipelineTest.messagePayloadTypeMustMatchPacketType` | passed |
| malformed message id mismatch rejected | `MeshDeliveryPipelineTest.messagePayloadIdMustMatchEnvelopeMessageId` | passed |
| malformed receipt id mismatch rejected | `MeshDeliveryPipelineTest.receiptPayloadIdMustMatchEnvelopeMessageId` | passed |
| A -> C -> B simulated relay | `SimulatedStoreAndForwardRelayTest.enabledRelayForwardsPacketAndDecrementsTtl` | passed |
| relay disabled blocks transit | `SimulatedStoreAndForwardRelayTest.relayDisabledBlocksTransitForwarding` | passed |
| receipt routes back in simulation | `SimulatedStoreAndForwardRelayTest.receiptCanRouteBackThroughSimulation` | passed |
| trust gate rejects pending relationship | `MeshTrustGateAuditTest.inboundPendingRelationshipRejected` | passed |
| trust gate rejects blocked/unlinked | `MeshTrustGateAuditTest.inboundBlockedAndUnlinkedRejected` | passed |
| realm membership gate blocks removed/restricted member | `MeshTrustGateAuditTest.inboundRealmPacketFromRemovedMemberRejected`, `outboundRealmPacketToRestrictedMemberRejected` | passed |

## Metrics

Manual latency statistics are not claimed yet. This report records deterministic
unit-test behavior. Current manual two-phone LAN/Wi-Fi smoke evidence
is tracked separately in `reports/out/two_device_delivery_evidence.md`.

Current automated result after report refresh:

```text
./gradlew test — passed
./gradlew assembleDebug — passed
./gradlew installDebug — passed on Samsung SM-S938B / R5CY22X6MSB
git diff --check — passed
```

Planned metrics after two-device LAN smoke:

- delivery success rate;
- median/p95 delivery latency;
- duplicate drop count;
- expired drop count;
- unknown peer rejection count;
- queue time;
- hop count for simulated relay.

## Limitations

- Simulated relay is separate from real relay.
- Real relay is disabled by default.
- Direct LAN/Wi-Fi manual two-phone smoke evidence exists in
  `reports/out/two_device_delivery_evidence.md`; repeatable automated two-device
  harness and latency/reliability metrics are still needed.
- Keystore-backed identity is tracked as a separate migration layer.
- Packet signatures/encryption are tracked as separate envelope layers.

## Граница Evidence

Этот отчёт покрывает детерминированное поведение mesh pipeline в unit-тестах и
моделируемую ретрансляцию. Ручная доставка на телефонах, надёжность маршрутов и
замеры задержки ведутся в отдельных отчётах.
