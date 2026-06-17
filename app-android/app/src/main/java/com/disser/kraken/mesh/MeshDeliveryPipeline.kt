package com.disser.kraken.mesh

import com.disser.kraken.crypto.AdamovaBoundCryptoEnvelope
import com.disser.kraken.crypto.AeadProvider
import com.disser.kraken.crypto.Ciphertext
import com.disser.kraken.crypto.DerivedKey
import com.disser.kraken.crypto.JcaAesGcmAeadProvider
import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.Plaintext
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.handshake.HandshakePayloadCodec
import com.disser.kraken.handshake.HandshakeResponsePayload
import com.disser.kraken.handshake.OfflineHandshakeResult
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.IssuedInviteLifecyclePolicy
import com.disser.kraken.invite.IssuedInviteRecord
import com.disser.kraken.message.LocalMessage
import com.disser.kraken.message.MessageDirection
import com.disser.kraken.message.MessageService
import com.disser.kraken.message.MessageStatus
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.Base64
import java.util.UUID

@Serializable
data class MessagePacketPayload(
    @SerialName("message_id")
    val messageId: String,
    val body: String,
    @SerialName("sender_created_at_epoch_millis")
    val senderCreatedAtEpochMillis: Long? = null,
    @SerialName("reply_to_message_id")
    val replyToMessageId: String? = null,
    @SerialName("reply_to_body_preview")
    val replyToBodyPreview: String? = null,
    @SerialName("reply_to_sender_name")
    val replyToSenderName: String? = null,
)

@Serializable
data class ReceiptPacketPayload(
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("message_id")
    val messageId: String?,
)

@Serializable
data class EncryptedPacketPayload(
    val algorithm: String,
    @SerialName("ciphertext_base64url")
    val ciphertextBase64Url: String,
    @SerialName("recipient_public_key")
    val recipientPublicKey: String,
) {
    companion object {
        fun from(ciphertext: Ciphertext): EncryptedPacketPayload =
            EncryptedPacketPayload(
                algorithm = ciphertext.algorithm,
                ciphertextBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext.bytes),
                recipientPublicKey = ciphertext.recipientPublicKey,
            )

        fun toCiphertext(payload: EncryptedPacketPayload): Ciphertext =
            Ciphertext(
                bytes = Base64.getUrlDecoder().decode(payload.ciphertextBase64Url),
                recipientPublicKey = payload.recipientPublicKey,
                algorithm = payload.algorithm,
            )
    }
}

data class OutboxSendResult(
    val packet: KrakenPacket?,
    val updatedMessage: LocalMessage?,
    val rejectedReason: MeshRejectionReason?,
    val transportError: String? = null,
)

data class InboxProcessResult(
    val acceptedMessage: LocalMessage?,
    val receiptPacket: KrakenPacket?,
    val deliveredMessageId: String?,
    val deliveredPacketId: String?,
    val rejectedReason: MeshRejectionReason?,
    val updatedRelationship: Relationship? = null,
    val acceptedHandshakeResponse: HandshakeResponsePayload? = null,
    val handshakeConfirmationPayloadJson: String? = null,
)

interface MessageSessionKeyProvider {
    fun keyFor(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        packet: KrakenPacket,
    ): DerivedKey?
}

enum class MessagePayloadProtectionPolicy {
    LEGACY_DEBUG_PLAINTEXT_ALLOWED,
    ADAMOVA_ENCRYPTED_REQUIRED,
}

class AdamovaMessagePayloadProtector(
    private val localIdentity: LocalIdentity,
    private val sessionKeyProvider: MessageSessionKeyProvider,
    private val admissionGate: ProductCryptoAdmissionGate = ProductCryptoAdmissionGate(),
    private val envelope: AdamovaBoundCryptoEnvelope = AdamovaBoundCryptoEnvelope(admissionGate),
    private val aead: AeadProvider = JcaAesGcmAeadProvider(),
) {
    fun seal(
        packet: KrakenPacket,
        relationship: Relationship,
        plaintextPayloadJson: String,
    ): Result<String> = runCatching {
        val context = AdamovaPacketCryptoBinding.contextFor(packet, relationship, admissionGate).getOrThrow()
        val key = sessionKeyProvider.keyFor(localIdentity, relationship, packet)
            ?: throw IllegalArgumentException("Message session key is unavailable.")
        val ciphertext = envelope.seal(
            plaintext = Plaintext(plaintextPayloadJson.encodeToByteArray()),
            key = key,
            context = context,
            aead = aead,
        )
        InvitePayloadCodec.json.encodeToString(EncryptedPacketPayload.from(ciphertext))
    }

    fun open(
        packet: KrakenPacket,
        relationship: Relationship,
    ): Result<String> = runCatching {
        val encrypted = InvitePayloadCodec.json.decodeFromString<EncryptedPacketPayload>(packet.payloadJson)
        val context = AdamovaPacketCryptoBinding.contextFor(packet, relationship, admissionGate).getOrThrow()
        val key = sessionKeyProvider.keyFor(localIdentity, relationship, packet)
            ?: throw IllegalArgumentException("Message session key is unavailable.")
        val plaintext = envelope.open(
            ciphertext = EncryptedPacketPayload.toCiphertext(encrypted),
            key = key,
            context = context,
            aead = aead,
        )
        plaintext.bytes.decodeToString()
    }
}

object PacketPayloadValidator {
    fun validate(packet: KrakenPacket): PacketValidationResult =
        when (packet.packetType) {
            KrakenPacketType.MESSAGE -> validateMessage(packet)
            KrakenPacketType.RECEIPT -> validateReceipt(packet)
            KrakenPacketType.PING -> validatePing(packet)
            KrakenPacketType.HANDSHAKE_RESPONSE -> validateHandshakeResponse(packet)
            KrakenPacketType.HANDSHAKE_CONFIRMATION -> validateHandshakeConfirmation(packet)
        }

    private fun validateMessage(packet: KrakenPacket): PacketValidationResult {
        if (packet.messageId.isNullOrBlank()) {
            return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
        return when (packet.payloadType) {
            PacketPayloadType.LOCAL_MESSAGE_JSON -> {
                val payload = runCatching {
                    InvitePayloadCodec.json.decodeFromString<MessagePacketPayload>(packet.payloadJson)
                }.getOrElse {
                    return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
                }
                if (payload.messageId == packet.messageId && payload.body.isNotBlank()) {
                    PacketValidationResult(true)
                } else {
                    PacketValidationResult(false, MeshRejectionReason.MALFORMED)
                }
            }
            PacketPayloadType.ENCRYPTED_MESSAGE_JSON -> {
                val payload = runCatching {
                    InvitePayloadCodec.json.decodeFromString<EncryptedPacketPayload>(packet.payloadJson)
                }.getOrElse {
                    return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
                }
                if (payload.algorithm.isNotBlank() && payload.ciphertextBase64Url.isNotBlank()) {
                    PacketValidationResult(true)
                } else {
                    PacketValidationResult(false, MeshRejectionReason.MALFORMED)
                }
            }
            else -> PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
    }

    private fun validateReceipt(packet: KrakenPacket): PacketValidationResult {
        if (packet.payloadType != PacketPayloadType.RECEIPT_JSON) {
            return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
        val payload = runCatching {
            InvitePayloadCodec.json.decodeFromString<ReceiptPacketPayload>(packet.payloadJson)
        }.getOrElse {
            return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
        return if (payload.packetId.isNotBlank() && payload.messageId == packet.messageId) {
            PacketValidationResult(true)
        } else {
            PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
    }

    private fun validatePing(packet: KrakenPacket): PacketValidationResult =
        if (packet.payloadType == PacketPayloadType.PING_JSON && packet.messageId == null) {
            PacketValidationResult(true)
        } else {
            PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }

    private fun validateHandshakeResponse(packet: KrakenPacket): PacketValidationResult =
        if (
            packet.payloadType == PacketPayloadType.HANDSHAKE_RESPONSE_JSON &&
            packet.messageId == null &&
            HandshakePayloadCodec.decodeResponse(packet.payloadJson).isSuccess
        ) {
            PacketValidationResult(true)
        } else {
            PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }

    private fun validateHandshakeConfirmation(packet: KrakenPacket): PacketValidationResult =
        if (
            packet.payloadType == PacketPayloadType.HANDSHAKE_CONFIRMATION_JSON &&
            packet.messageId == null &&
            HandshakePayloadCodec.decodeConfirmation(packet.payloadJson).isSuccess
        ) {
            PacketValidationResult(true)
        } else {
            PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
}

class MeshOutboxProcessor(
    private val localIdentity: LocalIdentity,
    private val transport: PeerTransport,
    private val realmSnapshot: RealmSnapshot? = null,
    private val admissionGate: ProductCryptoAdmissionGate = ProductCryptoAdmissionGate(),
    private val messagePayloadProtector: AdamovaMessagePayloadProtector? = null,
    private val messagePayloadProtectionPolicy: MessagePayloadProtectionPolicy =
        MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun sendMessage(
        message: LocalMessage,
        relationship: Relationship,
        peer: DiscoveredPeer,
    ): OutboxSendResult {
        validateOutboundBeforePayload(relationship)?.let { reason ->
            val updatedMessage = when (reason) {
                MeshRejectionReason.CRYPTO_PROFILE_REJECTED,
                MeshRejectionReason.UNKNOWN_CRYPTO_PROFILE,
                MeshRejectionReason.CRYPTO_PROFILE_MISMATCH,
                -> MessageService.updateStatus(message, MessageStatus.FAILED, now())
                else -> null
            }
            return OutboxSendResult(null, updatedMessage, reason)
        }
        val packet = runCatching { createMessagePacket(message, relationship) }.getOrElse { error ->
            return OutboxSendResult(
                packet = null,
                updatedMessage = MessageService.updateStatus(message, MessageStatus.FAILED, now()),
                rejectedReason = MeshRejectionReason.CRYPTO_PROFILE_REJECTED,
                transportError = error.message,
            )
        }
        val validation = MeshTrustGate.validateOutbound(localIdentity, relationship, packet, realmSnapshot, now())
        if (!validation.accepted) {
            return OutboxSendResult(null, null, validation.rejectionReason)
        }
        validatePacketAdmission(packet, relationship, admissionGate)?.let { reason ->
            return OutboxSendResult(packet, MessageService.updateStatus(message, MessageStatus.FAILED, now()), reason)
        }
        val transportResult = transport.send(peer, packet)
        if (!transportResult.success) {
            return OutboxSendResult(
                packet,
                MessageService.updateStatus(message, MessageStatus.READY_FOR_TRANSPORT, now()),
                transportResult.rejectionReason(),
                transportResult.error,
            )
        }
        return OutboxSendResult(packet, MessageService.updateStatus(message, MessageStatus.SENT_TO_TRANSPORT, now()), null)
    }

    private fun validateOutboundBeforePayload(relationship: Relationship): MeshRejectionReason? {
        if (relationship.localIdentityPublicKey != localIdentity.publicKeyEncoded) {
            return MeshRejectionReason.UNKNOWN_PEER
        }
        if (!RelationshipService.canSendMessage(relationship)) {
            return MeshRejectionReason.PENDING_RELATIONSHIP
        }
        val realmDecision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = localIdentity,
            relationship = relationship,
            realmSnapshot = realmSnapshot,
            nowEpochMillis = now(),
        )
        if (!realmDecision.allowed) {
            return MeshRejectionReason.REALM_MEMBERSHIP_BLOCKED
        }
        val profileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val admissionHash = relationship.admissionDecisionHash
            ?: admissionGate.evaluate(profileId)?.decisionHash
            ?: return MeshRejectionReason.UNKNOWN_CRYPTO_PROFILE
        val policyVersion = relationship.profilePolicyVersion ?: KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION
        return if (admissionGate.packetAdmissionAccepted(profileId, admissionHash, policyVersion)) {
            null
        } else {
            MeshRejectionReason.CRYPTO_PROFILE_REJECTED
        }
    }

    fun createMessagePacket(
        message: LocalMessage,
        relationship: Relationship,
        ttlHops: Int = 4,
    ): KrakenPacket {
        val profileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val admissionResult = admissionGate.evaluate(profileId)
        val plaintextPayloadJson = InvitePayloadCodec.json.encodeToString(
            MessagePacketPayload(
                messageId = message.messageId,
                body = message.body,
                senderCreatedAtEpochMillis = message.createdAtEpochMillis,
                replyToMessageId = message.replyToMessageId,
                replyToBodyPreview = message.replyToBodyPreview,
                replyToSenderName = message.replyToSenderName,
            ),
        )
        val basePacket = KrakenPacket(
            packetId = "packet-${UUID.randomUUID()}",
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = localIdentity.fingerprint,
            recipientFingerprint = relationship.peerFingerprint,
            relationshipId = relationship.relationshipId,
            conversationId = message.conversationId,
            messageId = message.messageId,
            createdAtEpochMillis = now(),
            expiresAtEpochMillis = now() + DEFAULT_PACKET_TTL_MILLIS,
            ttlHops = ttlHops,
            payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
            payloadJson = plaintextPayloadJson,
            cryptoProfileId = profileId,
            sessionProfileId = sessionProfileIdFor(relationship, profileId),
            admissionDecisionHash = relationship.admissionDecisionHash ?: admissionResult?.decisionHash,
            profilePolicyVersion = relationship.profilePolicyVersion ?: admissionResult?.policyVersion,
        )
        val protector = messagePayloadProtector
        if (protector == null) {
            if (messagePayloadProtectionPolicy == MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED) {
                throw IllegalStateException("Adamova-bound message payload protector is required.")
            }
            return basePacket
        }
        val encryptedPayloadJson = protector.seal(basePacket, relationship, plaintextPayloadJson).getOrThrow()
        return basePacket.copy(
            payloadType = PacketPayloadType.ENCRYPTED_MESSAGE_JSON,
            payloadJson = encryptedPayloadJson,
        )
    }

    fun createHandshakeConfirmationPacket(
        relationship: Relationship,
        confirmationPayloadJson: String,
        ttlHops: Int = 4,
    ): KrakenPacket {
        val profileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val admissionResult = admissionGate.evaluate(profileId)
        return KrakenPacket(
            packetId = "packet-handshake-confirmation-${UUID.randomUUID()}",
            packetType = KrakenPacketType.HANDSHAKE_CONFIRMATION,
            senderFingerprint = localIdentity.fingerprint,
            recipientFingerprint = relationship.peerFingerprint,
            relationshipId = relationship.relationshipId,
            conversationId = "handshake-${relationship.relationshipId}",
            messageId = null,
            createdAtEpochMillis = now(),
            expiresAtEpochMillis = now() + DEFAULT_PACKET_TTL_MILLIS,
            ttlHops = ttlHops,
            payloadType = PacketPayloadType.HANDSHAKE_CONFIRMATION_JSON,
            payloadJson = confirmationPayloadJson,
            cryptoProfileId = profileId,
            sessionProfileId = sessionProfileIdFor(relationship, profileId),
            admissionDecisionHash = relationship.admissionDecisionHash ?: admissionResult?.decisionHash,
            profilePolicyVersion = relationship.profilePolicyVersion ?: admissionResult?.policyVersion,
        )
    }

    fun createHandshakeResponsePacket(
        relationship: Relationship,
        responsePayloadJson: String,
        ttlHops: Int = 4,
    ): KrakenPacket {
        val profileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val admissionResult = admissionGate.evaluate(profileId)
        return KrakenPacket(
            packetId = "packet-handshake-response-${UUID.randomUUID()}",
            packetType = KrakenPacketType.HANDSHAKE_RESPONSE,
            senderFingerprint = localIdentity.fingerprint,
            recipientFingerprint = relationship.peerFingerprint,
            relationshipId = relationship.relationshipId,
            conversationId = "handshake-${relationship.relationshipId}",
            messageId = null,
            createdAtEpochMillis = now(),
            expiresAtEpochMillis = now() + DEFAULT_PACKET_TTL_MILLIS,
            ttlHops = ttlHops,
            payloadType = PacketPayloadType.HANDSHAKE_RESPONSE_JSON,
            payloadJson = responsePayloadJson,
            cryptoProfileId = profileId,
            sessionProfileId = sessionProfileIdFor(relationship, profileId),
            admissionDecisionHash = relationship.admissionDecisionHash ?: admissionResult?.decisionHash,
            profilePolicyVersion = relationship.profilePolicyVersion ?: admissionResult?.policyVersion,
        )
    }
}

class MeshInboxProcessor(
    private val localIdentity: LocalIdentity,
    private val relationships: List<Relationship>,
    private val issuedInvites: List<IssuedInviteRecord> = emptyList(),
    private val realmSnapshot: RealmSnapshot? = null,
    private val admissionGate: ProductCryptoAdmissionGate = ProductCryptoAdmissionGate(),
    private val messagePayloadProtector: AdamovaMessagePayloadProtector? = null,
    private val messagePayloadProtectionPolicy: MessagePayloadProtectionPolicy =
        MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun process(
        packet: KrakenPacket,
        alreadySeen: Boolean = false,
    ): InboxProcessResult {
        if (packet.packetType == KrakenPacketType.HANDSHAKE_RESPONSE) {
            return processHandshakeResponse(packet, alreadySeen)
        }
        val (validation, relationship) = MeshTrustGate.validateInbound(localIdentity, relationships, packet, alreadySeen, realmSnapshot, now())
        if (!validation.accepted || relationship == null) {
            return InboxProcessResult(null, null, null, null, validation.rejectionReason)
        }
        validatePacketAdmission(packet, relationship, admissionGate)?.let { reason ->
            return InboxProcessResult(null, null, null, null, reason)
        }
        val payloadValidation = PacketPayloadValidator.validate(packet)
        if (!payloadValidation.accepted) {
            return InboxProcessResult(null, null, null, null, payloadValidation.rejectionReason)
        }
        return when (packet.packetType) {
            KrakenPacketType.MESSAGE -> {
                val messagePayloadJson = if (packet.payloadType == PacketPayloadType.ENCRYPTED_MESSAGE_JSON) {
                    val protector = messagePayloadProtector
                        ?: return InboxProcessResult(null, null, null, null, MeshRejectionReason.UNKNOWN_CRYPTO_PROFILE)
                    protector.open(packet, relationship).getOrElse {
                        return InboxProcessResult(null, null, null, null, MeshRejectionReason.CRYPTO_PROFILE_MISMATCH)
                    }
                } else {
                    if (messagePayloadProtectionPolicy == MessagePayloadProtectionPolicy.ADAMOVA_ENCRYPTED_REQUIRED) {
                        return InboxProcessResult(null, null, null, null, MeshRejectionReason.CRYPTO_PROFILE_REJECTED)
                    }
                    packet.payloadJson
                }
                val payload = runCatching {
                    InvitePayloadCodec.json.decodeFromString<MessagePacketPayload>(messagePayloadJson)
                }.getOrElse {
                    return InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
                }
                val message = MessageService.createIncomingMessage(
                    relationship = relationship,
                    messageId = payload.messageId,
                    body = payload.body,
                    replyToMessageId = payload.replyToMessageId,
                    replyToBodyPreview = payload.replyToBodyPreview,
                    replyToSenderName = payload.replyToSenderName,
                    nowEpochMillis = payload.senderCreatedAtEpochMillis ?: packet.createdAtEpochMillis,
                )
                val receipt = createReceiptPacket(packet, relationship)
                InboxProcessResult(message, receipt, null, null, null)
            }
            KrakenPacketType.RECEIPT -> {
                val payload = runCatching {
                    InvitePayloadCodec.json.decodeFromString<ReceiptPacketPayload>(packet.payloadJson)
                }.getOrElse {
                    return InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
                }
                InboxProcessResult(null, null, payload.messageId, payload.packetId, null)
            }
            KrakenPacketType.PING -> InboxProcessResult(null, null, null, null, null)
            KrakenPacketType.HANDSHAKE_RESPONSE -> processHandshakeResponse(packet, alreadySeen)
            KrakenPacketType.HANDSHAKE_CONFIRMATION -> processHandshakeConfirmation(packet)
        }
    }

    fun createReceiptPacket(
        sourcePacket: KrakenPacket,
        relationship: Relationship,
        ttlHops: Int = 4,
    ): KrakenPacket =
        KrakenPacket(
            packetId = "packet-${UUID.randomUUID()}",
            packetType = KrakenPacketType.RECEIPT,
            senderFingerprint = localIdentity.fingerprint,
            recipientFingerprint = sourcePacket.senderFingerprint,
            relationshipId = relationship.relationshipId,
            conversationId = sourcePacket.conversationId,
            messageId = sourcePacket.messageId,
            createdAtEpochMillis = now(),
            expiresAtEpochMillis = now() + DEFAULT_PACKET_TTL_MILLIS,
            ttlHops = ttlHops,
            payloadType = PacketPayloadType.RECEIPT_JSON,
            payloadJson = InvitePayloadCodec.json.encodeToString(
                ReceiptPacketPayload(
                    packetId = sourcePacket.packetId,
                    messageId = sourcePacket.messageId,
                ),
            ),
            cryptoProfileId = sourcePacket.cryptoProfileId,
            sessionProfileId = sourcePacket.sessionProfileId,
            admissionDecisionHash = sourcePacket.admissionDecisionHash,
            profilePolicyVersion = sourcePacket.profilePolicyVersion,
        )

    private fun processHandshakeConfirmation(packet: KrakenPacket): InboxProcessResult {
        val relationship = relationships.firstOrNull {
            it.relationshipId == packet.relationshipId &&
                it.peerFingerprint == packet.senderFingerprint &&
                it.localIdentityPublicKey == localIdentity.publicKeyEncoded
        } ?: return InboxProcessResult(null, null, null, null, MeshRejectionReason.UNKNOWN_PEER)
        if (relationship.state != RelationshipState.PENDING_HANDSHAKE) {
            return InboxProcessResult(null, null, null, null, MeshRejectionReason.PENDING_RELATIONSHIP)
        }
        val payload = HandshakePayloadCodec.decodeConfirmation(packet.payloadJson).getOrElse {
            return InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
        }
        if (relationship.sourceInviteId != payload.inviteId) {
            return InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
        }
        return when (
            val result = OfflineHandshakeService().processConfirmationPayload(
                localIdentity = localIdentity,
                relationships = relationships,
                payload = payload,
                nowEpochMillis = now(),
            )
        ) {
            is OfflineHandshakeResult.ConfirmationAccepted ->
                InboxProcessResult(null, null, null, null, null, result.relationship)
            is OfflineHandshakeResult.Error ->
                InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
            is OfflineHandshakeResult.ResponseAccepted ->
                InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
        }
    }

    private fun processHandshakeResponse(
        packet: KrakenPacket,
        alreadySeen: Boolean = false,
    ): InboxProcessResult {
        val base = PacketValidator.validateForStorage(packet, now(), alreadySeen)
        if (!base.accepted) return InboxProcessResult(null, null, null, null, base.rejectionReason)
        if (packet.recipientFingerprint != localIdentity.fingerprint) {
            return InboxProcessResult(null, null, null, null, MeshRejectionReason.WRONG_RECIPIENT)
        }
        val payloadValidation = PacketPayloadValidator.validate(packet)
        if (!payloadValidation.accepted) {
            return InboxProcessResult(null, null, null, null, payloadValidation.rejectionReason)
        }
        val payload = HandshakePayloadCodec.decodeResponse(packet.payloadJson).getOrElse {
            return InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
        }
        val expectedRelationshipId = RelationshipService.offlineHandshakeRelationshipId(
            inviteId = payload.inviteId,
            inviterFingerprint = localIdentity.fingerprint,
            responderFingerprint = payload.responderFingerprint,
        )
        if (
            packet.relationshipId != expectedRelationshipId ||
            packet.senderFingerprint != payload.responderFingerprint ||
            packet.recipientFingerprint != payload.inviterFingerprint
        ) {
            return InboxProcessResult(null, null, null, null, MeshRejectionReason.WRONG_RECIPIENT)
        }
        val lifecycle = IssuedInviteLifecyclePolicy.knownLifecycleForResponse(
            payload = payload,
            issuedInvites = issuedInvites,
            localFingerprint = localIdentity.fingerprint,
        ).getOrElse {
            return InboxProcessResult(null, null, null, null, MeshRejectionReason.UNKNOWN_PEER)
        }
        return when (
            val result = OfflineHandshakeService(admissionGate).processResponsePayload(
                localIdentity = localIdentity,
                relationships = relationships,
                payload = payload,
                knownInviteLifecycle = lifecycle,
                nowEpochMillis = now(),
            )
        ) {
            is OfflineHandshakeResult.ConfirmationAccepted ->
                InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
            is OfflineHandshakeResult.Error ->
                InboxProcessResult(null, null, null, null, MeshRejectionReason.MALFORMED)
            is OfflineHandshakeResult.ResponseAccepted ->
                InboxProcessResult(
                    acceptedMessage = null,
                    receiptPacket = null,
                    deliveredMessageId = null,
                    deliveredPacketId = null,
                    rejectedReason = null,
                    updatedRelationship = result.relationship,
                    acceptedHandshakeResponse = payload,
                    handshakeConfirmationPayloadJson = HandshakePayloadCodec.encodeConfirmation(result.confirmationPayload),
                )
        }
    }
}

const val DEFAULT_PACKET_TTL_MILLIS: Long = 24L * 60L * 60L * 1_000L

private fun sessionProfileIdFor(relationship: Relationship, profileId: String): String =
    "session-${relationship.relationshipId}-$profileId"

fun validatePacketAdmission(
    packet: KrakenPacket,
    relationship: Relationship,
    admissionGate: ProductCryptoAdmissionGate,
): MeshRejectionReason? {
    val expectedProfileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
    if (packet.cryptoProfileId != expectedProfileId) {
        return MeshRejectionReason.CRYPTO_PROFILE_MISMATCH
    }
    val expectedSessionProfileId = sessionProfileIdFor(relationship, expectedProfileId)
    if (packet.sessionProfileId != expectedSessionProfileId) {
        return MeshRejectionReason.CRYPTO_PROFILE_MISMATCH
    }
    val expectedAdmissionHash = relationship.admissionDecisionHash
        ?: admissionGate.evaluate(expectedProfileId)?.decisionHash
        ?: return MeshRejectionReason.UNKNOWN_CRYPTO_PROFILE
    val expectedPolicyVersion = relationship.profilePolicyVersion ?: KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION
    if (packet.admissionDecisionHash != expectedAdmissionHash || packet.profilePolicyVersion != expectedPolicyVersion) {
        return MeshRejectionReason.CRYPTO_PROFILE_MISMATCH
    }
    return if (admissionGate.packetAdmissionAccepted(
            cryptoProfileId = expectedProfileId,
            admissionDecisionHash = expectedAdmissionHash,
            profilePolicyVersion = expectedPolicyVersion,
        )
    ) {
        null
    } else {
        MeshRejectionReason.CRYPTO_PROFILE_REJECTED
    }
}
