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

object LocalPacketCrypto : PacketSigner, PacketVerifier, PacketEncryptor {
    const val ALGORITHM = "local-packet-check-v1"
    const val WARNING = "Локальный контур подписи и шифрования используется только в debug-сборках."

    fun isAllowedForBuildType(buildType: String): Boolean =
        buildType.lowercase() !in setOf("release", "prod", "public")

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
            payload = "local-payload:${packet.packetId}:${recipientPublicKey.encoded}",
        )
}
