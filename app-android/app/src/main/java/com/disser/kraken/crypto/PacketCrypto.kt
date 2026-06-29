package com.disser.kraken.crypto

import com.disser.kraken.mesh.KrakenPacket

data class PacketSignature(
    val algorithm: String,
    val value: String,
)

data class PacketCiphertext(
    val algorithm: String,
    val payload: String,
)

interface PacketSigner {
    fun sign(packet: KrakenPacket, privateKeyReference: PrivateKeyReference): PacketSignature
}

interface PacketVerifier {
    fun verify(packet: KrakenPacket, signature: PacketSignature, senderPublicKey: PublicKeyMaterial): Boolean
}

interface PacketEncryptor {
    fun encrypt(packet: KrakenPacket, recipientPublicKey: PublicKeyMaterial): PacketCiphertext
}

interface PacketDecryptor {
    fun decrypt(ciphertext: PacketCiphertext, privateKeyReference: PrivateKeyReference): KrakenPacket
}

object DebugPlaintextPacketCrypto : PacketSigner, PacketVerifier, PacketEncryptor {
    const val ALGORITHM = "debug-plaintext-packet-compat"
    const val WARNING = "Debug packet compatibility path: plaintext compatibility signature/envelope marker."

    fun isAllowedForBuildType(buildType: String): Boolean =
        buildType.lowercase() !in setOf("release", "prod", "production")

    override fun sign(packet: KrakenPacket, privateKeyReference: PrivateKeyReference): PacketSignature =
        PacketSignature(
            algorithm = ALGORITHM,
            value = "unsigned:${packet.packetId}:${privateKeyReference.reference}",
        )

    override fun verify(
        packet: KrakenPacket,
        signature: PacketSignature,
        senderPublicKey: PublicKeyMaterial,
    ): Boolean =
        signature.algorithm == ALGORITHM &&
            signature.value.startsWith("unsigned:${packet.packetId}:") &&
            senderPublicKey.encoded.isNotBlank()

    override fun encrypt(packet: KrakenPacket, recipientPublicKey: PublicKeyMaterial): PacketCiphertext =
        PacketCiphertext(
            algorithm = ALGORITHM,
            payload = "debug-plaintext:${packet.packetId}:${recipientPublicKey.encoded}",
        )
}
