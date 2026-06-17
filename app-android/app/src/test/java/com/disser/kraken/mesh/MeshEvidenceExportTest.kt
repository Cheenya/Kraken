package com.disser.kraken.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageStatus
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState

class MeshEvidenceExportTest {
    @Test
    fun routeSpecificEvidenceJsonContainsRouteCountersBuildAndDeviceShape() {
        val snapshot = MeshServiceSnapshot(
            state = MeshState.PEER_FOUND,
            transportMode = "multi-route",
            discoveredPeers = listOf(DiscoveredPeer(peerId = "peer-1", fingerprint = "peer-fingerprint")),
            peerRouteEvidence = listOf(
                DiscoveredPeerRouteEvidence(
                    fingerprint = "peer-fingerprint",
                    transportId = KrakenTransportCatalog.LAN_NSD_TCP.id,
                    observedAtEpochMillis = 10_000,
                ),
            ),
            transportDiagnostics = MeshTransportDiagnostics(
                localPort = 49152,
                localAddresses = listOf("192.168.1.10"),
                acceptedConnections = 2,
                inboundPackets = 1,
                malformedFramesDropped = 3,
                sendFailures = 4,
                registrationState = "wifi-direct:registered:_kraken._tcp.",
                discoveryState = "wifi-direct:discovering:_kraken._tcp.",
                discoveredPeerCount = 1,
                manualPeerCount = 0,
                p2pVisibleDeviceCount = 2,
                p2pThisDeviceStatus = "available",
                discoveryCycleCount = 3,
                p2pServiceFoundCount = 4,
                p2pTxtRecordCount = 5,
                p2pTxtRejectedCount = 1,
                p2pTxtBoundPeerCount = 2,
                p2pUnboundVisibleDeviceCount = 1,
                wifiDirectLastBindingError = "wifi-direct-kraken-txt-missing-port:display_name,fingerprint",
                peerFingerprints = listOf("peer-fingerprint-abcdef"),
                p2pInterfaceAddresses = listOf("192.168.49.1"),
                wifiDirectGroupFormed = true,
                wifiDirectIsGroupOwner = true,
                wifiDirectGroupRole = "owner",
                wifiDirectGroupOwnerAddress = "192.168.49.1",
                wifiDirectLocalP2pAddress = "192.168.49.1",
                wifiDirectServerBindAddress = "192.168.49.1",
                wifiDirectLastSendHost = "192.168.49.59",
                wifiDirectLastSendPort = 43195,
                wifiDirectEndpointBindingState = "BOUND",
                wifiDirectEndpointBindingReason = "fallback-p2p-client-host",
                wifiDirectRelationshipPeerFingerprintPrefix = "peer-fingerp",
                wifiDirectLastConnectResult = "failed:attempt=1:intent=0:reason=ERROR(0)",
                wifiDirectLastConnectFailureReason = 0,
                wifiDirectConnectAttempts = listOf(
                    WifiDirectConnectAttemptDiagnostic(
                        attempt = 1,
                        groupOwnerIntent = 0,
                        result = "failed:attempt=1:intent=0:reason=ERROR(0)",
                        failureReason = 0,
                        failureReasonName = "ERROR",
                        stopPeerDiscoveryResult = "stopped",
                        preConnectCancelResult = null,
                    ),
                ),
                wifiDirectDiscoveredPeers = listOf(
                    WifiDirectPeerEndpointDiagnostic(
                        fingerprintPrefix = "peer-fingerp",
                        deviceAddress = "aa:bb:cc:dd:ee:ff",
                        deviceName = "Peer Phone",
                        host = "192.168.49.59",
                        port = 48381,
                        bindingState = "BOUND",
                        bindingSource = "fallback-p2p-client-host",
                    ),
                ),
                wifiDirectVisibleDevices = listOf(
                    WifiDirectVisibleDeviceDiagnostic(
                        deviceAddress = "aa:bb:cc:dd:ee:ff",
                        deviceName = "Peer Phone",
                        status = "available",
                    ),
                ),
                wifiDirectTxtRecords = listOf(
                    WifiDirectTxtRecordDiagnostic(
                        deviceAddress = "aa:bb:cc:dd:ee:ff",
                        deviceName = "Peer Phone",
                        fingerprintPrefix = "peer-fingerp",
                        port = 48381,
                        keys = listOf("display_name", "fingerprint", "port"),
                        accepted = true,
                    ),
                ),
                wifiDirectBoundEndpoints = listOf(
                    WifiDirectBoundEndpointDiagnostic(
                        fingerprintPrefix = "peer-fingerp",
                        deviceAddress = "aa:bb:cc:dd:ee:ff",
                        deviceName = "Peer Phone",
                        host = "192.168.49.59",
                        port = 48381,
                        bindingSource = "fallback-p2p-client-host",
                    ),
                ),
                transportModes = listOf(KrakenTransportCatalog.BLE_GATT.id, KrakenTransportCatalog.LAN_NSD_TCP.id),
                recentRouteAttempts = listOf(
                    MeshRouteAttempt(
                        route = KrakenTransportCatalog.LAN_NSD_TCP.id,
                        success = false,
                        error = "tcp-timeout",
                        atEpochMillis = 10_100,
                    ),
                    MeshRouteAttempt(
                        route = KrakenTransportCatalog.BLE_GATT.id,
                        success = true,
                        error = null,
                        atEpochMillis = 10_200,
                    ),
                ),
            ),
            queuedPackets = 2,
            lastPacketStatus = "receipt-received",
            metrics = MeshMetricsSnapshot(
                packetsQueued = 2,
                packetsSent = 3,
                packetsReceived = 4,
                receiptsReceived = 5,
                duplicatesDropped = 6,
                expiredDropped = 7,
                unknownPeerRejected = 8,
                wrongRecipientRejected = 9,
                lastDeliveryLatencyMs = 123,
            ),
            debugSmoke = MeshDebugSmokeSnapshot(
                evidenceMode = "debug_local_inbox_packet_injection_and_queue_retry_probe",
                ranAtEpochMillis = 12_000,
                unknownPeerInjected = true,
                wrongRecipientInjected = true,
                duplicateInjected = true,
                queueRetryMessageId = "message-debug",
                queueRetryBody = "queueRetry123456",
                queuedBeforeTransportRestart = true,
                queueSizeBeforeRestart = 1,
                sentAfterTransportRestart = true,
                deliveredAfterTransportRestart = false,
                queueSizeAfterRestart = 0,
                messageStatusAfterRestart = "SENT_TO_TRANSPORT",
            ),
            realmRelayCandidates = listOf(
                MeshRealmRelayCandidateSnapshot(
                    peerId = "peer-relay-1",
                    peerFingerprint = "relay-fingerprint-abcdef",
                    peerFingerprintPrefix = "relay-finge",
                    displayName = "Relay",
                    realmId = "realm-1",
                    relayPeerPublicKeyPrefix = "placeholder-pub:",
                ),
            ),
        )

        val export = MeshEvidenceExporter.build(
            snapshot = snapshot,
            generatedAtEpochMillis = 11_000,
            appBuildType = "debug",
            appVersionName = "0.1.0",
            gitSha = "abc1234",
            deviceModel = "SM-S938B",
            sourceState = "dirty_working_tree_based_on_abc1234",
            transportReadiness = MeshEvidenceTransportExport(
                wifiDirectPermissions = MeshEvidenceWifiDirectPermissionExport(
                    nearbyWifiDevicesRequired = true,
                    nearbyWifiDevicesDeclared = true,
                    nearbyWifiDevicesGranted = true,
                    fineLocationRequired = true,
                    fineLocationDeclared = true,
                    fineLocationGranted = false,
                    fineLocationAppOp = "ignored",
                    warning = "wifi-direct-fine-location-missing-modern-android",
                ),
            ),
            messages = listOf(
                LocalMessage(
                    messageId = "message-target-123",
                    conversationId = "conversation-1",
                    relationshipId = "relationship-1",
                    peerFingerprint = "peer-fingerprint-abcdef",
                    direction = MessageDirection.INCOMING,
                    status = MessageStatus.DELIVERED_TO_PEER,
                    body = "target message",
                    createdAtEpochMillis = 10_300,
                    updatedAtEpochMillis = 10_400,
                ),
            ),
            receivedPackets = listOf(
                StoredPacket(
                    packet = KrakenPacket(
                        packetId = "packet-target-123",
                        packetType = KrakenPacketType.MESSAGE,
                        senderFingerprint = "peer-fingerprint-abcdef",
                        recipientFingerprint = "local-fingerprint-abcdef",
                        relationshipId = "relationship-1",
                        conversationId = "conversation-1",
                        messageId = "message-target-123",
                        createdAtEpochMillis = 10_250,
                        expiresAtEpochMillis = 70_250,
                        ttlHops = 4,
                        payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
                        payloadJson = "{}",
                    ),
                    status = PacketStoreStatus.RECEIVED,
                    storedAtEpochMillis = 10_350,
                ),
            ),
        )
        val json = MeshEvidenceExporter.toJson(export)
        val markdown = MeshEvidenceExporter.toMarkdownSummary(export)

        assertEquals(KrakenTransportCatalog.BLE_GATT.id, export.transport.selectedRoute)
        assertEquals(
            listOf(KrakenTransportCatalog.BLE_GATT.id, KrakenTransportCatalog.LAN_NSD_TCP.id),
            export.transport.enabledTransportModes,
        )
        assertEquals(49152, export.transport.localPort)
        assertEquals(listOf("192.168.1.10"), export.transport.localAddresses)
        assertEquals(listOf("192.168.49.1"), export.transport.p2pInterfaceAddresses)
        assertEquals(true, export.transport.wifiDirectGroupFormed)
        assertEquals(true, export.transport.wifiDirectIsGroupOwner)
        assertEquals("owner", export.transport.wifiDirectGroupRole)
        assertEquals("192.168.49.1", export.transport.wifiDirectGroupOwnerAddress)
        assertEquals("192.168.49.1", export.transport.wifiDirectLocalP2pAddress)
        assertEquals("192.168.49.1", export.transport.wifiDirectServerBindAddress)
        assertEquals("192.168.49.59", export.transport.wifiDirectLastSendHost)
        assertEquals(43195, export.transport.wifiDirectLastSendPort)
        assertEquals("BOUND", export.transport.wifiDirectEndpointBindingState)
        assertEquals("fallback-p2p-client-host", export.transport.wifiDirectEndpointBindingReason)
        assertEquals("peer-fingerp", export.transport.wifiDirectRelationshipPeerFingerprintPrefix)
        assertEquals(1, export.transport.wifiDirectConnectAttempts.size)
        assertEquals("ERROR", export.transport.wifiDirectConnectAttempts.single().failureReasonName)
        assertEquals(1, export.transport.wifiDirectDiscoveredPeers.size)
        assertEquals(1, export.transport.wifiDirectVisibleDevices.size)
        assertEquals(1, export.transport.wifiDirectTxtRecords.size)
        assertEquals(1, export.transport.wifiDirectBoundEndpoints.size)
        assertEquals(2, export.transport.acceptedConnections)
        assertEquals(1, export.transport.inboundPackets)
        assertEquals(3, export.transport.malformedFramesDropped)
        assertEquals(4, export.transport.sendFailures)
        assertEquals("wifi-direct:registered:_kraken._tcp.", export.transport.registrationState)
        assertEquals("wifi-direct:discovering:_kraken._tcp.", export.transport.discoveryState)
        assertEquals(1, export.transport.transportDiscoveredPeerCount)
        assertEquals(listOf("peer-fingerp"), export.transport.observedPeerFingerprintPrefixes)
        assertEquals(0, export.transport.manualPeerCount)
        assertEquals(2, export.transport.p2pVisibleDeviceCount)
        assertEquals("available", export.transport.p2pThisDeviceStatus)
        assertEquals(3, export.transport.discoveryCycleCount)
        assertEquals(4, export.transport.p2pServiceFoundCount)
        assertEquals(5, export.transport.p2pTxtRecordCount)
        assertEquals(1, export.transport.p2pTxtRejectedCount)
        assertEquals(2, export.transport.p2pTxtBoundPeerCount)
        assertEquals(1, export.transport.p2pUnboundVisibleDeviceCount)
        assertEquals(
            "wifi-direct-kraken-txt-missing-port:display_name,fingerprint",
            export.transport.wifiDirectLastBindingError,
        )
        assertEquals(false, export.transport.wifiDirectPermissions?.fineLocationGranted)
        assertEquals(
            "wifi-direct-fine-location-missing-modern-android",
            export.transport.wifiDirectPermissions?.warning,
        )
        assertEquals("message-target-123", export.targetDelivery.recentMessages.single().messageId)
        assertEquals("packet-target-123", export.targetDelivery.receivedPackets.single().packetId)
        assertEquals(1, export.transport.realmRelayCandidateCount)
        assertEquals("realm-1", export.transport.realmRelayCandidates.single().realmId)
        assertEquals(false, export.transport.realmRelayCandidates.single().directMessageAllowed)
        assertEquals(2, export.queueSize)
        assertEquals(9, export.metrics.wrongRecipientRejected)
        assertEquals("SM-S938B", export.deviceModel)

        listOf(
            "\"report_version\": \"kraken.mesh.evidence.snapshot.v2\"",
            "\"selected_route\": \"ble-gatt\"",
            "\"enabled_transport_modes\": [",
            "\"registration_state\": \"wifi-direct:registered:_kraken._tcp.\"",
            "\"discovery_state\": \"wifi-direct:discovering:_kraken._tcp.\"",
            "\"transport_discovered_peer_count\": 1",
            "\"observed_peer_fingerprint_prefixes\": [",
            "\"peer-fingerp\"",
            "\"manual_peer_count\": 0",
            "\"p2p_visible_device_count\": 2",
            "\"p2p_this_device_status\": \"available\"",
            "\"discovery_cycle_count\": 3",
            "\"p2p_service_found_count\": 4",
            "\"p2p_txt_record_count\": 5",
            "\"p2p_txt_rejected_count\": 1",
            "\"p2p_txt_bound_peer_count\": 2",
            "\"p2p_unbound_visible_device_count\": 1",
            "\"wifi_direct_last_binding_error\": \"wifi-direct-kraken-txt-missing-port:display_name,fingerprint\"",
            "\"wifi_direct_permissions\": {",
            "\"nearby_wifi_devices_required\": true",
            "\"nearby_wifi_devices_granted\": true",
            "\"fine_location_required\": true",
            "\"fine_location_declared\": true",
            "\"fine_location_granted\": false",
            "\"fine_location_app_op\": \"ignored\"",
            "\"warning\": \"wifi-direct-fine-location-missing-modern-android\"",
            "\"target_delivery\": {",
            "\"recent_messages\": [",
            "\"message_id\": \"message-target-123\"",
            "\"direction\": \"INCOMING\"",
            "\"status\": \"DELIVERED_TO_PEER\"",
            "\"received_packets\": [",
            "\"packet_id\": \"packet-target-123\"",
            "\"packet_type\": \"MESSAGE\"",
            "\"sender_fingerprint_prefix\": \"peer-fingerp\"",
            "\"local_port\": 49152",
            "\"local_addresses\": [",
            "\"p2p_interface_addresses\": [",
            "\"wifi_direct_group_formed\": true",
            "\"wifi_direct_is_group_owner\": true",
            "\"wifi_direct_group_role\": \"owner\"",
            "\"wifi_direct_group_owner_address\": \"192.168.49.1\"",
            "\"wifi_direct_local_p2p_address\": \"192.168.49.1\"",
            "\"wifi_direct_server_bind_address\": \"192.168.49.1\"",
            "\"wifi_direct_last_send_host\": \"192.168.49.59\"",
            "\"wifi_direct_last_send_port\": 43195",
            "\"wifi_direct_endpoint_binding_state\": \"BOUND\"",
            "\"wifi_direct_endpoint_binding_reason\": \"fallback-p2p-client-host\"",
            "\"wifi_direct_relationship_peer_fingerprint_prefix\": \"peer-fingerp\"",
            "\"wifi_direct_last_connect_result\": \"failed:attempt=1:intent=0:reason=ERROR(0)\"",
            "\"wifi_direct_last_connect_failure_reason\": 0",
            "\"wifi_direct_connect_attempts\": [",
            "\"failure_reason_name\": \"ERROR\"",
            "\"stop_peer_discovery_result\": \"stopped\"",
            "\"wifi_direct_discovered_peers\": [",
            "\"binding_state\": \"BOUND\"",
            "\"binding_source\": \"fallback-p2p-client-host\"",
            "\"wifi_direct_visible_devices\": [",
            "\"status\": \"available\"",
            "\"wifi_direct_txt_records\": [",
            "\"accepted\": true",
            "\"wifi_direct_bound_endpoints\": [",
            "\"realm_relay_candidate_count\": 1",
            "\"realm_relay_candidates\": [",
            "\"direct_message_allowed\": false",
            "\"accepted_connections\": 2",
            "\"inbound_packets\": 1",
            "\"malformed_frames_dropped\": 3",
            "\"send_failures\": 4",
            "\"recent_route_attempts\"",
            "\"packets_sent\": 3",
            "\"packets_received\": 4",
            "\"receipts_received\": 5",
            "\"duplicates_dropped\": 6",
            "\"expired_dropped\": 7",
            "\"unknown_peer_rejected\": 8",
            "\"wrong_recipient_rejected\": 9",
            "\"last_delivery_latency_ms\": 123",
            "\"queue_size\": 2",
            "\"last_packet_status\": \"receipt-received\"",
            "\"app_build_type\": \"debug\"",
            "\"app_version_name\": \"0.1.0\"",
            "\"git_sha\": \"abc1234\"",
            "\"device_model\": \"SM-S938B\"",
            "\"debug_smoke\"",
            "\"evidence_mode\": \"debug_local_inbox_packet_injection_and_queue_retry_probe\"",
            "\"unknown_peer_injected\": true",
            "\"wrong_recipient_injected\": true",
            "\"duplicate_injected\": true",
            "\"queued_before_transport_restart\": true",
            "\"queue_size_before_restart\": 1",
            "\"sent_after_transport_restart\": true",
            "\"queue_size_after_restart\": 0",
            "\"message_status_after_restart\": \"SENT_TO_TRANSPORT\"",
            "\"claim_boundary\": \"${MeshEvidenceExporter.CLAIM_BOUNDARY}\"",
        ).forEach { required ->
            assertTrue("JSON evidence must contain $required", json.contains(required))
        }

        assertTrue(markdown.contains("Selected route: `ble-gatt`"))
        assertTrue(markdown.contains("Enabled transport modes: `ble-gatt, lan-nsd-tcp`"))
        assertTrue(markdown.contains("Registration state: `wifi-direct:registered:_kraken._tcp.`"))
        assertTrue(markdown.contains("Discovery state: `wifi-direct:discovering:_kraken._tcp.`"))
        assertTrue(markdown.contains("Transport discovered peers: `1`"))
        assertTrue(markdown.contains("Observed peer fingerprint prefixes: `peer-fingerp`"))
        assertTrue(markdown.contains("P2P visible devices: `2`"))
        assertTrue(markdown.contains("P2P this-device status: `available`"))
        assertTrue(markdown.contains("Discovery cycles: `3`"))
        assertTrue(markdown.contains("Local transport endpoint: `192.168.1.10:49152`"))
        assertTrue(markdown.contains("P2P interface addresses: `192.168.49.1`"))
        assertTrue(markdown.contains("Wi-Fi Direct group formed: `true`"))
        assertTrue(markdown.contains("Wi-Fi Direct group role: `owner`"))
        assertTrue(markdown.contains("Wi-Fi Direct local P2P address: `192.168.49.1`"))
        assertTrue(markdown.contains("Wi-Fi Direct server bind address: `192.168.49.1`"))
        assertTrue(markdown.contains("Wi-Fi Direct last send endpoint: `192.168.49.59:43195`"))
        assertTrue(markdown.contains("Wi-Fi Direct endpoint binding state: `BOUND`"))
        assertTrue(markdown.contains("Wi-Fi Direct endpoint binding reason: `fallback-p2p-client-host`"))
        assertTrue(markdown.contains("Wi-Fi Direct discovered peer endpoints: `1`"))
        assertTrue(markdown.contains("Wi-Fi Direct visible devices: `1`"))
        assertTrue(markdown.contains("Wi-Fi Direct TXT record diagnostics: `1`"))
        assertTrue(markdown.contains("Wi-Fi Direct bound endpoints: `1`"))
        assertTrue(markdown.contains("Realm relay candidates: `1`"))
        assertTrue(markdown.contains("Realm relay candidate prefixes: `relay-finge`"))
        assertTrue(markdown.contains("Wi-Fi Direct fine location declared/granted/app-op: `true/false/ignored`"))
        assertTrue(markdown.contains("Wi-Fi Direct permission warning: `wifi-direct-fine-location-missing-modern-android`"))
        assertTrue(markdown.contains("recentMessages: `1`"))
        assertTrue(markdown.contains("receivedPackets: `1`"))
        assertTrue(markdown.contains("recentMessageIds: `message-target-123`"))
        assertTrue(markdown.contains("receivedPacketIds: `packet-target-123`"))
        assertTrue(markdown.contains("wrongRecipientRejected: `9`"))
        assertTrue(markdown.contains("malformedFramesDropped: `3`"))
        assertTrue(markdown.contains("evidenceMode: `debug_local_inbox_packet_injection_and_queue_retry_probe`"))
        assertTrue(markdown.contains("queuedBeforeTransportRestart: `true`"))
        assertTrue(markdown.contains("sentAfterTransportRestart: `true`"))
        assertTrue(markdown.contains("Prototype evidence only. Message payload encryption is implemented for the protected path; production key storage, signatures, replay hardening and external review are still separate work."))
    }

    @Test
    fun wrongRecipientDropIsRecordedAsDedicatedCounter() {
        val service = MeshService()

        val snapshot = service.recordDrop(emptyList(), MeshRejectionReason.WRONG_RECIPIENT)

        assertEquals(1, snapshot.metrics.wrongRecipientRejected)
        assertEquals(0, snapshot.metrics.unknownPeerRejected)
        assertEquals("rejected-WRONG_RECIPIENT", snapshot.lastPacketStatus)
    }

    @Test
    fun wifiDirectCanBeSelectedAsRouteEvidence() {
        val snapshot = MeshServiceSnapshot(
            state = MeshState.PEER_FOUND,
            transportMode = "multi-route",
            peerRouteEvidence = listOf(
                DiscoveredPeerRouteEvidence(
                    fingerprint = "peer-fingerprint",
                    transportId = KrakenTransportCatalog.WIFI_DIRECT.id,
                    observedAtEpochMillis = 10_000,
                ),
            ),
        )

        val export = MeshEvidenceExporter.build(
            snapshot = snapshot,
            generatedAtEpochMillis = 11_000,
            appBuildType = "debug",
            appVersionName = "0.1.0",
            gitSha = "abc1234",
            deviceModel = "SM-S938B",
            sourceState = "dirty_working_tree_based_on_abc1234",
        )

        assertEquals(KrakenTransportCatalog.WIFI_DIRECT.id, export.transport.selectedRoute)
    }

    @Test
    fun debugNegativeEvidenceProbeUpdatesDedicatedCounters() {
        val service = MeshService()
        val alice = identity("alice", "Alice", "ALICE-FP")
        val bob = identity("bob", "Bob", "BOB-FP")

        val snapshot = service.recordDebugNegativeEvidence(
            messages = emptyList(),
            localIdentity = bob,
            relationships = listOf(relationship(bob, alice, RelationshipState.ACTIVE)),
        )

        assertEquals(1, snapshot.metrics.unknownPeerRejected)
        assertEquals(1, snapshot.metrics.wrongRecipientRejected)
        assertEquals(1, snapshot.metrics.duplicatesDropped)
        assertTrue(snapshot.debugSmoke.unknownPeerInjected)
        assertTrue(snapshot.debugSmoke.wrongRecipientInjected)
        assertTrue(snapshot.debugSmoke.duplicateInjected)
        assertEquals("debug_local_inbox_packet_injection_and_queue_retry_probe", snapshot.debugSmoke.evidenceMode)
        assertEquals("debug-local-inbox-injection-recorded", snapshot.lastPacketStatus)
    }

    @Test
    fun debugNegativeEvidenceProbeDoesNotInventCountersWithoutRelationship() {
        val service = MeshService()
        val bob = identity("bob", "Bob", "BOB-FP")

        val snapshot = service.recordDebugNegativeEvidence(
            messages = emptyList(),
            localIdentity = bob,
            relationships = emptyList(),
        )

        assertEquals(0, snapshot.metrics.unknownPeerRejected)
        assertEquals(0, snapshot.metrics.wrongRecipientRejected)
        assertEquals(0, snapshot.metrics.duplicatesDropped)
        assertEquals("debug_local_inbox_packet_injection_unavailable", snapshot.debugSmoke.evidenceMode)
        assertEquals("debug-local-inbox-injection-unavailable", snapshot.lastPacketStatus)
    }

    private fun identity(id: String, name: String, fingerprint: String): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = name,
            publicKeyEncoded = "placeholder-pub:$id",
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = fingerprint,
            createdAtEpochMillis = 1_700_000_000_000,
        )

    private fun relationship(
        localIdentity: LocalIdentity,
        peerIdentity: LocalIdentity,
        state: RelationshipState,
    ): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = localIdentity.publicKeyEncoded,
            peerPublicKey = peerIdentity.publicKeyEncoded,
            peerDisplayName = peerIdentity.displayName,
            peerFingerprint = peerIdentity.fingerprint,
            realmId = null,
            state = state,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = "invite-1",
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )
}
