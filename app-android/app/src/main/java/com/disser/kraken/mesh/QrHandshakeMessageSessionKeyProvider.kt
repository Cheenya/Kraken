package com.disser.kraken.mesh

import com.disser.kraken.crypto.DerivedKey
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.Relationship
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class QrHandshakeMessageSessionKeyProvider : MessageSessionKeyProvider {
    override fun keyFor(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        packet: KrakenPacket,
    ): DerivedKey? {
        val inviteSecret = relationship.sourceInviteId?.takeIf { it.isNotBlank() } ?: return null
        val orderedFingerprints = listOf(localIdentity.fingerprint, relationship.peerFingerprint).sorted()
        val orderedPublicKeys = listOf(localIdentity.publicKeyEncoded, relationship.peerPublicKey).sorted()
        val salt = listOf(
            "kraken-message-session-salt-v1",
            relationship.relationshipId,
            orderedFingerprints.joinToString(":"),
            orderedPublicKeys.joinToString(":"),
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8)
        val info = listOf(
            "kraken-message-session-key-v1",
            packet.cryptoProfileId.orEmpty(),
            packet.sessionProfileId.orEmpty(),
            packet.admissionDecisionHash.orEmpty(),
            packet.profilePolicyVersion?.toString().orEmpty(),
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8)
        return DerivedKey(hkdfSha256(inviteSecret.toByteArray(StandardCharsets.UTF_8), salt, info, KEY_BYTES))
    }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputSize: Int,
    ): ByteArray {
        val pseudoRandomKey = hmacSha256(salt, inputKeyMaterial)
        var previous = ByteArray(0)
        val output = ArrayList<Byte>(outputSize)
        var counter = 1
        while (output.size < outputSize) {
            previous = hmacSha256(
                pseudoRandomKey,
                previous + info + counter.toByte(),
            )
            previous.forEach { byte ->
                if (output.size < outputSize) output += byte
            }
            counter += 1
        }
        return output.toByteArray()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(HMAC_SHA256).run {
            init(SecretKeySpec(key, HMAC_SHA256))
            doFinal(data)
        }

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
        const val KEY_BYTES = 32
    }
}
