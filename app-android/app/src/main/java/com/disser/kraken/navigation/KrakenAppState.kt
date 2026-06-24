package com.disser.kraken.navigation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.disser.kraken.BuildConfig
import com.disser.kraken.channel.ChannelMembership
import com.disser.kraken.channel.ChannelService
import com.disser.kraken.channel.ChannelSnapshot
import com.disser.kraken.channel.ChannelStore
import com.disser.kraken.demo.DemoDataSeeder
import com.disser.kraken.group.SmallGroupService
import com.disser.kraken.group.SmallGroupSnapshot
import com.disser.kraken.group.SmallGroupStore
import com.disser.kraken.handshake.HandshakePayloadCodec
import com.disser.kraken.handshake.HandshakePayloadKind
import com.disser.kraken.handshake.HandshakeResponsePayload
import com.disser.kraken.handshake.KnownInviteLifecycle
import com.disser.kraken.handshake.OfflineHandshakeResult
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.IdentityStore
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.identity.SecureRandomPlaceholderIdentityKeyProvider
import com.disser.kraken.invite.InviteImportResult
import com.disser.kraken.invite.InviteImportService
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.InvitePayloadFactory
import com.disser.kraken.invite.InviteScope
import com.disser.kraken.invite.IssuedInviteLifecyclePolicy
import com.disser.kraken.invite.IssuedInviteRecord
import com.disser.kraken.invite.IssuedInviteStore
import com.disser.kraken.invite.isUsableAt
import com.disser.kraken.invite.OneTimeInvitePayload
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.invite.PendingInviteState
import com.disser.kraken.invite.PendingInviteStore
import com.disser.kraken.message.ChatPreferencesStore
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.SavedMessage
import com.disser.kraken.message.SavedMessageStore
import com.disser.kraken.mesh.MeshForegroundService
import com.disser.kraken.mesh.MeshRuntime
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.mesh.MeshTransportSelection
import com.disser.kraken.mesh.LanEndpointPayloadCodec
import com.disser.kraken.qr.KrakenQrPayloadCodec
import com.disser.kraken.qr.QrScanImportResult
import com.disser.kraken.qr.QrScanImportService
import com.disser.kraken.realm.ApprovalOutcome
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmManagementPolicy
import com.disser.kraken.realm.RealmService
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.realm.RealmStore
import com.disser.kraken.relationship.ComplaintEvent
import com.disser.kraken.relationship.ComplaintStore
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipNotificationStore
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.relationship.RelationshipStore
import com.disser.kraken.relay.RelayMode
import com.disser.kraken.relay.RelayPolicyState
import com.disser.kraken.relay.RelayPolicyStore
import com.disser.kraken.ui.theme.KrakenThemePreset
import com.disser.kraken.ui.theme.ThemePresetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class KrakenAppState(private val context: Context) {
    private val identityStore = IdentityStore(context)
    private val issuedInviteStore = IssuedInviteStore(context)
    private val pendingInviteStore = PendingInviteStore(context)
    private val relationshipStore = RelationshipStore(context)
    private val relationshipNotificationStore = RelationshipNotificationStore(context)
    private val complaintStore = ComplaintStore(context)
    private val realmStore = RealmStore(context)
    private val relayPolicyStore = RelayPolicyStore(context)
    private val channelStore = ChannelStore(context)
    private val smallGroupStore = SmallGroupStore(context)
    private val themePresetStore = ThemePresetStore(context)
    private val chatPreferencesStore = ChatPreferencesStore(context)
    private val savedMessageStore = SavedMessageStore(context)
    private val meshRuntime = MeshRuntime.get(context)
    private val inviteImportService = InviteImportService()
    private val qrScanImportService = QrScanImportService(inviteImportService)
    private val offlineHandshakeService = OfflineHandshakeService()

    var localIdentity: LocalIdentity? by mutableStateOf(identityStore.load())
        private set
    var issuedInvites: List<IssuedInviteRecord> by mutableStateOf(issuedInviteStore.load())
        private set
    var pendingInvites: List<PendingInviteImport> by mutableStateOf(pendingInviteStore.load())
        private set
    var relationships: List<Relationship> by mutableStateOf(relationshipStore.load())
        private set
    var complaints: List<ComplaintEvent> by mutableStateOf(complaintStore.load())
        private set
    var realmSnapshot: RealmSnapshot by mutableStateOf(realmStore.snapshot())
        private set
    var relayPolicyState: RelayPolicyState by mutableStateOf(relayPolicyStore.load())
        private set
    var channelSnapshot: ChannelSnapshot by mutableStateOf(channelStore.snapshot())
        private set
    var smallGroupSnapshot: SmallGroupSnapshot by mutableStateOf(smallGroupStore.snapshot())
        private set
    var themePreset: KrakenThemePreset by mutableStateOf(themePresetStore.load())
        private set
    var quickReaction: String by mutableStateOf(chatPreferencesStore.loadQuickReaction())
        private set
    var globalChatBackground: String by mutableStateOf(chatPreferencesStore.loadGlobalBackground())
        private set
    var chatBackgroundOverrides: Map<String, String> by mutableStateOf(
        relationshipStore.load()
            .mapNotNull { relationship ->
                chatPreferencesStore.loadRelationshipBackground(relationship.relationshipId)
                    ?.let { relationship.relationshipId to it }
            }
            .toMap(),
    )
        private set
    var messages: List<LocalMessage> by mutableStateOf(meshRuntime.loadMessages())
        private set
    var savedMessages: List<SavedMessage> by mutableStateOf(savedMessageStore.load())
        private set
    var meshSnapshot: MeshServiceSnapshot by mutableStateOf(meshRuntime.snapshot())
        private set
    var latestHandshakeCompletion: Relationship? by mutableStateOf(null)
        private set
    var meshTransportProfile: String by mutableStateOf(meshRuntime.prefs.transportProfile)
        private set
    var selectedRealmId: String? by mutableStateOf(null)
        private set
    var selectedChatRelationshipId: String? by mutableStateOf(null)
        private set
    var mutedRelationshipIds: Set<String> by mutableStateOf(relationshipNotificationStore.loadMutedRelationshipIds())
        private set
    private val announcedHandshakeCompletionIds = relationships
        .filter { it.state == RelationshipState.ACTIVE && it.sourceInviteId != null }
        .mapTo(mutableSetOf()) { it.relationshipId }

    fun loadDemoData() {
        val demo = DemoDataSeeder.seed(
            identityStore = identityStore,
            pendingInviteStore = pendingInviteStore,
            relationshipStore = relationshipStore,
            complaintStore = complaintStore,
            realmStore = realmStore,
            channelStore = channelStore,
            smallGroupStore = smallGroupStore,
            currentIdentity = localIdentity,
        )
        localIdentity = demo.identity
        pendingInvites = demo.pendingInvites
        relationships = demo.relationships
        complaints = demo.complaints
        realmSnapshot = demo.realmSnapshot
        channelSnapshot = demo.channelSnapshot
        smallGroupSnapshot = demo.smallGroupSnapshot
    }

    fun resetDemoData() {
        val reset = DemoDataSeeder.reset(
            pendingInviteStore = pendingInviteStore,
            relationshipStore = relationshipStore,
            complaintStore = complaintStore,
            realmStore = realmStore,
            channelStore = channelStore,
            smallGroupStore = smallGroupStore,
        )
        pendingInvites = reset.pendingInvites
        issuedInvites = issuedInviteStore.clear()
        relationships = reset.relationships
        complaints = reset.complaints
        realmSnapshot = reset.realmSnapshot
        channelSnapshot = reset.channelSnapshot
        smallGroupSnapshot = reset.smallGroupSnapshot
    }

    fun createIdentity(displayName: String) {
        localIdentity = identityStore.create(
            displayName = displayName,
            keyProvider = SecureRandomPlaceholderIdentityKeyProvider(),
        )
    }

    fun currentDirectInviteRecord(): IssuedInviteRecord? {
        val identity = localIdentity ?: return null
        return issuedInvites
            .filter {
                it.scope == InviteScope.DIRECT_CONTACT &&
                    it.realmId == null &&
                    it.inviterFingerprint == identity.fingerprint
            }
            .maxByOrNull { it.createdAtEpochMillis }
    }

    fun currentDirectInvitePayload(): OneTimeInvitePayload? =
        currentDirectInviteRecord()?.payload

    fun createDirectInvite(): OneTimeInvitePayload? {
        val identity = localIdentity ?: return null
        val payload = InvitePayloadFactory.create(identity)
        issuedInvites = issuedInviteStore.add(payload)
        return payload
    }

    fun importPendingInvite(pendingImport: PendingInviteImport) {
        val handshakePendingImport = pendingImport.copy(state = PendingInviteState.PENDING_HANDSHAKE)
        pendingInvites = pendingInviteStore.add(handshakePendingImport)
        localIdentity?.let { identity ->
            val relationship = RelationshipService.startHandshake(
                RelationshipService.createFromPendingInvite(identity, handshakePendingImport),
            )
            relationships = relationshipStore.add(relationship)
            meshRuntime.prefs.meshEnabled = true
            MeshForegroundService.startMesh(context)
            meshSnapshot = meshRuntime.start()
            offlineHandshakeService.generateResponsePayload(identity, relationship)
                .onSuccess { responsePayload ->
                    meshSnapshot = meshRuntime.enqueueHandshakeResponse(
                        relationship = relationship,
                        responsePayloadJson = HandshakePayloadCodec.encodeResponse(responsePayload),
                    )
                    MeshForegroundService.syncNow(context)
                }
                .onFailure { error ->
                    meshSnapshot = meshRuntime.snapshot().copy(
                        lastPacketStatus = "handshake-response-queued-failed:${error.message ?: "unknown"}",
                    )
                }
        } ?: ensureMeshStarted()
    }

    fun importInviteJson(rawJson: String): InviteImportResult {
        val payload = InvitePayloadCodec.decode(rawJson).getOrNull()
        return when (
            val result = inviteImportService.import(
                rawJson = rawJson,
                localIdentity = localIdentity,
                existingImports = payload?.let(::pendingInvitesForFreshInvitePayload) ?: pendingInvites,
            )
        ) {
            is InviteImportResult.Error -> result
            is InviteImportResult.Success -> {
                importPendingInvite(result.pendingImport)
                result
            }
        }
    }

    fun importScannedInvite(scannedText: String): QrScanImportResult {
        val payload = scannedInvitePayload(scannedText)
        payload?.let(::existingRelationshipForInvite)?.let { relationship ->
            return QrScanImportResult.KnownContact(relationship)
        }
        return when (
            val result = qrScanImportService.importScannedText(
                scannedText = scannedText,
                localIdentity = localIdentity,
                existingImports = payload?.let(::pendingInvitesForFreshInvitePayload) ?: pendingInvites,
            )
        ) {
            is QrScanImportResult.KnownContact -> result
            is QrScanImportResult.Error -> result
            is QrScanImportResult.Success -> {
                importPendingInvite(result.pendingImport)
                result
            }
            is QrScanImportResult.HandshakeResponseAccepted,
            is QrScanImportResult.HandshakeConfirmationAccepted,
            is QrScanImportResult.LanEndpointAccepted -> QrScanImportResult.Error("Ожидалось QR-приглашение, но получен другой payload Kraken.")
        }
    }

    private fun scannedInvitePayload(scannedText: String): OneTimeInvitePayload? {
        val rawJson = KrakenQrPayloadCodec.normalizeScannedText(scannedText).getOrNull() ?: return null
        return InvitePayloadCodec.decode(rawJson).getOrNull()
    }

    fun processScannedQrPayload(scannedText: String): QrScanImportResult {
        val rawJson = KrakenQrPayloadCodec.normalizeScannedText(scannedText).getOrElse {
            return QrScanImportResult.Error(QrScanImportService.INVALID_QR_MESSAGE)
        }
        return when (HandshakePayloadCodec.detectKind(rawJson)) {
            HandshakePayloadKind.INVITE -> importScannedInvite(rawJson)
            HandshakePayloadKind.RESPONSE -> processHandshakeResponse(rawJson)
            HandshakePayloadKind.CONFIRMATION -> processHandshakeConfirmation(rawJson)
            HandshakePayloadKind.UNKNOWN,
            HandshakePayloadKind.INVALID -> processLanEndpointPayload(rawJson)
                ?: QrScanImportResult.Error("Этот QR не является корректным payload рукопожатия Kraken.")
        }
    }

    private fun existingRelationshipForInvite(payload: OneTimeInvitePayload): Relationship? {
        val identity = localIdentity ?: return null
        return relationships.knownRelationshipForInvite(identity, payload)
    }

    private fun pendingInvitesForFreshInvitePayload(payload: OneTimeInvitePayload): List<PendingInviteImport> {
        val current = pendingInviteStore.load()
        val reconciled = reconcilePendingInvitesForFreshInviteScan(
            existingImports = current,
            relationships = relationships,
            localIdentity = localIdentity,
            payload = payload,
        )
        if (reconciled != current) {
            pendingInviteStore.save(reconciled)
            pendingInvites = reconciled
        }
        return reconciled
    }

    private fun processLanEndpointPayload(rawJson: String): QrScanImportResult? {
        val payload = LanEndpointPayloadCodec.decode(rawJson).getOrNull() ?: return null
        val identity = localIdentity ?: return QrScanImportResult.Error("Создайте локальную личность перед добавлением LAN-конечной точки.")
        if (payload.fingerprint == identity.fingerprint) {
            return QrScanImportResult.Error("Нельзя добавить собственную LAN-конечную точку.")
        }
        if (!addManualLanPeer(payload.fingerprint, payload.host, payload.port.toString())) {
            return QrScanImportResult.Error("QR-адрес связи не добавлен: ${meshSnapshot.lastPacketStatus}.")
        }
        return QrScanImportResult.LanEndpointAccepted(
            fingerprint = payload.fingerprint,
            host = payload.host,
            port = payload.port,
            displayName = payload.displayName,
        )
    }

    private fun processHandshakeResponse(rawJson: String): QrScanImportResult {
        val identity = localIdentity ?: return QrScanImportResult.Error("Создайте локальную личность перед сканом ответного QR.")
        val payload = HandshakePayloadCodec.decodeResponse(rawJson).getOrElse { error ->
            return QrScanImportResult.Error(error.message ?: "Некорректный ответный QR рукопожатия.")
        }
        val lifecycle = responseLifecycleOrError(payload).getOrElse { reason ->
            completedRelationshipForResponse(payload)?.let { relationship ->
                val confirmationJson = offlineHandshakeService
                    .generateConfirmationPayload(identity, relationship)
                    .map { HandshakePayloadCodec.encodeConfirmation(it) }
                    .getOrDefault("{}")
                return QrScanImportResult.HandshakeResponseAccepted(
                    relationship = relationship,
                    confirmationPayloadJson = confirmationJson,
                )
            }
            return QrScanImportResult.Error(reason.message ?: "Ответный QR не прошёл локальную проверку invite.")
        }
        return when (
            val result = offlineHandshakeService.processResponsePayload(
                localIdentity = identity,
                relationships = relationships,
                payload = payload,
                knownInviteLifecycle = lifecycle,
            )
        ) {
            is OfflineHandshakeResult.Error -> QrScanImportResult.Error(result.reason)
            is OfflineHandshakeResult.ConfirmationAccepted -> QrScanImportResult.Error("Ожидался ответ рукопожатия, но получено финальное подтверждение.")
            is OfflineHandshakeResult.ResponseAccepted -> {
                upsertRelationship(result.relationship)
                if (!payload.requiresApproval && payload.realmId == null) {
                    issuedInvites = issuedInviteStore.markConsumed(
                        inviteId = payload.inviteId,
                        consumedByPublicKey = payload.responderPublicKeyEncoded,
                    )
                }
                if (payload.requiresApproval && createRealmMembershipRequestFromResponse(payload)) {
                    issuedInvites = issuedInviteStore.markConsumed(
                        inviteId = payload.inviteId,
                        consumedByPublicKey = payload.responderPublicKeyEncoded,
                    )
                }
                QrScanImportResult.HandshakeResponseAccepted(
                    relationship = result.relationship,
                    confirmationPayloadJson = HandshakePayloadCodec.encodeConfirmation(result.confirmationPayload),
                )
            }
        }
    }

    private fun responseLifecycleOrError(payload: HandshakeResponsePayload): Result<KnownInviteLifecycle> =
        IssuedInviteLifecyclePolicy.knownLifecycleForResponse(
            payload = payload,
            issuedInvites = issuedInvites,
            localFingerprint = localIdentity?.fingerprint.orEmpty(),
        )

    private fun completedRelationshipForResponse(payload: HandshakeResponsePayload): Relationship? {
        val identity = localIdentity ?: return null
        return relationships.firstOrNull { relationship ->
            relationship.state == RelationshipState.ACTIVE &&
                relationship.sourceInviteId == payload.inviteId &&
                relationship.localIdentityPublicKey == identity.publicKeyEncoded &&
                relationship.peerFingerprint == payload.responderFingerprint &&
                relationship.peerPublicKey == payload.responderPublicKeyEncoded
        }
    }

    private fun createRealmMembershipRequestFromResponse(payload: HandshakeResponsePayload): Boolean {
        if (!payload.requiresApproval) return false
        val identity = localIdentity ?: return false
        val realmId = payload.realmId ?: return false
        val issuedInvite = issuedInvites.firstOrNull {
            it.inviteId == payload.inviteId &&
                it.scope == InviteScope.REALM_MEMBERSHIP &&
                it.realmId == realmId &&
                it.inviterFingerprint == identity.fingerprint
        } ?: return false
        if (!issuedInvite.isUsableAt(System.currentTimeMillis())) return false
        val realm = realmSnapshot.realms.firstOrNull { it.realmId == realmId } ?: return false
        val alreadyKnown = realmSnapshot.pendingRequests.any {
            it.realmId == realmId &&
                it.inviteId == payload.inviteId &&
                it.inviteePublicKey == payload.responderPublicKeyEncoded
        } || realmSnapshot.membershipCertificates.any {
            it.realmId == realmId && it.memberPublicKey == payload.responderPublicKeyEncoded
        }
        if (alreadyKnown) return true
        realmSnapshot = realmStore.addPendingRequest(
            RealmService.createPendingMembershipRequest(
                realm = realm,
                inviteId = payload.inviteId,
                inviterPublicKey = identity.publicKeyEncoded,
                inviteePublicKey = payload.responderPublicKeyEncoded,
                inviteeDisplayName = payload.responderDisplayName,
                nowEpochMillis = payload.createdAtEpochMillis,
            )
        )
        return true
    }

    private fun processHandshakeConfirmation(rawJson: String): QrScanImportResult {
        val identity = localIdentity ?: return QrScanImportResult.Error("Создайте локальную личность перед сканом финального QR.")
        val payload = HandshakePayloadCodec.decodeConfirmation(rawJson).getOrElse { error ->
            return QrScanImportResult.Error(error.message ?: "Некорректный финальный QR рукопожатия.")
        }
        return when (
            val result = offlineHandshakeService.processConfirmationPayload(
                localIdentity = identity,
                relationships = relationships,
                payload = payload,
            )
        ) {
            is OfflineHandshakeResult.Error -> QrScanImportResult.Error(result.reason)
            is OfflineHandshakeResult.ResponseAccepted -> QrScanImportResult.Error("Ожидалось финальное подтверждение, но получен ответ рукопожатия.")
            is OfflineHandshakeResult.ConfirmationAccepted -> {
                upsertRelationship(result.relationship)
                applyRealmMembershipConfirmation(payload, result.relationship)
                QrScanImportResult.HandshakeConfirmationAccepted(
                    relationship = result.relationship,
                    realmMembershipApplied = payload.membershipCertificate != null,
                )
            }
        }
    }

    private fun applyRealmMembershipConfirmation(
        payload: com.disser.kraken.handshake.HandshakeConfirmationPayload,
        relationship: Relationship,
    ) {
        val certificate = payload.membershipCertificate ?: return
        realmSnapshot = realmStore.saveSnapshot(
            RealmService.applyMembershipConfirmation(
                snapshot = realmSnapshot,
                realmName = payload.realmName,
                inviteId = payload.inviteId,
                inviterPublicKey = relationship.peerPublicKey,
                certificate = certificate,
            )
        )
    }

    private fun upsertRelationship(relationship: Relationship) {
        val current = relationshipStore.load()
        val updated = if (current.any { it.isSameRelationshipRecord(relationship) }) {
            current.map { if (it.isSameRelationshipRecord(relationship)) relationship else it }
        } else {
            current + relationship
        }
        relationshipStore.save(updated)
        relationships = updated
    }

    fun updateRelationship(relationship: Relationship) {
        val updated = relationshipStore.update(relationship)
        relationships = updated
        if (selectedChatRelationshipId == relationship.relationshipId && relationship.state != RelationshipState.ACTIVE) {
            selectedChatRelationshipId = updated.firstOrNull { it.state == RelationshipState.ACTIVE }?.relationshipId
        }
    }

    fun cancelRelationshipPairing(relationship: Relationship) {
        removeRelationshipLocally(relationship, "pairing-cancelled-locally")
    }

    fun forgetRelationship(relationship: Relationship) {
        removeRelationshipLocally(relationship, "relationship-forgotten-locally")
    }

    private fun removeRelationshipLocally(relationship: Relationship, packetStatus: String) {
        relationships = relationshipStore.load()
            .filterNot { candidate -> candidate.belongsToSameLocalPairingBundle(relationship) }
            .also(relationshipStore::save)
        pendingInvites = pendingInviteStore.load()
            .filterNot { pending ->
                pending.inviteId == relationship.sourceInviteId ||
                    pending.inviterPublicKeyEncoded == relationship.peerPublicKey ||
                    pending.inviterFingerprint == relationship.peerFingerprint
            }
            .also(pendingInviteStore::save)
        issuedInvites = issuedInviteStore.load()
        if (latestHandshakeCompletion?.belongsToSameLocalPairingBundle(relationship) == true) {
            latestHandshakeCompletion = null
        }
        if (selectedChatRelationshipId == relationship.relationshipId) {
            selectedChatRelationshipId = relationships.firstOrNull { it.state == RelationshipState.ACTIVE }?.relationshipId
        }
        meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = packetStatus)
    }

    fun selectChatRelationship(relationship: Relationship) {
        selectChatRelationshipId(relationship.relationshipId)
    }

    fun selectChatRelationshipId(relationshipId: String) {
        selectedChatRelationshipId = relationshipId
        markRelationshipNotificationsRead(relationshipId)
    }

    fun clearChatSelection() {
        selectedChatRelationshipId = null
    }

    fun sendLocalMessage(relationship: Relationship, body: String, replyToMessage: LocalMessage? = null) {
        val identity = localIdentity ?: return
        val realmDecision = RealmCommunicationPolicy.canUseRelationship(identity, relationship, realmSnapshot)
        if (!realmDecision.allowed) {
            meshSnapshot = meshRuntime.snapshot().copy(
                lastPacketStatus = "send-blocked:${realmDecision.blockReason?.name ?: "unknown"}",
            )
            return
        }
        ensureMeshStarted()
        val message = MessageService.createOutgoingMessage(
            localIdentity = identity,
            relationship = relationship,
            body = body,
            replyToMessage = replyToMessage,
        )
        messages = meshRuntime.addOutgoingMessage(message)
        MeshForegroundService.syncNow(context)
        meshSnapshot = meshRuntime.snapshot()
    }

    fun upsertMessage(message: LocalMessage) {
        messages = meshRuntime.upsertMessage(message)
        meshSnapshot = meshRuntime.snapshot()
    }

    fun retryMessage(messageId: String) {
        messages = meshRuntime.requeueMessage(messageId)
        ensureMeshStarted()
        MeshForegroundService.syncNow(context)
        meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = "message-retry-requested")
    }

    fun deleteMessage(messageId: String) {
        messages = meshRuntime.deleteMessage(messageId)
        meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = "message-deleted-locally")
    }

    fun deleteMessages(messageIds: Set<String>) {
        messageIds.forEach { messageId ->
            messages = meshRuntime.deleteMessage(messageId)
        }
        meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = "messages-deleted-locally:${messageIds.size}")
    }

    fun saveMessagesToFavorites(selectedMessages: List<LocalMessage>) {
        val now = System.currentTimeMillis()
        val relationshipNames = relationships.associate { relationship ->
            relationship.relationshipId to (relationship.peerDisplayName ?: relationship.peerFingerprint.take(12))
        }
        savedMessages = savedMessageStore.add(
            selectedMessages.map { message ->
                SavedMessage(
                    savedMessageId = "saved-${message.messageId}",
                    sourceMessageId = message.messageId,
                    sourceRelationshipId = message.relationshipId,
                    sourceDisplayName = relationshipNames[message.relationshipId] ?: message.peerFingerprint.take(12),
                    body = message.body,
                    originalCreatedAtEpochMillis = message.createdAtEpochMillis,
                    savedAtEpochMillis = now,
                )
            },
        )
    }

    fun clearConversation(relationship: Relationship) {
        messages = meshRuntime.clearConversation(MessageService.conversationIdFor(relationship))
        meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = "conversation-cleared-locally")
    }

    fun markRelationshipNotificationsRead(relationshipId: String) {
        meshRuntime.markRelationshipNotificationsRead(relationshipId)
    }

    fun setRelationshipMuted(relationship: Relationship, muted: Boolean) {
        mutedRelationshipIds = relationshipNotificationStore.setMuted(relationship.relationshipId, muted)
    }

    fun startMesh() {
        if (meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)) return
        startMeshWithTransportProfile(
            MeshTransportSelection.PROFILE_HOTSPOT_COMPATIBLE,
            "service-start-requested:hotspot-compatible",
        )
    }

    fun startHotspotCompatibleMesh() {
        startMeshWithTransportProfile(
            MeshTransportSelection.PROFILE_HOTSPOT_COMPATIBLE,
            "service-start-requested:hotspot-compatible",
        )
    }

    fun startWifiDirectTrialMesh() {
        startMeshWithTransportProfile(
            MeshTransportSelection.PROFILE_WIFI_DIRECT_ONLY,
            "service-start-requested:wifi-direct-trial",
        )
    }

    private fun startMeshWithTransportProfile(profile: String, lastPacketStatus: String) {
        meshRuntime.prefs.transportProfile = profile
        meshTransportProfile = profile
        meshRuntime.prefs.meshEnabled = true
        meshRuntime.prefs.lastServiceStartedAtEpochMillis = System.currentTimeMillis()
        startForegroundMeshServiceForProfile(profile)
        meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = lastPacketStatus)
    }

    fun refreshMeshSnapshot() {
        messages = meshRuntime.loadMessages()
        updateRelationshipsFromStore()
        meshTransportProfile = meshRuntime.prefs.transportProfile
        meshSnapshot = meshRuntime.snapshot()
    }

    fun consumeHandshakeCompletion(relationshipId: String) {
        if (latestHandshakeCompletion?.relationshipId == relationshipId) {
            latestHandshakeCompletion = null
        }
    }

    fun restartMeshAfterPermissionChange() {
        if (localIdentity != null && meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)) {
            meshRuntime.prefs.meshEnabled = true
            meshRuntime.prefs.transportProfile = meshTransportProfile
            meshRuntime.prefs.lastServiceStartedAtEpochMillis = System.currentTimeMillis()
            startForegroundMeshServiceForProfile(meshTransportProfile)
            meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = "service-start-requested")
        } else {
            refreshMeshSnapshot()
        }
    }

    fun ensureMeshStarted() {
        if (localIdentity != null && meshSnapshot.state in setOf(MeshState.OFF, MeshState.ERROR)) {
            startMesh()
        }
    }

    fun stopMesh() {
        meshRuntime.prefs.meshEnabled = false
        MeshForegroundService.stopMesh(context)
        meshRuntime.markForegroundServiceStopped()
        meshSnapshot = meshRuntime.stop()
        meshTransportProfile = meshRuntime.prefs.transportProfile
    }

    private fun startForegroundMeshServiceForProfile(profile: String) {
        if (profile == MeshTransportSelection.PROFILE_WIFI_DIRECT_ONLY) {
            MeshForegroundService.startDebugWifiDirectOnly(context)
        } else {
            MeshForegroundService.startMesh(context)
        }
    }

    fun addManualLanPeer(fingerprint: String, host: String, portText: String): Boolean {
        ensureMeshStarted()
        val port = portText.trim().toIntOrNull()
        if (port == null) {
            meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = "manual-peer-failed:invalid-port")
            return false
        }
        meshSnapshot = meshRuntime.addManualLanPeer(
            fingerprint = fingerprint,
            host = host,
            port = port,
        )
        return meshSnapshot.lastPacketStatus == "manual-peer-added"
    }

    suspend fun runDebugRouteSpecificEvidenceProbe(): String {
        if (!BuildConfig.DEBUG) {
            meshSnapshot = meshRuntime.snapshot().copy(lastPacketStatus = "debug-evidence-disabled")
            return "debug evidence disabled for non-debug build"
        }
        val relationship = relationships.firstOrNull { RelationshipService.canSendMessage(it) }
            ?: return "no active relationship for queue retry probe"

        meshSnapshot = meshRuntime.recordDebugNegativeEvidence()

        MeshForegroundService.stopMesh(context)
        meshRuntime.markForegroundServiceStopped()
        meshSnapshot = meshRuntime.stop()

        val body = "queueRetry${System.currentTimeMillis().toString().takeLast(6)}"
        messages = meshRuntime.addOutgoingTextMessage(relationship.relationshipId, body)
        val queuedSnapshot = meshRuntime.snapshot()
        val queuedMessage = messages.lastOrNull { it.body == body }
            ?: return "queue retry message was not created"

        meshRuntime.prefs.meshEnabled = true
        meshRuntime.prefs.lastServiceStartedAtEpochMillis = System.currentTimeMillis()
        MeshForegroundService.startMesh(context)
        meshRuntime.start()
        delay(3_000)
        var sentAfterRestart = false
        var deliveredAfterRestart = false
        var latestMessages = messages
        repeat(8) { attempt ->
            val result = withContext(Dispatchers.IO) { meshRuntime.syncNow() }
            latestMessages = result.messages
            val currentMessage = latestMessages.firstOrNull { it.messageId == queuedMessage.messageId }
            sentAfterRestart = sentAfterRestart ||
                result.sentCount > 0 ||
                currentMessage?.status?.name in setOf("SENT_TO_TRANSPORT", "DELIVERED_TO_PEER")
            deliveredAfterRestart = deliveredAfterRestart || currentMessage?.status?.name == "DELIVERED_TO_PEER"
            if (deliveredAfterRestart) {
                return@repeat
            }
            if (attempt < 7) delay(2_000)
        }
        messages = latestMessages

        val afterMessage = messages.firstOrNull { it.messageId == queuedMessage.messageId }
        val afterSnapshot = meshRuntime.snapshot()
        meshSnapshot = meshRuntime.recordDebugQueueRetryEvidence(
            queueRetryMessageId = queuedMessage.messageId,
            queueRetryBody = body,
            queuedBeforeTransportRestart = queuedSnapshot.queuedPackets > 0,
            queueSizeBeforeRestart = queuedSnapshot.queuedPackets,
            sentAfterTransportRestart = sentAfterRestart,
            deliveredAfterTransportRestart = deliveredAfterRestart,
            queueSizeAfterRestart = afterSnapshot.queuedPackets,
            messageStatusAfterRestart = afterMessage?.status?.name,
        )
        return "debug evidence: local hostile packets rejected, queue retry body=$body"
    }

    suspend fun syncMeshNow() {
        val result = withContext(Dispatchers.IO) { meshRuntime.syncNow() }
        messages = result.messages
        updateRelationshipsFromStore()
        meshSnapshot = result.snapshot
        selectedChatRelationshipId?.let { relationshipId ->
            markRelationshipNotificationsRead(relationshipId)
        }
    }

    suspend fun syncMeshIfRunning() {
        if (!meshRuntime.prefs.meshEnabled) {
            refreshMeshSnapshot()
            return
        }
        if (localIdentity != null && meshSnapshot.state == MeshState.OFF) {
            MeshForegroundService.startMesh(context)
        }
        if (meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)) {
            syncMeshNow()
        } else {
            refreshMeshSnapshot()
        }
    }

    fun addComplaint(complaint: ComplaintEvent) {
        complaints = complaintStore.add(complaint)
    }

    fun createRealm(name: String) {
        localIdentity?.let { identity ->
            realmSnapshot = realmStore.addRealmCreation(RealmService.createRealm(identity, name))
        }
    }

    fun createRealmInvite(realm: Realm): OneTimeInvitePayload? {
        val identity = localIdentity ?: return null
        val certificate = localRealmCertificate(realm) ?: return null
        val role = RealmManagementPolicy.roleFor(realm, certificate, identity)
        if (role !in setOf(
                com.disser.kraken.realm.RealmManagementRole.OWNER,
                com.disser.kraken.realm.RealmManagementRole.ADMIN,
            ) ||
            !RealmService.canCreateInvite(realm.capacityState)
        ) {
            return null
        }
        val payload = InvitePayloadFactory.createRealmInvite(
            identity = identity,
            realm = realm,
            certificate = certificate,
        )
        issuedInvites = issuedInviteStore.add(payload)
        return payload
    }

    fun revokeIssuedInvite(inviteId: String) {
        issuedInvites = issuedInviteStore.revoke(inviteId)
    }

    fun updateRealm(realm: Realm) {
        realmSnapshot = realmStore.updateRealm(realm)
    }

    fun selectRealm(realmId: String) {
        selectedRealmId = realmId
    }

    fun deleteLocalRealmRecord(realm: Realm) {
        realmSnapshot = realmStore.deleteLocalRealmRecord(realm.realmId)
        if (selectedRealmId == realm.realmId) {
            selectedRealmId = null
        }
    }

    fun createDemoPendingRequest(realm: Realm) {
        localIdentity?.let { identity ->
            realmSnapshot = realmStore.addPendingRequest(
                RealmService.createDemoPendingRequest(
                    realm = realm,
                    inviterPublicKey = identity.publicKeyEncoded,
                )
            )
        }
    }

    fun applyApprovalOutcome(outcome: ApprovalOutcome) {
        realmSnapshot = realmStore.applyApprovalOutcome(outcome)
        if (outcome.membershipCertificate != null) {
            issuedInvites = issuedInviteStore.markConsumed(
                inviteId = outcome.request.inviteId,
                consumedByPublicKey = outcome.request.inviteePublicKey,
            )
        }
    }

    fun promoteRealmMember(certificate: MembershipCertificate) {
        updateRealmMember(certificate) { realm, actorRole, target ->
            RealmService.promoteToAdmin(realm, actorRole, target)
        }
    }

    fun demoteRealmMember(certificate: MembershipCertificate) {
        updateRealmMember(certificate) { realm, actorRole, target ->
            RealmService.demoteToMember(realm, actorRole, target)
        }
    }

    fun restrictRealmMember(certificate: MembershipCertificate) {
        updateRealmMember(certificate) { realm, actorRole, target ->
            RealmService.restrictMember(realm, actorRole, target)
        }
    }

    fun restoreRealmMember(certificate: MembershipCertificate) {
        updateRealmMember(certificate) { realm, actorRole, target ->
            RealmService.restoreMember(realm, actorRole, target)
        }
    }

    fun removeRealmMember(certificate: MembershipCertificate) {
        val realm = realmSnapshot.realms.firstOrNull { it.realmId == certificate.realmId } ?: return
        val actorRole = realmActorRole(realm)
        if (RealmManagementPolicy.canRemoveMember(actorRole, realm, certificate)) {
            realmSnapshot = realmStore.removeMembershipCertificate(certificate.realmId, certificate.membershipId)
        }
    }

    private fun updateRealmMember(
        certificate: MembershipCertificate,
        transform: (Realm, com.disser.kraken.realm.RealmManagementRole, MembershipCertificate) -> MembershipCertificate,
    ) {
        val realm = realmSnapshot.realms.firstOrNull { it.realmId == certificate.realmId } ?: return
        val actorRole = realmActorRole(realm)
        val updated = transform(realm, actorRole, certificate)
        if (updated != certificate) {
            realmSnapshot = realmStore.updateMembershipCertificate(updated)
        }
    }

    private fun realmActorRole(realm: Realm): com.disser.kraken.realm.RealmManagementRole {
        val identity = localIdentity
        val localCertificate = localRealmCertificate(realm)
        return RealmManagementPolicy.roleFor(realm, localCertificate, identity)
    }

    private fun localRealmCertificate(realm: Realm): MembershipCertificate? {
        val identity = localIdentity ?: return null
        return realmSnapshot.membershipCertificates.firstOrNull {
            it.realmId == realm.realmId && it.memberPublicKey == identity.publicKeyEncoded
        }
    }

    fun createDemoChannel() {
        val identity = localIdentity
        val realm = realmSnapshot.realms.firstOrNull()
        if (identity != null && realm != null) {
            channelSnapshot = channelStore.addDemoChannel(ChannelService.createDemoChannel(realm, identity))
        }
    }

    fun updateChannelMembership(membership: ChannelMembership) {
        channelSnapshot = channelStore.updateMembership(membership)
    }

    fun createDemoSmallGroup() {
        val identity = localIdentity
        val realm = realmSnapshot.realms.firstOrNull()
        if (identity != null && realm != null) {
            smallGroupSnapshot = smallGroupStore.addDemoGroup(SmallGroupService.createDemoGroup(realm, identity))
        }
    }

    fun updateDisplayName(displayName: String) {
        localIdentity = localIdentity?.let { identity ->
            identityStore.updateDisplayName(identity, displayName)
        }
    }

    fun selectRelayMode(mode: RelayMode) {
        relayPolicyState = relayPolicyStore.selectMode(mode)
    }

    fun selectThemePreset(preset: KrakenThemePreset) {
        themePreset = themePresetStore.save(preset)
    }

    fun selectQuickReaction(reaction: String) {
        quickReaction = chatPreferencesStore.saveQuickReaction(reaction)
    }

    fun selectGlobalChatBackground(backgroundKey: String) {
        globalChatBackground = chatPreferencesStore.saveGlobalBackground(backgroundKey)
    }

    fun chatBackgroundFor(relationshipId: String?): String =
        relationshipId?.let { chatBackgroundOverrides[it] } ?: globalChatBackground

    fun selectChatBackgroundOverride(relationship: Relationship, backgroundKey: String?) {
        val saved = chatPreferencesStore.saveRelationshipBackground(relationship.relationshipId, backgroundKey)
        chatBackgroundOverrides = if (saved == null) {
            chatBackgroundOverrides - relationship.relationshipId
        } else {
            chatBackgroundOverrides + (relationship.relationshipId to saved)
        }
    }

    private fun updateRelationshipsFromStore() {
        val previous = relationships
        val updated = relationshipStore.load()
        relationships = updated
        recordLatestHandshakeCompletion(previous, updated)
    }

    private fun recordLatestHandshakeCompletion(
        previous: List<Relationship>,
        updated: List<Relationship>,
    ) {
        val previousById = previous.associateBy { it.relationshipId }
        val completed = updated.firstOrNull { relationship ->
            relationship.state == RelationshipState.ACTIVE &&
                relationship.sourceInviteId != null &&
                relationship.relationshipId !in announcedHandshakeCompletionIds &&
                previousById[relationship.relationshipId]?.state != RelationshipState.ACTIVE
        } ?: return
        announcedHandshakeCompletionIds += completed.relationshipId
        latestHandshakeCompletion = completed
    }

    private fun Relationship.isSameRelationshipRecord(other: Relationship): Boolean =
        relationshipId == other.relationshipId ||
            (
                peerFingerprint == other.peerFingerprint &&
                    sourceInviteId != null &&
                    sourceInviteId == other.sourceInviteId
            )

    private fun Relationship.belongsToSameLocalPairingBundle(other: Relationship): Boolean {
        if (localIdentityPublicKey != other.localIdentityPublicKey) return false
        return relationshipId == other.relationshipId ||
            (
                sourceInviteId != null &&
                    sourceInviteId == other.sourceInviteId
            ) ||
            peerPublicKey == other.peerPublicKey ||
            peerFingerprint == other.peerFingerprint
    }
}
