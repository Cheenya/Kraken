package com.disser.kraken.crypto

data class PublicKeyMaterial(
    val encoded: String,
)

data class PrivateKeyReference(
    val reference: String,
)

data class SharedSecret(
    val bytes: ByteArray,
)

data class DerivedKey(
    val bytes: ByteArray,
)

data class Plaintext(
    val bytes: ByteArray,
)

data class Ciphertext(
    val bytes: ByteArray,
    val recipientPublicKey: String,
    val algorithm: String,
)

interface CryptoBox {
    fun encrypt(
        plaintext: Plaintext,
        recipientPublicKey: PublicKeyMaterial,
    ): Ciphertext

    fun decrypt(
        ciphertext: Ciphertext,
        privateKeyReference: PrivateKeyReference,
        localPublicKey: PublicKeyMaterial,
    ): Plaintext
}

interface KeyAgreementProvider {
    fun deriveSharedSecret(
        localPrivateKey: PrivateKeyReference,
        peerPublicKey: PublicKeyMaterial,
    ): SharedSecret
}

interface KdfProvider {
    fun deriveKey(
        sharedSecret: SharedSecret,
        context: ByteArray,
    ): DerivedKey
}

interface AeadProvider {
    fun seal(
        plaintext: Plaintext,
        key: DerivedKey,
        associatedData: ByteArray,
    ): Ciphertext

    fun open(
        ciphertext: Ciphertext,
        key: DerivedKey,
        associatedData: ByteArray,
    ): Plaintext
}

interface SecureRandomProvider {
    fun randomBytes(size: Int): ByteArray
}

object CandidateCryptoPrimitives {
    const val KEY_AGREEMENT = "X25519 or reviewed equivalent"
    const val KDF = "HKDF"
    const val AEAD = "ChaCha20-Poly1305 or AES-GCM"
    const val ADAMOVA_CONTEXT_BINDING = "Adamova admission decision bound into AEAD associated data"
    const val RANDOMNESS = "Platform secure randomness"
    const val WARNING = "Reviewed crypto library integration is required; Adamova policy binding is mandatory."
}
