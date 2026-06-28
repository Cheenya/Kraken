package com.disser.kraken.mesh

import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class KrakenPacketType {
    MESSAGE,
    RECEIPT,
    PING,
    HANDSHAKE_RESPONSE,
    HANDSHAKE_CONFIRMATION,
}

@Serializable
enum class PacketPayloadType {
    LOCAL_MESSAGE_JSON,
    ENCRYPTED_MESSAGE_JSON,
    RECEIPT_JSON,
    PING_JSON,
    HANDSHAKE_RESPONSE_JSON,
    HANDSHAKE_CONFIRMATION_JSON,
}

@Serializable
enum class PacketStoreStatus {
    QUEUED,
    RECEIVED,
    SENT,
    ACKED,
    EXPIRED,
    REJECTED,
}

@Serializable
enum class MeshRejectionReason {
    UNKNOWN_PEER,
    PENDING_RELATIONSHIP,
    WRONG_RECIPIENT,
    BLOCKED_OR_UNLINKED,
    EXPIRED,
    DUPLICATE,
    MALFORMED,
    TTL_EXHAUSTED,
    REALM_MEMBERSHIP_BLOCKED,
    UNKNOWN_CRYPTO_PROFILE,
    CRYPTO_PROFILE_REJECTED,
    CRYPTO_PROFILE_MISMATCH,
}

@Serializable
data class KrakenPacket(
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("protocol_version")
    val protocolVersion: Int = 1,
    @SerialName("packet_type")
    val packetType: KrakenPacketType,
    @SerialName("sender_fingerprint")
    val senderFingerprint: String,
    @SerialName("recipient_fingerprint")
    val recipientFingerprint: String,
    @SerialName("relationship_id")
    val relationshipId: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("message_id")
    val messageId: String?,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("expires_at_epoch_millis")
    val expiresAtEpochMillis: Long,
    @SerialName("ttl_hops")
    val ttlHops: Int,
    @SerialName("payload_type")
    val payloadType: PacketPayloadType,
    @SerialName("payload_json")
    val payloadJson: String,
    @SerialName("crypto_profile_id")
    val cryptoProfileId: String? = KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID,
    @SerialName("session_profile_id")
    val sessionProfileId: String? = null,
    @SerialName("admission_decision_hash")
    val admissionDecisionHash: String? = KrakenCryptoProfileDefaults.STANDARD_ADMISSION_DECISION_HASH,
    @SerialName("profile_policy_version")
    val profilePolicyVersion: Int? = KrakenCryptoProfileDefaults.ADMISSION_POLICY_VERSION,
    @SerialName("proof_mode")
    val proofMode: String = LOCAL_PROOF_MODE,
) {
    companion object {
        const val LOCAL_PROOF_MODE = "local-admission-check-v1"
    }
}

@Serializable
data class StoredPacket(
    val packet: KrakenPacket,
    val status: PacketStoreStatus,
    @SerialName("stored_at_epoch_millis")
    val storedAtEpochMillis: Long,
    @SerialName("last_error")
    val lastError: MeshRejectionReason? = null,
    val attempts: Int = 0,
    @SerialName("next_attempt_at_epoch_millis")
    val nextAttemptAtEpochMillis: Long = 0,
    @SerialName("last_attempt_at_epoch_millis")
    val lastAttemptAtEpochMillis: Long? = null,
)

@Serializable
data class SeenPacketId(
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("seen_at_epoch_millis")
    val seenAtEpochMillis: Long,
)

@Serializable
data class PacketReceipt(
    @SerialName("receipt_id")
    val receiptId: String,
    @SerialName("packet_id")
    val packetId: String,
    @SerialName("message_id")
    val messageId: String?,
    @SerialName("sender_fingerprint")
    val senderFingerprint: String,
    @SerialName("recipient_fingerprint")
    val recipientFingerprint: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

data class PacketValidationResult(
    val accepted: Boolean,
    val rejectionReason: MeshRejectionReason? = null,
)
