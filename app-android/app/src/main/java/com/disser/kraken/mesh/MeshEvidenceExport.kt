package com.disser.kraken.mesh

import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.message.LocalMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class MeshEvidenceMetricsExport(
    @SerialName("queue_size")
    val queueSize: Int,
    @SerialName("packets_queued")
    val packetsQueued: Int,
    @SerialName("packets_sent")
    val packetsSent: Int,
    @SerialName("packets_received")
    val packetsReceived: Int,
    @SerialName("receipts_received")
    val receiptsReceived: Int,
    @SerialName("duplicates_dropped")
    val duplicatesDropped: Int,
    @SerialName("expired_dropped")
    val expiredDropped: Int,
    @SerialName("unknown_peer_rejected")
    val unknownPeerRejected: Int,
    @SerialName("wrong_recipient_rejected")
    val wrongRecipientRejected: Int,
    @SerialName("relay_forwarded")
    val relayForwarded: Int,
    @SerialName("last_delivery_latency_ms")
    val lastDeliveryLatencyMs: Long?,
)

@Serializable
data class MeshEvidenceTransportPathExport(
    @SerialName("permission_granted")
    val permissionGranted: Boolean,
    @SerialName("radio_enabled")
    val radioEnabled: Boolean,
    @SerialName("service_available")
    val serviceAvailable: Boolean,
    val active: Boolean,
    @SerialName("inactive_reasons")
    val inactiveReasons: List<String>,
)

@Serializable
data class MeshEvidenceWifiDirectPermissionExport(
    @SerialName("nearby_wifi_devices_required")
    val nearbyWifiDevicesRequired: Boolean,
    @SerialName("nearby_wifi_devices_declared")
    val nearbyWifiDevicesDeclared: Boolean,
    @SerialName("nearby_wifi_devices_granted")
    val nearbyWifiDevicesGranted: Boolean,
    @SerialName("fine_location_required")
    val fineLocationRequired: Boolean,
    @SerialName("fine_location_declared")
    val fineLocationDeclared: Boolean,
    @SerialName("fine_location_granted")
    val fineLocationGranted: Boolean,
    @SerialName("fine_location_app_op")
    val fineLocationAppOp: String?,
    @SerialName("warning")
    val warning: String?,
)

@Serializable
data class MeshEvidenceRouteAttemptExport(
    val path: String,
    @SerialName("peer_id")
    val peerId: String,
    @SerialName("peer_fingerprint")
    val peerFingerprint: String,
    val success: Boolean,
    val error: String?,
    @SerialName("attempted_at_epoch_millis")
    val attemptedAtEpochMillis: Long,
)

@Serializable
data class MeshEvidenceRealmRelayCandidateExport(
    @SerialName("peer_id")
    val peerId: String,
    @SerialName("peer_fingerprint_prefix")
    val peerFingerprintPrefix: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("realm_id")
    val realmId: String,
    @SerialName("relay_peer_public_key_prefix")
    val relayPeerPublicKeyPrefix: String,
    @SerialName("direct_message_allowed")
    val directMessageAllowed: Boolean = false,
)

@Serializable
data class MeshEvidenceTransportExport(
    @SerialName("selected_route")
    val selectedRoute: String = "none",
    @SerialName("enabled_transport_modes")
    val enabledTransportModes: List<String> = emptyList(),
    @SerialName("registration_state")
    val registrationState: String = "not-started",
    @SerialName("discovery_state")
    val discoveryState: String = "not-started",
    @SerialName("transport_discovered_peer_count")
    val transportDiscoveredPeerCount: Int = 0,
    @SerialName("observed_peer_fingerprint_prefixes")
    val observedPeerFingerprintPrefixes: List<String> = emptyList(),
    @SerialName("manual_peer_count")
    val manualPeerCount: Int = 0,
    @SerialName("p2p_visible_device_count")
    val p2pVisibleDeviceCount: Int = 0,
    @SerialName("p2p_this_device_status")
    val p2pThisDeviceStatus: String? = null,
    @SerialName("discovery_cycle_count")
    val discoveryCycleCount: Int = 0,
    @SerialName("p2p_service_found_count")
    val p2pServiceFoundCount: Int = 0,
    @SerialName("p2p_txt_record_count")
    val p2pTxtRecordCount: Int = 0,
    @SerialName("p2p_txt_rejected_count")
    val p2pTxtRejectedCount: Int = 0,
    @SerialName("p2p_txt_bound_peer_count")
    val p2pTxtBoundPeerCount: Int = 0,
    @SerialName("p2p_unbound_visible_device_count")
    val p2pUnboundVisibleDeviceCount: Int = 0,
    @SerialName("wifi_direct_last_binding_error")
    val wifiDirectLastBindingError: String? = null,
    @SerialName("local_port")
    val localPort: Int? = null,
    @SerialName("local_addresses")
    val localAddresses: List<String> = emptyList(),
    @SerialName("p2p_interface_addresses")
    val p2pInterfaceAddresses: List<String> = emptyList(),
    @SerialName("wifi_direct_group_formed")
    val wifiDirectGroupFormed: Boolean? = null,
    @SerialName("wifi_direct_is_group_owner")
    val wifiDirectIsGroupOwner: Boolean? = null,
    @SerialName("wifi_direct_group_role")
    val wifiDirectGroupRole: String = "unknown",
    @SerialName("wifi_direct_group_owner_address")
    val wifiDirectGroupOwnerAddress: String? = null,
    @SerialName("wifi_direct_local_p2p_address")
    val wifiDirectLocalP2pAddress: String? = null,
    @SerialName("wifi_direct_server_bind_address")
    val wifiDirectServerBindAddress: String? = null,
    @SerialName("wifi_direct_last_send_host")
    val wifiDirectLastSendHost: String? = null,
    @SerialName("wifi_direct_last_send_port")
    val wifiDirectLastSendPort: Int? = null,
    @SerialName("wifi_direct_endpoint_binding_state")
    val wifiDirectEndpointBindingState: String = "UNSEEN",
    @SerialName("wifi_direct_endpoint_binding_reason")
    val wifiDirectEndpointBindingReason: String? = null,
    @SerialName("wifi_direct_relationship_peer_fingerprint_prefix")
    val wifiDirectRelationshipPeerFingerprintPrefix: String? = null,
    @SerialName("wifi_direct_last_connect_device_address")
    val wifiDirectLastConnectDeviceAddress: String? = null,
    @SerialName("wifi_direct_last_connect_device_name")
    val wifiDirectLastConnectDeviceName: String? = null,
    @SerialName("wifi_direct_last_connect_group_owner_intent")
    val wifiDirectLastConnectGroupOwnerIntent: Int? = null,
    @SerialName("wifi_direct_last_connect_result")
    val wifiDirectLastConnectResult: String? = null,
    @SerialName("wifi_direct_last_connect_failure_reason")
    val wifiDirectLastConnectFailureReason: Int? = null,
    @SerialName("wifi_direct_connect_attempts")
    val wifiDirectConnectAttempts: List<WifiDirectConnectAttemptDiagnostic> = emptyList(),
    @SerialName("wifi_direct_discovered_peers")
    val wifiDirectDiscoveredPeers: List<WifiDirectPeerEndpointDiagnostic> = emptyList(),
    @SerialName("wifi_direct_visible_devices")
    val wifiDirectVisibleDevices: List<WifiDirectVisibleDeviceDiagnostic> = emptyList(),
    @SerialName("wifi_direct_txt_records")
    val wifiDirectTxtRecords: List<WifiDirectTxtRecordDiagnostic> = emptyList(),
    @SerialName("wifi_direct_bound_endpoints")
    val wifiDirectBoundEndpoints: List<WifiDirectBoundEndpointDiagnostic> = emptyList(),
    @SerialName("accepted_connections")
    val acceptedConnections: Int = 0,
    @SerialName("inbound_packets")
    val inboundPackets: Int = 0,
    @SerialName("malformed_frames_dropped")
    val malformedFramesDropped: Int = 0,
    @SerialName("send_failures")
    val sendFailures: Int = 0,
    @SerialName("lan_wifi")
    val lanWifi: MeshEvidenceTransportPathExport? = null,
    @SerialName("ble")
    val ble: MeshEvidenceTransportPathExport? = null,
    @SerialName("wifi_direct")
    val wifiDirect: MeshEvidenceTransportPathExport? = null,
    @SerialName("wifi_direct_permissions")
    val wifiDirectPermissions: MeshEvidenceWifiDirectPermissionExport? = null,
    @SerialName("recent_route_attempts")
    val recentRouteAttempts: List<MeshEvidenceRouteAttemptExport> = emptyList(),
    @SerialName("realm_relay_candidate_count")
    val realmRelayCandidateCount: Int = 0,
    @SerialName("realm_relay_candidates")
    val realmRelayCandidates: List<MeshEvidenceRealmRelayCandidateExport> = emptyList(),
    @SerialName("last_transport_error")
    val lastTransportError: String? = null,
)

@Serializable
data class MeshEvidenceDebugSmokeExport(
    @SerialName("evidence_mode")
    val evidenceMode: String,
    @SerialName("ran_at_epoch_millis")
    val ranAtEpochMillis: Long?,
    @SerialName("unknown_peer_injected")
    val unknownPeerInjected: Boolean,
    @SerialName("wrong_recipient_injected")
    val wrongRecipientInjected: Boolean,
    @SerialName("duplicate_injected")
    val duplicateInjected: Boolean,
    @SerialName("queue_retry_message_id")
    val queueRetryMessageId: String?,
    @SerialName("queue_retry_body")
    val queueRetryBody: String?,
    @SerialName("queued_before_transport_restart")
    val queuedBeforeTransportRestart: Boolean,
    @SerialName("queue_size_before_restart")
    val queueSizeBeforeRestart: Int,
    @SerialName("sent_after_transport_restart")
    val sentAfterTransportRestart: Boolean,
    @SerialName("delivered_after_transport_restart")
    val deliveredAfterTransportRestart: Boolean,
    @SerialName("queue_size_after_restart")
    val queueSizeAfterRestart: Int,
    @SerialName("message_status_after_restart")
    val messageStatusAfterRestart: String?,
)

@Serializable
data class MeshEvidenceTargetMessageExport(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("relationship_id")
    val relationshipId: String,
    @SerialName("peer_fingerprint_prefix")
    val peerFingerprintPrefix: String,
    val direction: String,
    val status: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Serializable
data class MeshEvidenceTargetPacketExport(
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("message_id")
    val messageId: String?,
    @SerialName("packet_type")
    val packetType: String,
    @SerialName("sender_fingerprint_prefix")
    val senderFingerprintPrefix: String,
    @SerialName("recipient_fingerprint_prefix")
    val recipientFingerprintPrefix: String,
    val status: String,
    @SerialName("stored_at_epoch_millis")
    val storedAtEpochMillis: Long,
)

@Serializable
data class MeshEvidenceTargetDeliveryExport(
    @SerialName("recent_messages")
    val recentMessages: List<MeshEvidenceTargetMessageExport>,
    @SerialName("received_packets")
    val receivedPackets: List<MeshEvidenceTargetPacketExport>,
)

@Serializable
data class MeshEvidenceRejectedInboundPacketExport(
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("message_id")
    val messageId: String?,
    val reason: String?,
    @SerialName("sender_fingerprint")
    val senderFingerprint: String,
    @SerialName("recipient_fingerprint")
    val recipientFingerprint: String,
    @SerialName("relationship_id")
    val relationshipId: String,
    @SerialName("recipient_matches_local")
    val recipientMatchesLocal: Boolean,
    @SerialName("recipient_normalized_matches_local")
    val recipientNormalizedMatchesLocal: Boolean,
    @SerialName("relationship_id_known")
    val relationshipIdKnown: Boolean,
    @SerialName("sender_fingerprint_known")
    val senderFingerprintKnown: Boolean,
    @SerialName("sender_fingerprint_normalized_known")
    val senderFingerprintNormalizedKnown: Boolean,
    @SerialName("relationship_and_sender_known")
    val relationshipAndSenderKnown: Boolean,
    @SerialName("local_identity_public_key_matches_relationship")
    val localIdentityPublicKeyMatchesRelationship: Boolean,
    @SerialName("rejected_at_epoch_millis")
    val rejectedAtEpochMillis: Long,
)

@Serializable
data class MeshEvidenceSnapshotExport(
    @SerialName("report_version")
    val reportVersion: String,
    @SerialName("generated_at_epoch_millis")
    val generatedAtEpochMillis: Long,
    @SerialName("mesh_state")
    val meshState: String,
    @SerialName("transport_mode")
    val transportMode: String,
    @SerialName("discovered_peer_count")
    val discoveredPeerCount: Int,
    @SerialName("queued_packets")
    val queuedPackets: Int,
    @SerialName("queue_size")
    val queueSize: Int,
    @SerialName("last_packet_status")
    val lastPacketStatus: String,
    @SerialName("app_build_type")
    val appBuildType: String,
    @SerialName("app_version_name")
    val appVersionName: String,
    @SerialName("git_sha")
    val gitSha: String,
    @SerialName("device_model")
    val deviceModel: String,
    @SerialName("source_state")
    val sourceState: String,
    @SerialName("metrics")
    val metrics: MeshEvidenceMetricsExport,
    @SerialName("transport")
    val transport: MeshEvidenceTransportExport,
    @SerialName("debug_smoke")
    val debugSmoke: MeshEvidenceDebugSmokeExport,
    @SerialName("target_delivery")
    val targetDelivery: MeshEvidenceTargetDeliveryExport = MeshEvidenceTargetDeliveryExport(
        recentMessages = emptyList(),
        receivedPackets = emptyList(),
    ),
    @SerialName("recent_rejected_inbound_packets")
    val recentRejectedInboundPackets: List<MeshEvidenceRejectedInboundPacketExport> = emptyList(),
    @SerialName("adamova_profile_id")
    val adamovaProfileId: String,
    @SerialName("adamova_source_example_id")
    val adamovaSourceExampleId: String,
    @SerialName("adamova_evidence_asset_path")
    val adamovaEvidenceAssetPath: String,
    @SerialName("adamova_reference_validation_status")
    val adamovaReferenceValidationStatus: String,
    @SerialName("integrity_mode")
    val integrityMode: String,
    @SerialName("claim_boundary")
    val claimBoundary: String,
    @SerialName("manual_two_device_evidence")
    val manualTwoDeviceEvidence: String,
)

object MeshEvidenceExporter {
    const val REPORT_VERSION = "kraken.mesh.evidence.snapshot.v2"
    const val CLAIM_BOUNDARY =
        "prototype_p2p_integrity_and_adamova_admission_only_not_production_secure_messaging"
    const val INTEGRITY_MODE = "adamova-bound-aes-gcm-message-payload-no-production-signature-or-keystore"
    const val MANUAL_TWO_DEVICE_PENDING = "pending_real_two_device_lan_smoke"
    private const val ADAMOVA_PROFILE_ID = "native-cpp-adamova-v3"
    private const val ADAMOVA_SOURCE_EXAMPLE_ID = "android-research-diagnostic-service"
    private const val ADAMOVA_EVIDENCE_ASSET_PATH = "app-android/app/src/main/cpp/kraken_native_placeholder.cpp"
    private const val ADAMOVA_REFERENCE_VALIDATION_STATUS = "diagnostic-only-local-native-backend"

    fun build(
        snapshot: MeshServiceSnapshot,
        generatedAtEpochMillis: Long = System.currentTimeMillis(),
        appBuildType: String = "unit-test-or-unspecified",
        appVersionName: String = "unspecified",
        gitSha: String = "unknown",
        deviceModel: String = "unspecified",
        sourceState: String = "source_state_not_embedded",
        transportReadiness: MeshEvidenceTransportExport = MeshEvidenceTransportExport(),
        messages: List<LocalMessage> = emptyList(),
        receivedPackets: List<StoredPacket> = emptyList(),
    ): MeshEvidenceSnapshotExport {
        val selectedRoute = selectedRouteFor(snapshot, generatedAtEpochMillis)
        val routeAttempts = transportReadiness.recentRouteAttempts.ifEmpty {
            snapshot.transportDiagnostics.recentRouteAttempts.map {
                MeshEvidenceRouteAttemptExport(
                    path = it.route,
                    peerId = "unknown",
                    peerFingerprint = "unknown",
                    success = it.success,
                    error = it.error,
                    attemptedAtEpochMillis = it.atEpochMillis,
                )
            }
        }
        return MeshEvidenceSnapshotExport(
            reportVersion = REPORT_VERSION,
            generatedAtEpochMillis = generatedAtEpochMillis,
            meshState = snapshot.state.name,
            transportMode = snapshot.transportMode,
            discoveredPeerCount = snapshot.discoveredPeers.size,
            queuedPackets = snapshot.queuedPackets,
            queueSize = snapshot.queuedPackets,
            lastPacketStatus = snapshot.lastPacketStatus,
            appBuildType = appBuildType,
            appVersionName = appVersionName,
            gitSha = gitSha,
            deviceModel = deviceModel,
            sourceState = sourceState,
            metrics = MeshEvidenceMetricsExport(
                queueSize = snapshot.queuedPackets,
                packetsQueued = snapshot.metrics.packetsQueued,
                packetsSent = snapshot.metrics.packetsSent,
                packetsReceived = snapshot.metrics.packetsReceived,
                receiptsReceived = snapshot.metrics.receiptsReceived,
                duplicatesDropped = snapshot.metrics.duplicatesDropped,
                expiredDropped = snapshot.metrics.expiredDropped,
                unknownPeerRejected = snapshot.metrics.unknownPeerRejected,
                wrongRecipientRejected = snapshot.metrics.wrongRecipientRejected,
                relayForwarded = snapshot.metrics.relayForwarded,
                lastDeliveryLatencyMs = snapshot.metrics.lastDeliveryLatencyMs,
            ),
            transport = transportReadiness.copy(
                selectedRoute = selectedRoute,
                enabledTransportModes = snapshot.transportDiagnostics.transportModes,
                registrationState = snapshot.transportDiagnostics.registrationState,
                discoveryState = snapshot.transportDiagnostics.discoveryState,
                transportDiscoveredPeerCount = snapshot.transportDiagnostics.discoveredPeerCount,
                observedPeerFingerprintPrefixes = snapshot.transportDiagnostics.peerFingerprints
                    .map { it.fingerprintPrefix() }
                    .distinct(),
                manualPeerCount = snapshot.transportDiagnostics.manualPeerCount,
                p2pVisibleDeviceCount = snapshot.transportDiagnostics.p2pVisibleDeviceCount,
                p2pThisDeviceStatus = snapshot.transportDiagnostics.p2pThisDeviceStatus,
                discoveryCycleCount = snapshot.transportDiagnostics.discoveryCycleCount,
                p2pServiceFoundCount = snapshot.transportDiagnostics.p2pServiceFoundCount,
                p2pTxtRecordCount = snapshot.transportDiagnostics.p2pTxtRecordCount,
                p2pTxtRejectedCount = snapshot.transportDiagnostics.p2pTxtRejectedCount,
                p2pTxtBoundPeerCount = snapshot.transportDiagnostics.p2pTxtBoundPeerCount,
                p2pUnboundVisibleDeviceCount = snapshot.transportDiagnostics.p2pUnboundVisibleDeviceCount,
                wifiDirectLastBindingError = snapshot.transportDiagnostics.wifiDirectLastBindingError,
                localPort = snapshot.transportDiagnostics.localPort,
                localAddresses = snapshot.transportDiagnostics.localAddresses,
                p2pInterfaceAddresses = snapshot.transportDiagnostics.p2pInterfaceAddresses,
                wifiDirectGroupFormed = snapshot.transportDiagnostics.wifiDirectGroupFormed,
                wifiDirectIsGroupOwner = snapshot.transportDiagnostics.wifiDirectIsGroupOwner,
                wifiDirectGroupRole = snapshot.transportDiagnostics.wifiDirectGroupRole,
                wifiDirectGroupOwnerAddress = snapshot.transportDiagnostics.wifiDirectGroupOwnerAddress,
                wifiDirectLocalP2pAddress = snapshot.transportDiagnostics.wifiDirectLocalP2pAddress,
                wifiDirectServerBindAddress = snapshot.transportDiagnostics.wifiDirectServerBindAddress,
                wifiDirectLastSendHost = snapshot.transportDiagnostics.wifiDirectLastSendHost,
                wifiDirectLastSendPort = snapshot.transportDiagnostics.wifiDirectLastSendPort,
                wifiDirectEndpointBindingState = snapshot.transportDiagnostics.wifiDirectEndpointBindingState,
                wifiDirectEndpointBindingReason = snapshot.transportDiagnostics.wifiDirectEndpointBindingReason,
                wifiDirectRelationshipPeerFingerprintPrefix = snapshot.transportDiagnostics.wifiDirectRelationshipPeerFingerprintPrefix,
                wifiDirectLastConnectDeviceAddress = snapshot.transportDiagnostics.wifiDirectLastConnectDeviceAddress,
                wifiDirectLastConnectDeviceName = snapshot.transportDiagnostics.wifiDirectLastConnectDeviceName,
                wifiDirectLastConnectGroupOwnerIntent = snapshot.transportDiagnostics.wifiDirectLastConnectGroupOwnerIntent,
                wifiDirectLastConnectResult = snapshot.transportDiagnostics.wifiDirectLastConnectResult,
                wifiDirectLastConnectFailureReason = snapshot.transportDiagnostics.wifiDirectLastConnectFailureReason,
                wifiDirectConnectAttempts = snapshot.transportDiagnostics.wifiDirectConnectAttempts,
                wifiDirectDiscoveredPeers = snapshot.transportDiagnostics.wifiDirectDiscoveredPeers,
                wifiDirectVisibleDevices = snapshot.transportDiagnostics.wifiDirectVisibleDevices,
                wifiDirectTxtRecords = snapshot.transportDiagnostics.wifiDirectTxtRecords,
                wifiDirectBoundEndpoints = snapshot.transportDiagnostics.wifiDirectBoundEndpoints,
                acceptedConnections = snapshot.transportDiagnostics.acceptedConnections,
                inboundPackets = snapshot.transportDiagnostics.inboundPackets,
                malformedFramesDropped = snapshot.transportDiagnostics.malformedFramesDropped,
                sendFailures = snapshot.transportDiagnostics.sendFailures,
                recentRouteAttempts = routeAttempts,
                realmRelayCandidateCount = snapshot.realmRelayCandidates.size,
                realmRelayCandidates = snapshot.realmRelayCandidates.map { candidate ->
                    MeshEvidenceRealmRelayCandidateExport(
                        peerId = candidate.peerId,
                        peerFingerprintPrefix = candidate.peerFingerprintPrefix,
                        displayName = candidate.displayName,
                        realmId = candidate.realmId,
                        relayPeerPublicKeyPrefix = candidate.relayPeerPublicKeyPrefix,
                    )
                },
                lastTransportError = snapshot.transportDiagnostics.lastError ?: snapshot.queue.lastError?.name,
            ),
            debugSmoke = MeshEvidenceDebugSmokeExport(
                evidenceMode = snapshot.debugSmoke.evidenceMode,
                ranAtEpochMillis = snapshot.debugSmoke.ranAtEpochMillis,
                unknownPeerInjected = snapshot.debugSmoke.unknownPeerInjected,
                wrongRecipientInjected = snapshot.debugSmoke.wrongRecipientInjected,
                duplicateInjected = snapshot.debugSmoke.duplicateInjected,
                queueRetryMessageId = snapshot.debugSmoke.queueRetryMessageId,
                queueRetryBody = snapshot.debugSmoke.queueRetryBody,
                queuedBeforeTransportRestart = snapshot.debugSmoke.queuedBeforeTransportRestart,
                queueSizeBeforeRestart = snapshot.debugSmoke.queueSizeBeforeRestart,
                sentAfterTransportRestart = snapshot.debugSmoke.sentAfterTransportRestart,
                deliveredAfterTransportRestart = snapshot.debugSmoke.deliveredAfterTransportRestart,
                queueSizeAfterRestart = snapshot.debugSmoke.queueSizeAfterRestart,
                messageStatusAfterRestart = snapshot.debugSmoke.messageStatusAfterRestart,
            ),
            targetDelivery = buildTargetDeliveryEvidence(
                messages = messages,
                receivedPackets = receivedPackets,
            ),
            recentRejectedInboundPackets = snapshot.recentRejectedInboundPackets.map { rejected ->
                MeshEvidenceRejectedInboundPacketExport(
                    packetId = rejected.packetId,
                    messageId = rejected.messageId,
                    reason = rejected.reason?.name,
                    senderFingerprint = rejected.senderFingerprint,
                    recipientFingerprint = rejected.recipientFingerprint,
                    relationshipId = rejected.relationshipId,
                    recipientMatchesLocal = rejected.recipientMatchesLocal,
                    recipientNormalizedMatchesLocal = rejected.recipientNormalizedMatchesLocal,
                    relationshipIdKnown = rejected.relationshipIdKnown,
                    senderFingerprintKnown = rejected.senderFingerprintKnown,
                    senderFingerprintNormalizedKnown = rejected.senderFingerprintNormalizedKnown,
                    relationshipAndSenderKnown = rejected.relationshipAndSenderKnown,
                    localIdentityPublicKeyMatchesRelationship = rejected.localIdentityPublicKeyMatchesRelationship,
                    rejectedAtEpochMillis = rejected.rejectedAtEpochMillis,
                )
            },
            adamovaProfileId = ADAMOVA_PROFILE_ID,
            adamovaSourceExampleId = ADAMOVA_SOURCE_EXAMPLE_ID,
            adamovaEvidenceAssetPath = ADAMOVA_EVIDENCE_ASSET_PATH,
            adamovaReferenceValidationStatus = ADAMOVA_REFERENCE_VALIDATION_STATUS,
            integrityMode = INTEGRITY_MODE,
            claimBoundary = CLAIM_BOUNDARY,
            manualTwoDeviceEvidence = MANUAL_TWO_DEVICE_PENDING,
        )
    }

    fun toJson(export: MeshEvidenceSnapshotExport): String =
        InvitePayloadCodec.json.encodeToString(export)

    fun toMarkdownSummary(export: MeshEvidenceSnapshotExport): String =
        buildString {
            appendLine("# Kraken Route-Specific Evidence Summary")
            appendLine()
            appendLine("- Report version: `${export.reportVersion}`")
            appendLine("- Generated at epoch millis: `${export.generatedAtEpochMillis}`")
            appendLine("- Selected route: `${export.transport.selectedRoute}`")
            appendLine("- Enabled transport modes: `${export.transport.enabledTransportModes.joinToString()}`")
            appendLine("- Registration state: `${export.transport.registrationState}`")
            appendLine("- Discovery state: `${export.transport.discoveryState}`")
            appendLine("- Transport discovered peers: `${export.transport.transportDiscoveredPeerCount}`")
            appendLine("- Observed peer fingerprint prefixes: `${export.transport.observedPeerFingerprintPrefixes.joinToString().ifBlank { "none" }}`")
            appendLine("- Manual peers: `${export.transport.manualPeerCount}`")
            appendLine("- P2P visible devices: `${export.transport.p2pVisibleDeviceCount}`")
            appendLine("- P2P this-device status: `${export.transport.p2pThisDeviceStatus ?: "n/a"}`")
            appendLine("- Discovery cycles: `${export.transport.discoveryCycleCount}`")
            appendLine("- P2P service callbacks: `${export.transport.p2pServiceFoundCount}`")
            appendLine("- P2P TXT records: `${export.transport.p2pTxtRecordCount}`")
            appendLine("- P2P TXT rejected: `${export.transport.p2pTxtRejectedCount}`")
            appendLine("- P2P TXT bound peers: `${export.transport.p2pTxtBoundPeerCount}`")
            appendLine("- P2P unbound visible devices: `${export.transport.p2pUnboundVisibleDeviceCount}`")
            appendLine("- Wi-Fi Direct last binding error: `${export.transport.wifiDirectLastBindingError ?: "n/a"}`")
            appendLine("- Local transport endpoint: `${export.transport.localAddresses.joinToString()}:${export.transport.localPort ?: "n/a"}`")
            appendLine("- P2P interface addresses: `${export.transport.p2pInterfaceAddresses.joinToString().ifBlank { "none" }}`")
            appendLine("- Wi-Fi Direct group formed: `${export.transport.wifiDirectGroupFormed ?: "n/a"}`")
            appendLine("- Wi-Fi Direct group owner: `${export.transport.wifiDirectIsGroupOwner ?: "n/a"}`")
            appendLine("- Wi-Fi Direct group role: `${export.transport.wifiDirectGroupRole}`")
            appendLine("- Wi-Fi Direct group owner address: `${export.transport.wifiDirectGroupOwnerAddress ?: "n/a"}`")
            appendLine("- Wi-Fi Direct local P2P address: `${export.transport.wifiDirectLocalP2pAddress ?: "n/a"}`")
            appendLine("- Wi-Fi Direct server bind address: `${export.transport.wifiDirectServerBindAddress ?: "n/a"}`")
            appendLine("- Wi-Fi Direct last send endpoint: `${export.transport.wifiDirectLastSendHost ?: "n/a"}:${export.transport.wifiDirectLastSendPort ?: "n/a"}`")
            appendLine("- Wi-Fi Direct endpoint binding state: `${export.transport.wifiDirectEndpointBindingState}`")
            appendLine("- Wi-Fi Direct endpoint binding reason: `${export.transport.wifiDirectEndpointBindingReason ?: "n/a"}`")
            appendLine("- Wi-Fi Direct relationship peer prefix: `${export.transport.wifiDirectRelationshipPeerFingerprintPrefix ?: "n/a"}`")
            appendLine("- Wi-Fi Direct last connect target: `${export.transport.wifiDirectLastConnectDeviceName ?: "n/a"} / ${export.transport.wifiDirectLastConnectDeviceAddress ?: "n/a"}`")
            appendLine("- Wi-Fi Direct last connect group owner intent: `${export.transport.wifiDirectLastConnectGroupOwnerIntent ?: "n/a"}`")
            appendLine("- Wi-Fi Direct last connect result: `${export.transport.wifiDirectLastConnectResult ?: "n/a"}`")
            appendLine("- Wi-Fi Direct last connect failure reason: `${export.transport.wifiDirectLastConnectFailureReason ?: "n/a"}`")
            appendLine("- Wi-Fi Direct connect attempts: `${export.transport.wifiDirectConnectAttempts.size}`")
            appendLine("- Wi-Fi Direct discovered peer endpoints: `${export.transport.wifiDirectDiscoveredPeers.size}`")
            appendLine("- Wi-Fi Direct visible devices: `${export.transport.wifiDirectVisibleDevices.size}`")
            appendLine("- Wi-Fi Direct TXT record diagnostics: `${export.transport.wifiDirectTxtRecords.size}`")
            appendLine("- Wi-Fi Direct bound endpoints: `${export.transport.wifiDirectBoundEndpoints.size}`")
            appendLine("- Realm relay candidates: `${export.transport.realmRelayCandidateCount}`")
            appendLine("- Realm relay candidate prefixes: `${export.transport.realmRelayCandidates.map { it.peerFingerprintPrefix }.joinToString().ifBlank { "none" }}`")
            export.transport.wifiDirectPermissions?.let { permissions ->
                appendLine("- Wi-Fi Direct nearby permission required/granted: `${permissions.nearbyWifiDevicesRequired}/${permissions.nearbyWifiDevicesGranted}`")
                appendLine("- Wi-Fi Direct fine location declared/granted/app-op: `${permissions.fineLocationDeclared}/${permissions.fineLocationGranted}/${permissions.fineLocationAppOp ?: "n/a"}`")
                appendLine("- Wi-Fi Direct permission warning: `${permissions.warning ?: "none"}`")
            }
            appendLine("- Recent route attempts: `${export.transport.recentRouteAttempts.size}`")
            appendLine("- Queue size: `${export.queueSize}`")
            appendLine("- Last packet status: `${export.lastPacketStatus}`")
            appendLine("- App version: `${export.appVersionName}`")
            appendLine("- Build type: `${export.appBuildType}`")
            appendLine("- Git SHA: `${export.gitSha}`")
            appendLine("- Device model: `${export.deviceModel}`")
            appendLine()
            appendLine("## Counters")
            appendLine()
            appendLine("- packetsSent: `${export.metrics.packetsSent}`")
            appendLine("- packetsReceived: `${export.metrics.packetsReceived}`")
            appendLine("- receiptsReceived: `${export.metrics.receiptsReceived}`")
            appendLine("- duplicatesDropped: `${export.metrics.duplicatesDropped}`")
            appendLine("- expiredDropped: `${export.metrics.expiredDropped}`")
            appendLine("- unknownPeerRejected: `${export.metrics.unknownPeerRejected}`")
            appendLine("- wrongRecipientRejected: `${export.metrics.wrongRecipientRejected}`")
            appendLine("- lastDeliveryLatencyMs: `${export.metrics.lastDeliveryLatencyMs ?: "n/a"}`")
            appendLine("- acceptedConnections: `${export.transport.acceptedConnections}`")
            appendLine("- inboundPackets: `${export.transport.inboundPackets}`")
            appendLine("- malformedFramesDropped: `${export.transport.malformedFramesDropped}`")
            appendLine("- sendFailures: `${export.transport.sendFailures}`")
            appendLine()
            appendLine("## Target Delivery")
            appendLine()
            appendLine("- recentMessages: `${export.targetDelivery.recentMessages.size}`")
            appendLine("- receivedPackets: `${export.targetDelivery.receivedPackets.size}`")
            appendLine("- recentMessageIds: `${export.targetDelivery.recentMessages.map { it.messageId }.joinToString().ifBlank { "none" }}`")
            appendLine("- receivedPacketIds: `${export.targetDelivery.receivedPackets.map { it.packetId }.joinToString().ifBlank { "none" }}`")
            appendLine()
            appendLine("## Debug Smoke")
            appendLine()
            appendLine("- evidenceMode: `${export.debugSmoke.evidenceMode}`")
            appendLine("- unknownPeerInjected: `${export.debugSmoke.unknownPeerInjected}`")
            appendLine("- wrongRecipientInjected: `${export.debugSmoke.wrongRecipientInjected}`")
            appendLine("- duplicateInjected: `${export.debugSmoke.duplicateInjected}`")
            appendLine("- queuedBeforeTransportRestart: `${export.debugSmoke.queuedBeforeTransportRestart}`")
            appendLine("- queueSizeBeforeRestart: `${export.debugSmoke.queueSizeBeforeRestart}`")
            appendLine("- sentAfterTransportRestart: `${export.debugSmoke.sentAfterTransportRestart}`")
            appendLine("- deliveredAfterTransportRestart: `${export.debugSmoke.deliveredAfterTransportRestart}`")
            appendLine("- queueSizeAfterRestart: `${export.debugSmoke.queueSizeAfterRestart}`")
            appendLine("- messageStatusAfterRestart: `${export.debugSmoke.messageStatusAfterRestart ?: "n/a"}`")
            appendLine()
            appendLine("## Claim Boundary")
            appendLine()
            appendLine("- Integrity mode: `${export.integrityMode}`")
            appendLine()
            appendLine("`${export.claimBoundary}`")
            appendLine()
            appendLine("Prototype evidence only. Message payload encryption is implemented for the protected path; production key storage, signatures, replay hardening and external review are still separate work.")
        }

    private fun buildTargetDeliveryEvidence(
        messages: List<LocalMessage>,
        receivedPackets: List<StoredPacket>,
    ): MeshEvidenceTargetDeliveryExport =
        MeshEvidenceTargetDeliveryExport(
            recentMessages = messages
                .sortedBy { it.updatedAtEpochMillis }
                .takeLast(TARGET_DELIVERY_LIMIT)
                .map { message ->
                    MeshEvidenceTargetMessageExport(
                        messageId = message.messageId,
                        relationshipId = message.relationshipId,
                        peerFingerprintPrefix = message.peerFingerprint.fingerprintPrefix(),
                        direction = message.direction.name,
                        status = message.status.name,
                        createdAtEpochMillis = message.createdAtEpochMillis,
                        updatedAtEpochMillis = message.updatedAtEpochMillis,
                    )
                },
            receivedPackets = receivedPackets
                .sortedBy { it.storedAtEpochMillis }
                .takeLast(TARGET_DELIVERY_LIMIT)
                .map { storedPacket ->
                    MeshEvidenceTargetPacketExport(
                        packetId = storedPacket.packet.packetId,
                        messageId = storedPacket.packet.messageId,
                        packetType = storedPacket.packet.packetType.name,
                        senderFingerprintPrefix = storedPacket.packet.senderFingerprint.fingerprintPrefix(),
                        recipientFingerprintPrefix = storedPacket.packet.recipientFingerprint.fingerprintPrefix(),
                        status = storedPacket.status.name,
                        storedAtEpochMillis = storedPacket.storedAtEpochMillis,
                    )
                },
        )

    private fun selectedRouteFor(
        snapshot: MeshServiceSnapshot,
        nowEpochMillis: Long,
    ): String {
        snapshot.transportDiagnostics.recentRouteAttempts
            .asReversed()
            .firstOrNull { it.success && it.route.isEvidenceRoute() }
            ?.let { return it.route }

        val freshEvidence = (snapshot.peerRouteEvidence.ifEmpty { snapshot.transportDiagnostics.peerRouteEvidence })
            .filter { it.transportId.isEvidenceRoute() }
            .filter { it.observedAtEpochMillis <= nowEpochMillis + CLOCK_SKEW_TOLERANCE_MS }
            .filter { nowEpochMillis - it.observedAtEpochMillis <= ROUTE_EVIDENCE_TTL_MS }

        freshEvidence
            .maxWithOrNull(
                compareBy<DiscoveredPeerRouteEvidence> { routePriority(it.transportId) }
                    .thenBy { it.observedAtEpochMillis },
            )
            ?.let { return it.transportId }

        return if (snapshot.transportMode.isEvidenceRoute()) snapshot.transportMode else "none"
    }

    private fun String.isEvidenceRoute(): Boolean =
        this == KrakenTransportCatalog.BLE_GATT.id ||
            this == KrakenTransportCatalog.LAN_NSD_TCP.id ||
            this == KrakenTransportCatalog.WIFI_DIRECT.id ||
            this == ROUTED_MESH_TRANSPORT_ID

    private fun routePriority(route: String): Int =
        when (route) {
            KrakenTransportCatalog.LAN_NSD_TCP.id -> 3
            KrakenTransportCatalog.WIFI_DIRECT.id -> 3
            KrakenTransportCatalog.BLE_GATT.id -> 2
            ROUTED_MESH_TRANSPORT_ID -> 1
            else -> 0
        }

    private const val ROUTED_MESH_TRANSPORT_ID = "routed-mesh"
    private const val ROUTE_EVIDENCE_TTL_MS = 30_000L
    private const val CLOCK_SKEW_TOLERANCE_MS = 1_000L
    private const val TARGET_DELIVERY_LIMIT = 50
}

internal fun String.fingerprintPrefix(): String =
    trim().take(12)
