package com.disser.kraken.crypto

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AdamovaCryptoPolicyContext(
    val profileId: String,
    val profileHash: String,
    val admissionDecisionHash: String,
    val profilePolicyVersion: Int,
    val nativeBackendVersion: String,
    val sessionProfileId: String,
    val relationshipId: String,
)

class AdamovaCryptoPolicyException(message: String) : IllegalArgumentException(message)

class AdamovaBoundCryptoEnvelope(
    private val admissionGate: ProductCryptoAdmissionGate = ProductCryptoAdmissionGate(),
) {
    fun seal(
        plaintext: Plaintext,
        key: DerivedKey,
        context: AdamovaCryptoPolicyContext,
        aead: AeadProvider,
    ): Ciphertext {
        requireAcceptedContext(context)
        return aead.seal(
            plaintext = plaintext,
            key = key,
            associatedData = associatedData(context),
        )
    }

    fun open(
        ciphertext: Ciphertext,
        key: DerivedKey,
        context: AdamovaCryptoPolicyContext,
        aead: AeadProvider,
    ): Plaintext {
        requireAcceptedContext(context)
        return aead.open(
            ciphertext = ciphertext,
            key = key,
            associatedData = associatedData(context),
        )
    }

    fun associatedData(context: AdamovaCryptoPolicyContext): ByteArray =
        listOf(
            "kraken-message-crypto-v1",
            "adamova_profile_id=${context.profileId}",
            "adamova_profile_hash=${context.profileHash}",
            "adamova_admission_decision_hash=${context.admissionDecisionHash}",
            "adamova_policy_version=${context.profilePolicyVersion}",
            "adamova_native_backend=${context.nativeBackendVersion}",
            "session_profile_id=${context.sessionProfileId}",
            "relationship_id=${context.relationshipId}",
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8)

    private fun requireAcceptedContext(context: AdamovaCryptoPolicyContext): ProductCryptoAdmissionResult {
        val result = admissionGate.evaluate(context.profileId)
            ?: throw AdamovaCryptoPolicyException("Криптографический профиль неизвестен.")
        if (!result.acceptedForPacketPolicy) {
            throw AdamovaCryptoPolicyException("Криптографический профиль отклонён политикой Adamova: ${result.decision}.")
        }
        if (result.profileHash != context.profileHash) {
            throw AdamovaCryptoPolicyException("Hash криптографического профиля не совпадает с локальной политикой.")
        }
        if (result.decisionHash != context.admissionDecisionHash) {
            throw AdamovaCryptoPolicyException("Решение допуска Adamova не совпадает с локальной проверкой.")
        }
        if (result.policyVersion != context.profilePolicyVersion) {
            throw AdamovaCryptoPolicyException("Версия политики Adamova не совпадает.")
        }
        if (context.sessionProfileId.isBlank() || context.relationshipId.isBlank()) {
            throw AdamovaCryptoPolicyException("Контекст криптографической сессии неполный.")
        }
        return result
    }
}

class JcaAesGcmAeadProvider(
    private val secureRandom: SecureRandom = SecureRandom(),
) : AeadProvider {
    override fun seal(
        plaintext: Plaintext,
        key: DerivedKey,
        associatedData: ByteArray,
    ): Ciphertext {
        val nonce = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(key), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        val sealed = cipher.doFinal(plaintext.bytes)
        return Ciphertext(
            bytes = nonce + sealed,
            recipientPublicKey = "bound-by-adamova-context",
            algorithm = ALGORITHM,
        )
    }

    override fun open(
        ciphertext: Ciphertext,
        key: DerivedKey,
        associatedData: ByteArray,
    ): Plaintext {
        require(ciphertext.algorithm == ALGORITHM) { "Unsupported AEAD algorithm: ${ciphertext.algorithm}" }
        require(ciphertext.bytes.size > NONCE_BYTES) { "Ciphertext is too short." }
        val nonce = ciphertext.bytes.copyOfRange(0, NONCE_BYTES)
        val sealed = ciphertext.bytes.copyOfRange(NONCE_BYTES, ciphertext.bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keySpec(key), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return Plaintext(cipher.doFinal(sealed))
    }

    private fun keySpec(key: DerivedKey): SecretKeySpec {
        require(key.bytes.size in setOf(16, 24, 32)) {
            "AES-GCM requires 128, 192 or 256-bit key material."
        }
        return SecretKeySpec(key.bytes, "AES")
    }

    companion object {
        const val ALGORITHM = "AES-GCM/NoPadding;adamova-context-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
    }
}
