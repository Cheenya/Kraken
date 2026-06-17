package com.disser.kraken.mesh

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.RealmRelayBlockReason
import com.disser.kraken.realm.RealmRelayPolicy
import com.disser.kraken.realm.RealmSnapshot

enum class RelaySimulationStatus {
    FORWARDED,
    RELAY_DISABLED,
    RELAY_POLICY_BLOCKED,
    DROPPED,
    SEND_FAILED,
}

data class RelaySimulationResult(
    val status: RelaySimulationStatus,
    val packet: KrakenPacket? = null,
    val rejectionReason: MeshRejectionReason? = null,
    val relayBlockReason: RealmRelayBlockReason? = null,
)

class SimulatedStoreAndForwardRelay(
    private val prototypeRelayEnabled: Boolean,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun relayInRealm(
        localIdentity: LocalIdentity,
        realmId: String,
        relayPeerPublicKey: String,
        realmSnapshot: RealmSnapshot?,
        packet: KrakenPacket,
        nextPeer: DiscoveredPeer,
        transport: PeerTransport,
        alreadySeen: Boolean = false,
    ): RelaySimulationResult {
        val decision = RealmRelayPolicy.canUseRelayPeer(
            localIdentity = localIdentity,
            realmId = realmId,
            relayPeerPublicKey = relayPeerPublicKey,
            realmSnapshot = realmSnapshot,
            nowEpochMillis = now(),
        )
        if (!decision.allowed) {
            return RelaySimulationResult(
                status = RelaySimulationStatus.RELAY_POLICY_BLOCKED,
                relayBlockReason = decision.blockReason,
            )
        }
        return relay(
            packet = packet,
            nextPeer = nextPeer,
            transport = transport,
            alreadySeen = alreadySeen,
        )
    }

    fun relay(
        packet: KrakenPacket,
        nextPeer: DiscoveredPeer,
        transport: PeerTransport,
        alreadySeen: Boolean = false,
    ): RelaySimulationResult {
        if (!prototypeRelayEnabled) {
            return RelaySimulationResult(RelaySimulationStatus.RELAY_DISABLED)
        }
        val frameSizeValid = runCatching { LanFrameCodec.encode(packet).size <= LanFrameCodec.MAX_FRAME_BYTES + 4 }
            .getOrDefault(false)
        if (!frameSizeValid) {
            return RelaySimulationResult(RelaySimulationStatus.DROPPED, rejectionReason = MeshRejectionReason.MALFORMED)
        }
        val validation = PacketValidator.validateForStorage(
            packet = packet,
            nowEpochMillis = now(),
            alreadySeen = alreadySeen,
            requireForwardableTtl = true,
        )
        if (!validation.accepted) {
            return RelaySimulationResult(RelaySimulationStatus.DROPPED, rejectionReason = validation.rejectionReason)
        }
        val relayedPacket = packet.copy(ttlHops = packet.ttlHops - 1)
        val send = transport.send(nextPeer, relayedPacket)
        return if (send.success) {
            RelaySimulationResult(RelaySimulationStatus.FORWARDED, packet = relayedPacket)
        } else {
            RelaySimulationResult(RelaySimulationStatus.SEND_FAILED, packet = relayedPacket)
        }
    }
}
