package com.disser.kraken.mesh

import android.content.Context
import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.CryptoProfileAdmissionStore
import com.disser.kraken.crypto.NoOpCryptoProfileAdmissionStore
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.IssuedInviteRecord
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.MessageStatus
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmRelayPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService

enum class MeshState {
    OFF,
    STARTING,
    SCANNING,
    PEER_FOUND,
    CONNECTED,
    DEGRADED,
    ERROR,
}

data class MeshMetricsSnapshot(
    val packetsQueued: Int = 0,
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val receiptsReceived: Int = 0,
    val duplicatesDropped: Int = 0,
    val expiredDropped: Int = 0,
    val unknownPeerRejected: Int = 0,
    val wrongRecipientRejected: Int = 0,
    val relayForwarded: Int = 0,
    val lastDeliveryLatencyMs: Long? = null,
)

data class MeshQueueSnapshot(
    val pendingForDelivery: Int = 0,
    val readyMessages: Int = 0,
    val outboxQueued: Int = 0,
    val sentAwaitingAck: Int = 0,
    val queuedReceipts: Int = 0,
    val rejectedPackets: Int = 0,
    val expiredPackets: Int = 0,
    val storedOutboxPackets: Int = 0,
    val nextAttemptAtEpochMillis: Long? = null,
    val lastError: MeshRejectionReason? = null,
)

data class MeshDebugSmokeSnapshot(
    val evidenceMode: String = "not-run",
    val ranAtEpochMillis: Long? = null,
    val unknownPeerInjected: Boolean = false,
    val wrongRecipientInjected: Boolean = false,
    val duplicateInjected: Boolean = false,
    val queueRetryMessageId: String? = null,
    val queueRetryBody: String? = null,
    val queuedBeforeTransportRestart: Boolean = false,
    val queueSizeBeforeRestart: Int = 0,
    val sentAfterTransportRestart: Boolean = false,
    val deliveredAfterTransportRestart: Boolean = false,
    val queueSizeAfterRestart: Int = 0,
    val messageStatusAfterRestart: String? = null,
)

data class MeshRejectedInboundPacketSnapshot(
    val packetId: String,
    val messageId: String?,
    val reason: MeshRejectionReason?,
    val senderFingerprint: String,
    val recipientFingerprint: String,
    val relationshipId: String,
    val recipientMatchesLocal: Boolean,
    val recipientNormalizedMatchesLocal: Boolean,
    val relationshipIdKnown: Boolean,
    val senderFingerprintKnown: Boolean,
    val senderFingerprintNormalizedKnown: Boolean,
    val relationshipAndSenderKnown: Boolean,
    val localIdentityPublicKeyMatchesRelationship: Boolean,
    val rejectedAtEpochMillis: Long,
)

data class MeshRealmRelayCandidateSnapshot(
    val peerId: String,
    val peerFingerprint: String,
    val peerFingerprintPrefix: String,
    val displayName: String?,
    val realmId: String,
    val relayPeerPublicKeyPrefix: String,
)

data class MeshServiceSnapshot(
    val state: MeshState = MeshState.OFF,
    val transportMode: String = "loopback-local",
    val discoveredPeers: List<DiscoveredPeer> = emptyList(),
    val peerRouteEvidence: List<DiscoveredPeerRouteEvidence> = emptyList(),
    val transportDiagnostics: MeshTransportDiagnostics = MeshTransportDiagnostics(),
    val queuedPackets: Int = 0,
    val queue: MeshQueueSnapshot = MeshQueueSnapshot(),
    val lastPacketStatus: String = "idle",
    val lastSyncSummary: String = "sync-not-run",
    val lastSyncAtEpochMillis: Long? = null,
    val metrics: MeshMetricsSnapshot = MeshMetricsSnapshot(),
    val debugSmoke: MeshDebugSmokeSnapshot = MeshDebugSmokeSnapshot(),
    val recentRejectedInboundPackets: List<MeshRejectedInboundPacketSnapshot> = emptyList(),
    val realmRelayCandidates: List<MeshRealmRelayCandidateSnapshot> = emptyList(),
    val foregroundServiceEnabled: Boolean = false,
    val lastServiceStartedAtEpochMillis: Long? = null,
    val compatibilityNotice: String = "Локальная связь использует защищённый путь доставки данных; хранение ключей и защита от повторной отправки вынесены в отдельный слой.",
)

data class MeshSyncResult(
    val messages: List<LocalMessage>,
    val snapshot: MeshServiceSnapshot,
    val sentCount: Int,
    val receivedCount: Int,
    val receiptsApplied: Int,
    val rejectedCount: Int,
    val updatedRelationships: List<Relationship> = emptyList(),
)

data class DebugDirectSendResult(
    val message: LocalMessage?,
    val packetId: String?,
    val success: Boolean,
    val error: MeshRejectionReason? = null,
    val transportError: String? = null,
)

class MeshMetricsRecorder {
    private var snapshot = MeshMetricsSnapshot()

    fun snapshot(queuedPackets: Int = snapshot.packetsQueued): MeshMetricsSnapshot =
        snapshot.copy(packetsQueued = queuedPackets)

    fun recordSent() {
        snapshot = snapshot.copy(packetsSent = snapshot.packetsSent + 1)
    }

    fun recordReceived() {
        snapshot = snapshot.copy(packetsReceived = snapshot.packetsReceived + 1)
    }

    fun recordReceipt(latencyMs: Long? = null) {
        snapshot = snapshot.copy(
            receiptsReceived = snapshot.receiptsReceived + 1,
            lastDeliveryLatencyMs = latencyMs ?: snapshot.lastDeliveryLatencyMs,
        )
    }

    fun recordDrop(reason: MeshRejectionReason?) {
        snapshot = when (reason) {
            MeshRejectionReason.DUPLICATE -> snapshot.copy(duplicatesDropped = snapshot.duplicatesDropped + 1)
            MeshRejectionReason.EXPIRED -> snapshot.copy(expiredDropped = snapshot.expiredDropped + 1)
            MeshRejectionReason.UNKNOWN_PEER -> snapshot.copy(unknownPeerRejected = snapshot.unknownPeerRejected + 1)
            MeshRejectionReason.WRONG_RECIPIENT -> snapshot.copy(
                wrongRecipientRejected = snapshot.wrongRecipientRejected + 1,
            )
            else -> snapshot
        }
    }

    fun recordRelayForwarded() {
        snapshot = snapshot.copy(relayForwarded = snapshot.relayForwarded + 1)
    }
}

class TransportManager {
    private var transport: PeerTransport? = null

    fun startLoopback(localIdentity: LocalIdentity): PeerTransport {
        stop()
        val localPeer = DiscoveredPeer(
            peerId = "local-${localIdentity.identityId}",
            fingerprint = localIdentity.fingerprint,
            displayName = localIdentity.displayName,
        )
        val loopback = LoopbackTransport(localPeer)
        loopback.start()
        transport = loopback
        return loopback
    }

    fun startLan(
        context: Context,
        localIdentity: LocalIdentity,
        wifiDirectEnabled: Boolean = true,
        transportSelection: MeshTransportSelection = MeshTransportSelection.fromWifiDirectEnabled(wifiDirectEnabled),
    ): PeerTransport {
        stop()
        val localPeer = DiscoveredPeer(
            peerId = "local-${localIdentity.identityId}",
            fingerprint = localIdentity.fingerprint,
            displayName = localIdentity.displayName,
        )
        val multiRoute = CompositePeerTransport(
            buildList {
                if (transportSelection.wifiDirect) {
                    add(WifiDirectTransport(context, localPeer))
                }
                if (transportSelection.bleGatt) {
                    add(BleGattTransport(context, localPeer))
                }
                if (transportSelection.lanNsdTcp) {
                    add(DirectLanTransport(context, localPeer))
                }
            },
        )
        multiRoute.start()
        transport = multiRoute
        return multiRoute
    }

    fun stop() {
        transport?.stop()
        transport = null
    }

    fun currentTransport(): PeerTransport? = transport

    fun ensureDebugWifiDirectGroupOwner(): String {
        val wifiDirect = transport as? WifiDirectDebugControl
            ?: return "wifi-direct-control-unavailable"
        return wifiDirect.ensureDebugGroupOwner()
    }

    fun setForTest(peerTransport: PeerTransport) {
        transport = peerTransport
    }
}

class MeshService(
    private val transportManager: TransportManager = TransportManager(),
    private val metricsRecorder: MeshMetricsRecorder = MeshMetricsRecorder(),
    private val repository: MeshRepository? = null,
    private val retryPolicy: TransportRetryPolicy = TransportRetryPolicy(),
    private val messageSessionKeyProvider: MessageSessionKeyProvider = QrHandshakeMessageSessionKeyProvider(),
    private val messagePayloadProtectionPolicy: MessagePayloadProtectionPolicy =
        MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED,
) {
    private var state: MeshState = MeshState.OFF
    private var lastPacketStatus: String = "idle"
    private var lastSyncSummary: String = "sync-not-run"
    private var lastSyncAtEpochMillis: Long? = null
    private var debugSmoke: MeshDebugSmokeSnapshot = MeshDebugSmokeSnapshot()
    private var recentRejectedInboundPackets: List<MeshRejectedInboundPacketSnapshot> = emptyList()

    fun start(localIdentity: LocalIdentity?): MeshServiceSnapshot {
        if (localIdentity == null) {
            state = MeshState.ERROR
            lastPacketStatus = "identity-required"
            return snapshot(emptyList())
        }
        state = MeshState.STARTING
        val transport = transportManager.startLoopback(localIdentity)
        val peers = transport.observePeers()
        state = if (peers.isEmpty()) MeshState.SCANNING else MeshState.PEER_FOUND
        lastPacketStatus = "loopback-ready"
        return snapshot(emptyList())
    }

    fun startLan(
        context: Context,
        localIdentity: LocalIdentity?,
        wifiDirectEnabled: Boolean = true,
        transportSelection: MeshTransportSelection = MeshTransportSelection.fromWifiDirectEnabled(wifiDirectEnabled),
    ): MeshServiceSnapshot {
        if (localIdentity == null) {
            state = MeshState.ERROR
            lastPacketStatus = "identity-required"
            return snapshot(emptyList())
        }
        state = MeshState.STARTING
        val transport = transportManager.startLan(context, localIdentity, wifiDirectEnabled, transportSelection)
        val peers = transport.observePeers()
        state = if (peers.isEmpty()) MeshState.SCANNING else MeshState.PEER_FOUND
        lastPacketStatus = "lan-ready"
        return snapshot(emptyList())
    }

    fun stop(messages: List<LocalMessage>): MeshServiceSnapshot {
        transportManager.stop()
        state = MeshState.OFF
        lastPacketStatus = "stopped"
        return snapshot(messages)
    }

    fun snapshot(
        messages: List<LocalMessage>,
        localIdentity: LocalIdentity? = null,
        realmSnapshot: RealmSnapshot? = null,
    ): MeshServiceSnapshot {
        val queue = queueSnapshot(messages)
        val transport = transportManager.currentTransport()
        val peers = transport?.observePeers().orEmpty()
        val diagnostics = transport?.diagnostics() ?: MeshTransportDiagnostics()
        state = when {
            state == MeshState.OFF || state == MeshState.ERROR -> state
            peers.isNotEmpty() -> MeshState.PEER_FOUND
            else -> MeshState.SCANNING
        }
        return MeshServiceSnapshot(
            state = state,
            transportMode = transport?.modeId ?: "loopback-local",
            discoveredPeers = peers,
            peerRouteEvidence = diagnostics.peerRouteEvidence,
            transportDiagnostics = diagnostics,
            queuedPackets = queue.pendingForDelivery,
            queue = queue,
            lastPacketStatus = lastPacketStatus,
            lastSyncSummary = lastSyncSummary,
            lastSyncAtEpochMillis = lastSyncAtEpochMillis,
            metrics = metricsRecorder.snapshot(queue.pendingForDelivery),
            debugSmoke = debugSmoke,
            recentRejectedInboundPackets = recentRejectedInboundPackets,
            realmRelayCandidates = realmRelayCandidates(
                localIdentity = localIdentity,
                realmSnapshot = realmSnapshot,
                peers = peers,
            ),
        )
    }

    private fun realmRelayCandidates(
        localIdentity: LocalIdentity?,
        realmSnapshot: RealmSnapshot?,
        peers: List<DiscoveredPeer>,
    ): List<MeshRealmRelayCandidateSnapshot> {
        val identity = localIdentity ?: return emptyList()
        val snapshot = realmSnapshot ?: return emptyList()
        val localFingerprint = compactFingerprint(identity.fingerprint)
        return peers
            .asSequence()
            .filter { compactFingerprint(it.fingerprint) != localFingerprint }
            .flatMap { peer ->
                snapshot.membershipCertificates.asSequence()
                    .filter { certificate ->
                        compactFingerprint(FingerprintFormatter.shortFingerprint(certificate.memberPublicKey)) ==
                            compactFingerprint(peer.fingerprint)
                    }
                    .mapNotNull { certificate ->
                        val decision = RealmRelayPolicy.canUseRelayPeer(
                            localIdentity = identity,
                            realmId = certificate.realmId,
                            relayPeerPublicKey = certificate.memberPublicKey,
                            realmSnapshot = snapshot,
                        )
                        if (!decision.allowed) return@mapNotNull null
                        MeshRealmRelayCandidateSnapshot(
                            peerId = peer.peerId,
                            peerFingerprint = peer.fingerprint,
                            peerFingerprintPrefix = peer.fingerprint.fingerprintPrefix(),
                            displayName = peer.displayName,
                            realmId = certificate.realmId,
                            relayPeerPublicKeyPrefix = certificate.memberPublicKey.take(16),
                        )
                    }
            }
            .distinctBy { "${it.realmId}:${compactFingerprint(it.peerFingerprint)}" }
            .toList()
    }

    private fun queueSnapshot(messages: List<LocalMessage>): MeshQueueSnapshot {
        val readyMessages = messages.count { it.status == MessageStatus.READY_FOR_TRANSPORT }
        val outbox = repository?.outboxStore?.load().orEmpty()
        if (outbox.isEmpty()) {
            return MeshQueueSnapshot(
                pendingForDelivery = readyMessages,
                readyMessages = readyMessages,
            )
        }
        val outboxQueued = outbox.count {
            it.packet.packetType == KrakenPacketType.MESSAGE && it.status == PacketStoreStatus.QUEUED
        }
        val sentAwaitingAck = outbox.count {
            it.packet.packetType == KrakenPacketType.MESSAGE && it.status == PacketStoreStatus.SENT
        }
        val queuedReceipts = outbox.count {
            it.packet.packetType == KrakenPacketType.RECEIPT && it.status == PacketStoreStatus.QUEUED
        }
        val queuedHandshakePackets = outbox.count {
            it.packet.packetType in setOf(KrakenPacketType.HANDSHAKE_RESPONSE, KrakenPacketType.HANDSHAKE_CONFIRMATION) &&
                it.status in setOf(PacketStoreStatus.QUEUED, PacketStoreStatus.SENT)
        }
        val nonFinalOutbox = outboxQueued + sentAwaitingAck + queuedReceipts + queuedHandshakePackets
        val retryCandidates = outbox.filter {
            it.status in setOf(PacketStoreStatus.QUEUED, PacketStoreStatus.SENT) &&
                it.nextAttemptAtEpochMillis > 0
        }
        val lastError = outbox
            .filter { it.lastError != null }
            .maxByOrNull { it.lastAttemptAtEpochMillis ?: it.storedAtEpochMillis }
            ?.lastError
        return MeshQueueSnapshot(
            pendingForDelivery = maxOf(readyMessages, nonFinalOutbox),
            readyMessages = readyMessages,
            outboxQueued = outboxQueued,
            sentAwaitingAck = sentAwaitingAck,
            queuedReceipts = queuedReceipts,
            rejectedPackets = outbox.count { it.status == PacketStoreStatus.REJECTED },
            expiredPackets = outbox.count { it.status == PacketStoreStatus.EXPIRED },
            storedOutboxPackets = outbox.size,
            nextAttemptAtEpochMillis = retryCandidates.minOfOrNull { it.nextAttemptAtEpochMillis },
            lastError = lastError,
        )
    }

    fun recordSend(messages: List<LocalMessage>): MeshServiceSnapshot {
        metricsRecorder.recordSent()
        lastPacketStatus = "sent-to-transport"
        return snapshot(messages)
    }

    fun recordReceive(messages: List<LocalMessage>): MeshServiceSnapshot {
        metricsRecorder.recordReceived()
        lastPacketStatus = "packet-received"
        return snapshot(messages)
    }

    fun recordReceipt(messages: List<LocalMessage>, latencyMs: Long? = null): MeshServiceSnapshot {
        metricsRecorder.recordReceipt(latencyMs)
        lastPacketStatus = "receipt-received"
        return snapshot(messages)
    }

    fun recordDrop(messages: List<LocalMessage>, reason: MeshRejectionReason?): MeshServiceSnapshot {
        metricsRecorder.recordDrop(reason)
        lastPacketStatus = "rejected-${reason ?: MeshRejectionReason.MALFORMED}"
        return snapshot(messages)
    }

    fun recordRelayForwarded(messages: List<LocalMessage>): MeshServiceSnapshot {
        metricsRecorder.recordRelayForwarded()
        lastPacketStatus = "relay-forwarded"
        return snapshot(messages)
    }

    fun recordDebugNegativeEvidence(
        messages: List<LocalMessage>,
        localIdentity: LocalIdentity?,
        relationships: List<Relationship>,
        realmSnapshot: RealmSnapshot? = null,
    ): MeshServiceSnapshot {
        val relationship = localIdentity?.let { identity ->
            relationships.firstOrNull {
                it.localIdentityPublicKey == identity.publicKeyEncoded &&
                    RelationshipService.canSendMessage(it)
            }
        }
        if (localIdentity == null || relationship == null) {
            debugSmoke = debugSmoke.copy(
                evidenceMode = DEBUG_EVIDENCE_UNAVAILABLE_MODE,
                ranAtEpochMillis = System.currentTimeMillis(),
                unknownPeerInjected = false,
                wrongRecipientInjected = false,
                duplicateInjected = false,
            )
            lastPacketStatus = "debug-local-inbox-injection-unavailable"
            return snapshot(messages)
        }

        val nowEpochMillis = System.currentTimeMillis()
        val inbox = MeshInboxProcessor(
            localIdentity = localIdentity,
            relationships = relationships,
            realmSnapshot = realmSnapshot,
            now = { nowEpochMillis },
        )
        val unknownPeerRejected = inbox.process(
            debugMessageProbePacket(
                localIdentity = localIdentity,
                relationship = relationship,
                packetId = "debug-unknown-peer-$nowEpochMillis",
                senderFingerprint = "DEBUG-UNKNOWN-FP",
                recipientFingerprint = localIdentity.fingerprint,
                nowEpochMillis = nowEpochMillis,
            ),
        ).rejectedReason == MeshRejectionReason.UNKNOWN_PEER
        val wrongRecipientRejected = inbox.process(
            debugMessageProbePacket(
                localIdentity = localIdentity,
                relationship = relationship,
                packetId = "debug-wrong-recipient-$nowEpochMillis",
                senderFingerprint = relationship.peerFingerprint,
                recipientFingerprint = "DEBUG-WRONG-RECIPIENT-FP",
                nowEpochMillis = nowEpochMillis,
            ),
        ).rejectedReason == MeshRejectionReason.WRONG_RECIPIENT
        val duplicateRejected = inbox.process(
            debugMessageProbePacket(
                localIdentity = localIdentity,
                relationship = relationship,
                packetId = "debug-duplicate-$nowEpochMillis",
                senderFingerprint = relationship.peerFingerprint,
                recipientFingerprint = localIdentity.fingerprint,
                nowEpochMillis = nowEpochMillis,
            ),
            alreadySeen = true,
        ).rejectedReason == MeshRejectionReason.DUPLICATE

        if (unknownPeerRejected) metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
        if (wrongRecipientRejected) metricsRecorder.recordDrop(MeshRejectionReason.WRONG_RECIPIENT)
        if (duplicateRejected) metricsRecorder.recordDrop(MeshRejectionReason.DUPLICATE)
        debugSmoke = debugSmoke.copy(
            evidenceMode = DEBUG_EVIDENCE_MODE,
            ranAtEpochMillis = nowEpochMillis,
            unknownPeerInjected = unknownPeerRejected,
            wrongRecipientInjected = wrongRecipientRejected,
            duplicateInjected = duplicateRejected,
        )
        lastPacketStatus = if (unknownPeerRejected && wrongRecipientRejected && duplicateRejected) {
            "debug-local-inbox-injection-recorded"
        } else {
            "debug-local-inbox-injection-partial"
        }
        return snapshot(messages)
    }

    fun recordDebugQueueRetryEvidence(
        messages: List<LocalMessage>,
        queueRetryMessageId: String,
        queueRetryBody: String,
        queuedBeforeTransportRestart: Boolean,
        queueSizeBeforeRestart: Int,
        sentAfterTransportRestart: Boolean,
        deliveredAfterTransportRestart: Boolean,
        queueSizeAfterRestart: Int,
        messageStatusAfterRestart: String?,
    ): MeshServiceSnapshot {
        debugSmoke = debugSmoke.copy(
            evidenceMode = DEBUG_EVIDENCE_MODE,
            ranAtEpochMillis = System.currentTimeMillis(),
            queueRetryMessageId = queueRetryMessageId,
            queueRetryBody = queueRetryBody,
            queuedBeforeTransportRestart = queuedBeforeTransportRestart,
            queueSizeBeforeRestart = queueSizeBeforeRestart,
            sentAfterTransportRestart = sentAfterTransportRestart,
            deliveredAfterTransportRestart = deliveredAfterTransportRestart,
            queueSizeAfterRestart = queueSizeAfterRestart,
            messageStatusAfterRestart = messageStatusAfterRestart,
        )
        lastPacketStatus = "debug-queue-retry-evidence-recorded"
        return snapshot(messages)
    }

    fun addManualLanPeer(
        messages: List<LocalMessage>,
        fingerprint: String,
        host: String,
        port: Int,
        displayName: String? = null,
    ): MeshServiceSnapshot {
        val transport = transportManager.currentTransport()
        if (transport !is ManualPeerTransport) {
            lastPacketStatus = "manual-peer-unavailable"
            return snapshot(messages)
        }
        val result = transport.addManualPeer(
            fingerprint = fingerprint,
            host = host,
            port = port,
            displayName = displayName,
        )
        lastPacketStatus = if (result.success) {
            "manual-peer-added"
        } else {
            "manual-peer-failed:${result.error ?: "unknown"}"
        }
        return snapshot(messages)
    }

    fun ensureDebugWifiDirectGroupOwner(): String =
        transportManager.ensureDebugWifiDirectGroupOwner()

    fun addDebugWifiDirectPeer(
        messages: List<LocalMessage>,
        localIdentity: LocalIdentity?,
        relationships: List<Relationship>,
        realmSnapshot: RealmSnapshot,
        deviceAddress: String,
        deviceName: String?,
        port: Int,
    ): MeshServiceSnapshot {
        val identity = localIdentity ?: return snapshot(messages)
        val relationship = relationships.firstOrNull {
            RealmCommunicationPolicy.canUseRelationship(identity, it, realmSnapshot).allowed
        } ?: return snapshot(messages)
        val transport = transportManager.currentTransport()
        if (transport !is WifiDirectDebugControl) {
            lastPacketStatus = "debug-wifi-direct-peer-unavailable"
            return snapshot(messages)
        }
        val result = transport.addDebugPeer(
            fingerprint = relationship.peerFingerprint,
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            port = port,
        )
        lastPacketStatus = "debug-wifi-direct-peer:$result"
        return snapshot(messages)
    }

    fun sendDebugDirectMessage(
        messages: List<LocalMessage>,
        localIdentity: LocalIdentity?,
        relationship: Relationship?,
        message: LocalMessage?,
        realmSnapshot: RealmSnapshot,
    ): DebugDirectSendResult {
        val identity = localIdentity ?: return DebugDirectSendResult(null, null, false, MeshRejectionReason.UNKNOWN_PEER)
        val activeRelationship = relationship ?: return DebugDirectSendResult(null, null, false, MeshRejectionReason.UNKNOWN_PEER)
        val outgoing = message ?: return DebugDirectSendResult(null, null, false, MeshRejectionReason.MALFORMED)
        val transport = transportManager.currentTransport()
            ?: return DebugDirectSendResult(outgoing, null, false, MeshRejectionReason.UNKNOWN_PEER)
        val peer = transport.observePeers().firstOrNull { it.fingerprint == activeRelationship.peerFingerprint }
            ?: DiscoveredPeer(
                peerId = "relationship-${activeRelationship.relationshipId}",
                fingerprint = activeRelationship.peerFingerprint,
                displayName = activeRelationship.peerDisplayName,
            )
        val admissionGate = ProductCryptoAdmissionGate(
            admissionStore = repository?.admissionStore ?: NoOpCryptoProfileAdmissionStore,
        )
        val result = outboxProcessor(
            identity = identity,
            transport = transport,
            realmSnapshot = realmSnapshot,
            admissionGate = admissionGate,
        ).sendMessage(
            message = outgoing,
            relationship = activeRelationship,
            peer = peer,
        )
        if (result.updatedMessage != null) {
            lastPacketStatus = if (result.rejectedReason == null) {
                metricsRecorder.recordSent()
                "debug-direct-send-sent"
            } else {
                metricsRecorder.recordDrop(result.rejectedReason)
                "debug-direct-send-failed:${result.rejectedReason}"
            }
        }
        return DebugDirectSendResult(
            message = result.updatedMessage ?: outgoing,
            packetId = result.packet?.packetId,
            success = result.rejectedReason == null,
            error = result.rejectedReason,
            transportError = result.transportError,
        )
    }

    fun syncNow(
        localIdentity: LocalIdentity?,
        relationships: List<Relationship>,
        messages: List<LocalMessage>,
        issuedInvites: List<IssuedInviteRecord> = emptyList(),
        realmSnapshot: RealmSnapshot? = null,
    ): MeshSyncResult {
        val identity = localIdentity
        val transport = transportManager.currentTransport()
        val admissionGate = ProductCryptoAdmissionGate(
            admissionStore = repository?.admissionStore ?: NoOpCryptoProfileAdmissionStore,
        )
        if (identity == null || transport == null) {
            state = if (identity == null) MeshState.ERROR else MeshState.OFF
            lastPacketStatus = if (identity == null) "identity-required" else "transport-disabled"
            recordSyncSummary(sent = 0, received = 0, receiptsApplied = 0, rejected = 0)
            return MeshSyncResult(messages, snapshot(messages, identity, realmSnapshot), 0, 0, 0, 0)
        }

        var updatedMessages = messages
        var sent = 0
        var received = 0
        var receiptsApplied = 0
        var rejected = 0
        var updatedRelationships = relationships
        val syncStartedAt = System.currentTimeMillis()
        var relationshipsById = updatedRelationships.associateBy { it.relationshipId }
        val peersByFingerprint = transport.observePeers().associateBy { it.fingerprint }
        val receiptsQueuedThisSync = mutableSetOf<String>()

        transport.observePackets().forEach { receivedPacket ->
            val alreadySeen = repository?.seenStore?.contains(receivedPacket.packet.packetId) == true
            val result = inboxProcessor(
                identity = identity,
                relationships = updatedRelationships,
                issuedInvites = issuedInvites,
                realmSnapshot = realmSnapshot,
                admissionGate = admissionGate,
            ).process(receivedPacket.packet, alreadySeen)
            if (result.rejectedReason != null) {
                rejected += 1
                recordRejectedInboundPacket(
                    identity = identity,
                    relationships = relationships,
                    packet = receivedPacket.packet,
                    reason = result.rejectedReason,
                    rejectedAtEpochMillis = receivedPacket.receivedAtEpochMillis,
                )
                recordDrop(updatedMessages, result.rejectedReason)
                return@forEach
            }
            repository?.seenStore?.markSeen(receivedPacket.packet.packetId)
            repository?.inboxStore?.add(
                StoredPacket(
                    packet = receivedPacket.packet,
                    status = PacketStoreStatus.RECEIVED,
                    storedAtEpochMillis = receivedPacket.receivedAtEpochMillis,
                ),
            )
            result.acceptedMessage?.let { incoming ->
                if (updatedMessages.none { it.messageId == incoming.messageId }) {
                    updatedMessages = MessageService.pruneMessages(updatedMessages + incoming)
                    received += 1
                    recordReceive(updatedMessages)
                }
            }
            result.updatedRelationship?.let { updated ->
                var matchedRelationship = false
                updatedRelationships = updatedRelationships.map { relationship ->
                    if (
                        relationship.relationshipId == updated.relationshipId ||
                        (
                            relationship.sourceInviteId == updated.sourceInviteId &&
                                relationship.peerFingerprint == updated.peerFingerprint
                        )
                    ) {
                        matchedRelationship = true
                        updated
                    } else {
                        relationship
                    }
                }
                if (!matchedRelationship) {
                    updatedRelationships = updatedRelationships + updated
                }
                relationshipsById = updatedRelationships.associateBy { it.relationshipId }
                received += 1
                recordReceive(updatedMessages)
                result.handshakeConfirmationPayloadJson?.let { confirmationPayloadJson ->
                    queueHandshakeConfirmationPacket(
                        identity = identity,
                        transport = transport,
                        relationship = updated,
                        confirmationPayloadJson = confirmationPayloadJson,
                        nowEpochMillis = syncStartedAt,
                    )
                }
            }
            result.receiptPacket?.let { receipt ->
                repository?.outboxStore?.upsert(
                    StoredPacket(
                        packet = receipt,
                        status = PacketStoreStatus.QUEUED,
                        storedAtEpochMillis = syncStartedAt,
                    ),
                )
                receiptsQueuedThisSync += receipt.packetId
                val peer = peersByFingerprint[receipt.recipientFingerprint] ?: receivedPacket.fromPeer
                val receiptSend = transport.send(peer, receipt)
                if (receiptSend.success) {
                    repository?.outboxStore?.markStatus(receipt.packetId, PacketStoreStatus.SENT)
                    recordSend(updatedMessages)
                } else {
                    val reason = receiptSend.rejectionReason()
                    repository?.outboxStore?.markStatus(receipt.packetId, packetStoreStatusForTransportFailure(reason), reason)
                    rejected += 1
                    recordDrop(updatedMessages, reason)
                }
            }
            result.deliveredMessageId?.let { deliveredId ->
                val originalPacketId = result.deliveredPacketId
                val matchingOutboxPacket = originalPacketId?.let { packetId ->
                    repository?.outboxStore?.load()
                        ?.firstOrNull { stored ->
                            stored.packet.packetId == packetId &&
                                stored.packet.messageId == deliveredId &&
                                stored.packet.recipientFingerprint == receivedPacket.packet.senderFingerprint &&
                                stored.status in setOf(PacketStoreStatus.SENT, PacketStoreStatus.ACKED)
                        }
                }
                if (repository != null && matchingOutboxPacket == null) {
                    rejected += 1
                    recordDrop(updatedMessages, MeshRejectionReason.MALFORMED)
                    return@let
                }
                if (matchingOutboxPacket?.status == PacketStoreStatus.ACKED) {
                    return@let
                }
                if (updatedMessages.any { it.messageId == deliveredId && it.status == MessageStatus.DELIVERED_TO_PEER }) {
                    markMessagePacketsAcked(deliveredId)
                    return@let
                }
                updatedMessages = updatedMessages.map { message ->
                    if (message.messageId == deliveredId) {
                        MessageService.updateStatus(message, MessageStatus.DELIVERED_TO_PEER)
                    } else {
                        message
                    }
                }
                repository?.receiptStore?.add(
                    PacketReceipt(
                        receiptId = "receipt-${receivedPacket.packet.packetId}",
                        packetId = receivedPacket.packet.packetId,
                        messageId = deliveredId,
                        senderFingerprint = receivedPacket.packet.senderFingerprint,
                        recipientFingerprint = receivedPacket.packet.recipientFingerprint,
                        createdAtEpochMillis = receivedPacket.receivedAtEpochMillis,
                    ),
                )
                markMessagePacketsAcked(deliveredId)
                receiptsApplied += 1
                val deliveryLatencyMs = matchingOutboxPacket?.let { stored ->
                    val sentAt = stored.lastAttemptAtEpochMillis
                        ?: stored.packet.createdAtEpochMillis
                    (receivedPacket.receivedAtEpochMillis - sentAt).coerceAtLeast(0)
                }
                recordReceipt(updatedMessages, deliveryLatencyMs)
            }
        }

        val receiptRetryResult = sendQueuedReceiptPackets(
            identity = identity,
            relationships = updatedRelationships,
            realmSnapshot = realmSnapshot,
            transport = transport,
            peersByFingerprint = peersByFingerprint,
            nowEpochMillis = syncStartedAt,
            skipPacketIds = receiptsQueuedThisSync,
        )
        sent += receiptRetryResult.sent
        rejected += receiptRetryResult.rejected

        val handshakeResponseRetryResult = sendQueuedHandshakeResponsePackets(
            identity = identity,
            relationships = updatedRelationships,
            transport = transport,
            peersByFingerprint = peersByFingerprint,
            nowEpochMillis = syncStartedAt,
        )
        sent += handshakeResponseRetryResult.sent
        rejected += handshakeResponseRetryResult.rejected

        val handshakeRetryResult = sendQueuedHandshakeConfirmationPackets(
            identity = identity,
            relationships = updatedRelationships,
            transport = transport,
            peersByFingerprint = peersByFingerprint,
            nowEpochMillis = syncStartedAt,
        )
        sent += handshakeRetryResult.sent
        rejected += handshakeRetryResult.rejected

        val messageRetryResult = sendQueuedMessagePackets(
            messages = updatedMessages,
            identity = identity,
            relationships = updatedRelationships,
            realmSnapshot = realmSnapshot,
            transport = transport,
            peersByFingerprint = peersByFingerprint,
            nowEpochMillis = syncStartedAt,
        )
        updatedMessages = messageRetryResult.messages
        sent += messageRetryResult.sent
        rejected += messageRetryResult.rejected

        updatedMessages = updatedMessages.map { message ->
            MeshTransportHardening.requeueUnacknowledgedSentMessage(
                message = message,
                ackTimeoutMillis = SENT_ACK_RETRY_TIMEOUT_MILLIS,
                nowEpochMillis = syncStartedAt,
            )
        }

        updatedMessages
            .filter { it.status == MessageStatus.READY_FOR_TRANSPORT }
            .forEach { message ->
                if (repository?.outboxStore?.hasNonFinalMessagePacket(message.messageId) == true) {
                    return@forEach
                }
                val relationship = relationshipsById[message.relationshipId]
                val realmDecision = relationship?.let {
                    RealmCommunicationPolicy.canUseRelationship(
                        localIdentity = identity,
                        relationship = it,
                        realmSnapshot = realmSnapshot,
                    )
                }
                val peer = relationship?.takeIf { realmDecision?.allowed == true }?.let { peersByFingerprint[it.peerFingerprint] }
                if (relationship == null || realmDecision?.allowed != true || peer == null) {
                    val reason = when {
                        relationship == null || !RelationshipService.canSendMessage(relationship) -> MeshRejectionReason.PENDING_RELATIONSHIP
                        realmDecision?.allowed != true -> MeshRejectionReason.REALM_MEMBERSHIP_BLOCKED
                        else -> MeshRejectionReason.UNKNOWN_PEER
                    }
                    if (relationship != null && realmDecision?.allowed == true && reason == MeshRejectionReason.UNKNOWN_PEER) {
                        val packet = runCatching {
                            outboxProcessor(
                                identity = identity,
                                transport = transport,
                                realmSnapshot = realmSnapshot,
                                admissionGate = admissionGate,
                            ).createMessagePacket(message, relationship)
                        }.getOrElse {
                            rejected += 1
                            recordDrop(updatedMessages, MeshRejectionReason.CRYPTO_PROFILE_REJECTED)
                            return@forEach
                        }
                        repository?.outboxStore?.upsert(
                            firstStoredAttempt(
                                packet = packet,
                                status = PacketStoreStatus.QUEUED,
                                nowEpochMillis = syncStartedAt,
                                lastError = MeshRejectionReason.UNKNOWN_PEER,
                            ),
                        )
                    }
                    rejected += 1
                    recordDrop(updatedMessages, reason)
                } else {
                    val result = outboxProcessor(
                        identity = identity,
                        transport = transport,
                        realmSnapshot = realmSnapshot,
                        admissionGate = admissionGate,
                    ).sendMessage(message, relationship, peer)
                    result.packet?.let { packet ->
                        val packetAttempt = firstStoredAttempt(
                            packet = packet,
                            status = if (result.rejectedReason == null) {
                                PacketStoreStatus.SENT
                            } else if (result.rejectedReason == MeshRejectionReason.UNKNOWN_PEER) {
                                PacketStoreStatus.QUEUED
                            } else {
                                PacketStoreStatus.REJECTED
                            },
                            nowEpochMillis = syncStartedAt,
                            lastError = result.rejectedReason,
                        )
                        repository?.outboxStore?.upsert(
                            packetAttempt,
                        )
                    }
                    result.updatedMessage?.let { updated ->
                        val finalUpdated = if (
                            result.rejectedReason != null &&
                            result.rejectedReason != MeshRejectionReason.UNKNOWN_PEER
                        ) {
                            MessageService.updateStatus(updated, MessageStatus.FAILED, syncStartedAt)
                        } else {
                            updated
                        }
                        updatedMessages = updatedMessages.map {
                            if (it.messageId == finalUpdated.messageId) finalUpdated else it
                        }
                    }
                    if (result.rejectedReason == null) {
                        sent += 1
                        recordSend(updatedMessages)
                    } else {
                        rejected += 1
                        recordDrop(updatedMessages, result.rejectedReason)
                    }
                }
            }

        val changedRelationshipsCount = resultUpdatedRelationships(updatedRelationships, relationships)
        lastPacketStatus = when {
            changedRelationshipsCount > 0 -> "handshake-confirmed-$changedRelationshipsCount"
            received > 0 -> "messages-received-$received"
            receiptsApplied > 0 -> "receipts-applied-$receiptsApplied"
            sent > 0 -> "messages-sent-$sent"
            rejected > 0 -> "rejected-$rejected"
            else -> "sync-noop"
        }
        recordSyncSummary(sent, received, receiptsApplied, rejected)
        return MeshSyncResult(
            messages = updatedMessages,
            snapshot = snapshot(updatedMessages, identity, realmSnapshot),
            sentCount = sent,
            receivedCount = received,
            receiptsApplied = receiptsApplied,
            rejectedCount = rejected,
            updatedRelationships = if (changedRelationshipsCount > 0) updatedRelationships else emptyList(),
        )
    }

    fun enqueueHandshakeConfirmation(
        localIdentity: LocalIdentity?,
        relationship: Relationship,
        confirmationPayloadJson: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): MeshServiceSnapshot {
        val identity = localIdentity ?: run {
            lastPacketStatus = "handshake-confirmation-queued-failed:identity-required"
            return snapshot(emptyList())
        }
        val transport = transportManager.currentTransport() ?: run {
            lastPacketStatus = "handshake-confirmation-queued-no-transport"
            return snapshot(emptyList(), identity)
        }
        val admissionGate = ProductCryptoAdmissionGate(
            admissionStore = repository?.admissionStore ?: NoOpCryptoProfileAdmissionStore,
        )
        val packet = outboxProcessor(
            identity = identity,
            transport = transport,
            realmSnapshot = null,
            admissionGate = admissionGate,
        ).createHandshakeConfirmationPacket(
            relationship = relationship,
            confirmationPayloadJson = confirmationPayloadJson,
        )
        val queued = queuePacketForRelationship(
            identity = identity,
            relationship = relationship,
            packet = packet,
            nowEpochMillis = nowEpochMillis,
        )
        if (!queued) {
            val validation = MeshTrustGate.validateOutbound(identity, relationship, packet, nowEpochMillis = nowEpochMillis)
            lastPacketStatus = "handshake-confirmation-queued-failed:${validation.rejectionReason}"
            return snapshot(emptyList(), identity)
        }
        lastPacketStatus = "handshake-confirmation-queued"
        return snapshot(emptyList(), identity)
    }

    fun enqueueHandshakeResponse(
        localIdentity: LocalIdentity?,
        relationship: Relationship,
        responsePayloadJson: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): MeshServiceSnapshot {
        val identity = localIdentity ?: run {
            lastPacketStatus = "handshake-response-queued-failed:identity-required"
            return snapshot(emptyList())
        }
        val transport = transportManager.currentTransport() ?: run {
            lastPacketStatus = "handshake-response-queued-no-transport"
            return snapshot(emptyList(), identity)
        }
        val admissionGate = ProductCryptoAdmissionGate(
            admissionStore = repository?.admissionStore ?: NoOpCryptoProfileAdmissionStore,
        )
        val packet = outboxProcessor(
            identity = identity,
            transport = transport,
            realmSnapshot = null,
            admissionGate = admissionGate,
        ).createHandshakeResponsePacket(
            relationship = relationship,
            responsePayloadJson = responsePayloadJson,
        )
        val queued = queuePacketForRelationship(
            identity = identity,
            relationship = relationship,
            packet = packet,
            nowEpochMillis = nowEpochMillis,
        )
        if (!queued) {
            val validation = MeshTrustGate.validateOutbound(identity, relationship, packet, nowEpochMillis = nowEpochMillis)
            lastPacketStatus = "handshake-response-queued-failed:${validation.rejectionReason}"
            return snapshot(emptyList(), identity)
        }
        lastPacketStatus = "handshake-response-queued"
        return snapshot(emptyList(), identity)
    }

    private fun queueHandshakeConfirmationPacket(
        identity: LocalIdentity,
        transport: PeerTransport,
        relationship: Relationship,
        confirmationPayloadJson: String,
        nowEpochMillis: Long,
    ) {
        val admissionGate = ProductCryptoAdmissionGate(
            admissionStore = repository?.admissionStore ?: NoOpCryptoProfileAdmissionStore,
        )
        val packet = outboxProcessor(
            identity = identity,
            transport = transport,
            realmSnapshot = null,
            admissionGate = admissionGate,
        ).createHandshakeConfirmationPacket(
            relationship = relationship,
            confirmationPayloadJson = confirmationPayloadJson,
        )
        queuePacketForRelationship(
            identity = identity,
            relationship = relationship,
            packet = packet,
            nowEpochMillis = nowEpochMillis,
        )
    }

    private fun queuePacketForRelationship(
        identity: LocalIdentity,
        relationship: Relationship,
        packet: KrakenPacket,
        nowEpochMillis: Long,
    ): Boolean {
        val validation = MeshTrustGate.validateOutbound(identity, relationship, packet, nowEpochMillis = nowEpochMillis)
        if (!validation.accepted) return false
        repository?.outboxStore?.upsert(
            StoredPacket(
                packet = packet,
                status = PacketStoreStatus.QUEUED,
                storedAtEpochMillis = nowEpochMillis,
                attempts = 0,
                nextAttemptAtEpochMillis = 0,
            ),
        )
        return true
    }

    private fun sendQueuedMessagePackets(
        messages: List<LocalMessage>,
        identity: LocalIdentity,
        relationships: List<Relationship>,
        realmSnapshot: RealmSnapshot?,
        transport: PeerTransport,
        peersByFingerprint: Map<String, DiscoveredPeer>,
        nowEpochMillis: Long,
    ): MessageRetryResult {
        val repo = repository ?: return MessageRetryResult(messages = messages)
        val outboxStore = repo.outboxStore
        val admissionStore = repo.admissionStore
        var updatedMessages = messages
        var sent = 0
        var rejected = 0

        outboxStore.terminalOutgoingMessages(nowEpochMillis, retryPolicy).forEach { stored ->
            if (updatedMessages.any { it.messageId == stored.packet.messageId && it.status == MessageStatus.DELIVERED_TO_PEER }) {
                outboxStore.markStatus(stored.packet.packetId, PacketStoreStatus.ACKED)
                return@forEach
            }
            outboxStore.markStatus(stored.packet.packetId, PacketStoreStatus.EXPIRED, MeshRejectionReason.EXPIRED)
            updatedMessages = updateMessageStatus(
                messages = updatedMessages,
                messageId = stored.packet.messageId,
                status = MessageStatus.FAILED,
                nowEpochMillis = nowEpochMillis,
            )
            rejected += 1
            metricsRecorder.recordDrop(MeshRejectionReason.EXPIRED)
        }

        outboxStore.eligibleOutgoingMessages(nowEpochMillis, retryPolicy).forEach { stored ->
            val packet = stored.packet
            if (updatedMessages.any { it.messageId == packet.messageId && it.status == MessageStatus.DELIVERED_TO_PEER }) {
                outboxStore.markStatus(packet.packetId, PacketStoreStatus.ACKED)
                return@forEach
            }
            val relationship = relationships.firstOrNull {
                it.relationshipId == packet.relationshipId &&
                    it.peerFingerprint == packet.recipientFingerprint &&
                    it.localIdentityPublicKey == identity.publicKeyEncoded
            }
            if (relationship == null) {
                outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, MeshRejectionReason.UNKNOWN_PEER)
                updatedMessages = updateMessageStatus(updatedMessages, packet.messageId, MessageStatus.FAILED, nowEpochMillis)
                rejected += 1
                metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                return@forEach
            }
            val validation = MeshTrustGate.validateOutbound(identity, relationship, packet, realmSnapshot, nowEpochMillis)
            if (!validation.accepted) {
                outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, validation.rejectionReason)
                updatedMessages = updateMessageStatus(updatedMessages, packet.messageId, MessageStatus.FAILED, nowEpochMillis)
                rejected += 1
                metricsRecorder.recordDrop(validation.rejectionReason)
                return@forEach
            }
            validatePacketAdmission(packet, relationship, ProductCryptoAdmissionGate(admissionStore = admissionStore))?.let { reason ->
                outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, reason)
                updatedMessages = updateMessageStatus(updatedMessages, packet.messageId, MessageStatus.FAILED, nowEpochMillis)
                rejected += 1
                metricsRecorder.recordDrop(reason)
                return@forEach
            }
            if (
                messagePayloadProtectionPolicy == MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED &&
                packet.packetType == KrakenPacketType.MESSAGE &&
                packet.payloadType != PacketPayloadType.ENCRYPTED_MESSAGE_JSON
            ) {
                outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, MeshRejectionReason.CRYPTO_PROFILE_REJECTED)
                updatedMessages = updateMessageStatus(updatedMessages, packet.messageId, MessageStatus.FAILED, nowEpochMillis)
                rejected += 1
                metricsRecorder.recordDrop(MeshRejectionReason.CRYPTO_PROFILE_REJECTED)
                return@forEach
            }
            val peer = peersByFingerprint[packet.recipientFingerprint]
            if (peer == null) {
                outboxStore.recordAttempt(
                    packetId = packet.packetId,
                    status = PacketStoreStatus.QUEUED,
                    nowEpochMillis = nowEpochMillis,
                    nextAttemptAtEpochMillis = nextRetryAt(stored, nowEpochMillis),
                    lastError = MeshRejectionReason.UNKNOWN_PEER,
                )
                updatedMessages = updateMessageStatus(updatedMessages, packet.messageId, MessageStatus.READY_FOR_TRANSPORT, nowEpochMillis)
                rejected += 1
                metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                return@forEach
            }
            val sendResult = transport.send(peer, packet)
            if (sendResult.success) {
                outboxStore.recordAttempt(
                    packetId = packet.packetId,
                    status = PacketStoreStatus.SENT,
                    nowEpochMillis = nowEpochMillis,
                    nextAttemptAtEpochMillis = nowEpochMillis + SENT_ACK_RETRY_TIMEOUT_MILLIS,
                )
                updatedMessages = updateMessageStatus(updatedMessages, packet.messageId, MessageStatus.SENT_TO_TRANSPORT, nowEpochMillis)
                sent += 1
                metricsRecorder.recordSent()
            } else {
                val reason = sendResult.rejectionReason()
                val failureStatus = packetStoreStatusForTransportFailure(reason)
                outboxStore.recordAttempt(
                    packetId = packet.packetId,
                    status = failureStatus,
                    nowEpochMillis = nowEpochMillis,
                    nextAttemptAtEpochMillis = if (failureStatus == PacketStoreStatus.QUEUED) {
                        nextRetryAt(stored, nowEpochMillis)
                    } else {
                        0
                    },
                    lastError = reason,
                )
                updatedMessages = updateMessageStatus(
                    updatedMessages,
                    packet.messageId,
                    if (failureStatus == PacketStoreStatus.QUEUED) MessageStatus.READY_FOR_TRANSPORT else MessageStatus.FAILED,
                    nowEpochMillis,
                )
                rejected += 1
                metricsRecorder.recordDrop(reason)
            }
        }

        return MessageRetryResult(messages = updatedMessages, sent = sent, rejected = rejected)
    }

    private fun outboxProcessor(
        identity: LocalIdentity,
        transport: PeerTransport,
        realmSnapshot: RealmSnapshot?,
        admissionGate: ProductCryptoAdmissionGate,
    ): MeshOutboxProcessor =
        MeshOutboxProcessor(
            localIdentity = identity,
            transport = transport,
            realmSnapshot = realmSnapshot,
            admissionGate = admissionGate,
            messagePayloadProtector = AdamovaMessagePayloadProtector(
                localIdentity = identity,
                sessionKeyProvider = messageSessionKeyProvider,
                admissionGate = admissionGate,
            ),
            messagePayloadProtectionPolicy = messagePayloadProtectionPolicy,
        )

    private fun inboxProcessor(
        identity: LocalIdentity,
        relationships: List<Relationship>,
        issuedInvites: List<IssuedInviteRecord> = emptyList(),
        realmSnapshot: RealmSnapshot?,
        admissionGate: ProductCryptoAdmissionGate,
    ): MeshInboxProcessor =
        MeshInboxProcessor(
            localIdentity = identity,
            relationships = relationships,
            issuedInvites = issuedInvites,
            realmSnapshot = realmSnapshot,
            admissionGate = admissionGate,
            messagePayloadProtector = AdamovaMessagePayloadProtector(
                localIdentity = identity,
                sessionKeyProvider = messageSessionKeyProvider,
                admissionGate = admissionGate,
            ),
            messagePayloadProtectionPolicy = messagePayloadProtectionPolicy,
        )

    private fun recordRejectedInboundPacket(
        identity: LocalIdentity,
        relationships: List<Relationship>,
        packet: KrakenPacket,
        reason: MeshRejectionReason?,
        rejectedAtEpochMillis: Long,
    ) {
        val compactSender = compactFingerprint(packet.senderFingerprint)
        val compactRecipient = compactFingerprint(packet.recipientFingerprint)
        val compactLocal = compactFingerprint(identity.fingerprint)
        val relationshipIdKnown = relationships.any { it.relationshipId == packet.relationshipId }
        val senderFingerprintKnown = relationships.any { it.peerFingerprint == packet.senderFingerprint }
        val senderFingerprintNormalizedKnown = relationships.any {
            compactFingerprint(it.peerFingerprint) == compactSender
        }
        val relationshipAndSenderKnown = relationships.any {
            it.relationshipId == packet.relationshipId &&
                compactFingerprint(it.peerFingerprint) == compactSender
        }
        val localIdentityPublicKeyMatchesRelationship = relationships.any {
            it.relationshipId == packet.relationshipId &&
                compactFingerprint(it.peerFingerprint) == compactSender &&
                it.localIdentityPublicKey == identity.publicKeyEncoded
        }
        val diagnostic = MeshRejectedInboundPacketSnapshot(
            packetId = packet.packetId,
            messageId = packet.messageId,
            reason = reason,
            senderFingerprint = packet.senderFingerprint,
            recipientFingerprint = packet.recipientFingerprint,
            relationshipId = packet.relationshipId,
            recipientMatchesLocal = packet.recipientFingerprint == identity.fingerprint,
            recipientNormalizedMatchesLocal = compactRecipient == compactLocal,
            relationshipIdKnown = relationshipIdKnown,
            senderFingerprintKnown = senderFingerprintKnown,
            senderFingerprintNormalizedKnown = senderFingerprintNormalizedKnown,
            relationshipAndSenderKnown = relationshipAndSenderKnown,
            localIdentityPublicKeyMatchesRelationship = localIdentityPublicKeyMatchesRelationship,
            rejectedAtEpochMillis = rejectedAtEpochMillis,
        )
        recentRejectedInboundPackets = (recentRejectedInboundPackets + diagnostic).takeLast(10)
    }

    private fun compactFingerprint(value: String): String =
        value.filter { it.isLetterOrDigit() }.uppercase()

    private fun sendQueuedReceiptPackets(
        identity: LocalIdentity,
        relationships: List<Relationship>,
        realmSnapshot: RealmSnapshot?,
        transport: PeerTransport,
        peersByFingerprint: Map<String, DiscoveredPeer>,
        nowEpochMillis: Long,
        skipPacketIds: Set<String> = emptySet(),
    ): ReceiptRetryResult {
        val repo = repository ?: return ReceiptRetryResult()
        val outboxStore = repo.outboxStore
        val admissionStore = repo.admissionStore
        var sent = 0
        var rejected = 0
        outboxStore.queuedReceipts()
            .filterNot { it.packet.packetId in skipPacketIds }
            .forEach { stored ->
                val packet = stored.packet
                val relationship = relationships.firstOrNull {
                    it.relationshipId == packet.relationshipId &&
                        it.peerFingerprint == packet.recipientFingerprint &&
                        it.localIdentityPublicKey == identity.publicKeyEncoded
                }
                if (relationship == null) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, MeshRejectionReason.UNKNOWN_PEER)
                    rejected += 1
                    metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                    return@forEach
                }
                val validation = MeshTrustGate.validateOutbound(identity, relationship, packet, realmSnapshot, nowEpochMillis)
                if (!validation.accepted) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, validation.rejectionReason)
                    rejected += 1
                    metricsRecorder.recordDrop(validation.rejectionReason)
                    return@forEach
                }
                validatePacketAdmission(packet, relationship, ProductCryptoAdmissionGate(admissionStore = admissionStore))?.let { reason ->
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, reason)
                    rejected += 1
                    metricsRecorder.recordDrop(reason)
                    return@forEach
                }
                val peer = peersByFingerprint[packet.recipientFingerprint]
                if (peer == null) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.QUEUED, MeshRejectionReason.UNKNOWN_PEER)
                    rejected += 1
                    metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                    return@forEach
                }
                val sendResult = transport.send(peer, packet)
                if (sendResult.success) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.SENT)
                    sent += 1
                    metricsRecorder.recordSent()
                } else {
                    val reason = sendResult.rejectionReason()
                    outboxStore.markStatus(packet.packetId, packetStoreStatusForTransportFailure(reason), reason)
                    rejected += 1
                    metricsRecorder.recordDrop(reason)
                }
            }
        return ReceiptRetryResult(sent = sent, rejected = rejected)
    }

    private fun sendQueuedHandshakeConfirmationPackets(
        identity: LocalIdentity,
        relationships: List<Relationship>,
        transport: PeerTransport,
        peersByFingerprint: Map<String, DiscoveredPeer>,
        nowEpochMillis: Long,
    ): ReceiptRetryResult {
        val repo = repository ?: return ReceiptRetryResult()
        val outboxStore = repo.outboxStore
        val admissionStore = repo.admissionStore
        var sent = 0
        var rejected = 0
        outboxStore.eligibleHandshakeConfirmations(nowEpochMillis, retryPolicy)
            .forEach { stored ->
                val packet = stored.packet
                val relationship = relationships.firstOrNull {
                    it.relationshipId == packet.relationshipId &&
                        it.peerFingerprint == packet.recipientFingerprint &&
                        it.localIdentityPublicKey == identity.publicKeyEncoded
                }
                if (relationship == null) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, MeshRejectionReason.UNKNOWN_PEER)
                    rejected += 1
                    metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                    return@forEach
                }
                val validation = MeshTrustGate.validateOutbound(identity, relationship, packet, nowEpochMillis = nowEpochMillis)
                if (!validation.accepted) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, validation.rejectionReason)
                    rejected += 1
                    metricsRecorder.recordDrop(validation.rejectionReason)
                    return@forEach
                }
                validatePacketAdmission(packet, relationship, ProductCryptoAdmissionGate(admissionStore = admissionStore))?.let { reason ->
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, reason)
                    rejected += 1
                    metricsRecorder.recordDrop(reason)
                    return@forEach
                }
                val peer = peersByFingerprint[packet.recipientFingerprint]
                if (peer == null) {
                    outboxStore.recordAttempt(
                        packetId = packet.packetId,
                        status = PacketStoreStatus.QUEUED,
                        nowEpochMillis = nowEpochMillis,
                        nextAttemptAtEpochMillis = nextRetryAt(stored, nowEpochMillis),
                        lastError = MeshRejectionReason.UNKNOWN_PEER,
                    )
                    rejected += 1
                    metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                    return@forEach
                }
                val sendResult = transport.send(peer, packet)
                if (sendResult.success) {
                    outboxStore.recordAttempt(
                        packetId = packet.packetId,
                        status = PacketStoreStatus.SENT,
                        nowEpochMillis = nowEpochMillis,
                        nextAttemptAtEpochMillis = nowEpochMillis + SENT_ACK_RETRY_TIMEOUT_MILLIS,
                    )
                    sent += 1
                    metricsRecorder.recordSent()
                } else {
                    val reason = sendResult.rejectionReason()
                    val failureStatus = packetStoreStatusForTransportFailure(reason)
                    outboxStore.recordAttempt(
                        packetId = packet.packetId,
                        status = failureStatus,
                        nowEpochMillis = nowEpochMillis,
                        nextAttemptAtEpochMillis = if (failureStatus == PacketStoreStatus.QUEUED) {
                            nextRetryAt(stored, nowEpochMillis)
                        } else {
                            0
                        },
                        lastError = reason,
                    )
                    rejected += 1
                    metricsRecorder.recordDrop(reason)
                }
            }
        return ReceiptRetryResult(sent = sent, rejected = rejected)
    }

    private fun sendQueuedHandshakeResponsePackets(
        identity: LocalIdentity,
        relationships: List<Relationship>,
        transport: PeerTransport,
        peersByFingerprint: Map<String, DiscoveredPeer>,
        nowEpochMillis: Long,
    ): ReceiptRetryResult {
        val repo = repository ?: return ReceiptRetryResult()
        val outboxStore = repo.outboxStore
        val admissionStore = repo.admissionStore
        var sent = 0
        var rejected = 0
        outboxStore.eligibleHandshakeResponses(nowEpochMillis, retryPolicy)
            .forEach { stored ->
                val packet = stored.packet
                val relationship = relationships.firstOrNull {
                    it.relationshipId == packet.relationshipId &&
                        it.peerFingerprint == packet.recipientFingerprint &&
                        it.localIdentityPublicKey == identity.publicKeyEncoded
                }
                if (relationship == null) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, MeshRejectionReason.UNKNOWN_PEER)
                    rejected += 1
                    metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                    return@forEach
                }
                val validation = MeshTrustGate.validateOutbound(identity, relationship, packet, nowEpochMillis = nowEpochMillis)
                if (!validation.accepted) {
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, validation.rejectionReason)
                    rejected += 1
                    metricsRecorder.recordDrop(validation.rejectionReason)
                    return@forEach
                }
                validatePacketAdmission(packet, relationship, ProductCryptoAdmissionGate(admissionStore = admissionStore))?.let { reason ->
                    outboxStore.markStatus(packet.packetId, PacketStoreStatus.REJECTED, reason)
                    rejected += 1
                    metricsRecorder.recordDrop(reason)
                    return@forEach
                }
                val peer = peersByFingerprint[packet.recipientFingerprint]
                if (peer == null) {
                    outboxStore.recordAttempt(
                        packetId = packet.packetId,
                        status = PacketStoreStatus.QUEUED,
                        nowEpochMillis = nowEpochMillis,
                        nextAttemptAtEpochMillis = nextRetryAt(stored, nowEpochMillis),
                        lastError = MeshRejectionReason.UNKNOWN_PEER,
                    )
                    rejected += 1
                    metricsRecorder.recordDrop(MeshRejectionReason.UNKNOWN_PEER)
                    return@forEach
                }
                val sendResult = transport.send(peer, packet)
                if (sendResult.success) {
                    outboxStore.recordAttempt(
                        packetId = packet.packetId,
                        status = PacketStoreStatus.SENT,
                        nowEpochMillis = nowEpochMillis,
                        nextAttemptAtEpochMillis = nowEpochMillis + SENT_ACK_RETRY_TIMEOUT_MILLIS,
                    )
                    sent += 1
                    metricsRecorder.recordSent()
                } else {
                    val reason = sendResult.rejectionReason()
                    val failureStatus = packetStoreStatusForTransportFailure(reason)
                    outboxStore.recordAttempt(
                        packetId = packet.packetId,
                        status = failureStatus,
                        nowEpochMillis = nowEpochMillis,
                        nextAttemptAtEpochMillis = if (failureStatus == PacketStoreStatus.QUEUED) {
                            nextRetryAt(stored, nowEpochMillis)
                        } else {
                            0
                        },
                        lastError = reason,
                    )
                    rejected += 1
                    metricsRecorder.recordDrop(reason)
                }
            }
        return ReceiptRetryResult(sent = sent, rejected = rejected)
    }

    private fun firstStoredAttempt(
        packet: KrakenPacket,
        status: PacketStoreStatus,
        nowEpochMillis: Long,
        lastError: MeshRejectionReason?,
    ): StoredPacket {
        val nextAttemptAt = when (status) {
            PacketStoreStatus.SENT -> nowEpochMillis + SENT_ACK_RETRY_TIMEOUT_MILLIS
            PacketStoreStatus.QUEUED -> nextRetryAt(packet, attempts = 0, nowEpochMillis = nowEpochMillis)
            else -> 0
        }
        return StoredPacket(
            packet = packet,
            status = status,
            storedAtEpochMillis = nowEpochMillis,
            lastError = lastError,
            attempts = 1,
            lastAttemptAtEpochMillis = nowEpochMillis,
            nextAttemptAtEpochMillis = nextAttemptAt,
        )
    }

    private fun nextRetryAt(stored: StoredPacket, nowEpochMillis: Long): Long =
        nextRetryAt(stored.packet, stored.attempts, nowEpochMillis)

    private fun packetStoreStatusForTransportFailure(reason: MeshRejectionReason?): PacketStoreStatus =
        when (reason) {
            MeshRejectionReason.UNKNOWN_PEER,
            null -> PacketStoreStatus.QUEUED
            MeshRejectionReason.EXPIRED -> PacketStoreStatus.EXPIRED
            else -> PacketStoreStatus.REJECTED
        }

    private fun nextRetryAt(packet: KrakenPacket, attempts: Int, nowEpochMillis: Long): Long =
        MeshTransportHardening.nextRetry(
            current = QueuedPacketAttempt(
                packetId = packet.packetId,
                attempts = attempts,
                nextAttemptAtEpochMillis = nowEpochMillis,
                expiresAtEpochMillis = packet.expiresAtEpochMillis,
            ),
            policy = retryPolicy,
            nowEpochMillis = nowEpochMillis,
        ).nextAttemptAtEpochMillis

    private fun resultUpdatedRelationships(
        updatedRelationships: List<Relationship>,
        originalRelationships: List<Relationship>,
    ): Int {
        val originalById = originalRelationships.associateBy { it.relationshipId }
        return updatedRelationships.count { updated ->
            originalById[updated.relationshipId] != updated
        }
    }

    private fun updateMessageStatus(
        messages: List<LocalMessage>,
        messageId: String?,
        status: MessageStatus,
        nowEpochMillis: Long,
    ): List<LocalMessage> =
        if (messageId == null) {
            messages
        } else {
            messages.map { message ->
                if (message.messageId == messageId) {
                    MessageService.updateStatus(message, status, nowEpochMillis)
                } else {
                    message
                }
            }
        }

    private fun markMessagePacketsAcked(messageId: String?) {
        if (messageId == null) return
        val outboxStore = repository?.outboxStore ?: return
        outboxStore
            .load()
            .filter { stored ->
                stored.packet.packetType == KrakenPacketType.MESSAGE &&
                    stored.packet.messageId == messageId &&
                    stored.status != PacketStoreStatus.ACKED
            }
            .forEach { stored ->
                outboxStore.markStatus(stored.packet.packetId, PacketStoreStatus.ACKED)
            }
    }

    private fun recordSyncSummary(
        sent: Int,
        received: Int,
        receiptsApplied: Int,
        rejected: Int,
    ) {
        lastSyncAtEpochMillis = System.currentTimeMillis()
        lastSyncSummary = "sent=$sent received=$received receipts=$receiptsApplied rejected=$rejected"
    }
}

private fun debugMessageProbePacket(
    localIdentity: LocalIdentity,
    relationship: Relationship,
    packetId: String,
    senderFingerprint: String,
    recipientFingerprint: String,
    nowEpochMillis: Long,
): KrakenPacket {
    val profileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
    val messageId = "message-$packetId"
    return KrakenPacket(
        packetId = packetId,
        packetType = KrakenPacketType.MESSAGE,
        senderFingerprint = senderFingerprint,
        recipientFingerprint = recipientFingerprint,
        relationshipId = relationship.relationshipId,
        conversationId = "conversation-${relationship.relationshipId}",
        messageId = messageId,
        createdAtEpochMillis = nowEpochMillis,
        expiresAtEpochMillis = nowEpochMillis + DEFAULT_PACKET_TTL_MILLIS,
        ttlHops = 4,
        payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
        payloadJson = """{"message_id":"$messageId","body":"debug hostile packet probe"}""",
        cryptoProfileId = profileId,
        sessionProfileId = "session-${relationship.relationshipId}-$profileId",
        admissionDecisionHash = relationship.admissionDecisionHash
            ?: KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH,
        profilePolicyVersion = relationship.profilePolicyVersion
            ?: KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
    )
}

private data class ReceiptRetryResult(
    val sent: Int = 0,
    val rejected: Int = 0,
)

private data class MessageRetryResult(
    val messages: List<LocalMessage>,
    val sent: Int = 0,
    val rejected: Int = 0,
)

class MeshRepository(
    val outboxStore: PacketOutbox,
    val inboxStore: PacketInbox,
    val seenStore: PacketSeen,
    val receiptStore: PacketReceipts,
    val admissionStore: CryptoProfileAdmissionStore = NoOpCryptoProfileAdmissionStore,
)

private const val SENT_ACK_RETRY_TIMEOUT_MILLIS = 15_000L
private const val DEBUG_EVIDENCE_MODE = "debug_local_inbox_packet_injection_and_queue_retry_probe"
private const val DEBUG_EVIDENCE_UNAVAILABLE_MODE = "debug_local_inbox_packet_injection_unavailable"
