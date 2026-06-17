package com.disser.kraken.mesh

import android.content.Context
import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.crypto.SharedPreferencesCryptoProfileAdmissionStore
import com.disser.kraken.identity.IdentityStore
import com.disser.kraken.invite.IssuedInviteStore
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.MessageStatus
import com.disser.kraken.message.MessageStore
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmStore
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipNotificationStore
import com.disser.kraken.relationship.RelationshipStore
import java.util.concurrent.atomic.AtomicBoolean

class MeshRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val identityStore = IdentityStore(appContext)
    private val issuedInviteStore = IssuedInviteStore(appContext)
    private val relationshipStore = RelationshipStore(appContext)
    private val relationshipNotificationStore = RelationshipNotificationStore(appContext)
    private val notificationInboxStore = KrakenNotificationInboxStore(appContext)
    private val realmStore = RealmStore(appContext)
    private val messageStore = MessageStore(appContext)
    private val packetOutboxStore = PacketOutboxStore(appContext)
    private val packetInboxStore = PacketInboxStore(appContext)
    private val packetSeenStore = PacketSeenStore(appContext)
    private val receiptStore = ReceiptStore(appContext)
    private val syncInFlight = AtomicBoolean(false)
    @Volatile
    private var foregroundServiceRunning = false
    val prefs = MeshRuntimePrefs(appContext)
    private val meshService = MeshService(
        repository = MeshRepository(
            outboxStore = packetOutboxStore,
            inboxStore = packetInboxStore,
            seenStore = packetSeenStore,
            receiptStore = receiptStore,
            admissionStore = SharedPreferencesCryptoProfileAdmissionStore(appContext),
        ),
    )

    fun start(): MeshServiceSnapshot {
        return startWithTransportSelection(prefs.transportSelection)
    }

    fun startHotspotCompatible(): MeshServiceSnapshot {
        prefs.transportProfile = MeshTransportSelection.PROFILE_HOTSPOT_COMPATIBLE
        return startWithTransportSelection(MeshTransportSelection.HOTSPOT_COMPATIBLE)
    }

    fun startAutoTransports(): MeshServiceSnapshot {
        prefs.transportProfile = MeshTransportSelection.PROFILE_AUTO
        return startWithTransportSelection(MeshTransportSelection.AUTO)
    }

    fun startDebugWifiDirectOnly(): MeshServiceSnapshot =
        startWithTransportSelection(MeshTransportSelection.WIFI_DIRECT_ONLY).also {
            prefs.transportProfile = MeshTransportSelection.PROFILE_WIFI_DIRECT_ONLY
        }

    fun startDebugLanOnly(): MeshServiceSnapshot =
        startWithTransportSelection(MeshTransportSelection.LAN_ONLY).also {
            prefs.transportProfile = MeshTransportSelection.PROFILE_LAN_ONLY
        }

    fun ensureDebugWifiDirectGroupOwner(): String =
        meshService.ensureDebugWifiDirectGroupOwner()

    fun addDebugWifiDirectPeerForFirstRelationship(
        deviceAddress: String,
        deviceName: String?,
        port: Int,
    ): MeshServiceSnapshot =
        withRuntimeState(
            meshService.addDebugWifiDirectPeer(
                messages = messageStore.load(),
                localIdentity = identityStore.load(),
                relationships = relationshipStore.load(),
                realmSnapshot = realmStore.snapshot(),
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                port = port,
            ),
        )

    private fun startWithTransportSelection(transportSelection: MeshTransportSelection): MeshServiceSnapshot {
        prefs.meshEnabled = true
        prefs.lastServiceStartedAtEpochMillis = System.currentTimeMillis()
        return withRuntimeState(
            meshService.startLan(
                context = appContext,
                localIdentity = identityStore.load(),
                wifiDirectEnabled = transportSelection.wifiDirect,
                transportSelection = transportSelection,
            ),
        )
    }

    fun stop(): MeshServiceSnapshot {
        prefs.meshEnabled = false
        return withRuntimeState(meshService.stop(messageStore.load()))
    }

    fun snapshot(): MeshServiceSnapshot =
        withRuntimeState(
            meshService.snapshot(
                messages = messageStore.load(),
                localIdentity = identityStore.load(),
                realmSnapshot = realmStore.snapshot(),
            ),
        )

    fun loadMessages(): List<LocalMessage> =
        messageStore.load()

    fun loadReceivedPackets(): List<StoredPacket> =
        packetInboxStore.load()

    fun relationshipDisplayName(relationshipId: String): String? =
        relationshipStore.load()
            .firstOrNull { it.relationshipId == relationshipId }
            ?.peerDisplayName
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun markForegroundServiceRunning() {
        foregroundServiceRunning = true
    }

    fun markForegroundServiceStopped() {
        foregroundServiceRunning = false
    }

    fun addOutgoingMessage(message: LocalMessage): List<LocalMessage> =
        messageStore.add(message)

    fun addOutgoingTextMessage(
        relationshipId: String,
        body: String,
        replyToMessage: LocalMessage? = null,
    ): List<LocalMessage> {
        val identity = identityStore.load() ?: return messageStore.load()
        val relationship = relationshipStore.load().firstOrNull {
            it.relationshipId == relationshipId
        } ?: return messageStore.load()
        val decision = RealmCommunicationPolicy.canUseRelationship(identity, relationship, realmStore.snapshot())
        if (!decision.allowed) return messageStore.load()
        val message = MessageService.createOutgoingMessage(
            localIdentity = identity,
            relationship = relationship,
            body = body,
            replyToMessage = replyToMessage,
        )
        return messageStore.add(message)
    }

    fun addDebugTextMessageToFirstSendableRelationship(body: String): LocalMessage? {
        val identity = identityStore.load() ?: return null
        val relationship = relationshipStore.load().firstOrNull { relationship ->
            RealmCommunicationPolicy.canUseRelationship(identity, relationship, realmStore.snapshot()).allowed
        } ?: return null
        val beforeMessageIds = messageStore.load().mapTo(mutableSetOf()) { it.messageId }
        return addOutgoingTextMessage(relationship.relationshipId, body)
            .firstOrNull { it.messageId !in beforeMessageIds }
    }

    fun sendDebugDirectTextToFirstSendableRelationship(body: String): DebugDirectSendResult {
        val identity = identityStore.load() ?: return DebugDirectSendResult(null, null, false)
        val realmSnapshot = realmStore.snapshot()
        val relationship = relationshipStore.load().firstOrNull { relationship ->
            RealmCommunicationPolicy.canUseRelationship(identity, relationship, realmSnapshot).allowed
        } ?: return DebugDirectSendResult(null, null, false)
        val message = MessageService.createOutgoingMessage(
            localIdentity = identity,
            relationship = relationship,
            body = body,
        )
        val result = meshService.sendDebugDirectMessage(
            messages = messageStore.load(),
            localIdentity = identity,
            relationship = relationship,
            message = message,
            realmSnapshot = realmSnapshot,
        )
        result.message?.let { updated -> messageStore.upsert(updated) }
        return result
    }

    fun debugTransportRelationshipAlignment(): MeshDebugTransportRelationshipAlignment {
        val identity = identityStore.load()
        val realmSnapshot = realmStore.snapshot()
        val sendableRelationships = if (identity == null) {
            emptyList()
        } else {
            relationshipStore.load().filter { relationship ->
                RealmCommunicationPolicy.canUseRelationship(identity, relationship, realmSnapshot).allowed
            }
        }
        val observedFingerprints = meshService.snapshot(messageStore.load())
            .discoveredPeers
            .map { it.fingerprint }
            .distinct()
        val firstRelationship = sendableRelationships.firstOrNull()
        val firstRelationshipFingerprint = firstRelationship?.peerFingerprint
        val admissionGate = ProductCryptoAdmissionGate(
            admissionStore = SharedPreferencesCryptoProfileAdmissionStore(appContext),
        )
        val firstRelationshipCryptoProfileId = firstRelationship?.cryptoProfileId
            ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val firstRelationshipAdmission = firstRelationshipCryptoProfileId
            .let(admissionGate::evaluate)
        return MeshDebugTransportRelationshipAlignment(
            identityFingerprint = identity?.fingerprint,
            identityFingerprintPrefix = identity?.fingerprint?.fingerprintPrefix(),
            sendableRelationshipCount = sendableRelationships.size,
            firstSendableRelationshipId = firstRelationship?.relationshipId,
            firstSendableRelationshipFingerprint = firstRelationshipFingerprint,
            firstSendableRelationshipFingerprintPrefix = firstRelationshipFingerprint?.fingerprintPrefix(),
            firstSendableRelationshipCryptoProfileId = firstRelationship?.let { firstRelationshipCryptoProfileId },
            firstSendableRelationshipSessionProfileId = firstRelationship?.let {
                "session-${it.relationshipId}-$firstRelationshipCryptoProfileId"
            },
            firstSendableRelationshipAdmissionDecisionHash = firstRelationship?.let {
                it.admissionDecisionHash ?: firstRelationshipAdmission?.decisionHash
            },
            firstSendableRelationshipProfilePolicyVersion = firstRelationship?.let {
                it.profilePolicyVersion
                    ?: firstRelationshipAdmission?.policyVersion
                    ?: KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION
            },
            observedPeerFingerprints = observedFingerprints,
            observedPeerFingerprintPrefixes = observedFingerprints.map { it.fingerprintPrefix() },
            relationshipPeerSeenByTransport = firstRelationshipFingerprint != null &&
                observedFingerprints.any { it == firstRelationshipFingerprint },
        )
    }

    fun recordDebugNegativeEvidence(): MeshServiceSnapshot =
        withRuntimeState(
            meshService.recordDebugNegativeEvidence(
                messages = messageStore.load(),
                localIdentity = identityStore.load(),
                relationships = relationshipStore.load(),
                realmSnapshot = realmStore.snapshot(),
            ),
        )

    fun recordDebugQueueRetryEvidence(
        queueRetryMessageId: String,
        queueRetryBody: String,
        queuedBeforeTransportRestart: Boolean,
        queueSizeBeforeRestart: Int,
        sentAfterTransportRestart: Boolean,
        deliveredAfterTransportRestart: Boolean,
        queueSizeAfterRestart: Int,
        messageStatusAfterRestart: String?,
    ): MeshServiceSnapshot =
        withRuntimeState(
            meshService.recordDebugQueueRetryEvidence(
                messages = messageStore.load(),
                queueRetryMessageId = queueRetryMessageId,
                queueRetryBody = queueRetryBody,
                queuedBeforeTransportRestart = queuedBeforeTransportRestart,
                queueSizeBeforeRestart = queueSizeBeforeRestart,
                sentAfterTransportRestart = sentAfterTransportRestart,
                deliveredAfterTransportRestart = deliveredAfterTransportRestart,
                queueSizeAfterRestart = queueSizeAfterRestart,
                messageStatusAfterRestart = messageStatusAfterRestart,
            ),
        )

    fun upsertMessage(message: LocalMessage): List<LocalMessage> =
        messageStore.upsert(message)

    fun deleteMessage(messageId: String): List<LocalMessage> {
        packetOutboxStore.deleteMessagePackets(messageId)
        return messageStore.delete(messageId)
    }

    fun clearConversation(conversationId: String): List<LocalMessage> {
        packetOutboxStore.deleteConversationPackets(conversationId)
        messageStore.load()
            .firstOrNull { it.conversationId == conversationId }
            ?.let { markRelationshipNotificationsRead(it.relationshipId) }
        return messageStore.clearConversation(conversationId)
    }

    fun markRelationshipNotificationsRead(relationshipId: String) {
        notificationInboxStore.clearRelationship(relationshipId)
        KrakenMessageNotifier.cancelRelationshipNotification(appContext, relationshipId)
    }

    fun requeueMessage(messageId: String): List<LocalMessage> {
        val now = System.currentTimeMillis()
        val target = messageStore.load().firstOrNull {
            it.messageId == messageId && it.status != MessageStatus.DELIVERED_TO_PEER
        } ?: return messageStore.load()
        packetOutboxStore.requeueMessagePackets(messageId, now)
        return messageStore.upsert(MessageService.updateStatus(target, MessageStatus.READY_FOR_TRANSPORT, now))
    }

    fun addManualLanPeer(
        fingerprint: String,
        host: String,
        port: Int,
    ): MeshServiceSnapshot =
        withRuntimeState(meshService.addManualLanPeer(
            messages = messageStore.load(),
            fingerprint = fingerprint,
            host = host,
            port = port,
        ))

    suspend fun syncNow(): MeshSyncResult {
        if (!syncInFlight.compareAndSet(false, true)) {
            val currentMessages = messageStore.load()
            return MeshSyncResult(
                messages = currentMessages,
                snapshot = withRuntimeState(meshService.snapshot(currentMessages)),
                sentCount = 0,
                receivedCount = 0,
                receiptsApplied = 0,
                rejectedCount = 0,
            )
        }
        return try {
            val beforeMessages = messageStore.load()
            val result = meshService.syncNow(
                localIdentity = identityStore.load(),
                relationships = relationshipStore.load(),
                messages = beforeMessages,
                issuedInvites = issuedInviteStore.load(),
                realmSnapshot = realmStore.snapshot(),
            )
            val mergedMessages = mergeConcurrentMessages(
                before = beforeMessages,
                synced = result.messages,
                current = messageStore.load(),
            )
            messageStore.save(mergedMessages)
            if (result.updatedRelationships.isNotEmpty()) {
                relationshipStore.save(result.updatedRelationships)
                result.updatedRelationships
                    .filter { relationship ->
                        relationship.realmId == null &&
                            relationship.sourceInviteId != null &&
                            relationship.localIdentityPublicKey == identityStore.load()?.publicKeyEncoded
                    }
                    .forEach { relationship ->
                        issuedInviteStore.markConsumed(
                            inviteId = relationship.sourceInviteId.orEmpty(),
                            consumedByPublicKey = relationship.peerPublicKey,
                        )
                    }
            }
            val newIncomingMessages = MeshNotificationDiff.newIncomingMessages(beforeMessages, mergedMessages)
                .filterNot { message -> relationshipNotificationStore.isMuted(message.relationshipId) }
            notificationInboxStore.addIncoming(newIncomingMessages)
            KrakenMessageNotifier.notifyIncomingMessages(
                context = appContext,
                messages = newIncomingMessages.distinctBy { it.relationshipId }.flatMap { message ->
                    notificationInboxStore.messagesFor(message.relationshipId)
                },
                contactNameFor = { message -> relationshipDisplayName(message.relationshipId) },
            )
            result.copy(messages = mergedMessages, snapshot = withRuntimeState(result.snapshot))
        } finally {
            syncInFlight.set(false)
        }
    }

    fun enqueueHandshakeConfirmation(
        relationship: Relationship,
        confirmationPayloadJson: String,
    ): MeshServiceSnapshot =
        withRuntimeState(
            meshService.enqueueHandshakeConfirmation(
                localIdentity = identityStore.load(),
                relationship = relationship,
                confirmationPayloadJson = confirmationPayloadJson,
            ),
        )

    fun enqueueHandshakeResponse(
        relationship: Relationship,
        responsePayloadJson: String,
    ): MeshServiceSnapshot =
        withRuntimeState(
            meshService.enqueueHandshakeResponse(
                localIdentity = identityStore.load(),
                relationship = relationship,
                responsePayloadJson = responsePayloadJson,
            ),
        )

    private fun withRuntimeState(snapshot: MeshServiceSnapshot): MeshServiceSnapshot =
        snapshot.copy(
            foregroundServiceEnabled = foregroundServiceRunning,
            lastServiceStartedAtEpochMillis = prefs.lastServiceStartedAtEpochMillis.takeIf { it > 0 },
        )

    companion object {
        @Volatile
        private var instance: MeshRuntime? = null

        internal fun mergeConcurrentMessages(
            before: List<LocalMessage>,
            synced: List<LocalMessage>,
            current: List<LocalMessage>,
        ): List<LocalMessage> {
            val beforeIds = before.mapTo(mutableSetOf()) { it.messageId }
            val syncedById = synced.associateBy { it.messageId }.toMutableMap()
            current
                .filter { it.messageId !in beforeIds && it.messageId !in syncedById }
                .forEach { syncedById[it.messageId] = it }
            return MessageService.pruneMessages(syncedById.values.toList())
        }

        fun get(context: Context): MeshRuntime =
            instance ?: synchronized(this) {
                instance ?: MeshRuntime(context).also { instance = it }
            }
    }
}

data class MeshDebugTransportRelationshipAlignment(
    val identityFingerprint: String?,
    val identityFingerprintPrefix: String?,
    val sendableRelationshipCount: Int,
    val firstSendableRelationshipId: String?,
    val firstSendableRelationshipFingerprint: String?,
    val firstSendableRelationshipFingerprintPrefix: String?,
    val firstSendableRelationshipCryptoProfileId: String?,
    val firstSendableRelationshipSessionProfileId: String?,
    val firstSendableRelationshipAdmissionDecisionHash: String?,
    val firstSendableRelationshipProfilePolicyVersion: Int?,
    val observedPeerFingerprints: List<String>,
    val observedPeerFingerprintPrefixes: List<String>,
    val relationshipPeerSeenByTransport: Boolean,
)
