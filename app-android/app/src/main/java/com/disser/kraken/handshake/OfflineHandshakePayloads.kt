package com.disser.kraken.handshake

import com.disser.kraken.realm.MembershipCertificate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class HandshakeResponsePayload(
    val type: String = TYPE,
    val version: Int = VERSION,
    @SerialName("response_id")
    val responseId: String,
    @SerialName("invite_id")
    val inviteId: String,
    @SerialName("realm_id")
    val realmId: String? = null,
    @SerialName("requires_approval")
    val requiresApproval: Boolean = false,
    @SerialName("responder_fingerprint")
    val responderFingerprint: String,
    @SerialName("responder_display_name")
    val responderDisplayName: String,
    @SerialName("responder_public_key_encoded")
    val responderPublicKeyEncoded: String,
    @SerialName("inviter_fingerprint")
    val inviterFingerprint: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("relationship_hint")
    val relationshipHint: String?,
    @SerialName("crypto_profile_id")
    val cryptoProfileId: String? = null,
    @SerialName("crypto_profile_hash")
    val cryptoProfileHash: String? = null,
    @SerialName("admission_decision_hash")
    val admissionDecisionHash: String? = null,
    @SerialName("profile_policy_version")
    val profilePolicyVersion: Int? = null,
    @SerialName("native_backend_version")
    val nativeBackendVersion: String? = null,
    @SerialName("proof_placeholder")
    val proofPlaceholder: String = "prototype-offline-qr-handshake-not-production-crypto",
) {
    companion object {
        const val TYPE = "kraken.handshake.response.v1"
        const val VERSION = 1
    }
}

@Serializable
data class HandshakeConfirmationPayload(
    val type: String = TYPE,
    val version: Int = VERSION,
    @SerialName("confirmation_id")
    val confirmationId: String,
    @SerialName("response_id")
    val responseId: String,
    @SerialName("invite_id")
    val inviteId: String,
    @SerialName("realm_id")
    val realmId: String? = null,
    @SerialName("realm_name")
    val realmName: String? = null,
    @SerialName("membership_certificate")
    val membershipCertificate: MembershipCertificate? = null,
    @SerialName("inviter_fingerprint")
    val inviterFingerprint: String,
    @SerialName("responder_fingerprint")
    val responderFingerprint: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @SerialName("crypto_profile_id")
    val cryptoProfileId: String? = null,
    @SerialName("crypto_profile_hash")
    val cryptoProfileHash: String? = null,
    @SerialName("admission_decision_hash")
    val admissionDecisionHash: String? = null,
    @SerialName("profile_policy_version")
    val profilePolicyVersion: Int? = null,
    @SerialName("native_backend_version")
    val nativeBackendVersion: String? = null,
    @SerialName("proof_placeholder")
    val proofPlaceholder: String = "prototype-offline-qr-confirmation-not-production-crypto",
) {
    companion object {
        const val TYPE = "kraken.handshake.confirmation.v1"
        const val VERSION = 1
    }
}

enum class HandshakePayloadKind {
    INVITE,
    RESPONSE,
    CONFIRMATION,
    UNKNOWN,
    INVALID,
}

object HandshakePayloadCodec {
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodeResponse(payload: HandshakeResponsePayload): String =
        json.encodeToString(payload)

    fun encodeConfirmation(payload: HandshakeConfirmationPayload): String =
        json.encodeToString(payload)

    fun decodeResponse(rawJson: String): Result<HandshakeResponsePayload> =
        decode { json.decodeFromString<HandshakeResponsePayload>(rawJson) }

    fun decodeConfirmation(rawJson: String): Result<HandshakeConfirmationPayload> =
        decode { json.decodeFromString<HandshakeConfirmationPayload>(rawJson) }

    fun detectKind(rawJson: String): HandshakePayloadKind =
        runCatching {
            val type = json.parseToJsonElement(rawJson).jsonObject["type"]?.jsonPrimitive?.content
            when (type) {
                "one_time_invite" -> HandshakePayloadKind.INVITE
                HandshakeResponsePayload.TYPE -> HandshakePayloadKind.RESPONSE
                HandshakeConfirmationPayload.TYPE -> HandshakePayloadKind.CONFIRMATION
                null -> HandshakePayloadKind.UNKNOWN
                else -> HandshakePayloadKind.UNKNOWN
            }
        }.getOrDefault(HandshakePayloadKind.INVALID)

    private inline fun <T> decode(block: () -> T): Result<T> =
        runCatching(block).recoverCatching { error ->
            if (error is SerializationException || error is IllegalArgumentException) {
                throw IllegalArgumentException("Invalid offline handshake payload.")
            }
            throw error
        }
}
